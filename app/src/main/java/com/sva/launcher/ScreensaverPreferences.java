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
    static final String WEATHER = "screensaverWeather";
    static final String EVENTS = "screensaverEvents";
    static final String[] THEMES = {"cinema", "darkroom", "editorial", "floating_prints", "monolith", "prism", "silhouette", "travel_journal", "photo_weave"};
    static final String[] NAMES = {"Cinema", "Darkroom", "Editorial", "Floating Prints", "Monolith", "Prism", "Silhouette", "Travel Journal", "Photo Weave"};
    static final String[] DESCRIPTIONS = {
            "Slow camera movement, soft black edge shading and a quiet cinematic fade.",
            "An amber-lit contact sheet, warm photographs and a passing trace of light.",
            "An oversized photograph, ivory margins and beautifully set personal captions.",
            "Album photographs drift as layered paper prints against colors drawn from your images.",
            "A sculptural floating photograph, deep image-inspired color and quiet movement.",
            "Translucent planes, emerald light ribbons and a luminous photographic centrepiece.",
            "Monochrome photography revealed through broad, moving vertical panels.",
            "Warm textured paper, photographic keepsakes and delicate route lines.",
            "Different album photographs woven into a slowly changing asymmetric grid."
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
        String[] legacy = {"cinema", "vintage", "memory", "postcards", "gallery", "aurora", "noir", "storybook", "mosaic"};
        for (int i = 0; i < legacy.length; i++) if (legacy[i].equals(theme)) return i;
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
