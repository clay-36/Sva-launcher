package com.deo.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/** Export/import for launcher configuration and the offline IPTV cache. */
final class LauncherBackup {
    private static final int VERSION = 1;

    private LauncherBackup() { }

    static JSONObject create(SharedPreferences launcher, SharedPreferences iptv,
                             IptvRepository repository) throws Exception {
        JSONObject backup = new JSONObject();
        backup.put("format", "duo-launcher-backup");
        backup.put("version", VERSION);
        backup.put("createdAt", System.currentTimeMillis());
        backup.put("launcher", encodePreferences(launcher.getAll()));
        backup.put("iptv", encodePreferences(iptv.getAll()));
        backup.put("channels", repository.exportCachedChannels());
        return backup;
    }

    static void restore(JSONObject backup, SharedPreferences launcher,
                        SharedPreferences iptv, IptvRepository repository) throws Exception {
        if (backup == null || !"duo-launcher-backup".equals(backup.optString("format"))
                || backup.optInt("version", -1) != VERSION) {
            throw new IllegalArgumentException("This backup file is not supported.");
        }
        JSONObject launcherValues = backup.optJSONObject("launcher");
        JSONObject iptvValues = backup.optJSONObject("iptv");
        JSONArray channelValues = backup.optJSONArray("channels");
        if (launcherValues == null || iptvValues == null || channelValues == null) {
            throw new IllegalArgumentException("The backup file is missing launcher, IPTV, or channel data.");
        }

        Map<String, Object> restoredLauncher = decodePreferences(launcherValues);
        Map<String, Object> restoredIptv = decodePreferences(iptvValues);
        // Keep refresh workers from overwriting the restored cache before its URLs are restored.
        repository.restoreCachedChannels(channelValues, () -> {
            replacePreferences(launcher, restoredLauncher);
            replacePreferences(iptv, restoredIptv);
        });
    }

    private static JSONObject encodePreferences(Map<String, ?> values) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
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

    private static Map<String, Object> decodePreferences(JSONObject encoded) throws Exception {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        Iterator<String> keys = encoded.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject typed = encoded.optJSONObject(key);
            if (key.trim().isEmpty() || typed == null || !typed.has("value")) {
                throw new IllegalArgumentException("The backup contains an invalid preference.");
            }
            String type = typed.optString("type");
            Object value;
            switch (type) {
                case "string": value = typed.getString("value"); break;
                case "boolean": value = typed.getBoolean("value"); break;
                case "integer": value = typed.getInt("value"); break;
                case "long": value = typed.getLong("value"); break;
                case "float": value = (float) typed.getDouble("value"); break;
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
