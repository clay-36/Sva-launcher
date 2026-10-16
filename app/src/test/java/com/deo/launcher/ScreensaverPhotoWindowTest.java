package com.sva.launcher;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class ScreensaverPhotoWindowTest {
    @Test public void emptyAndSinglePhotoAlbumsDoNotDuplicateCurrentPhoto() {
        assertTrue(ScreensaverPhotoWindow.candidates(Collections.emptyList(), Collections.emptyList(), "", 0).isEmpty());
        assertTrue(ScreensaverPhotoWindow.candidates(Arrays.asList("a"), Arrays.asList("a"), "a", 0).isEmpty());
        assertEquals(Arrays.asList("a"), ScreensaverPhotoWindow.sceneKeys("a", Arrays.asList("a"), 3));
    }
    @Test public void twoPhotosRotateWithoutRepeatingFrames() {
        assertEquals(Arrays.asList("b"), ScreensaverPhotoWindow.candidates(Arrays.asList("a","b"), Arrays.asList("a","b"), "a", 0));
        assertEquals(Arrays.asList("b","a"), ScreensaverPhotoWindow.sceneKeys("b", Arrays.asList("a","b"), 3));
    }
    @Test public void unseenPhotosFillTheWindowAndFailuresHaveBoundedCandidates() {
        assertEquals(Arrays.asList("d","e","b"), ScreensaverPhotoWindow.candidates(
                Arrays.asList("a","b","c","d","e"), Arrays.asList("a","b","c"), "a", 1));
        assertEquals(Arrays.asList("d","a","b"), ScreensaverPhotoWindow.sceneKeys("d", Arrays.asList("a","b","c"), 3));
    }
    @Test public void duplicateUrlsAndSinglePhotoThemesStayBounded() {
        assertEquals(Arrays.asList("b","c"), ScreensaverPhotoWindow.candidates(
                Arrays.asList("a","b","b","c"), Arrays.asList("a"), "a", 0));
        assertEquals(Arrays.asList("d"), ScreensaverPhotoWindow.sceneKeys("d", Arrays.asList("a","b","c"), 1));
    }
}
