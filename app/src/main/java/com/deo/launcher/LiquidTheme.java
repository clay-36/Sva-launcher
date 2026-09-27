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

    static int background(Context context) { return isLight(context) ? 0xFFE7EDF1 : 0xFF0B1420; }
    static int surface(Context context) { return isLight(context) ? 0xD9EDF3F7 : 0xBA101E2C; }
    static int raisedSurface(Context context) { return isLight(context) ? 0xF2FFFFFF : 0xD940596D; }
    static int text(Context context) { return isLight(context) ? 0xFF152A3B : 0xFFF3F8FC; }
    static int secondaryText(Context context) { return isLight(context) ? 0xFF415668 : 0xFFBECDD9; }
    static int border(Context context) { return isLight(context) ? 0x88FFFFFF : 0x55D8EBFA; }
    static int accent(Context context) { return isLight(context) ? 0xFF246080 : 0xFFBCE7FF; }
    static int lavender(Context context) { return secondaryText(context); }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void animateFocus(android.view.View view, boolean focused) {
        view.animate().cancel();
        view.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f)
                .translationZ(focused ? dp(view.getContext(), 8) : 0)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 180 : 0).start();
    }

    /** Focus animation for controls contained by a fixed rounded panel. */
    static void animateContainedFocus(android.view.View view, boolean focused) {
        view.animate().cancel();
        view.animate().scaleX(1f).scaleY(1f)
                .translationZ(focused ? dp(view.getContext(), 4) : 0)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 140 : 0).start();
    }
}
