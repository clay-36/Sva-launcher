package com.sva.launcher;

import android.content.SharedPreferences;
import java.net.URI;
import java.util.Locale;

final class ScreensaverPreferences {
    static final String ALBUM = "screensaverAlbum";
    static final String THEME = "screensaverTheme";
    static final String INTERVAL = "screensaverIntervalSeconds";
    static final String CAPTION = "screensaverCaption";
    static final String IDLE = "screensaverIdleMinutes";
    static final int[] IDLE_MINUTES = {0, 1, 3, 5, 10};
    static final String CLOCK = "screensaverClock";
    static final String[] THEMES = {"cinema", "vintage", "memory", "postcards", "gallery", "aurora", "noir", "storybook", "mosaic"};
    static final String[] NAMES = {"Cinema", "Vintage Reel", "Memory Lane", "Postcards", "Gallery", "Aurora", "Noir", "Storybook", "Mosaic"};
    static final String[] DESCRIPTIONS = {
            "Slow camera movement, soft surrounding shadows and a quiet cinematic fade.",
            "Warm film tones, a flickering projector, film perforations and numbered frames.",
            "A gentle tribute: monochrome portraits, soft floating lights and your own dedication.",
            "Layered instant prints, a paper backdrop and a handwritten travel-journal mood.",
            "A full photograph in a floating gallery frame, with colors drawn from the image.",
            "A luminous floating photograph against gently flowing emerald and violet light.",
            "Dramatic black-and-white photography, deep edge shadows and a widescreen composition.",
            "An open journal: a photograph on one page, your words in elegant type on the other.",
            "A staggered three-panel photo wall with generous spacing and slow floating motion."
    };
    static final int[] INTERVALS = {12, 20, 30, 60};
    private ScreensaverPreferences() { }
    static String album(SharedPreferences prefs) {
        return resolveAlbum(prefs.getString(ALBUM, ""), prefs.getString("wallpaperAlbum", ""));
    }
    static String resolveAlbum(String custom, String home) {
        return custom == null || custom.trim().isEmpty() ? (home == null ? "" : home.trim()) : custom.trim();
    }
    static boolean validAlbum(String value) {
        if (value.isEmpty()) return true;
        try {
            URI uri = new URI(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && (host.equals("photos.google.com")
                    || host.equals("photos.app.goo.gl") || host.equals("goo.gl"));
        } catch (Exception e) { return false; }
    }
    static int themeIndex(String theme) {
        for (int i = 0; i < THEMES.length; i++) if (THEMES[i].equals(theme)) return i;
        return 0;
    }
    static int idleMinutes(int value) {
        for (int allowed : IDLE_MINUTES) if (allowed == value) return value;
        return 0;
    }
    static int interval(int seconds) {
        for (int allowed : INTERVALS) if (allowed == seconds) return seconds;
        return 20;
    }
}
