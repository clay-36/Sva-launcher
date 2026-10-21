package com.sva.launcher;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/** Export/import for launcher configuration. Raw channel caches and event schedules are omitted. */
final class LauncherBackup {
    private static final int VERSION = 1;
    private static final Map<String, String> EXPECTED_TYPES = expectedTypes();
    private static final Set<String> EXCLUDED_PREFERENCES = new HashSet<>(Arrays.asList(
            "publicCalendarEvents", CalendarFeedCache.KEY
    ));

    private static Map<String, String> expectedTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        register(types, "string", "playlist guide drmHeaders drmLicenseUrl channelsCacheSource guideCacheSource "
                + "selectedApps wallpaper wallpaperAlbum wallpaperFolder photoFolderCacheUri photoFolderCacheImages "
                + "googlePhotosCacheImages googlePhotosCacheUrl publicCalendarEvents publicCalendarUrl programmeReminders "
                + "screensaverCacheImages screensaverCacheUrl screensaverAlbum screensaverCaption screensaverTheme "
                + "weatherCache weatherLocationLabel weatherLocationMode youtubeCards liquidGlassMode navigationOrder");
        register(types, "boolean", "24h customApps guideCollapsed showDate weatherFahrenheit weatherMph "
                + "youtubeArtworkFeedFallbackTried hideLiveTvNavigation hideGamesNavigation hideEventsNavigation "
                + "screensaverClock screensaverWeather screensaverEvents");
        register(types, "integer", "publicCalendarAttemptedVersion publicCalendarCacheVersion liveTvNavigationPosition "
                + "screensaverIdleMinutes screensaverIntervalSeconds");
        register(types, "long", "channelsAttempted channelsUpdated guideAttempted guideUpdated "
                + "googlePhotosAttemptedAt googlePhotosUpdatedAt publicCalendarAttemptedAt publicCalendarUpdatedAt "
                + "screensaverCacheUpdatedAt weatherAttemptedAt weatherUpdatedAt weatherLatitude weatherLongitude "
                + "scheduleSlideshowMs schedulePlaylistMs scheduleGuideMs scheduleCalendarMs scheduleGooglePhotosMs scheduleWeatherMs");
        register(types, "string-set", "hiddenChannels favoriteChannels");
        return types;
    }

    private static void register(Map<String, String> types, String type, String keys) {
        for (String key : keys.split(" ")) types.put(key, type);
    }

    private LauncherBackup() { }

    static JSONObject create(SharedPreferences launcher, SharedPreferences iptv) throws Exception {
        return create(launcher, iptv, null);
    }

    static JSONObject create(SharedPreferences launcher, SharedPreferences iptv,
                             IptvRepository repository) throws Exception {
        JSONObject backup = new JSONObject();
        backup.put("format", "sva-launcher-backup");
        backup.put("version", VERSION);
        backup.put("createdAt", System.currentTimeMillis());
        backup.put("launcher", encodePreferences(launcher.getAll()));
        backup.put("iptv", encodePreferences(iptv.getAll()));
        return backup;
    }

    static void restore(JSONObject backup, SharedPreferences launcher,
                        SharedPreferences iptv) throws Exception {
        restore(backup, launcher, iptv, null);
    }

    static void restore(JSONObject backup, SharedPreferences launcher,
                        SharedPreferences iptv, IptvRepository repository) throws Exception {
        if (backup == null || !"sva-launcher-backup".equals(backup.optString("format"))
                || backup.optInt("version", -1) != VERSION) {
            throw new IllegalArgumentException("This backup file is not supported.");
        }
        JSONObject launcherValues = backup.optJSONObject("launcher");
        JSONObject iptvValues = backup.optJSONObject("iptv");
        if (launcherValues == null || iptvValues == null) {
            throw new IllegalArgumentException("The backup file is missing launcher or IPTV data.");
        }

        Map<String, Object> restoredLauncher = decodePreferences(launcherValues);
        Map<String, Object> restoredIptv = decodePreferences(iptvValues);
        replacePreferences(launcher, restoredLauncher);
        replacePreferences(iptv, restoredIptv);
    }

    private static JSONObject encodePreferences(Map<String, ?> values) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            if (EXCLUDED_PREFERENCES.contains(key)) continue;
            Object value = entry.getValue();
            JSONObject typed = new JSONObject();
            if (value instanceof String) {
                typed.put("type", "string").put("value", value);
            } else if (value instanceof Boolean) {
                typed.put("type", "boolean").put("value", value);
            } else if (value instanceof Integer) {
                typed.put("type", "integer").put("value", value);
            } else if (value instanceof Long) {
                typed.put("type", "long").put("value", value);
            } else if (value instanceof Float) {
                typed.put("type", "float").put("value", value);
            } else if (value instanceof Set) {
                JSONArray strings = new JSONArray();
                for (Object item : (Set<?>) value) {
                    if (!(item instanceof String)) throw new IllegalArgumentException("Unsupported set in preferences.");
                    strings.put(item);
                }
                typed.put("type", "string-set").put("value", strings);
            } else {
                throw new IllegalArgumentException("Unsupported preference value: " + entry.getKey());
            }
            result.put(entry.getKey(), typed);
        }
        return result;
    }

    static Map<String, Object> decodePreferences(JSONObject encoded) throws Exception {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        Iterator<String> keys = encoded.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (EXCLUDED_PREFERENCES.contains(key)) continue;
            JSONObject typed = encoded.optJSONObject(key);
            if (key.trim().isEmpty() || typed == null || !typed.has("value")) {
                throw new IllegalArgumentException("The backup contains an invalid preference.");
            }
            String type = typed.optString("type");
            String expected = EXPECTED_TYPES.get(key);
            if (expected != null && !expected.equals(type))
                throw new IllegalArgumentException("The backup contains an incompatible preference type.");
            Object value;
            switch (type) {
                case "string": value = typed.getString("value"); break;
                case "boolean": value = typed.getBoolean("value"); break;
                case "integer": value = typed.getInt("value"); break;
                case "long": value = typed.getLong("value"); break;
                case "float":
                    float number = (float) typed.getDouble("value");
                    if (Float.isNaN(number) || Float.isInfinite(number))
                        throw new IllegalArgumentException("The backup contains an invalid number.");
                    value = number; break;
                case "string-set":
                    JSONArray array = typed.getJSONArray("value");
                    HashSet<String> strings = new HashSet<>();
                    for (int i = 0; i < array.length(); i++) strings.add(array.getString(i));
                    value = strings;
                    break;
                default: throw new IllegalArgumentException("The backup contains an unknown preference type.");
            }
            result.put(key, value);
        }
        return result;
    }

    private static void replacePreferences(SharedPreferences preferences, Map<String, Object> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof Set) {
                @SuppressWarnings("unchecked") Set<String> strings = (Set<String>) value;
                editor.putStringSet(entry.getKey(), strings);
            }
        }
        if (!editor.commit()) throw new IllegalStateException("Could not save restored settings.");
    }
}
