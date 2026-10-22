package com.sva.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/** Shared cinematic palette and saved appearance preference. */
final class LiquidTheme {
    private static Typeface semiboldFont;
    static final String PREF_MODE = "liquidGlassMode";
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String DEFAULT_MODE = DARK;

    private LiquidTheme() { }

    static Typeface font(Context context) {
        synchronized (LiquidTheme.class) {
            if (!"en".equals(AppLanguage.locale().getLanguage()))
                return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            if (semiboldFont == null)
                semiboldFont = context.getResources().getFont(R.font.manrope_semibold);
            return semiboldFont;
        }
    }

    static int alertDialogTheme(Context context) {
        return isLight(context) ? R.style.SvaLightAlertDialogTheme : R.style.SvaAlertDialogTheme;
    }

    static boolean isLight(Context context) {
        // SharedPreferences already caches values in memory. Read the preference so
        // backup restores and other preference writers cannot leave a stale theme.
        String mode = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
                .getString(PREF_MODE, DEFAULT_MODE);
        if (LIGHT.equals(mode)) return true;
        if (DARK.equals(mode)) return false;
        int nightMask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMask == Configuration.UI_MODE_NIGHT_NO;
    }

    static int background(Context context) { return isLight(context) ? 0xFFE9EFEC : 0xFF071417; }
    static int surface(Context context) { return isLight(context) ? 0xE9F9FCF8 : 0xB90A1B1F; }
    static int raisedSurface(Context context) { return isLight(context) ? 0xFFFDFEFB : 0xE0173032; }
    static int text(Context context) { return isLight(context) ? 0xFF132B2B : 0xFFF7FAF7; }
    static int secondaryText(Context context) { return isLight(context) ? 0xFF4D6968 : 0xFFB8CFCC; }
    static int border(Context context) { return isLight(context) ? 0x887C9E9A : 0x6684B0AA; }
    static int accent(Context context) { return isLight(context) ? 0xFF087D91 : 0xFF7BE7F4; }
    static int lavender(Context context) { return secondaryText(context); }

    static Drawable scenicBackground(Context context) {
        Drawable photo = context.getDrawable(R.drawable.mountain_wallpaper);
        GradientDrawable veil = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                isLight(context)
                        ? new int[]{0xB8E9F1EE, 0xE8E9F1EE, 0xF4E9F1EE}
                        : new int[]{0x91041114, 0xC907171A, 0xF4071417});
        return new LayerDrawable(new Drawable[]{photo, veil});
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void animateFocus(android.view.View view, boolean focused) {
        Motion.interaction(view, false);
    }

    static void animateContainedFocus(android.view.View view, boolean focused) {
        Motion.interaction(view, true);
    }
}
