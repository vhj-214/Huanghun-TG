package com.Huanghun;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.messenger.AndroidUtilities;
import tw.nekomimi.nekogram.settings.HuanghunWallpaperLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
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
            info.put("brand", safe(Build.BRAND));
            info.put("model", safe(Build.MODEL));
            info.put("device", safe(Build.DEVICE));
            info.put("product", safe(Build.PRODUCT));
            info.put("board", safe(Build.BOARD));
            info.put("hardware", safe(Build.HARDWARE));
            info.put("bootloader", safe(Build.BOOTLOADER));
            info.put("buildId", safe(Build.ID));
            info.put("buildType", safe(Build.TYPE));
            info.put("buildTags", safe(Build.TAGS));
            info.put("buildFingerprint", safe(Build.FINGERPRINT));
            info.put("androidRelease", safe(Build.VERSION.RELEASE));
            info.put("androidCodename", safe(Build.VERSION.CODENAME));
            info.put("sdk", Build.VERSION.SDK_INT);
            info.put("previewSdk", Build.VERSION.PREVIEW_SDK_INT);
            info.put("androidBuild", safe(Build.DISPLAY));
            info.put("androidIncremental", safe(Build.VERSION.INCREMENTAL));
            info.put("baseOs", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? valueOrUnavailable(Build.VERSION.BASE_OS) : "系统未提供");
            info.put("securityPatch", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? valueOrUnavailable(Build.VERSION.SECURITY_PATCH) : "系统未提供");
            info.put("buildTime", Build.TIME);
            info.put("kernelVersion", valueOrUnavailable(System.getProperty("os.version")));
            info.put("osArchitecture", valueOrUnavailable(System.getProperty("os.arch")));
            info.put("abi", getPrimaryAbi());
            info.put("supportedAbis", toJsonArray(Build.SUPPORTED_ABIS));
            info.put("process64Bit", Process.is64Bit());
            info.put("cores", Runtime.getRuntime().availableProcessors());
            info.put("cpuInfo", readCpuInfo());
            info.put("cpuOnline", readFirstLine("/sys/devices/system/cpu/online"));
            long cpuMaxFreqKHz = readLongFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            info.put("cpuMaxFreqMHz", cpuMaxFreqKHz > 0 ? roundOneDecimal(cpuMaxFreqKHz / 1000.0) : "系统未提供");
            info.put("deviceUptimeSeconds", SystemClock.elapsedRealtime() / 1000L);
            info.put("language", Locale.getDefault().toLanguageTag());
            info.put("timezone", TimeZone.getDefault().getID());

            CharSequence appLabel = context.getApplicationInfo().loadLabel(context.getPackageManager());
            info.put("appName", appLabel == null ? "黄昏客户端" : appLabel.toString());
            collectAppInfo(context, info);
            collectDisplayInfo(context, info);
            collectMemoryInfo(context, info);
            collectStorageInfo(context, info);
            collectBatteryInfo(context, info);
            collectNetworkInfo(context, info);
            collectSensors(context, info);
            collectSystemFeatures(context, info);
            collectThermalStatus(context, info);
        } catch (JSONException | RuntimeException error) {
            FileLog.e(error);
        }
        return info;
    }

    private void collectAppInfo(Context context, JSONObject info) throws JSONException {
        info.put("packageName", context.getPackageName());
        info.put("appTargetSdk", context.getApplicationInfo().targetSdkVersion);
        info.put("process64Bit", Process.is64Bit());
        info.put("appDataPath", context.getFilesDir().getParent());
        info.put("appFilesPath", context.getFilesDir().getAbsolutePath());
        info.put("appCachePath", context.getCacheDir().getAbsolutePath());
        File externalFiles = context.getExternalFilesDir(null);
        info.put("appExternalFilesPath", externalFiles == null ? "系统未提供" : externalFiles.getAbsolutePath());

        String versionName = "未知";
        long versionCode = 0;
        long firstInstallTime = 0;
        long lastUpdateTime = 0;
        JSONArray permissions = new JSONArray();
        int grantedPermissions = 0;
        int requestedPermissions = 0;
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (packageInfo.versionName != null && !packageInfo.versionName.trim().isEmpty()) {
                versionName = packageInfo.versionName.trim();
            }
            versionCode = Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
            firstInstallTime = packageInfo.firstInstallTime;
            lastUpdateTime = packageInfo.lastUpdateTime;
            String[] requested = packageInfo.requestedPermissions;
            int[] flags = packageInfo.requestedPermissionsFlags;
            if (requested != null) {
                requestedPermissions = requested.length;
                for (int i = 0; i < requested.length; i++) {
                    boolean granted = flags != null && i < flags.length
                            && (flags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                    if (granted) grantedPermissions++;
                    JSONObject permission = new JSONObject();
                    permission.put("name", requested[i]);
                    permission.put("granted", granted);
                    permissions.put(permission);
                }
            }
        } catch (Exception error) {
            FileLog.e(error);
        }
        info.put("appVersion", versionName);
        info.put("appVersionCode", versionCode);
        info.put("appFirstInstallTime", firstInstallTime);
        info.put("appLastUpdateTime", lastUpdateTime);
        info.put("appPermissionCount", requestedPermissions);
        info.put("appGrantedPermissionCount", grantedPermissions);
        info.put("appDeniedPermissionCount", Math.max(0, requestedPermissions - grantedPermissions));
        info.put("appPermissions", permissions);

        String installer = null;
        try {
            PackageManager packageManager = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= 30) {
                installer = packageManager.getInstallSourceInfo(context.getPackageName()).getInstallingPackageName();
            } else {
                installer = packageManager.getInstallerPackageName(context.getPackageName());
            }
        } catch (Exception error) {
            FileLog.e(error);
        }
        info.put("installerPackage", valueOrUnavailable(installer));
    }

    private void collectDisplayInfo(Context context, JSONObject info) throws JSONException {
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = null;
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            display = windowManager.getDefaultDisplay();
        }
        if (display != null) {
            display.getRealMetrics(metrics);
            info.put("refreshRate", display.getRefreshRate());
            info.put("screenRotation", display.getRotation());
            info.put("screenRotationDegrees", display.getRotation() * 90);
            if (Build.VERSION.SDK_INT >= 23) {
                Display.Mode currentMode = display.getMode();
                info.put("displayMode", currentMode.getPhysicalWidth() + " × " + currentMode.getPhysicalHeight()
                        + " @ " + roundOneDecimal(currentMode.getRefreshRate()) + " Hz");
                JSONArray modes = new JSONArray();
                Display.Mode[] supportedModes = display.getSupportedModes();
                if (supportedModes != null) {
                    for (Display.Mode mode : supportedModes) {
                        modes.put(mode.getPhysicalWidth() + " × " + mode.getPhysicalHeight()
                                + " @ " + roundOneDecimal(mode.getRefreshRate()) + " Hz");
                    }
                }
                info.put("supportedDisplayModes", modes);
            }
            if (Build.VERSION.SDK_INT >= 24) {
                Display.HdrCapabilities hdrCapabilities = display.getHdrCapabilities();
                JSONArray hdrTypes = new JSONArray();
                if (hdrCapabilities != null) {
                    for (int type : hdrCapabilities.getSupportedHdrTypes()) {
                        hdrTypes.put(hdrTypeLabel(type));
                    }
                }
                info.put("hdrTypes", hdrTypes);
            }
        } else {
            metrics.setTo(context.getResources().getDisplayMetrics());
            info.put("refreshRate", "系统未提供");
        }
        info.put("screenWidth", metrics.widthPixels);
        info.put("screenHeight", metrics.heightPixels);
        info.put("density", metrics.density);
        info.put("densityDpi", metrics.densityDpi);
        info.put("screenWidthDp", context.getResources().getConfiguration().screenWidthDp);
        info.put("screenHeightDp", context.getResources().getConfiguration().screenHeightDp);
    }

    private void collectMemoryInfo(Context context, JSONObject info) throws JSONException {
        Runtime runtime = Runtime.getRuntime();
        info.put("appMemoryLimit", bytesToMiB(runtime.maxMemory()));
        info.put("appHeapAllocated", bytesToMiB(runtime.totalMemory()));
        info.put("appHeapUsed", bytesToMiB(Math.max(0, runtime.totalMemory() - runtime.freeMemory())));
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            info.put("memoryTotal", "系统未提供");
            info.put("memoryAvailable", "系统未提供");
            info.put("memoryThreshold", "系统未提供");
            info.put("lowMemory", "系统未提供");
            info.put("lowRamDevice", "系统未提供");
            info.put("appPss", "系统未提供");
            return;
        }
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        info.put("lowMemory", memoryInfo.lowMemory);
        info.put("memoryTotal", bytesToGiB(memoryInfo.totalMem));
        info.put("memoryAvailable", bytesToGiB(memoryInfo.availMem));
        info.put("memoryThreshold", bytesToGiB(memoryInfo.threshold));
        info.put("lowRamDevice", activityManager.isLowRamDevice());
        try {
            Debug.MemoryInfo[] processMemory = activityManager.getProcessMemoryInfo(new int[]{Process.myPid()});
            info.put("appPss", processMemory != null && processMemory.length > 0
                    ? bytesToMiB(processMemory[0].getTotalPss() * 1024L) : "系统未提供");
        } catch (Exception error) {
            info.put("appPss", "系统未提供");
            FileLog.e(error);
        }
    }

    private void collectStorageInfo(Context context, JSONObject info) throws JSONException {
        try {
            File dataDirectory = Environment.getDataDirectory();
            StatFs data = new StatFs(dataDirectory.getAbsolutePath());
            long totalBytes = data.getBlockCountLong() * data.getBlockSizeLong();
            long availableBytes = data.getAvailableBytes();
            info.put("storageTotal", bytesToGiB(totalBytes));
            info.put("storageFree", bytesToGiB(availableBytes));
            info.put("storageUsable", bytesToGiB(availableBytes));
            info.put("dataPartitionPath", dataDirectory.getAbsolutePath());
        } catch (Exception error) {
            info.put("storageTotal", "系统未提供");
            info.put("storageFree", "系统未提供");
            info.put("storageUsable", "系统未提供");
            info.put("dataPartitionPath", "系统未提供");
            FileLog.e(error);
        }
        String externalState = Environment.getExternalStorageState();
        info.put("sharedStorageState", externalStateLabel(externalState));
        try {
            File external = Environment.getExternalStorageDirectory();
            StatFs shared = new StatFs(external.getAbsolutePath());
            info.put("sharedStoragePath", external.getAbsolutePath());
            info.put("sharedStorageTotal", bytesToGiB(shared.getBlockCountLong() * shared.getBlockSizeLong()));
            info.put("sharedStorageFree", bytesToGiB(shared.getAvailableBytes()));
        } catch (Exception error) {
            info.put("sharedStoragePath", "系统未提供");
            info.put("sharedStorageTotal", "系统未提供");
            info.put("sharedStorageFree", "系统未提供");
        }
    }

    private void collectBatteryInfo(Context context, JSONObject info) throws JSONException {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            info.put("batteryPercent", -1);
            info.put("charging", false);
            info.put("batteryStatus", "系统未提供");
            info.put("batteryHealth", "系统未提供");
            info.put("batteryPlugged", "系统未提供");
            info.put("batteryTemperatureC", "系统未提供");
            info.put("batteryVoltageMv", "系统未提供");
            info.put("batteryTechnology", "系统未提供");
        } else {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            info.put("batteryPercent", level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1);
            info.put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
            info.put("batteryStatus", batteryStatusLabel(status));
            info.put("batteryHealth", batteryHealthLabel(health));
            info.put("batteryPlugged", batteryPluggedLabel(plugged));
            info.put("batteryTemperatureC", temperature >= 0 ? roundOneDecimal(temperature / 10.0) : "系统未提供");
            info.put("batteryVoltageMv", voltage >= 0 ? voltage : "系统未提供");
            info.put("batteryTechnology", valueOrUnavailable(battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)));
        }
        int currentNow = Integer.MIN_VALUE;
        try {
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (Build.VERSION.SDK_INT >= 21 && batteryManager != null) {
                currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            }
        } catch (Exception error) {
            FileLog.e(error);
        }
        info.put("batteryCurrentMa", currentNow == Integer.MIN_VALUE || currentNow == Integer.MAX_VALUE
                ? "系统未提供" : roundOneDecimal(currentNow / 1000.0));
    }

    private void collectNetworkInfo(Context context, JSONObject info) throws JSONException {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean connected = false;
        boolean validated = false;
        boolean captivePortal = false;
        boolean vpn = false;
        boolean networkKnown = connectivity != null;
        boolean capabilitiesKnown = false;
        String transportName = "未连接";
        boolean metered = false;
        try {
            if (connectivity != null) {
                metered = connectivity.isActiveNetworkMetered();
                Network network = connectivity.getActiveNetwork();
                NetworkCapabilities capabilities = network == null ? null : connectivity.getNetworkCapabilities(network);
                if (capabilities != null) {
                    capabilitiesKnown = true;
                    connected = true;
                    validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    captivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
                    vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                    StringBuilder types = new StringBuilder();
                    appendTransport(types, capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI), "Wi‑Fi");
                    appendTransport(types, capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR), "蜂窝网络");
                    appendTransport(types, capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET), "以太网");
                    appendTransport(types, capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN), "VPN");
                    appendTransport(types, capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH), "蓝牙网络");
                    appendTransport(types, capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB), "USB 网络");
                    transportName = types.length() > 0 ? types.toString() : "其他 / 未识别的网络传输";
                } else {
                    NetworkInfo legacy = connectivity.getActiveNetworkInfo();
                    if (legacy != null) {
                        connected = legacy.isConnected();
                        transportName = connected ? safe(legacy.getTypeName()) : "未连接";
                    }
                }
            }
        } catch (Exception error) {
            networkKnown = false;
            capabilitiesKnown = false;
            transportName = "系统未提供";
            FileLog.e(error);
        }
        info.put("networkConnected", networkKnown ? connected : "系统未提供");
        info.put("networkType", transportName);
        info.put("networkValidated", capabilitiesKnown ? validated : "系统未提供");
        info.put("networkCaptivePortal", capabilitiesKnown ? captivePortal : "系统未提供");
        info.put("networkVpn", capabilitiesKnown ? vpn : "系统未提供");
        info.put("networkMetered", connectivity != null ? metered : "系统未提供");
    }

    private void collectSensors(Context context, JSONObject info) throws JSONException {
        JSONArray sensorsJson = new JSONArray();
        try {
            SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> sensors = sensorManager == null ? null : sensorManager.getSensorList(Sensor.TYPE_ALL);
            if (sensors != null) {
                for (Sensor sensor : sensors) {
                    JSONObject item = new JSONObject();
                    item.put("name", valueOrUnavailable(sensor.getName()));
                    item.put("vendor", valueOrUnavailable(sensor.getVendor()));
                    item.put("type", sensor.getType());
                    item.put("version", sensor.getVersion());
                    item.put("resolution", roundOneDecimal(sensor.getResolution()));
                    item.put("maxRange", roundOneDecimal(sensor.getMaximumRange()));
                    item.put("powerMa", roundOneDecimal(sensor.getPower()));
                    item.put("minDelayUs", sensor.getMinDelay());
                    item.put("wakeUp", sensor.isWakeUpSensor());
                    sensorsJson.put(item);
                }
            }
        } catch (Exception error) {
            FileLog.e(error);
        }
        info.put("sensorCount", sensorsJson.length());
        info.put("sensors", sensorsJson);
    }

    private void collectSystemFeatures(Context context, JSONObject info) throws JSONException {
        JSONArray featuresJson = new JSONArray();
        try {
            FeatureInfo[] features = context.getPackageManager().getSystemAvailableFeatures();
            if (features != null) {
                for (FeatureInfo feature : features) {
                    if (feature.name != null && !feature.name.trim().isEmpty()) {
                        featuresJson.put(feature.name);
                    } else if (feature.reqGlEsVersion != 0) {
                        int major = (feature.reqGlEsVersion >> 16) & 0xffff;
                        int minor = feature.reqGlEsVersion & 0xffff;
                        featuresJson.put("OpenGL ES " + major + "." + minor);
                    }
                }
            }
        } catch (Exception error) {
            FileLog.e(error);
        }
        info.put("systemFeatureCount", featuresJson.length());
        info.put("systemFeatures", featuresJson);
    }

    private void collectThermalStatus(Context context, JSONObject info) throws JSONException {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                info.put("thermalStatus", powerManager == null ? "系统未提供" : thermalStatusLabel(powerManager.getCurrentThermalStatus()));
                return;
            } catch (Exception error) {
                FileLog.e(error);
            }
        }
        info.put("thermalStatus", "系统版本未提供此接口");
    }

    private static JSONArray toJsonArray(String[] values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (String value : values) array.put(valueOrUnavailable(value));
        }
        return array;
    }

    private static String readCpuInfo() {
        StringBuilder result = new StringBuilder();
        String[] accepted = {"hardware", "model name", "processor", "cpu architecture", "cpu implementer", "cpu part", "features"};
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null && result.length() < 1600) {
                int separator = line.indexOf(':');
                if (separator <= 0) continue;
                String key = line.substring(0, separator).trim();
                String lowerKey = key.toLowerCase(Locale.US);
                boolean wanted = false;
                for (String candidate : accepted) {
                    if (candidate.equals(lowerKey)) {
                        wanted = true;
                        break;
                    }
                }
                if (wanted && result.indexOf(key + ":") < 0) {
                    if (result.length() > 0) result.append('\n');
                    result.append(key).append(": ").append(line.substring(separator + 1).trim());
                }
            }
        } catch (Exception error) {
            return "系统未提供（/proc/cpuinfo 不可读）";
        }
        return result.length() == 0 ? "系统未提供（/proc/cpuinfo 无可读字段）" : result.toString();
    }

    private static String readFirstLine(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return valueOrUnavailable(reader.readLine());
        } catch (Exception error) {
            return "系统未提供";
        }
    }

    private static long readLongFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String value = reader.readLine();
            return value == null ? -1 : Long.parseLong(value.trim());
        } catch (Exception error) {
            return -1;
        }
    }

    private static String externalStateLabel(String state) {
        if (Environment.MEDIA_MOUNTED.equals(state)) return "已挂载，可读写";
        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) return "已挂载，只读";
        if (Environment.MEDIA_UNMOUNTED.equals(state)) return "未挂载";
        if (Environment.MEDIA_REMOVED.equals(state)) return "介质已移除";
        return valueOrUnavailable(state);
    }

    private static String batteryStatusLabel(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "已连接电源但未充电";
            case BatteryManager.BATTERY_STATUS_FULL: return "已充满";
            default: return "系统未提供（状态码 " + status + "）";
        }
    }

    private static String batteryHealthLabel(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "电池失效";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "过压";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "系统报告故障";
            case BatteryManager.BATTERY_HEALTH_COLD: return "低温";
            default: return "系统未提供（状态码 " + health + "）";
        }
    }

    private static String batteryPluggedLabel(int plugged) {
        switch (plugged) {
            case 0: return "未连接外部电源";
            case BatteryManager.BATTERY_PLUGGED_AC: return "交流电源";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB 电源";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "无线充电";
            default: return "系统未提供（类型码 " + plugged + "）";
        }
    }

    private static String thermalStatusLabel(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "无热限制";
            case PowerManager.THERMAL_STATUS_LIGHT: return "轻微";
            case PowerManager.THERMAL_STATUS_MODERATE: return "中等";
            case PowerManager.THERMAL_STATUS_SEVERE: return "严重";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "危急";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "紧急";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "系统即将关机";
            default: return "未知状态（" + status + "）";
        }
    }

    private static String hdrTypeLabel(int type) {
        switch (type) {
            case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION: return "Dolby Vision";
            case Display.HdrCapabilities.HDR_TYPE_HDR10: return "HDR10";
            case Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS: return "HDR10+";
            case Display.HdrCapabilities.HDR_TYPE_HLG: return "HLG";
            default: return "HDR 类型 " + type;
        }
    }

    private static void appendTransport(StringBuilder builder, boolean present, String label) {
        if (!present) return;
        if (builder.length() > 0) builder.append(" / ");
        builder.append(label);
    }

    private static String valueOrUnavailable(String value) {
        return value == null || value.trim().isEmpty() ? "系统未提供" : value.trim();
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10d) / 10d;
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
