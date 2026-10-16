package com.sva.launcher;

import android.service.dreams.DreamService;

/** Selected by Android TV under its system Screensaver / Ambient settings. */
public final class ScreensaverService extends DreamService {
    private ScreensaverView slideshow;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        slideshow = new ScreensaverView(this);
        setContentView(slideshow);
    }
    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (slideshow == null) {
            slideshow = new ScreensaverView(this);
            setContentView(slideshow);
        }
        if (slideshow != null) slideshow.start();
    }
    @Override public void onDreamingStopped() {
        if (slideshow != null) slideshow.stop();
        slideshow = null;
        super.onDreamingStopped();
    }
    @Override public void onDetachedFromWindow() {
        if (slideshow != null) slideshow.stop();
        slideshow = null;
        super.onDetachedFromWindow();
    }
}
