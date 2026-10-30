package com.sva.launcher;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** Gradient artwork used when a news story has no usable image. */
final class NewsArtwork {
    private static final int[][] COLORS = {
            {0xFF38566C, 0xFF172937, 0xFF101820},
            {0xFF66506E, 0xFF342B48, 0xFF171A2B},
            {0xFF8B5A42, 0xFF503B3B, 0xFF211D27},
            {0xFF316C68, 0xFF204747, 0xFF14272D},
            {0xFF6D6542, 0xFF3F443A, 0xFF20272B}
    };

    private NewsArtwork() { }

    static void apply(View view, int storyIndex, float cornerRadiusPx) {
        int[] colors = COLORS[Math.floorMod(storyIndex, COLORS.length)];
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        gradient.setCornerRadius(cornerRadiusPx);
        view.setBackground(gradient);
    }
}
