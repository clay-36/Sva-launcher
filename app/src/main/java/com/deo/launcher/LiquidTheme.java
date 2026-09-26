package com.deo.launcher;

import android.content.Context;
import android.content.res.Configuration;

/** Shared liquid-glass palette and saved appearance preference. */
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

    static int surface(Context context) { return isLight(context) ? 0xDDF8FAFC : 0xC9161D2A; }
    static int raisedSurface(Context context) { return isLight(context) ? 0xF2FFFFFF : 0xF0263444; }
    static int text(Context context) { return isLight(context) ? 0xFF17212B : 0xFFF4F6FA; }
    static int secondaryText(Context context) { return isLight(context) ? 0xFF536273 : 0xFFA1AEC4; }
    static int border(Context context) { return isLight(context) ? 0x6693A5B8 : 0x557F91A6; }
    static int accent(Context context) { return isLight(context) ? 0xFF087F73 : 0xFF6EE8D3; }
    static int lavender(Context context) { return isLight(context) ? 0xFF6256A6 : 0xFFAEB4FF; }
}
