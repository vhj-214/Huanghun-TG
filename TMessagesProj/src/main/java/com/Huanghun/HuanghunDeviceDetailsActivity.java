package com.Huanghun;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.FrameLayout;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.messenger.AndroidUtilities;
import tw.nekomimi.nekogram.settings.HuanghunWallpaperLayout;

import java.util.Locale;
import java.util.TimeZone;

/** Read-only local device and client version details rendered by the bundled HTML page. */
public class HuanghunDeviceDetailsActivity extends BaseFragment {
    private static final String DETAILS_URL = "file:///android_asset/huanghun_device_details.html";
    /** Same translucent surface used by the Huanghun settings pages. */
    private static final int GLASS_SURFACE_COLOR = 0x20FFFFFF;

    private WebView webView;
    private JSONObject pendingDeviceInfo;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("本机详情");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        // Full-screen wallpaper root: a transparent legacy FrameLayout was laid out
        // below the action bar, which exposed the window's black surface on top.
        HuanghunWallpaperLayout root = new HuanghunWallpaperLayout(context, currentAccount);
        root.setFitsSystemWindows(true);
        actionBar.setDrawBlurBackground(root);
        actionBar.setBackgroundColor(GLASS_SURFACE_COLOR);
        actionBar.setCastShadows(false);

        webView = new WebView(context);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        pendingDeviceInfo = collectDeviceInfo(context);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request == null || request.getUrl() == null || !request.getUrl().toString().startsWith("file:///android_asset/");
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return url == null || !url.startsWith("file:///android_asset/");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (DETAILS_URL.equals(url) && pendingDeviceInfo != null && webView == view) {
                    String safeJson = JSONObject.quote(pendingDeviceInfo.toString());
                    view.evaluateJavascript("window.setDeviceInfo && window.setDeviceInfo(JSON.parse(" + safeJson + "))", null);
                }
            }
        });
        // The bundled page already reserves 56px for the action bar in CSS. Offset the
        // WebView by the status bar so that reservation lines up with the real bar.
        FrameLayout.LayoutParams webViewParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        webViewParams.topMargin = actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0;
        root.addView(webView, webViewParams);
        webView.loadUrl(DETAILS_URL);
        fragmentView = root;
        return fragmentView;
    }

    private JSONObject collectDeviceInfo(Context context) {
        JSONObject info = new JSONObject();
        try {
            info.put("manufacturer", safe(Build.MANUFACTURER));
            info.put("model", safe(Build.MODEL));
            info.put("device", safe(Build.DEVICE));
            info.put("product", safe(Build.PRODUCT));
            info.put("hardware", safe(Build.HARDWARE));
            info.put("cores", Runtime.getRuntime().availableProcessors());
            info.put("androidRelease", safe(Build.VERSION.RELEASE));
            info.put("sdk", Build.VERSION.SDK_INT);
            info.put("androidBuild", safe(Build.DISPLAY));
            info.put("securityPatch", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? safe(Build.VERSION.SECURITY_PATCH) : "系统未提供");
            info.put("abi", getPrimaryAbi());
            info.put("language", Locale.getDefault().toLanguageTag());
            info.put("timezone", TimeZone.getDefault().getID());
            info.put("packageName", context.getPackageName());
            CharSequence appLabel = context.getApplicationInfo().loadLabel(context.getPackageManager());
            info.put("appName", appLabel == null ? "黄昏客户端" : appLabel.toString());

            String versionName = "未知";
            long versionCode = 0;
            try {
                PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                if (packageInfo.versionName != null && !packageInfo.versionName.trim().isEmpty()) {
                    versionName = packageInfo.versionName.trim();
                }
                versionCode = Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
            } catch (Exception error) {
                FileLog.e(error);
            }
            info.put("appVersion", versionName);
            info.put("appVersionCode", versionCode);

            DisplayMetrics metrics = new DisplayMetrics();
            float refreshRate = 0;
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                Display display = windowManager.getDefaultDisplay();
                if (display != null) {
                    display.getRealMetrics(metrics);
                    refreshRate = display.getRefreshRate();
                }
            }
            info.put("screenWidth", metrics.widthPixels);
            info.put("screenHeight", metrics.heightPixels);
            info.put("density", metrics.density);
            info.put("densityDpi", metrics.densityDpi);
            info.put("refreshRate", refreshRate);

            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                activityManager.getMemoryInfo(memoryInfo);
            }
            info.put("lowMemory", memoryInfo.lowMemory);
            info.put("memoryTotal", bytesToGiB(memoryInfo.totalMem));
            info.put("memoryAvailable", bytesToGiB(memoryInfo.availMem));
            info.put("appMemoryLimit", bytesToMiB(Runtime.getRuntime().maxMemory()));

            StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long totalBytes = statFs.getBlockCountLong() * statFs.getBlockSizeLong();
            long freeBytes = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
            info.put("storageTotal", bytesToGiB(totalBytes));
            info.put("storageFree", bytesToGiB(freeBytes));

            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                info.put("batteryPercent", level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1);
                info.put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
            } else {
                info.put("batteryPercent", -1);
                info.put("charging", false);
            }
        } catch (JSONException | RuntimeException error) {
            FileLog.e(error);
        }
        return info;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    private static String getPrimaryAbi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            return safe(Build.SUPPORTED_ABIS[0]);
        }
        return safe(Build.CPU_ABI);
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "未知" : value.trim();
    }

    private static double bytesToGiB(long bytes) {
        return Math.round(bytes / (1024d * 1024d * 1024d) * 100d) / 100d;
    }

    private static double bytesToMiB(long bytes) {
        return Math.round(bytes / (1024d * 1024d) * 10d) / 10d;
    }

    @Override
    public void onFragmentDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        pendingDeviceInfo = null;
        super.onFragmentDestroy();
    }
}
