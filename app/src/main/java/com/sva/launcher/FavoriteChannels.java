package com.sva.launcher;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FavoriteChannels {
    public static final String PREF_FAVORITES = "favoriteChannels";
    public static final String CATEGORY_FAVORITES = "Favourites";

    private FavoriteChannels() { }

    public static Set<String> load(SharedPreferences preferences) {
        if (preferences == null) return new HashSet<>();
        return new HashSet<>(preferences.getStringSet(PREF_FAVORITES, Collections.emptySet()));
    }

    public static void save(SharedPreferences preferences, Set<String> favoriteKeys) {
        if (preferences == null) return;
        preferences.edit().putStringSet(PREF_FAVORITES, new HashSet<>(favoriteKeys)).apply();
    }

    public static boolean isFavorite(Set<String> favoriteKeys, IptvRepository.Channel channel) {
        return channel != null && favoriteKeys != null && favoriteKeys.contains(channel.stableKey());
    }

    public static boolean toggle(Set<String> favoriteKeys, IptvRepository.Channel channel) {
        if (channel == null || favoriteKeys == null) return false;
        String key = channel.stableKey();
        if (favoriteKeys.contains(key)) {
            favoriteKeys.remove(key);
            return false;
        } else {
            favoriteKeys.add(key);
            return true;
        }
    }

    public static List<IptvRepository.Channel> filterFavorites(List<IptvRepository.Channel> channels,
                                                               Set<String> hiddenKeys,
                                                               Set<String> favoriteKeys) {
        List<IptvRepository.Channel> favorites = new ArrayList<>();
        if (channels == null || favoriteKeys == null || favoriteKeys.isEmpty()) return favorites;
        for (IptvRepository.Channel channel : channels) {
            if (HiddenChannels.isHidden(hiddenKeys, channel)) continue;
            if (favoriteKeys.contains(channel.stableKey())) {
                favorites.add(channel);
            }
        }
        return favorites;
    }
}
