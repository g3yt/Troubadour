package com.troubadour.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Localization utilities similar to YTStrings.
 * Uses JSON files in assets/lang/<lang_code>.json.
 */
public class StringsUtils {
    private static final String TAG = "StringsUtils";
    private static final String DEFAULT_LANGUAGE = "ru";
    private static final String LANG_ASSET_PATH = "lang/";

    private static Context sContext;
    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> sLanguageMaps = new HashMap<>();
    private static String sCurrentLanguage = null;

    /**
     * Initialize with application context.
     */
    public static void init(Context context) {
        sContext = context.getApplicationContext();
        // Initialize with system language
        String sysLang = getSystemLanguage();
        sCurrentLanguage = sysLang;
        loadLanguage(sysLang);
    }

    /**
     * Get the system language (lowercase, two-letter code).
     */
    private static String getSystemLanguage() {
        Locale locale = Locale.getDefault();
        String lang = locale.getLanguage();
        if (TextUtils.isEmpty(lang)) {
            lang = DEFAULT_LANGUAGE;
        }
        return lang.toLowerCase();
    }

    /**
     * Set the current language and load its resources.
     */
    public static void setLanguage(String languageCode) {
        if (languageCode == null) {
            languageCode = getSystemLanguage();
        }
        languageCode = languageCode.toLowerCase();
        if (languageCode.equals(sCurrentLanguage)) {
            return;
        }
        sCurrentLanguage = languageCode;
        loadLanguage(languageCode);
    }

    /**
     * Get the current language code.
     */
    public static String getCurrentLanguage() {
        if (sCurrentLanguage == null) {
            // Initialize if not done
            if (sContext != null) {
                init(sContext);
            } else {
                return DEFAULT_LANGUAGE;
            }
        }
        return sCurrentLanguage;
    }

    /**
     * Load a language JSON file from assets.
     */
    private static void loadLanguage(String languageCode) {
        if (sContext == null) return;
        try {
            AssetManager assets = sContext.getAssets();
            String path = LANG_ASSET_PATH + languageCode + ".json";
            InputStream is = assets.open(path);
            InputStreamReader reader = new InputStreamReader(is);
            // Expecting a JSON object mapping string keys to translated strings
            Map<String, String> map = GSON.fromJson(reader, Map.class);
            sLanguageMaps.put(languageCode, map != null ? map : new HashMap<>());
            reader.close();
            is.close();
        } catch (IOException | JsonSyntaxException | JsonIOException e) {
            Log.w(TAG, "Failed to load language " + languageCode + ", falling back to default", e);
            // Fallback to default language map (empty)
            sLanguageMaps.put(languageCode, new HashMap<>());
        }
    }

    /**
     * Get translated string for the given key (source string in Russian).
     * Returns the key itself if translation not found.
     */
    public static String loc(String key) {
        if (TextUtils.isEmpty(key)) {
            return key;
        }
        Map<String, String> map = sLanguageMaps.get(sCurrentLanguage);
        if (map != null) {
            String translation = map.get(key);
            if (!TextUtils.isEmpty(translation)) {
                return translation;
            }
        }
        // Fallback to key (Russian)
        return key;
    }

    /**
     * Get translated string with format arguments.
     */
    public static String locFormat(String key, Object... args) {
        String format = loc(key);
        if (format == null) {
            format = key;
        }
        return String.format(format, args);
    }

    /**
     * Check if the current language is right-to-left.
     */
    public static boolean isRightToLeft() {
        String lang = getCurrentLanguage();
        return "ar".equals(lang) || "fa".equals(lang) || "he".equals(lang) || "ur".equals(lang);
    }

    /**
     * Format a large number similar to YTCompactCount.
     * Rules: up to 1000 as is; 1000-999999: one decimal digit if <10000, else integer; etc.
     * Suffixes: тыс., млн, млрд.
     * Decimal separator: dot for en, comma for ru, etc. (we'll use locale-sensitive via DecimalFormat? but we follow YouTube rules)
     * We'll implement as per original.
     */
    public static String compactCount(long value) {
        if (value < 1000) {
            return Long.toString(Math.max(0L, value));
        }
        long unit;
        String pattern;
        if (value < 1000000L) {
            unit = 1000L;
            pattern = "%s тыс.";
        } else if (value < 1000000000L) {
            unit = 1000000L;
            pattern = "%s млн";
        } else {
            unit = 1000000000L;
            pattern = "%s млрд";
        }
        long whole = value / unit;
        String number;
        if (whole < 10) {
            long tenths = value * 10 / unit;
            if (tenths % 10 == 0) {
                number = Long.toString(tenths / 10);
            } else {
                // Use decimal separator based on language? For simplicity, we use dot.
                // But we should follow language: Russian uses comma.
                String sep = ",";
                if ("ru".equals(sCurrentLanguage) || "uk".equals(sCurrentLanguage) || "be".equals(sCurrentLanguage) ||
                        "kk".equals(sCurrentLanguage) || "kg".equals(sCurrentLanguage)) {
                    sep = ",";
                } else {
                    sep = ".";
                }
                number = Long.toString(tenths / 10) + sep + Long.toString(tenths % 10);
            }
        } else {
            number = Long.toString(whole);
        }
        return String.format(Locale.US, pattern, number);
    }
}