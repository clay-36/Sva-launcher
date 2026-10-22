package com.sva.launcher;

import android.service.dreams.DreamService;

/** Selected by Android TV under its system Screensaver / Ambient settings. */
public final class ScreensaverService extends DreamService {
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }
    private ScreensaverView slideshow;
    private boolean dreaming;
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (slideshow != null) slideshow.stop();
        slideshow = new ScreensaverView(AppLanguage.wrap(this));
        setContentView(slideshow);
        if (dreaming) slideshow.start();
    }
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
        dreaming = true;
        if (slideshow == null) {
            slideshow = new ScreensaverView(this);
            setContentView(slideshow);
        }
        if (slideshow != null) slideshow.start();
    }
    @Override public void onDreamingStopped() {
        dreaming = false;
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
