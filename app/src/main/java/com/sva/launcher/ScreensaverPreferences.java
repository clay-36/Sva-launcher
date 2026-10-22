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

    static String[] names() {
        return new String[]{AppLanguage.get(R.string.ui_cinema), AppLanguage.get(R.string.ui_darkroom), AppLanguage.get(R.string.ui_editorial), AppLanguage.get(R.string.ui_floating_prints), AppLanguage.get(R.string.ui_monolith), AppLanguage.get(R.string.ui_prism), AppLanguage.get(R.string.ui_silhouette), AppLanguage.get(R.string.ui_travel_journal), AppLanguage.get(R.string.ui_photo_weave)};
    }

    static String[] descriptions() {
        return new String[]{AppLanguage.get(R.string.ui_slow_camera_movement_soft_black_edge_shading_and_a_quiet_cinemat), AppLanguage.get(R.string.ui_an_amber_lit_contact_sheet_warm_photographs_and_a_passing_trace), AppLanguage.get(R.string.ui_an_oversized_photograph_ivory_margins_and_beautifully_set_person), AppLanguage.get(R.string.ui_album_photographs_drift_as_layered_paper_prints_against_colors_d), AppLanguage.get(R.string.ui_a_sculptural_floating_photograph_deep_image_inspired_color_and_q), AppLanguage.get(R.string.ui_translucent_planes_emerald_light_ribbons_and_a_luminous_photogra), AppLanguage.get(R.string.ui_monochrome_photography_revealed_through_broad_moving_vertical_pa), AppLanguage.get(R.string.ui_warm_textured_paper_photographic_keepsakes_and_delicate_route_li), AppLanguage.get(R.string.ui_different_album_photographs_woven_into_a_slowly_changing_asymmet)};
    }
}
