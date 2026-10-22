package com.sva.launcher;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import java.util.Arrays;
import java.util.Locale;

/** One locale policy for activities, foreground work, and the independent DreamService. */
final class AppLanguage {
    static final String KEY = "appLanguage";
    static final String[] TAGS = {"", "en", "hi", "es", "fr", "ar", "pt", "zh-Hans", "zh-Hant",
            "de", "ja", "ko", "ru", "id", "tr", "bn", "ur", "ta", "te", "mr", "gu", "kn", "ml", "pa"};
    private static final String[] ENGLISH_NAMES = {"", "English", "Hindi", "Spanish", "French", "Arabic",
            "Portuguese", "Chinese (Simplified)", "Chinese (Traditional)", "German", "Japanese", "Korean",
            "Russian", "Indonesian", "Turkish", "Bengali", "Urdu", "Tamil", "Telugu", "Marathi",
            "Gujarati", "Kannada", "Malayalam", "Punjabi"};
    private static volatile Context application;
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static Context localized;
    private static Configuration localizedConfiguration;
    private static String localizedSelection;
    private AppLanguage() { }

    static String sanitize(String tag) {
        return tag != null && Arrays.asList(TAGS).contains(tag) ? tag : "";
    }
    static String supportedTag(Locale locale) {
        if ("zh".equals(locale.getLanguage())) {
            String country = locale.getCountry();
            return "Hant".equals(locale.getScript()) || "TW".equals(country) || "HK".equals(country) || "MO".equals(country)
                    ? "zh-Hant" : "zh-Hans";
        }
        String lang = locale.getLanguage();
        if ("in".equals(lang)) return "id";
        return sanitize(lang);
    }
    static String firstSupported(Locale... locales) {
        for (Locale locale : locales) {
            String tag = supportedTag(locale);
            if (!tag.isEmpty()) return tag;
        }
        return "en";
    }
    private static LocaleList effectiveLocales(Context context) {
        String tag = selected(context);
        if (tag.isEmpty()) {
            LocaleList system = Build.VERSION.SDK_INT >= 33
                    ? context.getSystemService(LocaleManager.class).getSystemLocales()
                    : Resources.getSystem().getConfiguration().getLocales();
            Locale[] choices = new Locale[system.size()];
            for (int i = 0; i < choices.length; i++) choices[i] = system.get(i);
            tag = firstSupported(choices);
        }
        if (tag.isEmpty()) tag = "en";
        return LocaleList.forLanguageTags("en".equals(tag) ? "en" : tag + ",en");
    }
    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }
    static void initialize(Context context) {
        application = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        SharedPreferences prefs = preferences(context);
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("appLanguageMigrated", false)) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            String saved = sanitize(prefs.getString(KEY, ""));
            // Do not overwrite a choice already made in Android's App Languages screen.
            if (manager.getApplicationLocales().isEmpty() && !saved.isEmpty())
                manager.setApplicationLocales(LocaleList.forLanguageTags(saved));
            prefs.edit().putBoolean("appLanguageMigrated", true).apply();
        }
        sync(context);
    }
    static String selected(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleList locales = context.getSystemService(LocaleManager.class).getApplicationLocales();
            return locales.isEmpty() ? "" : supportedTag(locales.get(0));
        }
        return sanitize(preferences(context).getString(KEY, ""));
    }
    static void sync(Context context) {
        SharedPreferences prefs = preferences(context);
        String current = selected(context);
        if (!current.equals(prefs.getString(KEY, null))) {
            prefs.edit().putString(KEY, current).apply();
            invalidate();
        }
    }
    static void select(Context context, String tag) {
        String valid = sanitize(tag);
        preferences(context).edit().putString(KEY, valid).apply();
        if (Build.VERSION.SDK_INT >= 33)
            context.getSystemService(LocaleManager.class).setApplicationLocales(LocaleList.forLanguageTags(valid));
        invalidate();
    }
    static void restored(Context context) {
        select(context, preferences(context).getString(KEY, ""));
    }
    static Context wrap(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        LocaleList locales = effectiveLocales(context);
        config.setLocales(locales);
        config.setLayoutDirection(locales.get(0));
        return context.createConfigurationContext(config);
    }
    static synchronized void invalidate() { localized = null; }
    static synchronized Context context() {
        if (application == null) throw new IllegalStateException("Sva language context is not initialized");
        Configuration current = application.getResources().getConfiguration();
        String selection = preferences(application).getString(KEY, "");
        if (localized == null || !current.equals(localizedConfiguration) || !selection.equals(localizedSelection)) {
            localized = wrap(application);
            localizedConfiguration = new Configuration(current);
            localizedSelection = selection;
        }
        return localized;
    }
    static Locale locale() { return context().getResources().getConfiguration().getLocales().get(0); }
    static String get(int id, Object... values) {
        String template = context().getString(id);
        if (values.length == 0) return template;
        Object[] display = values.clone();
        for (int i = 0; i < display.length; i++) {
            if (display[i] instanceof Number && template.contains("%" + (i + 1) + "$s"))
                display[i] = java.text.NumberFormat.getInstance(locale()).format(display[i]);
        }
        return String.format(locale(), template, display);
    }
    // Domain helpers retain deterministic English diagnostics when run without Android in JVM tests.
    static String text(int id, String english, Object... values) {
        return application == null ? (values.length == 0 ? english : String.format(Locale.US, english, values)) : get(id, values);
    }
    static String quantity(int id, int count) {
        return context().getResources().getQuantityString(id, count, count);
    }
    static String quantity(int id, int count, String one, String other) {
        return application == null ? String.format(Locale.US, count == 1 ? one : other, count) : quantity(id, count);
    }
    static String duration(long value, boolean slideshow, boolean everyLaunch) {
        if (value == SchedulePreferences.MANUAL) return get(slideshow ? R.string.ui_paused : R.string.ui_manual_only);
        if (everyLaunch && value == SchedulePreferences.EVERY_LAUNCH) return get(R.string.ui_every_launch);
        if (value < SchedulePreferences.MINUTE) return quantity(R.plurals.duration_seconds, (int)(value / SchedulePreferences.SECOND));
        if (value < SchedulePreferences.HOUR) return quantity(R.plurals.duration_minutes, (int)(value / SchedulePreferences.MINUTE));
        if (value < SchedulePreferences.DAY) return quantity(R.plurals.duration_hours, (int)(value / SchedulePreferences.HOUR));
        return quantity(R.plurals.duration_days, (int)(value / SchedulePreferences.DAY));
    }
    static String category(String name) {
        return FavoriteChannels.CATEGORY_FAVORITES.equals(name) ? get(R.string.favourites) : name;
    }
    static String number(long value) {
        return java.text.NumberFormat.getIntegerInstance(locale()).format(value);
    }
    static String bidi(String value) {
        return android.text.BidiFormatter.getInstance(locale()).unicodeWrap(value);
    }
    static String[] labels(Context context) {
        String[] labels = new String[TAGS.length];
        labels[0] = get(R.string.language_follow_tv);
        for (int i = 1; i < labels.length; i++) {
            Locale locale = Locale.forLanguageTag(TAGS[i]);
            String nativeName = locale.getDisplayName(locale);
            labels[i] = "en".equals(TAGS[i]) ? nativeName : nativeName + " · " + ENGLISH_NAMES[i];
        }
        return labels;
    }
}
