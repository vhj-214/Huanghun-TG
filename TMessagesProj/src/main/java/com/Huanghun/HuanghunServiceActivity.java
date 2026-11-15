package com.Huanghun;

import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
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

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Huanghun service configuration and update screen. */
public class HuanghunServiceActivity extends BaseFragment {
    private TextSettingsCell checkUpdateCell;
    private TextSettingsCell updateCell;
    private TextInfoPrivacyCell infoCell;
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
        infoCell.setText("检查客户端更新会比较本机版本与服务器版本。发现新版后可通过管理员配置的 HTTPS 更新链接打开外部更新页面。代理获取请前往：设置 → 数据和存储 → 代理设置。");
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
                    if ("未知".equals(current)) {
                        showNotice("无法读取当前客户端版本，暂时无法判断是否需要更新。", "版本检查失败");
                        return;
                    }
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
        if (compareVersions(latest, current) <= 0) {
            showNotice("当前客户端无需更新。", "版本检查结果");
            return;
        }
        if (!isValidHttpsUpdateUrl(config.updateUrl)) {
            showNotice("管理员尚未配置更新链接。", "更新链接不可用");
            return;
        }
        boolean enforce = config.forceUpdate;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("发现新版本 " + latest);
        builder.setMessage("当前客户端版本：" + current + "\n服务器最新版本：" + latest + "\n\n将通过管理员配置的 HTTPS 更新链接打开外部页面。");
        if (!enforce) builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton("打开更新链接", (dialog, which) ->
                Browser.openUrl(getParentActivity(), config.updateUrl.trim()));
        AlertDialog dialog = builder.create();
        dialog.setCancelable(!enforce);
        dialog.setCanceledOnTouchOutside(!enforce);
        showDialog(dialog);
    }

    static boolean isValidHttpsUpdateUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String candidate = value.trim();
        try {
            Uri uri = Uri.parse(candidate);
            java.net.URL url = new java.net.URL(candidate);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().trim().isEmpty()
                    && "https".equalsIgnoreCase(url.getProtocol())
                    && url.getHost() != null && !url.getHost().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String currentVersionName() {
        try {
            String version = getParentActivity().getPackageManager().getPackageInfo(getParentActivity().getPackageName(), 0).versionName;
            if (version != null && !version.trim().isEmpty()) return version.trim();
        } catch (Exception ignored) {}
        return "未知";
    }

    static int compareVersions(String left, String right) {
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
        return compareQualifiers(a.qualifier, b.qualifier);
    }

    private static int compareQualifiers(String left, String right) {
        String[] leftParts = left.split("[.-]", -1);
        String[] rightParts = right.split("[.-]", -1);
        int count = Math.min(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            String a = leftParts[i];
            String b = rightParts[i];
            boolean aNumeric = a.matches("\\d+");
            boolean bNumeric = b.matches("\\d+");
            int comparison;
            if (aNumeric && bNumeric) {
                comparison = compareNumericIdentifiers(a, b);
            } else if (aNumeric != bNumeric) {
                comparison = aNumeric ? -1 : 1;
            } else {
                comparison = a.compareToIgnoreCase(b);
            }
            if (comparison != 0) return comparison;
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    private static int compareNumericIdentifiers(String left, String right) {
        int leftStart = 0;
        int rightStart = 0;
        while (leftStart < left.length() - 1 && left.charAt(leftStart) == '0') leftStart++;
        while (rightStart < right.length() - 1 && right.charAt(rightStart) == '0') rightStart++;
        int leftLength = left.length() - leftStart;
        int rightLength = right.length() - rightStart;
        if (leftLength != rightLength) return Integer.compare(leftLength, rightLength);
        return left.substring(leftStart).compareTo(right.substring(rightStart));
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
                            + "\n更新链接：" + (isValidHttpsUpdateUrl(config.updateUrl) ? "已配置 HTTPS 链接" : "尚未配置有效 HTTPS 链接")
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
    public void onFragmentDestroy() {
        checkUpdateCell = null;
        updateCell = null;
        infoCell = null;
        super.onFragmentDestroy();
    }
}
