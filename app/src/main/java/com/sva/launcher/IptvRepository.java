package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

/** Small URL-backed M3U and EPG loader with a persisted channel/guide cache. */
final class IptvRepository {
    private static final String PREFS = "iptv";
    private static final String CACHE_FILE = "iptv-channels.json";
    private static final String CACHE_BACKUP_FILE = "iptv-channels-backup.json";
    private static final long PROGRAMME_CACHE_HORIZON_MS = 48L * 60L * 60L * 1000L;
    private static final int MAX_CACHED_PROGRAMMES_PER_CHANNEL = 128;
    private static final int MAX_PLAYLIST_BYTES = 24 * 1024 * 1024;
    private static final int MAX_XML_GUIDE_BYTES = 128 * 1024 * 1024;
    private static final int MAX_JSON_GUIDE_BYTES = 16 * 1024 * 1024;
    private static final String GUIDE_TIME_ZONE = "Asia/Kolkata";
    private static final Object CACHE_REFRESH_LOCK = new Object();

    static final class Programme {
        final String title;
        final long startMs;
        final long endMs;

        Programme(String title, long startMs, long endMs) {
            this.title = title;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    static final class Channel {
        final String name;
        final String streamUrl;
        final String epgId;
        final String group;
        final String logoUrl;
        final String drmType;
        final String drmLicenseUrl;
        final Map<String, String> drmHeaders;
        final Map<String, String> streamHeaders;
        final String mimeType;
        final String clearKeyData;
        String now = "";
        String next = "";
        long nowStartMs;
        long nowEndMs;
        final List<Programme> programmes = new ArrayList<>();

        Channel(String name, String streamUrl, String epgId, String group, String logoUrl,
                String drmType, String drmLicenseUrl, Map<String, String> drmHeaders) {
            this(name, streamUrl, epgId, group, logoUrl, drmType, drmLicenseUrl, drmHeaders,
                    "", new LinkedHashMap<>(), "");
        }

        Channel(String name, String streamUrl, String epgId, String group, String logoUrl,
                String drmType, String drmLicenseUrl, Map<String, String> drmHeaders,
                String mimeType, Map<String, String> streamHeaders, String clearKeyData) {
            this.name = name;
            this.streamUrl = streamUrl;
            this.epgId = epgId;
            this.group = group;
            this.logoUrl = logoUrl;
            this.drmType = drmType;
            this.drmLicenseUrl = drmLicenseUrl;
            this.drmHeaders = new LinkedHashMap<>(drmHeaders);
            this.mimeType = StreamOptions.mimeType(mimeType);
            this.streamHeaders = StreamOptions.headers();
            this.streamHeaders.putAll(streamHeaders);
            this.clearKeyData = clearKeyData;
        }

        String stableKey() { return epgId.isEmpty() ? "url:" + streamUrl : "id:" + epgId; }
        boolean sameRenderIdentity(Channel other) {
            return stableKey().equals(other.stableKey()) && streamUrl.equals(other.streamUrl)
                    && name.equals(other.name) && group.equals(other.group) && logoUrl.equals(other.logoUrl)
                    && drmType.equals(other.drmType) && drmLicenseUrl.equals(other.drmLicenseUrl)
                    && drmHeaders.equals(other.drmHeaders) && streamHeaders.equals(other.streamHeaders)
                    && mimeType.equals(other.mimeType) && clearKeyData.equals(other.clearKeyData);
        }
    }

    interface Callback {
        void onLoaded(List<Channel> channels, String message);
    }

    private final SharedPreferences prefs;
    private final Context context;

    IptvRepository(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String playlistUrl() { return prefs.getString("playlist", ""); }
    String guideUrl() { return prefs.getString("guide", ""); }
    static List<String> sourceUrls(String value) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        if (value != null) {
            for (String line : value.split("\\r?\\n")) {
                String source = line.trim();
                if (!source.isEmpty()) sources.add(source);
            }
        }
        return new ArrayList<>(sources);
    }
    String drmLicenseUrl() { return prefs.getString("drmLicenseUrl", ""); }
    String drmHeaderText() { return prefs.getString("drmHeaders", ""); }
    Map<String, String> drmHeaders() { return parseHeaders(drmHeaderText()); }
    void saveUrls(String playlist, String guide) {
        String normalizedPlaylist = playlist.trim();
        String normalizedGuide = guide.trim();
        String previousPlaylist = playlistUrl();
        String previousGuide = guideUrl();
        SharedPreferences.Editor editor = prefs.edit().putString("playlist", normalizedPlaylist).putString("guide", normalizedGuide);
        if (!normalizedPlaylist.equals(previousPlaylist)) {
            if (!previousPlaylist.isEmpty()) editor.putString("channelsCacheSource", previousPlaylist);
            editor.remove("channelsUpdated").remove("channelsAttempted");
        }
        if (!normalizedGuide.equals(previousGuide)) {
            if (!previousGuide.isEmpty()) editor.putString("guideCacheSource", previousGuide);
            editor.remove("guideUpdated").remove("guideAttempted");
        }
        editor.apply();
    }
    void saveDrmSettings(String url, String headers) {
        prefs.edit().putString("drmLicenseUrl", url.trim()).putString("drmHeaders", headers.trim()).apply();
    }

    boolean isGuideStale() {
        return isGuideStale(System.currentTimeMillis(), lastGuideUpdatedAt(),
                prefs.getLong("guideAttempted", lastGuideUpdatedAt()), guideRefreshInterval());
    }
    boolean isGuideStale(List<Channel> channels) {
        long updatedAt = lastGuideUpdatedAt();
        long attemptedAt = prefs.getLong("guideAttempted", updatedAt);
        boolean hasProgrammeSchedule = false;
        if (channels != null) {
            for (Channel channel : channels) {
                if (!channel.programmes.isEmpty()) {
                    hasProgrammeSchedule = true;
                    break;
                }
            }
        }
        return isGuideStale(System.currentTimeMillis(), updatedAt, attemptedAt,
                channels != null && !channels.isEmpty(), hasProgrammeSchedule, guideRefreshInterval());
    }
    static boolean isGuideStale(long now, long updatedAt, long attemptedAt) {
        return isGuideStale(now, updatedAt, attemptedAt, SchedulePreferences.DEFAULT_GUIDE);
    }
    static boolean isGuideStale(long now, long updatedAt, long attemptedAt, long interval) {
        return interval > 0 && now - Math.max(updatedAt, attemptedAt) >= interval;
    }
    static boolean isGuideStale(long now, long updatedAt, long attemptedAt,
                                boolean hasChannels, boolean hasProgrammeSchedule) {
        return isGuideStale(now, updatedAt, attemptedAt, hasChannels, hasProgrammeSchedule,
                SchedulePreferences.DEFAULT_GUIDE);
    }
    static boolean isGuideStale(long now, long updatedAt, long attemptedAt,
                                boolean hasChannels, boolean hasProgrammeSchedule, long interval) {
        if (interval <= 0) return false;
        if (hasChannels && !hasProgrammeSchedule && updatedAt > 0 && attemptedAt <= updatedAt) return true;
        return isGuideStale(now, updatedAt, attemptedAt, interval);
    }
    boolean isChannelsStale() {
        return isChannelsStale(System.currentTimeMillis(), lastUpdatedAt(),
                prefs.getLong("channelsAttempted", lastUpdatedAt()), playlistRefreshInterval());
    }
    static boolean isChannelsStale(long now, long updatedAt, long attemptedAt) {
        return isChannelsStale(now, updatedAt, attemptedAt, SchedulePreferences.DEFAULT_PLAYLIST);
    }
    static boolean isChannelsStale(long now, long updatedAt, long attemptedAt, long interval) {
        return interval > 0 && now - Math.max(updatedAt, attemptedAt) >= interval;
    }
    private long playlistRefreshInterval() {
        return SchedulePreferences.get(context.getSharedPreferences("launcher", Context.MODE_PRIVATE),
                SchedulePreferences.PLAYLIST, SchedulePreferences.PLAYLIST_VALUES,
                SchedulePreferences.DEFAULT_PLAYLIST);
    }
    private long guideRefreshInterval() {
        return SchedulePreferences.get(context.getSharedPreferences("launcher", Context.MODE_PRIVATE),
                SchedulePreferences.GUIDE, SchedulePreferences.GUIDE_VALUES, SchedulePreferences.DEFAULT_GUIDE);
    }
    long millisUntilChannelsRefresh() {
        return millisUntilRefresh(System.currentTimeMillis(), lastUpdatedAt(),
                prefs.getLong("channelsAttempted", lastUpdatedAt()), playlistRefreshInterval());
    }
    long millisUntilGuideRefresh() {
        return millisUntilRefresh(System.currentTimeMillis(), lastGuideUpdatedAt(),
                prefs.getLong("guideAttempted", lastGuideUpdatedAt()), guideRefreshInterval());
    }
    static long millisUntilRefresh(long now, long updatedAt, long attemptedAt, long interval) {
        if (interval <= 0) return Long.MAX_VALUE;
        return Math.max(0L, interval - Math.max(0L, now - Math.max(updatedAt, attemptedAt)));
    }
    long lastUpdatedAt() { return prefs.getLong("channelsUpdated", prefs.getLong("updated", 0)); }
    long lastGuideUpdatedAt() { return prefs.getLong("guideUpdated", 0); }

    List<Channel> cachedChannels() {
        List<Channel> cached = selectCachedChannels(readCache(CACHE_FILE), readCache(CACHE_BACKUP_FILE));
        if (!cacheMatchesSource(playlistUrl(), prefs.getString("channelsCacheSource", ""))) {
            return new ArrayList<>();
        }
        if (!cacheMatchesSource(guideUrl(), prefs.getString("guideCacheSource", ""))) {
            cached = copyChannels(cached);
            for (Channel channel : cached) clearProgrammeData(channel);
        }
        return cached;
    }

    static boolean cacheMatchesSource(String configured, String cachedSource) {
        return cachedSource == null || cachedSource.isEmpty() || cachedSource.equals(configured);
    }

    private static void clearProgrammeData(Channel channel) {
        channel.now = "";
        channel.next = "";
        channel.nowStartMs = 0L;
        channel.nowEndMs = 0L;
        channel.programmes.clear();
    }

    static List<Channel> selectCachedChannels(List<Channel> primary, List<Channel> backup) {
        if (primary != null && !primary.isEmpty()) return primary;
        if (backup != null && !backup.isEmpty()) return backup;
        if (primary != null) return primary;
        return backup != null ? backup : new ArrayList<>();
    }

    static void restoreProgrammes(Channel channel, JSONArray savedProgrammes) {
        if (savedProgrammes == null) return;
        for (int i = 0; i < savedProgrammes.length()
                && channel.programmes.size() < MAX_CACHED_PROGRAMMES_PER_CHANNEL; i++) {
            JSONObject value = savedProgrammes.optJSONObject(i);
            if (value == null) continue;
            String title = value.optString("title", "").trim();
            long startMs = value.optLong("startMs", 0);
            long endMs = value.optLong("endMs", 0);
            if (!title.isEmpty() && startMs > 0 && endMs > startMs) {
                channel.programmes.add(new Programme(title, startMs, endMs));
            }
        }
    }

    private List<Channel> readCache(String fileName) {
        AtomicFile file = new AtomicFile(new java.io.File(context.getFilesDir(), fileName));
        try (FileInputStream input = file.openRead();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder raw = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) raw.append(buffer, 0, count);
            JSONArray array = new JSONArray(raw.toString());
            ArrayList<Channel> result = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Map<String, String> headers = new LinkedHashMap<>();
                JSONObject headerObject = o.optJSONObject("drmHeaders");
                if (headerObject != null) {
                    java.util.Iterator<String> keys = headerObject.keys();
                    while (keys.hasNext()) { String key = keys.next(); headers.put(key, headerObject.optString(key)); }
                }
                Channel c = new Channel(o.optString("name"), o.optString("url"), o.optString("id"),
                        o.optString("group"), o.optString("logo"), o.optString("drmType"),
                        o.optString("drmLicenseUrl"), headers, o.optString("mimeType"),
                        StreamOptions.jsonHeaders(o.optJSONObject("streamHeaders")), o.optString("clearKeyData"));
                c.now = o.optString("now");
                c.next = o.optString("next");
                c.nowStartMs = o.optLong("nowStartMs");
                c.nowEndMs = o.optLong("nowEndMs");
                restoreProgrammes(c, o.optJSONArray("programmes"));
                refreshProgrammeDisplay(c, System.currentTimeMillis());
                result.add(c);
            }
            return result;
        } catch (Exception error) {
            Log.w("IptvRepository", "Could not read channel cache " + fileName, error);
            return null;
        }
    }

