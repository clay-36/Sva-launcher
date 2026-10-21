package com.sva.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class LauncherBackupTest {

    @Test
    public void decodePreferencesExtractsHiddenAndFavoriteChannels() throws Exception {
        JSONObject encoded = new JSONObject();

        JSONObject hiddenObj = new JSONObject();
        hiddenObj.put("type", "string-set");
        JSONArray hiddenArray = new JSONArray();
        hiddenArray.put("id:ts550");
        hiddenArray.put("id:ts86");
        hiddenObj.put("value", hiddenArray);
        encoded.put("hiddenChannels", hiddenObj);

        JSONObject favObj = new JSONObject();
        favObj.put("type", "string-set");
        JSONArray favArray = new JSONArray();
        favArray.put("id:ts8");
        favObj.put("value", favArray);
        encoded.put("favoriteChannels", favObj);

        Map<String, Object> decoded = LauncherBackup.decodePreferences(encoded);
        assertNotNull(decoded);

        assertTrue(decoded.get("hiddenChannels") instanceof Set);
        @SuppressWarnings("unchecked")
        Set<String> hidden = (Set<String>) decoded.get("hiddenChannels");
        assertEquals(2, hidden.size());
        assertTrue(hidden.contains("id:ts550"));
        assertTrue(hidden.contains("id:ts86"));

        assertTrue(decoded.get("favoriteChannels") instanceof Set);
        @SuppressWarnings("unchecked")
        Set<String> favorites = (Set<String>) decoded.get("favoriteChannels");
        assertEquals(1, favorites.size());
        assertTrue(favorites.contains("id:ts8"));
    }

    @Test
    public void decodePreferencesSkipsPublicCalendarEvents() throws Exception {
        JSONObject encoded = new JSONObject();
        JSONObject eventObj = new JSONObject();
        eventObj.put("type", "string");
        eventObj.put("value", "[{\"title\":\"Meeting\"}]");
        encoded.put("publicCalendarEvents", eventObj);

        Map<String, Object> decoded = LauncherBackup.decodePreferences(encoded);
        assertNotNull(decoded);
        assertTrue(!decoded.containsKey("publicCalendarEvents"));
    }

    @Test
    public void createOmitsChannelsAndPublicCalendarEvents() throws Exception {
        android.content.SharedPreferences launcher = new FakeSharedPreferences(
                java.util.Collections.singletonMap("publicCalendarEvents", "[{\"title\":\"Event\"}]"),
                java.util.Collections.singletonMap("hiddenChannels", new java.util.HashSet<>(java.util.Arrays.asList("id:ts205")))
        );
        android.content.SharedPreferences iptv = new FakeSharedPreferences(
                java.util.Collections.singletonMap("playlist", "https://example.com/playlist.m3u"),
                java.util.Collections.emptyMap()
        );

        JSONObject backup = LauncherBackup.create(launcher, iptv);
        assertEquals("sva-launcher-backup", backup.getString("format"));
        assertEquals(1, backup.getInt("version"));
        assertTrue(!backup.has("channels"));

        JSONObject launcherObj = backup.getJSONObject("launcher");
        assertTrue(!launcherObj.has("publicCalendarEvents"));
        assertTrue(launcherObj.has("hiddenChannels"));
    }

    @Test
    public void restoreSucceedsWithoutChannelsArray() throws Exception {
        JSONObject backup = new JSONObject();
        backup.put("format", "sva-launcher-backup");
        backup.put("version", 1);
        backup.put("launcher", new JSONObject().put("liquidGlassMode", new JSONObject().put("type", "string").put("value", "dark")));
        backup.put("iptv", new JSONObject().put("playlist", new JSONObject().put("type", "string").put("value", "https://test.m3u")));

        FakeSharedPreferences launcher = new FakeSharedPreferences();
        FakeSharedPreferences iptv = new FakeSharedPreferences();

        LauncherBackup.restore(backup, launcher, iptv);
        assertEquals("dark", launcher.getString("liquidGlassMode", ""));
        assertEquals("https://test.m3u", iptv.getString("playlist", ""));
    }

    @Test
    public void restoreIgnoresLegacyChannelsArray() throws Exception {
        JSONObject backup = new JSONObject();
        backup.put("format", "sva-launcher-backup");
        backup.put("version", 1);
        backup.put("launcher", new JSONObject().put("liquidGlassMode", new JSONObject().put("type", "string").put("value", "dark")));
        backup.put("iptv", new JSONObject().put("playlist", new JSONObject().put("type", "string").put("value", "https://test.m3u")));
        // Legacy backup containing raw channels array
        JSONArray legacyChannels = new JSONArray();
        legacyChannels.put(new JSONObject().put("id", "ts205").put("name", "News18 Rajasthan"));
        backup.put("channels", legacyChannels);

        FakeSharedPreferences launcher = new FakeSharedPreferences();
        FakeSharedPreferences iptv = new FakeSharedPreferences();

        // Restore should succeed and simply ignore the legacy channels array
        LauncherBackup.restore(backup, launcher, iptv);
        assertEquals("dark", launcher.getString("liquidGlassMode", ""));
        assertEquals("https://test.m3u", iptv.getString("playlist", ""));
    }

    @Test public void multipleCalendarUrlsRoundTripButBothCachesAreExcluded() throws Exception {
        String urls = "https://example.com/a.ics\nhttp://192.168.1.20:8080/b.ics";
        FakeSharedPreferences original = new FakeSharedPreferences();
        original.edit().putString("publicCalendarUrl", urls)
                .putString("publicCalendarEvents", "[]").putString(CalendarFeedCache.KEY, "{cached}").commit();
        JSONObject backup = LauncherBackup.create(original, new FakeSharedPreferences());
        JSONObject launcherValues = backup.getJSONObject("launcher");
        assertTrue(!launcherValues.has("publicCalendarEvents"));
        assertTrue(!launcherValues.has(CalendarFeedCache.KEY));
        // Ignore cached source snapshots even in backups made by another version.
        launcherValues.put(CalendarFeedCache.KEY, new JSONObject().put("type", "string").put("value", "{cached}"));
        FakeSharedPreferences restored = new FakeSharedPreferences();
        LauncherBackup.restore(backup, restored, new FakeSharedPreferences());
        assertEquals(urls, restored.getString("publicCalendarUrl", ""));
        assertTrue(!restored.contains(CalendarFeedCache.KEY));
        assertTrue(!restored.contains("publicCalendarEvents"));
    }

    private static final class FakeSharedPreferences implements android.content.SharedPreferences {
        private final Map<String, Object> data = new java.util.HashMap<>();

        FakeSharedPreferences() {}

        FakeSharedPreferences(Map<String, ?> strings, Map<String, Set<String>> stringSets) {
            if (strings != null) data.putAll(strings);
            if (stringSets != null) data.putAll(stringSets);
        }

        @Override public Map<String, ?> getAll() { return new java.util.HashMap<>(data); }
        @Override public String getString(String key, String defValue) { Object v = data.get(key); return v instanceof String ? (String) v : defValue; }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) { Object v = data.get(key); return v instanceof Set ? (Set<String>) v : defValues; }
        @Override public int getInt(String key, int defValue) { Object v = data.get(key); return v instanceof Integer ? (Integer) v : defValue; }
        @Override public long getLong(String key, long defValue) { Object v = data.get(key); return v instanceof Long ? (Long) v : defValue; }
        @Override public float getFloat(String key, float defValue) { Object v = data.get(key); return v instanceof Float ? (Float) v : defValue; }
        @Override public boolean getBoolean(String key, boolean defValue) { Object v = data.get(key); return v instanceof Boolean ? (Boolean) v : defValue; }
        @Override public boolean contains(String key) { return data.containsKey(key); }
        @Override public Editor edit() { return new FakeEditor(this); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private static final class FakeEditor implements Editor {
            private final FakeSharedPreferences target;
            private final Map<String, Object> pending = new java.util.HashMap<>();
            private boolean clearPending = false;

            FakeEditor(FakeSharedPreferences target) { this.target = target; }

            @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> values) { pending.put(key, values); return this; }
            @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
            @Override public Editor remove(String key) { pending.put(key, null); return this; }
            @Override public Editor clear() { clearPending = true; pending.clear(); return this; }
            @Override public boolean commit() {
                if (clearPending) target.data.clear();
                for (Map.Entry<String, Object> entry : pending.entrySet()) {
                    if (entry.getValue() == null) target.data.remove(entry.getKey());
                    else target.data.put(entry.getKey(), entry.getValue());
                }
                return true;
            }
            @Override public void apply() { commit(); }
        }
    }
}
