package com.Huanghun;

import android.content.Context;
import android.content.pm.PackageInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.lang.ref.WeakReference;

/** Checks for client updates whenever the main app returns to the foreground. */
public final class HuanghunUpdatePrompt {
    private static final int MAX_DISPLAY_RETRIES = 10;
    private static final long DISPLAY_RETRY_DELAY_MS = 500L;

    private static boolean checkInFlight;
    private static boolean foregroundPendingOnError;
    private static boolean displayRetryScheduled;
    private static int displayRetryCount;
    private static WeakReference<LaunchActivity> resumedActivity = new WeakReference<>(null);
    private static PendingUpdate pendingUpdate;

    private HuanghunUpdatePrompt() {
    }

    public static void checkOnAppForeground(LaunchActivity activity) {
        if (activity == null || !activity.isHuanghunUpdatePromptActive()) return;

        boolean startCheck;
        synchronized (HuanghunUpdatePrompt.class) {
            resumedActivity = new WeakReference<>(activity);
            displayRetryCount = 0;
            if (checkInFlight) {
                foregroundPendingOnError = true;
                startCheck = false;
            } else {
                foregroundPendingOnError = false;
                checkInFlight = true;
                startCheck = true;
            }
        }
        tryShowPendingUpdate(activity);
        if (!startCheck) return;

        BuiltInProxyClient.fetchConfig(new BuiltInProxyClient.ResultCallback<BuiltInProxyClient.RemoteConfig>() {
            @Override
            public void onSuccess(BuiltInProxyClient.RemoteConfig config) {
                AndroidUtilities.runOnUIThread(() -> handleConfig(config));
            }

            @Override
            public void onError(String message) {
                AndroidUtilities.runOnUIThread(HuanghunUpdatePrompt::handleRequestError);
                // Startup checks are intentionally silent while offline; manual checking remains available.
            }
        });
    }

    public static void onAppPaused(LaunchActivity activity) {
        synchronized (HuanghunUpdatePrompt.class) {
            if (resumedActivity.get() == activity) {
                resumedActivity = new WeakReference<>(null);
            }
        }
    }

    private static void handleConfig(BuiltInProxyClient.RemoteConfig config) {
        finishRequest();
        Context context = ApplicationLoader.applicationContext;
        if (context == null || config == null) return;

        saveRemoteConfig(context, config);
        String latest = config.latestVersion == null ? "" : config.latestVersion.trim();
        String current = currentVersionName(context);
        PendingUpdate pending = null;
        if (!latest.isEmpty() && !"未知".equals(current)
                && HuanghunServiceActivity.compareVersions(latest, current) > 0) {
            pending = new PendingUpdate(config, latest);
        }

        LaunchActivity activity;
        synchronized (HuanghunUpdatePrompt.class) {
            pendingUpdate = pending;
            displayRetryCount = 0;
            activity = resumedActivity.get();
        }
        if (activity != null) {
            tryShowPendingUpdate(activity);
        }
    }

    private static void finishRequest() {
        synchronized (HuanghunUpdatePrompt.class) {
            checkInFlight = false;
            foregroundPendingOnError = false;
        }
    }

    private static void handleRequestError() {
        LaunchActivity retryActivity = null;
        synchronized (HuanghunUpdatePrompt.class) {
            checkInFlight = false;
            if (foregroundPendingOnError) {
                retryActivity = resumedActivity.get();
            }
            foregroundPendingOnError = false;
        }
        if (retryActivity != null && retryActivity.isHuanghunUpdatePromptActive()) {
            checkOnAppForeground(retryActivity);
        }
    }

    private static void tryShowPendingUpdate(LaunchActivity activity) {
        if (activity == null || !activity.isHuanghunUpdatePromptActive()) return;

        PendingUpdate pending;
        synchronized (HuanghunUpdatePrompt.class) {
            pending = pendingUpdate;
        }
        if (pending == null) return;

        String current = currentVersionName(activity);
        if ("未知".equals(current) || HuanghunServiceActivity.compareVersions(pending.latest, current) <= 0) {
            synchronized (HuanghunUpdatePrompt.class) {
                if (pendingUpdate == pending) pendingUpdate = null;
                displayRetryCount = 0;
            }
            return;
        }

        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (!BulletinFactory.canShowBulletin(fragment)) {
            scheduleDisplayRetry();
            return;
        }

        synchronized (HuanghunUpdatePrompt.class) {
            if (pendingUpdate != pending) return;
            pendingUpdate = null;
            displayRetryCount = 0;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(
                R.raw.ic_download,
                "发现黄昏客户端新版本",
                "最新版本：" + pending.latest,
                "更新黄昏客户端",
                () -> showUpdateDialog(activity, pending.config, current, pending.latest)
        ).show();
    }

    private static void scheduleDisplayRetry() {
        synchronized (HuanghunUpdatePrompt.class) {
            if (displayRetryScheduled || displayRetryCount >= MAX_DISPLAY_RETRIES) return;
            displayRetryScheduled = true;
            displayRetryCount++;
        }
        AndroidUtilities.runOnUIThread(() -> {
            LaunchActivity activity;
            synchronized (HuanghunUpdatePrompt.class) {
                displayRetryScheduled = false;
                activity = resumedActivity.get();
            }
            tryShowPendingUpdate(activity);
        }, DISPLAY_RETRY_DELAY_MS);
    }

    private static String currentVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (info.versionName != null && !info.versionName.trim().isEmpty()) return info.versionName.trim();
        } catch (Exception ignore) {
        }
        return "未知";
    }

    private static void saveRemoteConfig(Context context, BuiltInProxyClient.RemoteConfig config) {
        context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).edit()
                .putInt("huanghun_config_revision", config.revision)
                .putString("huanghun_latest_version", config.latestVersion)
                .putString("huanghun_update_url", config.updateUrl)
                .putBoolean("huanghun_force_update", config.forceUpdate)
                .putString("huanghun_announcement", config.announcement)
                .putInt("huanghun_proxy_count", config.proxyCount)
                .putInt("huanghun_proxy_revision", config.proxyRevision)
                .apply();
    }

    private static void showUpdateDialog(LaunchActivity activity, BuiltInProxyClient.RemoteConfig config, String current, String latest) {
        if (activity == null || !activity.isHuanghunUpdatePromptActive()) return;

        boolean hasUpdateLink = HuanghunServiceActivity.isValidHttpsUpdateUrl(config.updateUrl);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("发现黄昏客户端新版本")
                .setMessage("当前版本：" + current + "\n最新版本：" + latest + "\n\n"
                        + (hasUpdateLink
                        ? "是否打开管理员配置的 HTTPS 更新链接？"
                        : "管理员尚未配置有效的 HTTPS 更新链接，请稍后再试或联系管理员。"));

        if (hasUpdateLink) {
            if (!config.forceUpdate) {
                builder.setNegativeButton(org.telegram.messenger.LocaleController.getString(R.string.Cancel), null);
            }
            builder.setPositiveButton("打开更新链接", (dialog, which) ->
                    Browser.openUrl(activity, config.updateUrl.trim()));
            AlertDialog dialog = builder.create();
            dialog.setCancelable(!config.forceUpdate);
            dialog.setCanceledOnTouchOutside(!config.forceUpdate);
            dialog.show();
        } else {
            builder.setPositiveButton("知道了", null).show();
        }
    }

    private static final class PendingUpdate {
        final BuiltInProxyClient.RemoteConfig config;
        final String latest;

        PendingUpdate(BuiltInProxyClient.RemoteConfig config, String latest) {
            this.config = config;
            this.latest = latest;
        }
    }
}
