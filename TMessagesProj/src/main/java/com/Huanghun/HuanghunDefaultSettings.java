package com.Huanghun;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import com.google.gson.JsonObject;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.utils.GsonUtil;
import xyz.nextalone.nagram.NaConfig;

/**
 * Applies Huanghun's bundled N-Settings backup exactly once for a newly installed app.
 * Existing installations, upgrades, and all subsequent user edits are intentionally preserved.
 */
public final class HuanghunDefaultSettings {
    private static final String ASSET_NAME = "huanghun_default_settings.json";
    private static final String PREFS_NAME = "huanghun_bootstrap";
    private static final String KEY_APPLIED = "builtin_default_settings_v1_applied";

    private HuanghunDefaultSettings() {
    }

    public static void applyForFreshInstall() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }

        SharedPreferences bootstrap = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (bootstrap.getBoolean(KEY_APPLIED, false) || !isFreshInstall(context)) {
            return;
        }

        try {
            JsonObject settings = GsonUtil.toJsonObject(readAsset(context, ASSET_NAME));
            SettingsBackupHelper.importSettings(settings);

            // The importer may initialize these caches while validating N-Settings entries.
            // Reload once so this first launch immediately uses the bundled values.
            NekoConfig.loadConfig(true);
            NaConfig.INSTANCE.loadConfig(true);

            if (!bootstrap.edit().putBoolean(KEY_APPLIED, true).commit()) {
                throw new IllegalStateException("Unable to save Huanghun default-settings marker");
            }
        } catch (Throwable error) {
            FileLog.e(error);
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

    private static String readAsset(Context context, String assetName) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream input = context.getAssets().open(assetName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                content.append(buffer, 0, count);
            }
        }
        return content.toString();
    }
}
