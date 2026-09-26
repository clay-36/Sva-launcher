package com.deo.launcher;

import android.content.Context;
import android.content.res.Configuration;

/** Shared cinematic palette and saved appearance preference. */
final class LiquidTheme {
    static final String PREF_MODE = "liquidGlassMode";
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";

    private LiquidTheme() { }

    static boolean isLight(Context context) {
        String mode = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
                .getString(PREF_MODE, SYSTEM);
        if (LIGHT.equals(mode)) return true;
        if (DARK.equals(mode)) return false;
        int nightMask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMask == Configuration.UI_MODE_NIGHT_NO;
    }

    static int background(Context context) { return isLight(context) ? 0xFFF5F5F3 : 0xFF090909; }
    static int surface(Context context) { return isLight(context) ? 0xF2FFFFFF : 0xF21B1B1B; }
    static int raisedSurface(Context context) { return isLight(context) ? 0xFFFFFFFF : 0xFF2A2A2A; }
    static int text(Context context) { return isLight(context) ? 0xFF171717 : 0xFFF5F5F5; }
    static int secondaryText(Context context) { return isLight(context) ? 0xFF626262 : 0xFFB7B7B7; }
    static int border(Context context) { return isLight(context) ? 0xFFCCCCCC : 0xFF505050; }
    static int accent(Context context) { return isLight(context) ? 0xFFC8101A : 0xFFE50914; }
    static int lavender(Context context) { return secondaryText(context); }
}
