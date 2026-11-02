package com.rinda.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class LocaleHelper {

    private static final String PREF_LOCALE = "rinda_app_locale_pref";
    private static final String KEY_LANG = "selected_language";

    public static void setLocale(Context context, String langCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_LOCALE, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, langCode).apply();

        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(langCode);
        AppCompatDelegate.setApplicationLocales(appLocale);
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_LOCALE, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANG, "en");
    }

    public static boolean isKinyarwanda(Context context) {
        return "rw".equalsIgnoreCase(getLanguage(context));
    }
}
