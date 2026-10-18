package com.sva.launcher;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public final class FavoriteChannelsTest {

    private IptvRepository.Channel channel(String name, String id, String group) {
        return new IptvRepository.Channel(name, "https://stream.test/" + id, id, group, "", "", "", Collections.emptyMap());
    }

    @Test
    public void isFavoriteIdentifiesChannelsByStableKey() {
        IptvRepository.Channel ch1 = channel("STAR Plus", "star-plus", "Hindi");
        IptvRepository.Channel ch2 = channel("Sony SAB", "sony-sab", "Hindi");
        Set<String> favorites = new HashSet<>(Collections.singletonList(ch1.stableKey()));

        assertTrue(FavoriteChannels.isFavorite(favorites, ch1));
        assertFalse(FavoriteChannels.isFavorite(favorites, ch2));
        assertFalse(FavoriteChannels.isFavorite(null, ch1));
        assertFalse(FavoriteChannels.isFavorite(favorites, null));
    }

    @Test
    public void toggleAddsAndRemovesChannelKey() {
        IptvRepository.Channel ch = channel("STAR Plus", "star-plus", "Hindi");
        Set<String> favorites = new HashSet<>();

        boolean added = FavoriteChannels.toggle(favorites, ch);
        assertTrue(added);
        assertTrue(favorites.contains(ch.stableKey()));

        boolean removed = FavoriteChannels.toggle(favorites, ch);
        assertFalse(removed);
        assertFalse(favorites.contains(ch.stableKey()));
    }

    @Test
    public void filterFavoritesExcludesHiddenAndNonFavoriteChannels() {
        IptvRepository.Channel ch1 = channel("STAR Plus", "star-plus", "Hindi");
        IptvRepository.Channel ch2 = channel("Sony SAB", "sony-sab", "Hindi");
        IptvRepository.Channel ch3 = channel("Zee TV", "zee-tv", "Hindi");

        List<IptvRepository.Channel> all = Arrays.asList(ch1, ch2, ch3);
        Set<String> favorites = new HashSet<>(Arrays.asList(ch1.stableKey(), ch2.stableKey()));
        Set<String> hidden = new HashSet<>(Collections.singletonList(ch2.stableKey()));

        List<IptvRepository.Channel> filtered = FavoriteChannels.filterFavorites(all, hidden, favorites);
        assertEquals(1, filtered.size());
        assertEquals("STAR Plus", filtered.get(0).name);
    }

    @Test
    public void backupTypesIncludeFavoriteChannels() throws Exception {
        org.json.JSONObject preferences = new org.json.JSONObject();
        org.json.JSONObject favObj = new org.json.JSONObject();
        favObj.put("type", "string-set");
        org.json.JSONArray array = new org.json.JSONArray();
        array.put("id:star-plus");
        favObj.put("value", array);
        preferences.put("favoriteChannels", favObj);

        java.util.Map<String, Object> decoded = LauncherBackup.decodePreferences(preferences);
        assertTrue(decoded.containsKey("favoriteChannels"));
        @SuppressWarnings("unchecked")
        Set<String> decodedSet = (Set<String>) decoded.get("favoriteChannels");
        assertNotNull(decodedSet);
        assertTrue(decodedSet.contains("id:star-plus"));
    }
}
