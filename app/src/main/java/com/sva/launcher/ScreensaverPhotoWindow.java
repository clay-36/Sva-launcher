package com.sva.launcher;

import java.util.*;

/** Selection policy shared by cached startup and the bounded slideshow window. */
final class ScreensaverPhotoWindow {
    private ScreensaverPhotoWindow() { }

    static List<String> candidates(List<String> urls, Collection<String> loaded, String current, int index) {
        LinkedHashSet<String> unseen = new LinkedHashSet<>(), existing = new LinkedHashSet<>();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(Math.floorMod(index + i, urls.size()));
            if (url.equals(current)) continue;
            if (loaded.contains(url)) existing.add(url); else unseen.add(url);
        }
        // Fill the first scene with distinct photos before rotating already loaded images.
        List<String> result = new ArrayList<>(unseen);
        result.addAll(existing);
        return result.subList(0, Math.min(3, result.size()));
    }

    static List<String> sceneKeys(String lead, Collection<String> previous, int limit) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(lead);
        keys.addAll(previous);
        List<String> result = new ArrayList<>(keys);
        return new ArrayList<>(result.subList(0, Math.min(limit, result.size())));
    }
}
