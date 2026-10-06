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

    private LiquidTheme() { }

    static Typeface font(Context context) {
        synchronized (LiquidTheme.class) {
            if (semiboldFont == null)
                semiboldFont = context.getResources().getFont(R.font.manrope_semibold);
            return semiboldFont;
        }
    }

    static int alertDialogTheme(Context context) {
        return isLight(context) ? R.style.SvaLightAlertDialogTheme : R.style.SvaAlertDialogTheme;
    }

    static boolean isLight(Context context) {
        String mode = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
                .getString(PREF_MODE, SYSTEM);
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
    static int accent(Context context) { return isLight(context) ? 0xFF88A915 : 0xFFD7FF57; }
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
        view.animate().cancel();
        view.animate().scaleX(1f).scaleY(1f)
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
