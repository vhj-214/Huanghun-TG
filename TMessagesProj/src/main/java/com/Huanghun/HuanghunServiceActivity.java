package com.Huanghun;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Huanghun service configuration and update screen. */
public class HuanghunServiceActivity extends BaseFragment {
    private TextSettingsCell checkUpdateCell;
    private TextSettingsCell updateCell;
    private TextInfoPrivacyCell infoCell;
    private File pendingApkInstall;
    private boolean loading;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("黄昏服务");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        checkUpdateCell = new TextSettingsCell(context);
        checkUpdateCell.setTextAndValue("检查客户端更新", "立即检查", false);
        checkUpdateCell.setOnClickListener(v -> checkForUpdates());
        content.addView(checkUpdateCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

        updateCell = new TextSettingsCell(context);
        updateCell.setTextAndValue("更新配置", "立即同步", false);
        updateCell.setOnClickListener(v -> updateConfiguration());
        content.addView(updateCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

        infoCell = new TextInfoPrivacyCell(context);
        infoCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        infoCell.setText("检查客户端更新会比较本机版本与服务器版本。发现新版后可选择服务器下载或打开网盘链接；安装时仍需 Android 系统确认。代理获取请前往：设置 → 数据和存储 → 代理设置。");
        content.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        fragmentView = root;
        return fragmentView;
    }

    private void checkForUpdates() {
        if (loading || getParentActivity() == null) return;
        loading = true;
        checkUpdateCell.setTextAndValue("检查客户端更新", "正在检查…", false);
        BuiltInProxyClient.fetchConfig(new BuiltInProxyClient.ResultCallback<BuiltInProxyClient.RemoteConfig>() {
            @Override public void onSuccess(BuiltInProxyClient.RemoteConfig config) {
                AndroidUtilities.runOnUIThread(() -> {
                    loading = false;
                    if (checkUpdateCell == null || getParentActivity() == null) return;
                    checkUpdateCell.setTextAndValue("检查客户端更新", "立即检查", false);
                    saveRemoteConfig(config);
                    String current = currentVersionName();
                    String remote = config.latestVersion == null ? "" : config.latestVersion.trim();
                    if (remote.isEmpty()) {
                        showNotice("服务器尚未设置客户端版本号。", "版本信息不完整");
                        return;
                    }
                    int comparison = compareVersions(remote, current);
                    if (comparison > 0) {
                        showUpdateOptions(config, current, remote);
                    } else {
                        String message = comparison == 0
                                ? "已是最新版。\n当前客户端：" + current + "\n服务器版本：" + remote
                                : "当前客户端版本高于服务器发布版本，无需更新。\n当前客户端：" + current + "\n服务器版本：" + remote;
                        showNotice(message, "版本检查结果");
                    }
                });
            }
            @Override public void onError(String message) {
                AndroidUtilities.runOnUIThread(() -> {
                    loading = false;
                    if (checkUpdateCell != null) checkUpdateCell.setTextAndValue("检查客户端更新", "重试", false);
                    if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showUpdateOptions(BuiltInProxyClient.RemoteConfig config, String current, String latest) {
        boolean hasCloudUrl = config.updateUrl != null && !config.updateUrl.trim().isEmpty();
        boolean enforce = config.forceUpdate && (config.serverDownloadAvailable || hasCloudUrl);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("发现新版本 " + latest);
        builder.setMessage("当前客户端版本：" + current + "\n服务器最新版本：" + latest + "\n\n请选择下载方式。下载安装包后，Android 会显示系统安装确认界面。");
        if (!enforce) builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        if (!enforce || config.serverDownloadAvailable) {
            builder.setPositiveButton("服务器端更新", (dialog, which) -> downloadAndInstall(config));
        }
        if (!enforce || hasCloudUrl) {
        builder.setNeutralButton("网盘下载", (dialog, which) -> {
            if (config.updateUrl == null || config.updateUrl.trim().isEmpty()) {
                Toast.makeText(getParentActivity(), "管理员尚未配置网盘更新链接。", Toast.LENGTH_LONG).show();
            } else {
                Browser.openUrl(getParentActivity(), config.updateUrl);
            }
        });
        }
        AlertDialog dialog = builder.create();
        dialog.setCancelable(!enforce);
        dialog.setCanceledOnTouchOutside(!enforce);
        showDialog(dialog);
    }

    private void downloadAndInstall(BuiltInProxyClient.RemoteConfig config) {
        if (getParentActivity() == null) return;
        if (!config.serverDownloadAvailable) {
            Toast.makeText(getParentActivity(), "服务器尚未上传该版本 APK，请选择网盘下载或联系管理员。", Toast.LENGTH_LONG).show();
            return;
        }
        AlertDialog progress = new AlertDialog(getParentActivity(), 3);
        progress.setMessage("正在从服务器下载并校验 APK…");
        progress.setCanceledOnTouchOutside(false);
        showDialog(progress);
        BuiltInProxyClient.downloadServerApk(config, new BuiltInProxyClient.ResultCallback<File>() {
            @Override public void onSuccess(File file) {
                AndroidUtilities.runOnUIThread(() -> {
                    try { progress.dismiss(); } catch (Exception ignored) {}
                    installDownloadedApk(file);
                });
            }
            @Override public void onError(String message) {
                AndroidUtilities.runOnUIThread(() -> {
                    try { progress.dismiss(); } catch (Exception ignored) {}
                    if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void installDownloadedApk(File apk) {
        if (getParentActivity() == null || apk == null || !apk.isFile()) return;
        if (Build.VERSION.SDK_INT >= 26 && !getParentActivity().getPackageManager().canRequestPackageInstalls()) {
            try {
                pendingApkInstall = apk;
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getParentActivity().getPackageName()));
                startActivityForResult(settings, 9217);
                Toast.makeText(getParentActivity(), "请允许此应用安装未知来源应用，返回后将继续安装。", Toast.LENGTH_LONG).show();
            } catch (Exception error) {
                Toast.makeText(getParentActivity(), "无法打开安装权限设置。", Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", apk);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(install, 9218);
        } catch (Exception error) {
            Toast.makeText(getParentActivity(), "无法启动 Android 安装器：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String currentVersionName() {
        try {
            String version = getParentActivity().getPackageManager().getPackageInfo(getParentActivity().getPackageName(), 0).versionName;
            if (version != null && !version.trim().isEmpty()) return version.trim();
        } catch (Exception ignored) {}
        return "未知";
    }

    private static int compareVersions(String left, String right) {
        ParsedVersion a = parseVersion(left);
        ParsedVersion b = parseVersion(right);
        int count = Math.max(a.parts.size(), b.parts.size());
        for (int i = 0; i < count; i++) {
            long x = i < a.parts.size() ? a.parts.get(i) : 0;
            long y = i < b.parts.size() ? b.parts.get(i) : 0;
            if (x != y) return Long.compare(x, y);
        }
        if (a.qualifier.isEmpty() && b.qualifier.isEmpty()) return 0;
        if (a.qualifier.isEmpty()) return 1;
        if (b.qualifier.isEmpty()) return -1;
        return a.qualifier.compareToIgnoreCase(b.qualifier);
    }

    private static ParsedVersion parseVersion(String value) {
        String normalized = value == null ? "" : value.trim();
        int plus = normalized.indexOf('+');
        if (plus >= 0) normalized = normalized.substring(0, plus);
        String qualifier = "";
        int dash = normalized.indexOf('-');
        if (dash >= 0) {
            String suffix = normalized.substring(dash + 1);
            // Gradle appends a seven-character git SHA to versionName; it is build metadata, not a release version.
            if (!suffix.matches("(?i)[0-9a-f]{7,40}")) qualifier = suffix;
            normalized = normalized.substring(0, dash);
        }
        ArrayList<Long> parts = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(normalized);
        while (matcher.find()) {
            try { parts.add(Long.parseLong(matcher.group())); }
            catch (NumberFormatException ignored) { parts.add(Long.MAX_VALUE); }
        }
        return new ParsedVersion(parts, qualifier);
    }

    private static final class ParsedVersion {
        final ArrayList<Long> parts;
        final String qualifier;
        ParsedVersion(ArrayList<Long> parts, String qualifier) {
            this.parts = parts;
            this.qualifier = qualifier;
        }
    }

    private void updateConfiguration() {
        if (loading || getParentActivity() == null) return;
        loading = true;
        updateCell.setTextAndValue("更新配置", "正在连接…", false);
        BuiltInProxyClient.fetchConfig(new BuiltInProxyClient.ResultCallback<BuiltInProxyClient.RemoteConfig>() {
            @Override public void onSuccess(BuiltInProxyClient.RemoteConfig config) {
                AndroidUtilities.runOnUIThread(() -> {
                    loading = false;
                    if (updateCell == null || infoCell == null || getParentActivity() == null) return;
                    updateCell.setTextAndValue("更新配置", "已同步", false);
                    saveRemoteConfig(config);
                    String message = "配置已更新。\n\n当前客户端：" + currentVersionName()
                            + "\n服务器最新版本：" + (config.latestVersion.isEmpty() ? "未设置" : config.latestVersion)
                            + "\n服务器 APK：" + (config.serverDownloadAvailable ? "可下载" : "尚未上传")
                            + "\n网盘链接：" + (config.updateUrl.isEmpty() ? "未设置" : "已设置")
                            + "\n服务器内置代理：" + config.proxyCount + " 条";
                    if (!config.announcement.isEmpty()) message += "\n\n公告：" + config.announcement;
                    infoCell.setText(message);
                    if (config.forceUpdate && compareVersions(config.latestVersion, currentVersionName()) > 0) {
                        showUpdateOptions(config, currentVersionName(), config.latestVersion);
                    } else {
                        Toast.makeText(getParentActivity(), "黄昏配置已更新", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override public void onError(String message) {
                AndroidUtilities.runOnUIThread(() -> {
                    loading = false;
                    if (updateCell != null) updateCell.setTextAndValue("更新配置", "重试", false);
                    if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveRemoteConfig(BuiltInProxyClient.RemoteConfig config) {
        getParentActivity().getSharedPreferences("mainconfig", Context.MODE_PRIVATE).edit()
                .putInt("huanghun_config_revision", config.revision)
                .putString("huanghun_latest_version", config.latestVersion)
                .putString("huanghun_update_url", config.updateUrl)
                .putBoolean("huanghun_force_update", config.forceUpdate)
                .putBoolean("huanghun_server_download_available", config.serverDownloadAvailable)
                .putString("huanghun_server_download_url", config.serverDownloadUrl)
                .putString("huanghun_announcement", config.announcement)
                .putInt("huanghun_proxy_count", config.proxyCount)
                .putInt("huanghun_proxy_revision", config.proxyRevision)
                .apply();
    }

    private void showNotice(String message, String title) {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity()).setTitle(title).setMessage(message)
                .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode == 9217 && pendingApkInstall != null) {
            File apk = pendingApkInstall;
            pendingApkInstall = null;
            if (Build.VERSION.SDK_INT < 26 || getParentActivity() != null && getParentActivity().getPackageManager().canRequestPackageInstalls()) {
                installDownloadedApk(apk);
            } else if (getParentActivity() != null) {
                Toast.makeText(getParentActivity(), "未授予安装权限；APK 已保存在应用缓存中。", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        checkUpdateCell = null;
        updateCell = null;
        infoCell = null;
        super.onFragmentDestroy();
    }
}
