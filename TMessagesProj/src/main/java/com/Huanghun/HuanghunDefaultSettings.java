package com.Huanghun;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import com.google.gson.JsonObject;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.translate.Translator;
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
    private static final String KEY_DEFAULT_LANGUAGE_APPLIED = "official_zh_hans_language_applied";
    private static final String KEY_CHINESE_PREFERENCE_SEEDED = "official_zh_hans_preference_seeded_v2";
    private static final String KEY_TRANSLATION_PROVIDERS_APPLIED = "translation_providers_v1_applied";
    public static final String OFFICIAL_SIMPLIFIED_CHINESE = "zh_hans";

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

            // LocaleController reads this preference during initialization. The remote zh-hans
            // pack itself is downloaded and applied once its metadata becomes available.
            seedOfficialChinesePreference(context, true);

            // The importer may initialize these caches while validating N-Settings entries.
            // Reload once so this first launch immediately uses the bundled values.
            NekoConfig.loadConfig(true);
            NaConfig.INSTANCE.loadConfig(true);

            // Explicit Huanghun defaults requested for a fresh install. They are written only
            // after the bundled JSON has been imported, then remain fully user-editable.
            NekoConfig.customSavePath.setConfigString("黄昏");
            NaConfig.INSTANCE.getSaveToChatSubfolder().setConfigBool(true);

            if (!bootstrap.edit().putBoolean(KEY_APPLIED, true).commit()) {
                throw new IllegalStateException("Unable to save Huanghun default-settings marker");
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    /**
     * Applies the user-selected translation providers once after an upgrade or a fresh install.
     * Later manual selections remain fully editable and are not overwritten on subsequent starts.
     */
    public static void applyTranslationProviderDefaultsIfNeeded() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        SharedPreferences bootstrap = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (bootstrap.getBoolean(KEY_TRANSLATION_PROVIDERS_APPLIED, false)) {
            return;
        }
        try {
            NekoConfig.loadConfig(true);
            NaConfig.INSTANCE.loadConfig(true);
            NekoConfig.translationProvider.setConfigInt(Translator.providerGoogle);
            NaConfig.INSTANCE.getOutgoingAutoTranslateProvider().setConfigInt(Translator.providerYandex);
            if (!bootstrap.edit().putBoolean(KEY_TRANSLATION_PROVIDERS_APPLIED, true).commit()) {
                throw new IllegalStateException("Unable to save translation-provider migration marker");
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    /**
     * Seeds the official Chinese preference once before LocaleController is created.
     * This also upgrades Huanghun's previous English / zhcncc default without changing
     * users who had already selected a different language themselves.
     */
    public static void applyOfficialChineseDefaultIfNeeded() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        seedOfficialChinesePreference(context, false);
    }

    /** Returns true while the seeded zh-hans preference still needs its official remote pack. */
    public static boolean isOfficialChineseLanguagePending() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return false;
        }
        SharedPreferences bootstrap = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String language = MessagesController.getGlobalMainSettings().getString("language", null);
        return bootstrap.getBoolean(KEY_CHINESE_PREFERENCE_SEEDED, false)
                && OFFICIAL_SIMPLIFIED_CHINESE.equals(language)
                && !bootstrap.getBoolean(KEY_DEFAULT_LANGUAGE_APPLIED, false);
    }

    /** Marks the bundled language preference as resolved without changing any later user choice. */
    public static void markOfficialChineseLanguageApplied() {
        Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_DEFAULT_LANGUAGE_APPLIED, true)
                    .apply();
        }
    }

    private static void seedOfficialChinesePreference(Context context, boolean force) {
        SharedPreferences bootstrap = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (bootstrap.getBoolean(KEY_CHINESE_PREFERENCE_SEEDED, false)) {
            return;
        }
        SharedPreferences languagePreferences = MessagesController.getGlobalMainSettings();
        String currentLanguage = languagePreferences.getString("language", null);
        boolean isPreviousHuanghunDefault = currentLanguage == null
                || "en".equalsIgnoreCase(currentLanguage)
                || "zhcncc".equalsIgnoreCase(currentLanguage)
                || "zh_cn".equalsIgnoreCase(currentLanguage);
        if (!force && !isPreviousHuanghunDefault) {
            return;
        }
        languagePreferences.edit().putString("language", OFFICIAL_SIMPLIFIED_CHINESE).apply();
        bootstrap.edit().putBoolean(KEY_CHINESE_PREFERENCE_SEEDED, true).apply();
    }

    /** Disable account-region language recommendation dialogs; manual language selection remains available. */
    public static boolean suppressSuggestedLanguageAlerts() {
        return true;
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
