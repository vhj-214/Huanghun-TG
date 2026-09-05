package com.Huanghun;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;

/**
 * Applies Huanghun's preferred Telegram theme once, after the first account login on a fresh install.
 * The marker is permanent so a user's later theme changes are never overridden.
 */
public final class HuanghunDefaultTheme implements NotificationCenter.NotificationCenterDelegate {
    private static final String THEME_SLUG = "wechatv8";
    private static final String PREFS_NAME = "huanghun_bootstrap";
    private static final String KEY_APPLIED = "default_telegram_theme_v2_applied";

    private static HuanghunDefaultTheme activeLoader;

    private final int account;
    private String themeFileName;
    private TLRPC.TL_theme remoteTheme;

    private HuanghunDefaultTheme(int account) {
        this.account = account;
    }

    public static void applyAfterFirstLoginIfNeeded(int account) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || !UserConfig.getInstance(account).isClientActivated() || !isFreshInstall(context)) {
            return;
        }
        SharedPreferences bootstrap = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (bootstrap.getBoolean(KEY_APPLIED, false) || activeLoader != null) {
            return;
        }

        HuanghunDefaultTheme loader = new HuanghunDefaultTheme(account);
        activeLoader = loader;
        try {
            loader.requestTheme();
        } catch (Throwable error) {
            // 首次启动的可选主题下载绝不能成为启动失败的原因。
            FileLog.e(error);
            loader.finish(false);
        }
    }

    private void requestTheme() {
        TL_account.getTheme request = new TL_account.getTheme();
        request.format = "android";
        TLRPC.TL_inputThemeSlug input = new TLRPC.TL_inputThemeSlug();
        input.slug = THEME_SLUG;
        request.theme = input;

        try {
            ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (!(response instanceof TLRPC.TL_theme)) {
                        finish(false);
                        return;
                    }
                    remoteTheme = (TLRPC.TL_theme) response;
                    if (remoteTheme.document != null) {
                        loadThemeFile();
                    } else {
                        finish(applyAccentTheme());
                    }
                } catch (Throwable callbackError) {
                    FileLog.e(callbackError);
                    finish(false);
                }
            }));
        } catch (Throwable error) {
            FileLog.e(error);
            finish(false);
        }
    }

    private void loadThemeFile() {
        try {
            if (remoteTheme == null || remoteTheme.document == null) {
                finish(false);
                return;
            }
            themeFileName = FileLoader.getAttachFileName(remoteTheme.document);
            File localFile = FileLoader.getInstance(account).getPathToAttach(remoteTheme.document, true);
            if (localFile.exists()) {
                finish(applyThemeFile(localFile));
                return;
            }

            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoadFailed);
            FileLoader.getInstance(account).loadFile(remoteTheme.document, remoteTheme, FileLoader.PRIORITY_NORMAL, 1);
        } catch (Throwable error) {
            FileLog.e(error);
            finish(false);
        }
    }

    private boolean applyThemeFile(File themeFile) {
        try {
            Theme.ThemeInfo applied = Theme.applyThemeFile(themeFile, remoteTheme.title, remoteTheme, false);
            if (applied != null) {
                MessagesController.getInstance(account).saveTheme(applied, null, false, false);
                return true;
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
        return false;
    }

    private boolean applyAccentTheme() {
        try {
            if (remoteTheme.settings == null || remoteTheme.settings.isEmpty()) {
                return false;
            }
            String baseThemeKey = Theme.getBaseThemeKey(remoteTheme.settings.get(0));
            Theme.ThemeInfo baseTheme = Theme.getTheme(baseThemeKey);
            if (baseTheme == null) {
                return false;
            }
            Theme.ThemeAccent accent = baseTheme.createNewAccent(remoteTheme, account);
            if (accent == null) {
                return false;
            }
            baseTheme.prevAccentId = baseTheme.currentAccentId;
            baseTheme.setCurrentAccentId(accent.id);
            Theme.saveThemeAccents(baseTheme, true, false, false, false);
            Theme.applyTheme(baseTheme, false);
            return true;
        } catch (Throwable error) {
            FileLog.e(error);
            return false;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != this.account || themeFileName == null || args.length == 0 || !(args[0] instanceof String)) {
            return;
        }
        if (!themeFileName.equals(args[0])) {
            return;
        }
        if (id == NotificationCenter.fileLoaded && args.length > 1 && args[1] instanceof File) {
            finish(applyThemeFile((File) args[1]));
        } else if (id == NotificationCenter.fileLoadFailed) {
            finish(false);
        }
    }

    private void finish(boolean applied) {
        try {
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoadFailed);
        } catch (Throwable ignore) {
        }
        if (applied) {
            Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_APPLIED, true)
                        .apply();
            }
        }
        themeFileName = null;
        remoteTheme = null;
        if (activeLoader == this) {
            activeLoader = null;
        }
    }

    private static boolean isFreshInstall(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.firstInstallTime == packageInfo.lastUpdateTime;
        } catch (Throwable error) {
            FileLog.e(error);
            return false;
        }
    }
}
