package com.sva.launcher;

import android.content.Context;
import android.widget.FrameLayout;

/** Same content and teardown for preview, launcher idle, and Android DreamService. */
final class ScreensaverContent extends FrameLayout {
    private final ScreensaverView photos;
    private final AerialVideoView aerial;
    ScreensaverContent(Context context) {
        this(context, context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
                .getBoolean(AerialCatalog.SCREENSAVER, false));
    }
    ScreensaverContent(Context context, boolean enabled) {
        super(context);
        aerial = enabled ? new AerialVideoView(context, true) : null;
        if (aerial != null) addView(aerial, new LayoutParams(-1, -1));
        photos = new ScreensaverView(context, enabled);
        addView(photos, new LayoutParams(-1, -1));
    }
    void start() { photos.start(); if (aerial != null) aerial.start(); }
    void stop() { photos.stop(); if (aerial != null) aerial.stop(); }
}