    JSONArray exportCachedChannels() throws Exception {
        return serializeChannels(cachedChannels());
    }

    void restoreCachedChannels(JSONArray array) throws Exception {
        restoreCachedChannels(array, null);
    }

    void restoreCachedChannels(JSONArray array, Runnable onRestored) throws Exception {
        if (array == null || array.length() > 50000) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_backup_contains_too_many_channels, "Backup contains too many channels."));
        ArrayList<Channel> restored = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.optJSONObject(i);
            if (value == null) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_backup_contains_an_invalid_channel_entry, "Backup contains an invalid channel entry."));
            String name = value.optString("name", "").trim();
            String url = value.optString("url", "").trim();
            if (name.isEmpty() || url.isEmpty()) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_backup_contains_a_channel_without_a_name_or_stream_url, "Backup contains a channel without a name or stream URL."));
            Map<String, String> headers = new LinkedHashMap<>();
            JSONObject headerObject = value.optJSONObject("drmHeaders");
            if (headerObject != null) {
                java.util.Iterator<String> keys = headerObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    headers.put(key, headerObject.optString(key));
                }
            }
            Channel channel = new Channel(name, url, value.optString("id", ""),
                    value.optString("group", ""), value.optString("logo", ""),
                    value.optString("drmType", ""), value.optString("drmLicenseUrl", ""), headers,
                    value.optString("mimeType"), StreamOptions.jsonHeaders(value.optJSONObject("streamHeaders")),
                    value.optString("clearKeyData"));
            channel.now = value.optString("now", "");
            channel.next = value.optString("next", "");
            channel.nowStartMs = value.optLong("nowStartMs", 0);
            channel.nowEndMs = value.optLong("nowEndMs", 0);
            restoreProgrammes(channel, value.optJSONArray("programmes"));
            refreshProgrammeDisplay(channel, System.currentTimeMillis());
            restored.add(channel);
        }
        synchronized (CACHE_REFRESH_LOCK) {
            saveCache(restored);
            if (onRestored != null) onRestored.run();
        }
    }

    void refreshChannels(Callback callback) {
        new Thread(() -> {
            synchronized (CACHE_REFRESH_LOCK) {
                List<Channel> loaded;
                String message;
                try {
                    String playlist = playlistUrl();
                    List<String> sources = sourceUrls(playlist);
                    if (sources.isEmpty()) throw new IllegalStateException(AppLanguage.text(R.string.ui_add_one_or_more_m3u_playlist_urls_in_settings, "Add one or more M3U playlist URLs in Settings."));
                    prefs.edit().putLong("channelsAttempted", System.currentTimeMillis()).apply();
                    Map<String, Channel> combined = new LinkedHashMap<>();
                    List<String> failures = new ArrayList<>();
                    for (int i = 0; i < sources.size(); i++) {
                        try {
                            List<Channel> loadedSource = downloadPlaylist(sources.get(i));
                            if (loadedSource.isEmpty()) {
                                failures.add(AppLanguage.text(R.string.ui_playlist_1_s_was_empty, "Playlist %1$s was empty", (i + 1)));
                            } else {
                                for (Channel channel : loadedSource) {
                                    combined.putIfAbsent(cacheIdentity(channel), channel);
                                }
                            }
                        } catch (Exception sourceError) {
                            failures.add(AppLanguage.text(R.string.ui_playlist_1_s_2_s, "Playlist %1$s: %2$s", (i + 1), safeMessage(sourceError)));
                        }
                    }
                    List<Channel> channels = new ArrayList<>(combined.values());
                    if (channels.isEmpty()) {
                        loaded = cachedChannels();
                        message = loaded.isEmpty()
                                ? (failures.isEmpty() ? AppLanguage.text(R.string.ui_no_channels_found_in_the_playlists, "No channels found in the playlists.")
                                : String.join("; ", failures))
                                : AppLanguage.text(R.string.ui_no_channels_loaded_keeping_saved_channels_1_s, "No channels loaded. Keeping saved channels. %1$s", String.join("; ", failures));
                    } else if (!playlist.equals(playlistUrl())) {
                        loaded = cachedChannels();
                        message = AppLanguage.text(R.string.ui_playlist_sources_changed_during_refresh_refresh_channels_again, "Playlist sources changed during refresh. Refresh channels again.");
                    } else {
                        preserveGuideData(channels, cachedChannels());
                        saveCache(channels);
                        long completedAt = System.currentTimeMillis();
                        prefs.edit().putLong("channelsUpdated", completedAt)
                                .putLong("channelsAttempted", completedAt)
                                .putString("channelsCacheSource", playlist).apply();
                        loaded = channels;
                        message = failures.isEmpty()
                                ? AppLanguage.text(R.string.ui_refresh_playlist_success, "Channels updated from %1$s.", AppLanguage.quantity(R.plurals.playlist_count, sources.size(), "%1$d playlist", "%1$d playlists"))
                                : AppLanguage.text(R.string.ui_loaded_1_s_channels_2_s, "Loaded %1$s channels; %2$s.", channels.size(), String.join("; ", failures));
                    }
                } catch (Exception e) {
                    loaded = cachedChannels();
                    message = loaded.isEmpty()
                            ? AppLanguage.text(R.string.ui_could_not_load_playlist_1_s, "Could not load playlist: %1$s", safeMessage(e))
                            : AppLanguage.text(R.string.ui_showing_saved_channels_channel_refresh_failed_1_s, "Showing saved channels. Channel refresh failed: %1$s", safeMessage(e));
                }
                callback.onLoaded(loaded, message);
            }
        }, "iptv-channel-refresh").start();
    }

    void refreshGuide(List<Channel> fallbackChannels, Callback callback) {
        new Thread(() -> {
            synchronized (CACHE_REFRESH_LOCK) {
                List<Channel> cached = cacheOrFallback(cachedChannels(), fallbackChannels);
                List<Channel> loaded;
                String message;
                String guide = guideUrl();
                try {
                    List<String> sources = sourceUrls(guide);
                    if (sources.isEmpty()) throw new IllegalStateException(AppLanguage.text(R.string.ui_add_and_save_one_or_more_programme_guide_urls_in_settings, "Add and save one or more programme guide URLs in Settings."));
                    if (cached.isEmpty()) throw new IllegalStateException(AppLanguage.text(R.string.ui_refresh_channels_first_to_load_the_programme_guide, "Refresh channels first to load the programme guide."));
                    prefs.edit().putLong("guideAttempted", System.currentTimeMillis()).apply();
                    List<Channel> updated = copyChannels(cached);
                    clearGuideData(updated);
                    List<String> failures = new ArrayList<>();
                    int succeeded = 0;
                    for (int i = 0; i < sources.size(); i++) {
                        List<Channel> sourceChannels = copyChannels(cached);
                        clearGuideData(sourceChannels);
                        try {
                            attachGuide(sourceChannels, sources.get(i));
                            mergeGuideData(sourceChannels, updated, System.currentTimeMillis());
                            succeeded++;
                        } catch (Exception sourceError) {
                            failures.add(AppLanguage.text(R.string.ui_guide_1_s_2_s, "Guide %1$s: %2$s", (i + 1), safeMessage(sourceError)));
                        }
                    }
                    if (succeeded == 0) {
                        throw new IllegalStateException(failures.isEmpty()
                                ? AppLanguage.text(R.string.ui_no_programme_guide_sources_were_loaded, "No programme guide sources were loaded.") : String.join("; ", failures));
                    }
                    refreshProgrammeDisplay(updated, System.currentTimeMillis());
                    if (!guide.equals(guideUrl())) {
                        prefs.edit().remove("guideUpdated").remove("guideAttempted").apply();
                        throw new IllegalStateException(AppLanguage.text(R.string.ui_programme_guide_sources_changed_during_refresh_refresh_the_guide, "Programme guide sources changed during refresh. Refresh the guide again."));
                    }
                    saveCache(updated);
                    long completedAt = System.currentTimeMillis();
                    prefs.edit().putLong("guideUpdated", completedAt).putLong("guideAttempted", completedAt)
                            .putString("guideCacheSource", guide).apply();
                    loaded = updated;
                    message = failures.isEmpty()
                            ? AppLanguage.text(R.string.ui_refresh_guide_success, "Programme guide updated from %1$s.", AppLanguage.quantity(R.plurals.source_count, succeeded, "%1$d source", "%1$d sources"))
                            : AppLanguage.text(R.string.ui_guide_updated_from_1_s_of_2_s_sources_3_s, "Guide updated from %1$s of %2$s sources. %3$s.", succeeded, sources.size(), String.join("; ", failures));
                } catch (Exception e) {
                    loaded = cacheOrFallback(cachedChannels(), cached);
                    if (!guide.equals(guideUrl())) {
                        prefs.edit().remove("guideUpdated").remove("guideAttempted").apply();
                        message = AppLanguage.text(R.string.ui_programme_guide_sources_changed_during_refresh_refresh_the_guide, "Programme guide sources changed during refresh. Refresh the guide again.");
                    } else {
                        message = loaded.isEmpty()
                                ? safeMessage(e)
                                : AppLanguage.text(R.string.ui_showing_saved_channels_guide_refresh_failed_1_s, "Showing saved channels. Guide refresh failed: %1$s", safeMessage(e));
                    }
                }
                callback.onLoaded(loaded, message);
            }
        }, "iptv-guide-refresh").start();
    }

    static List<Channel> cacheOrFallback(List<Channel> cached, List<Channel> fallback) {
        if (cached != null && !cached.isEmpty()) return cached;
        return copyChannels(fallback == null ? new ArrayList<>() : fallback);
    }

    static void preserveGuideData(List<Channel> channels, List<Channel> cached) {
        Map<String, Channel> byExactIdentity = new HashMap<>();
        Map<String, Integer> cachedKeyCounts = new HashMap<>();
        Map<String, Integer> freshKeyCounts = new HashMap<>();
        for (Channel channel : cached) {
            String key = channel.stableKey();
            cachedKeyCounts.put(key, cachedKeyCounts.getOrDefault(key, 0) + 1);
            byExactIdentity.put(cacheIdentity(channel), channel);
        }
        for (Channel channel : channels) {
            String key = channel.stableKey();
            freshKeyCounts.put(key, freshKeyCounts.getOrDefault(key, 0) + 1);
        }
        Map<String, Channel> cachedUniqueByKey = new HashMap<>();
        for (Channel channel : cached) {
            if (cachedKeyCounts.getOrDefault(channel.stableKey(), 0) == 1) {
                cachedUniqueByKey.put(channel.stableKey(), channel);
            }
        }
        for (Channel channel : channels) {
            Channel previous = byExactIdentity.get(cacheIdentity(channel));
            if (previous == null && cachedKeyCounts.getOrDefault(channel.stableKey(), 0) == 1
                    && freshKeyCounts.getOrDefault(channel.stableKey(), 0) == 1) {
                previous = cachedUniqueByKey.get(channel.stableKey());
            }
            if (previous == null) continue;
            channel.now = previous.now;
            channel.next = previous.next;
            channel.nowStartMs = previous.nowStartMs;
            channel.nowEndMs = previous.nowEndMs;
            channel.programmes.clear();
            channel.programmes.addAll(previous.programmes);
        }
    }

    private static String cacheIdentity(Channel channel) {
        return channel.stableKey() + "\u001f" + channel.streamUrl;
    }

    private static List<Channel> copyChannels(List<Channel> channels) {
        ArrayList<Channel> copy = new ArrayList<>(channels.size());
        for (Channel channel : channels) {
            Channel clone = new Channel(channel.name, channel.streamUrl, channel.epgId, channel.group,
                    channel.logoUrl, channel.drmType, channel.drmLicenseUrl, channel.drmHeaders,
                    channel.mimeType, channel.streamHeaders, channel.clearKeyData);
            clone.now = channel.now;
            clone.next = channel.next;
            clone.nowStartMs = channel.nowStartMs;
            clone.nowEndMs = channel.nowEndMs;
            clone.programmes.addAll(channel.programmes);
            copy.add(clone);
        }
        return copy;
    }

    private HttpURLConnection openConnection(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "SvaLauncher/1.0");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException(AppLanguage.text(R.string.ui_http_1_s, "HTTP %1$s", code));
        }
        return connection;
    }

    private List<Channel> downloadPlaylist(String address) throws Exception {
        HttpURLConnection connection = openConnection(address);
        try {
            int length = connection.getContentLength();
            if (length > MAX_PLAYLIST_BYTES) throw new IllegalStateException(AppLanguage.text(R.string.ui_playlist_is_larger_than_24_mb, "Playlist is larger than 24 MB."));
            try (InputStream stream = new ByteLimitInputStream(connection.getInputStream(), MAX_PLAYLIST_BYTES);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return parseLines(reader);
            }
        } finally { connection.disconnect(); }
    }

    static List<Channel> parseM3u(String content) {
        try { return parseLines(new BufferedReader(new StringReader(content))); }
        catch (Exception ignored) { return new ArrayList<>(); }
    }

    private static List<Channel> parseLines(BufferedReader reader) throws Exception {
        ArrayList<Channel> channels = new ArrayList<>();
        String name = null, id = "", group = "", logo = "", drmType = "", drmKey = "", mime = "", line;
        Map<String, String> vlcHeaders = StreamOptions.headers(), kodiHeaders = StreamOptions.headers();
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#EXTINF")) {
                if (name != null) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_playlist_ended_a_channel_entry_before_its_stream_url, "Playlist ended a channel entry before its stream URL."));
                int comma = extinfTitleSeparator(line);
                name = comma >= 0 ? line.substring(comma + 1).trim() : attribute(line, "tvg-name");
                id = attribute(line, "tvg-id"); group = attribute(line, "group-title"); logo = attribute(line, "tvg-logo");
                drmType = ""; drmKey = ""; mime = ""; vlcHeaders.clear(); kodiHeaders.clear();
                if (name == null || name.isEmpty()) name = "Channel";
            } else if (line.startsWith("#KODIPROP:") && name != null) {
                int split = line.indexOf('=', 10);
                if (split > 0) {
                    String property = line.substring(10, split).trim().toLowerCase(Locale.US);
                    String value = line.substring(split + 1).trim();
                    if (property.endsWith("license_type")) drmType = value;
                    else if (property.endsWith("license_key")) drmKey = value;
                    else if (property.endsWith("stream_headers")) kodiHeaders.putAll(StreamOptions.encodedHeaders(value));
                    else if (property.endsWith("manifest_type") || property.endsWith("mime_type") || property.equals("mimetype"))
                        mime = StreamOptions.mimeType(value);
                }
            } else if (line.startsWith("#EXTVLCOPT:") && name != null) {
                int split = line.indexOf('=');
                if (split > 0) {
                    String option = line.substring(11, split).trim().toLowerCase(Locale.US);
                    String value = line.substring(split + 1).trim();
                    String header = option.equals("http-user-agent") ? "User-Agent"
                            : option.equals("http-referrer") || option.equals("http-referer") ? "Referer"
                            : option.equals("http-origin") ? "Origin" : option.equals("http-cookie") ? "Cookie" : "";
                    if (!header.isEmpty()) StreamOptions.putHeader(vlcHeaders, header, value);
                }
            } else if (!line.isEmpty() && !line.startsWith("#") && name != null) {
                boolean clearKey = StreamOptions.isClearKey(drmType.trim());
                LicenseData license;
                String inlineKeys = "";
                if (clearKey && !StreamOptions.isHttp(drmKey)) {
                    inlineKeys = drmKey; license = new LicenseData("", new LinkedHashMap<>());
                } else if (clearKey) {
                    String[] parts = drmKey.split("\\|", -1);
                    license = new LicenseData(parts[0].trim(), parts.length > 1
                            ? StreamOptions.encodedHeaders(parts[1]) : StreamOptions.headers());
                } else license = parseKodiLicenseKey(drmKey);
                String type = clearKey ? "clearkey"
                        : drmType.toLowerCase(Locale.US).contains("widevine") || !drmKey.isEmpty() ? "widevine" : "";
                Map<String, String> streamHeaders = StreamOptions.headers();
                streamHeaders.putAll(vlcHeaders); streamHeaders.putAll(kodiHeaders);
                channels.add(new Channel(name, line, id, group, logo, type, license.url, license.headers,
                        mime, streamHeaders, inlineKeys));
                name = null; id = ""; group = ""; logo = ""; drmType = ""; drmKey = ""; mime = "";
                vlcHeaders.clear(); kodiHeaders.clear();
            }
        }
        if (name != null) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_playlist_ended_before_the_final_channel_stream_url, "Playlist ended before the final channel stream URL."));
        return channels;
    }

    private static final class ByteLimitInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        ByteLimitInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0 && ++count > limit) throw tooLarge();
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int bytes = super.read(buffer, offset, length);
            if (bytes > 0) {
                count += bytes;
                if (count > limit) throw tooLarge();
            }
            return bytes;
        }

        private IllegalStateException tooLarge() {
            return new IllegalStateException(AppLanguage.text(R.string.ui_source_exceeds_the_1_s_mb_size_limit, "Source exceeds the %1$s MB size limit.", (limit / (1024 * 1024))));
        }
    }

    private static int extinfTitleSeparator(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char value = line.charAt(i);
            if (value == '"') quoted = !quoted;
            else if (value == ',' && !quoted) return i;
        }
        return -1;
    }

    private static final class LicenseData {
        final String url;
        final Map<String, String> headers;
        LicenseData(String url, Map<String, String> headers) { this.url = url; this.headers = headers; }
    }

    private static LicenseData parseKodiLicenseKey(String value) {
        if (value == null || value.isEmpty()) return new LicenseData("", new LinkedHashMap<>());
        String[] parts = value.split("\\|", -1);
        String url = parts.length > 0 ? parts[0].trim() : "";
        String encodedHeaders = parts.length > 1 ? parts[1].trim() : "";
        try { encodedHeaders = URLDecoder.decode(encodedHeaders, "UTF-8"); }
        catch (Exception ignored) { }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String pair : encodedHeaders.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) headers.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
        }
        return new LicenseData(url, headers);
    }

    static Map<String, String> parseHeaders(String text) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (text == null) return headers;
        for (String line : text.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (!key.isEmpty()) headers.put(key, value);
            }
        }
        return headers;
    }

    private static String attribute(String source, String key) {
        String marker = key + "=\"";
        int start = source.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = source.indexOf('"', start);
        return end < 0 ? "" : source.substring(start, end);
    }

    private void attachGuide(List<Channel> channels, String address) throws Exception {
        HttpURLConnection connection = openConnection(address);
        try (InputStream raw = new BufferedInputStream(connection.getInputStream())) {
            raw.mark(64);
            int first = raw.read();
            while (first >= 0 && Character.isWhitespace((char) first)) first = raw.read();
            raw.reset();
            String contentType = connection.getContentType();
            boolean json = first == '[' || (contentType != null
                    && contentType.toLowerCase(Locale.US).contains("json"));
            if (json) {
                int length = connection.getContentLength();
                if (length > MAX_JSON_GUIDE_BYTES) throw new IllegalStateException(AppLanguage.text(R.string.ui_json_guide_is_larger_than_16_mb, "JSON guide is larger than 16 MB."));
                applyJsonGuide(channels, new JSONArray(readGuideJson(raw)), System.currentTimeMillis());
            } else {
                attachXmlGuide(channels, guideStream(raw));
            }
        } finally { connection.disconnect(); }
    }

    private static String readGuideJson(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_JSON_GUIDE_BYTES) {
                throw new IllegalStateException(AppLanguage.text(R.string.ui_json_guide_is_larger_than_16_mb, "JSON guide is larger than 16 MB."));
            }
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void attachXmlGuide(List<Channel> channels, InputStream stream) throws Exception {
        List<Channel> working = copyChannels(channels);
        clearGuideData(working);
        Map<String, List<Channel>> byId = new HashMap<>();
        for (Channel channel : working) {
            if (!channel.epgId.isEmpty()) byId.computeIfAbsent(channel.epgId, ignored -> new ArrayList<>()).add(channel);
        }
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(stream, "UTF-8");
        long now = System.currentTimeMillis();
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US);
        String channelId = null, title = null, start = null, stop = null;
        int matchedProgrammes = 0;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if ("programme".equals(parser.getName())) {
                    channelId = parser.getAttributeValue(null, "channel");
                    start = parser.getAttributeValue(null, "start");
                    stop = parser.getAttributeValue(null, "stop");
                    title = null;
                } else if ("title".equals(parser.getName())) {
                    title = parser.nextText();
                }
            } else if (event == XmlPullParser.END_TAG && "programme".equals(parser.getName())) {
                List<Channel> matched = byId.get(channelId);
                long begin = xmlTime(start, timeFormat), end = xmlTime(stop, timeFormat);
                if (matched != null && title != null && !title.trim().isEmpty() && begin > 0 && end > begin) {
                    for (Channel channel : matched) recordProgramme(channel, title.trim(), begin, end, now);
                    matchedProgrammes++;
                }
                title = null;
            }
        }
        if (matchedProgrammes == 0) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_guide_contains_no_valid_programmes_matching_playlist_tvg_id_valu, "Guide contains no valid programmes matching playlist tvg-id values."));
        refreshProgrammeDisplay(working, now);
        if (!hasCurrentOrUpcomingProgramme(working)) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_guide_has_no_current_or_upcoming_programmes, "Guide has no current or upcoming programmes."));
        copyGuideData(working, channels);
    }

    static void applyJsonGuide(List<Channel> channels, JSONArray feed, long now) throws Exception {
        if (feed == null || feed.length() == 0) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_json_guide_contains_no_channels, "JSON guide contains no channels."));
        GuideAccumulator accumulator = new GuideAccumulator(channels, now);
        for (int i = 0; i < feed.length(); i++) {
            JSONObject item = feed.optJSONObject(i);
            if (item == null) continue;
            accumulator.addJson(item);
        }
        accumulator.finish(AppLanguage.text(R.string.ui_json_guide_contains_no_channel_entries, "JSON guide contains no channel entries."),
                AppLanguage.text(R.string.ui_json_guide_contains_no_valid_programmes_matching_playlist_channe, "JSON guide contains no valid programmes matching playlist channel IDs or names."));
    }

    static void applyJsonGuideEntries(List<Channel> channels, List<Map<String, Object>> feed, long now) {
        if (feed == null || feed.isEmpty()) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_json_guide_contains_no_channel_entries, "JSON guide contains no channel entries."));
        GuideAccumulator accumulator = new GuideAccumulator(channels, now);
        for (Map<String, Object> item : feed) {
            accumulator.addMap(item);
        }
        accumulator.finish(AppLanguage.text(R.string.ui_json_guide_contains_no_channel_entries, "JSON guide contains no channel entries."),
                AppLanguage.text(R.string.ui_json_guide_contains_no_valid_programmes_matching_playlist_channe, "JSON guide contains no valid programmes matching playlist channel IDs or names."));
    }

    private static final class GuideAccumulator {
        private final List<Channel> original;
        private final List<Channel> working;
        private final long now;
        private final SimpleDateFormat timeFormat;
        private final Map<String, Channel> byId = new HashMap<>();
        private final java.util.Set<String> ambiguousIds = new java.util.HashSet<>();
        private final Map<String, Channel> byName = new HashMap<>();
        private final java.util.Set<String> ambiguousNames = new java.util.HashSet<>();
        private int channelEntries;
        private int matchedProgrammes;

        GuideAccumulator(List<Channel> channels, long now) {
            this.original = channels;
            this.working = copyChannels(channels);
            this.now = now;
            this.timeFormat = guideTimeFormat();
            clearGuideData(working);
            for (Channel channel : working) {
                addUnique(byId, ambiguousIds, channel.epgId, channel);
                addUnique(byName, ambiguousNames, channel.name, channel);
            }
        }

        void addJson(JSONObject item) {
            channelEntries++;
            Channel channel = findChannel(item.optString("service_id", ""), item.optString("unique_id", ""),
                    item.optString("matching_key", ""), item.optString("channel", ""));
            JSONArray schedule = item.optJSONArray("schedule");
            if (channel == null || schedule == null) return;
            for (int i = 0; i < schedule.length(); i++) {
                JSONObject programme = schedule.optJSONObject(i);
                if (programme != null) addProgramme(channel, programme.optString("title", ""),
                        programme.optString("start", ""), programme.optString("end", ""));
            }
        }

        void addMap(Map<String, Object> item) {
            if (item == null) return;
            channelEntries++;
            Channel channel = findChannel(stringValue(item.get("service_id")), stringValue(item.get("unique_id")),
                    stringValue(item.get("matching_key")), stringValue(item.get("channel")));
            Object schedule = item.get("schedule");
            if (channel == null || !(schedule instanceof List)) return;
            for (Object value : (List<?>) schedule) {
                if (!(value instanceof Map)) continue;
                Map<?, ?> programme = (Map<?, ?>) value;
                addProgramme(channel, stringValue(programme.get("title")), stringValue(programme.get("start")),
                        stringValue(programme.get("end")));
            }
        }

        private Channel findChannel(String serviceId, String uniqueId, String matchingKey, String name) {
            Channel channel = uniqueMatch(byId, ambiguousIds, serviceId);
            if (channel == null) channel = uniqueMatch(byId, ambiguousIds, uniqueId);
            if (channel == null) channel = uniqueMatch(byId, ambiguousIds, matchingKey);
            if (channel == null) channel = uniqueMatch(byName, ambiguousNames, name);
            return channel;
        }

        private void addProgramme(Channel channel, String title, String start, String end) {
            String cleanTitle = title.trim();
            long begin = jsonTime(start, timeFormat);
            long finish = jsonTime(end, timeFormat);
            if (cleanTitle.isEmpty() || begin <= 0 || finish <= begin) return;
            recordProgramme(channel, cleanTitle, begin, finish, now);
            matchedProgrammes++;
        }

        void finish(String noEntriesMessage, String noMatchesMessage) {
            if (channelEntries == 0) throw new IllegalArgumentException(noEntriesMessage);
            if (matchedProgrammes == 0) throw new IllegalArgumentException(noMatchesMessage);
            refreshProgrammeDisplay(working, now);
            if (!hasCurrentOrUpcomingProgramme(working)) throw new IllegalArgumentException(AppLanguage.text(R.string.ui_json_guide_has_no_current_or_upcoming_programmes, "JSON guide has no current or upcoming programmes."));
            copyGuideData(working, original);
        }
    }

    private static void recordProgramme(Channel channel, String title, long begin, long end, long now) {
        if (end <= now || begin > now + PROGRAMME_CACHE_HORIZON_MS
                || channel.programmes.size() >= MAX_CACHED_PROGRAMMES_PER_CHANNEL) return;
        for (Programme programme : channel.programmes) {
            if (programme.startMs == begin && programme.endMs == end && programme.title.equals(title)) return;
        }
        channel.programmes.add(new Programme(title, begin, end));
    }

    static void refreshProgrammeDisplay(List<Channel> channels, long now) {
        for (Channel channel : channels) refreshProgrammeDisplay(channel, now);
    }

    static void refreshProgrammeDisplay(Channel channel, long now) {
        if (channel.programmes.isEmpty()) {
            // Older cache entries only contain the active and following titles.
            if (channel.nowEndMs > 0 && channel.nowEndMs <= now) {
                if (!channel.next.isEmpty()) {
                    channel.now = channel.next;
                    channel.nowStartMs = channel.nowEndMs;
                    channel.nowEndMs = 0;
                    channel.next = "";
                } else {
                    channel.now = "";
                    channel.nowStartMs = 0;
                    channel.nowEndMs = 0;
                }
            }
            return;
        }
        Programme current = null;
        Programme next = null;
        for (Programme programme : channel.programmes) {
            if (programme.startMs <= now && programme.endMs > now
                    && (current == null || programme.startMs > current.startMs)) {
                current = programme;
            } else if (programme.startMs > now
                    && (next == null || programme.startMs < next.startMs)) {
                next = programme;
            }
        }
        channel.now = current == null ? "" : current.title;
        channel.nowStartMs = current == null ? 0 : current.startMs;
        channel.nowEndMs = current == null ? 0 : current.endMs;
        channel.next = next == null ? "" : next.title;
    }

    private static boolean hasCurrentOrUpcomingProgramme(List<Channel> channels) {
        for (Channel channel : channels) if (!channel.now.isEmpty() || !channel.next.isEmpty()) return true;
        return false;
    }

    private static String stringValue(Object value) { return value instanceof String ? (String) value : ""; }

    private static void clearGuideData(List<Channel> channels) {
        for (Channel channel : channels) {
            channel.now = "";
            channel.next = "";
            channel.nowStartMs = 0;
            channel.nowEndMs = 0;
            channel.programmes.clear();
        }
    }

    private static void copyGuideData(List<Channel> source, List<Channel> destination) {
        int count = Math.min(source.size(), destination.size());
        for (int i = 0; i < count; i++) {
            Channel from = source.get(i);
            Channel to = destination.get(i);
            to.now = from.now;
            to.next = from.next;
            to.nowStartMs = from.nowStartMs;
            to.nowEndMs = from.nowEndMs;
            to.programmes.clear();
            to.programmes.addAll(from.programmes);
        }
    }

    static void mergeGuideData(List<Channel> source, List<Channel> destination, long now) {
        int count = Math.min(source.size(), destination.size());
        for (int i = 0; i < count; i++) {
            Channel from = source.get(i);
            Channel to = destination.get(i);
            for (Programme programme : from.programmes) {
                recordProgramme(to, programme.title, programme.startMs, programme.endMs, now);
            }
        }
        refreshProgrammeDisplay(destination, now);
    }

    private static void addUnique(Map<String, Channel> index, java.util.Set<String> ambiguous,
                                  String key, Channel channel) {
        String normalized = normalizeMatchKey(key);
        if (normalized.isEmpty() || ambiguous.contains(normalized)) return;
        Channel previous = index.putIfAbsent(normalized, channel);
        if (previous != null && previous != channel) {
            index.remove(normalized);
            ambiguous.add(normalized);
        }
    }

    private static Channel uniqueMatch(Map<String, Channel> index, java.util.Set<String> ambiguous, String key) {
        String normalized = normalizeMatchKey(key);
        return normalized.isEmpty() || ambiguous.contains(normalized) ? null : index.get(normalized);
    }

    private static String normalizeMatchKey(String value) {
        if (value == null) return "";
        StringBuilder normalized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(ch)) normalized.append(ch);
        }
        return normalized.toString();
    }

    private static SimpleDateFormat guideTimeFormat() {
        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone(GUIDE_TIME_ZONE));
        return format;
    }

    private static long jsonTime(String value, SimpleDateFormat format) {
        if (value == null || value.trim().isEmpty()) return 0;
        ParsePosition position = new ParsePosition(0);
        java.util.Date parsed = format.parse(value.trim(), position);
        return parsed == null || position.getIndex() != value.trim().length() ? 0 : parsed.getTime();
    }

    static InputStream guideStream(InputStream raw) throws Exception {
        BufferedInputStream buffered = new BufferedInputStream(raw);
        buffered.mark(2);
        int first = buffered.read();
        int second = buffered.read();
        buffered.reset();
        InputStream decoded = first == 0x1f && second == 0x8b ? new GZIPInputStream(buffered) : buffered;
        return new ByteLimitInputStream(decoded, MAX_XML_GUIDE_BYTES);
    }

    private static long xmlTime(String value, SimpleDateFormat format) {
        if (value == null) return 0;
        try {
            String compact = value.trim().replace(" ", "");
            if (compact.length() == 14) compact += "+0000";
            else if (compact.length() > 19) compact = compact.substring(0, 19);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            java.util.Date parsed = format.parse(compact, position);
            return parsed == null || position.getIndex() != compact.length() ? 0 : parsed.getTime();
        } catch (Exception ignored) { return 0; }
    }

    private void saveCache(List<Channel> channels) throws Exception {
        String serialized = serializeChannels(channels).toString();
        // Keep two independent atomic copies. If an interrupted write or filesystem
        // corruption makes the primary unreadable, startup can recover the last cache.
        writeCache(CACHE_BACKUP_FILE, serialized);
        writeCache(CACHE_FILE, serialized);
    }

    private void writeCache(String fileName, String serialized) throws Exception {
        AtomicFile file = new AtomicFile(new java.io.File(context.getFilesDir(), fileName));
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(serialized.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception e) {
            if (output != null) file.failWrite(output);
            throw e;
        }
    }

    private JSONArray serializeChannels(List<Channel> channels) throws Exception {
        JSONArray array = new JSONArray();
        for (Channel c : channels) {
            JSONObject o = new JSONObject();
            o.put("name", c.name); o.put("url", c.streamUrl); o.put("id", c.epgId);
            o.put("group", c.group); o.put("logo", c.logoUrl); o.put("drmType", c.drmType);
            o.put("drmLicenseUrl", c.drmLicenseUrl); o.put("drmHeaders", new JSONObject(c.drmHeaders));
            o.put("mimeType", c.mimeType); o.put("streamHeaders", new JSONObject(c.streamHeaders));
            o.put("clearKeyData", c.clearKeyData);
            o.put("now", c.now); o.put("next", c.next);
            o.put("nowStartMs", c.nowStartMs); o.put("nowEndMs", c.nowEndMs);
            o.put("programmes", serializeProgrammes(c.programmes));
            array.put(o);
        }
        return array;
    }

    static JSONArray serializeProgrammes(List<Programme> programmes) throws Exception {
        JSONArray result = new JSONArray();
        for (Programme programme : programmes) {
            JSONObject value = new JSONObject();
            value.put("title", programme.title);
            value.put("startMs", programme.startMs);
            value.put("endMs", programme.endMs);
            result.put(value);
        }
        return result;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
