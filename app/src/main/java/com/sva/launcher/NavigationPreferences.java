package com.sva.launcher;

import java.util.ArrayList;
import java.util.List;

/** Stable navigation names in preferences; Home is always outside the reorderable list. */
final class NavigationPreferences {
    static final String ORDER = "navigationOrder";
    private static final String[] NAMES = {"live_tv", "games", "events", "saved", "news", "activities"};

    private static final String[] HIDDEN_KEYS = {"hideLiveTvNavigation", "hideGamesNavigation", "hideEventsNavigation",
            "hideSavedNavigation", "hideNewsNavigation", "hideActivitiesNavigation"};

    static String hiddenKey(int tab) {
        return tab >= 1 && tab <= HIDDEN_KEYS.length ? HIDDEN_KEYS[tab - 1] : null;
    }

    private NavigationPreferences() { }

    static List<Integer> order(String saved, int legacyLivePosition) {
        ArrayList<Integer> result = new ArrayList<>();
        if (saved != null) for (String name : saved.split(",")) {
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equals(name.trim()) && !result.contains(i + 1)) result.add(i + 1);
            }
        }
        ArrayList<Integer> defaults = new ArrayList<>();
        defaults.add(6); defaults.add(2); defaults.add(3); defaults.add(4); defaults.add(5);
        defaults.add(Math.max(0, Math.min(3, legacyLivePosition)), 1);
        for (int tab : defaults) if (!result.contains(tab)) result.add(tab);
        return result;
    }

    static String encode(List<Integer> tabs) {
        ArrayList<String> names = new ArrayList<>();
        for (int tab : tabs) if (tab >= 1 && tab <= NAMES.length && !names.contains(NAMES[tab - 1]))
            names.add(NAMES[tab - 1]);
        return String.join(",", names);
    }
}
