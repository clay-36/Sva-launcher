package com.sva.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ScreensaverPreferencesTest {
    @Test public void followsHomeUnlessSeparateAlbumIsSet() {
        assertEquals("home", ScreensaverPreferences.resolveAlbum("", "home"));
        assertEquals("new-home", ScreensaverPreferences.resolveAlbum("  ", "new-home"));
        assertEquals("separate", ScreensaverPreferences.resolveAlbum(" separate ", "home"));
        assertEquals("", ScreensaverPreferences.resolveAlbum(null, null));
    }
    @Test public void restoredInvalidPreferencesHaveSafeDefaults() {
        assertEquals(0, ScreensaverPreferences.themeIndex("removed-theme"));
        assertEquals(0, ScreensaverPreferences.idleMinutes(-1));
        assertEquals(0, ScreensaverPreferences.idleMinutes(100));
        assertEquals(3, ScreensaverPreferences.idleMinutes(3));
        assertEquals(20, ScreensaverPreferences.interval(-1));
        assertEquals(20, ScreensaverPreferences.interval(0));
        assertEquals(60, ScreensaverPreferences.interval(60));
    }
    @Test public void onlyPublicGooglePhotosLinkHostsAreAccepted() {
        assertTrue(ScreensaverPreferences.validAlbum(""));
        assertTrue(ScreensaverPreferences.validAlbum("https://photos.app.goo.gl/album"));
        assertTrue(ScreensaverPreferences.validAlbum("https://photos.google.com/share/album"));
        assertFalse(ScreensaverPreferences.validAlbum("http://photos.google.com/share/album"));
        assertFalse(ScreensaverPreferences.validAlbum("https://photos.google.com.example.com/share/album"));
        assertFalse(ScreensaverPreferences.validAlbum("not a link"));
    }
    @Test public void themeMetadataMatchesRendererIndexes() {
        assertEquals(9, ScreensaverPreferences.THEMES.length);
        assertEquals(ScreensaverPreferences.THEMES.length, ScreensaverPreferences.NAMES.length);
        assertEquals(ScreensaverPreferences.THEMES.length, ScreensaverPreferences.DESCRIPTIONS.length);
        for (int i = 0; i < ScreensaverPreferences.THEMES.length; i++)
            assertEquals(i, ScreensaverPreferences.themeIndex(ScreensaverPreferences.THEMES[i]));
    }
}
