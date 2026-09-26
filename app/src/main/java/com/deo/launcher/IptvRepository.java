package com.deo.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Small URL-backed M3U/XMLTV loader with a persisted channel/guide cache. */
final class IptvRepository {
    private static final String PREFS = "iptv";
    private static final String CACHE_FILE = "iptv-channels.json";
    private static final long REFRESH_AFTER_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_PLAYLIST_BYTES = 24 * 1024 * 1024;

    static final class Channel {
        final String name;
        final String streamUrl;
        final String epgId;
        final String group;
        final String logoUrl;
        final String drmType;
        final String drmLicenseUrl;
        final Map<String, String> drmHeaders;
        String now = "";
        String next = "";
        long nowStartMs;
        long nowEndMs;

        Channel(String name, String streamUrl, String epgId, String group, String logoUrl,
                String drmType, String drmLicenseUrl, Map<String, String> drmHeaders) {
            this.name = name;
            this.streamUrl = streamUrl;
            this.epgId = epgId;
            this.group = group;
            this.logoUrl = logoUrl;
            this.drmType = drmType;
            this.drmLicenseUrl = drmLicenseUrl;
            this.drmHeaders = new LinkedHashMap<>(drmHeaders);
        }

        String stableKey() { return epgId.isEmpty() ? "url:" + streamUrl : "id:" + epgId; }
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
    String drmLicenseUrl() { return prefs.getString("drmLicenseUrl", ""); }
    String drmHeaderText() { return prefs.getString("drmHeaders", ""); }
    Map<String, String> drmHeaders() { return parseHeaders(drmHeaderText()); }
    void saveUrls(String playlist, String guide) {
        prefs.edit().putString("playlist", playlist.trim()).putString("guide", guide.trim()).apply();
    }
    void saveDrmSettings(String url, String headers) {
        prefs.edit().putString("drmLicenseUrl", url.trim()).putString("drmHeaders", headers.trim()).apply();
    }

    boolean isStale() { return System.currentTimeMillis() - prefs.getLong("updated", 0) > REFRESH_AFTER_MS; }

    List<Channel> cachedChannels() {
        ArrayList<Channel> result = new ArrayList<>();
        AtomicFile file = new AtomicFile(new java.io.File(context.getFilesDir(), CACHE_FILE));
        try (FileInputStream input = file.openRead();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder raw = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) raw.append(buffer, 0, count);
            JSONArray array = new JSONArray(raw);
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
                        o.optString("drmLicenseUrl"), headers);
                c.now = o.optString("now");
                c.next = o.optString("next");
                c.nowStartMs = o.optLong("nowStartMs");
                c.nowEndMs = o.optLong("nowEndMs");
                result.add(c);
            }
        } catch (Exception ignored) { }
        return result;
    }

    void refresh(Callback callback) {
        final String playlist = playlistUrl();
        final String guide = guideUrl();
        new Thread(() -> {
            try {
                if (playlist.isEmpty()) throw new IllegalStateException("Add an M3U playlist URL in Settings.");
                List<Channel> channels = downloadPlaylist(playlist);
                if (!guide.isEmpty()) attachGuide(channels, guide);
                saveCache(channels);
                prefs.edit().putLong("updated", System.currentTimeMillis()).apply();
                callback.onLoaded(channels, channels.isEmpty() ? "No channels found in the playlist." : "Updated");
            } catch (Exception e) {
                List<Channel> cached = cachedChannels();
                callback.onLoaded(cached, cached.isEmpty() ? "Could not load playlist: " + safeMessage(e) : "Showing saved channels. Refresh failed: " + safeMessage(e));
            }
        }, "iptv-refresh").start();
    }

    private HttpURLConnection openConnection(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "DuoLauncher/1.0");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }
        return connection;
    }

    private List<Channel> downloadPlaylist(String address) throws Exception {
        HttpURLConnection connection = openConnection(address);
        try {
            int length = connection.getContentLength();
            if (length > MAX_PLAYLIST_BYTES) throw new IllegalStateException("Playlist is larger than 24 MB.");
            try (InputStream stream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return parseLines(reader, MAX_PLAYLIST_BYTES);
            }
        } finally { connection.disconnect(); }
    }

    static List<Channel> parseM3u(String content) {
        try { return parseLines(new BufferedReader(new StringReader(content)), Integer.MAX_VALUE); }
        catch (Exception ignored) { return new ArrayList<>(); }
    }

    private static List<Channel> parseLines(BufferedReader reader, long byteLimit) throws Exception {
        ArrayList<Channel> channels = new ArrayList<>();
        String name = null, id = "", group = "", logo = "", drmType = "", drmKey = "", line;
        long total = 0;
        while ((line = reader.readLine()) != null) {
            total += line.length() * 2L;
            if (total > byteLimit) throw new IllegalStateException("Playlist is larger than 24 MB.");
            line = line.trim();
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                name = comma >= 0 ? line.substring(comma + 1).trim() : attribute(line, "tvg-name");
                id = attribute(line, "tvg-id");
                group = attribute(line, "group-title");
                logo = attribute(line, "tvg-logo");
                drmType = "";
                drmKey = "";
                if (name == null || name.isEmpty()) name = "Channel";
            } else if (line.startsWith("#KODIPROP:")) {
                int split = line.indexOf('=', 10);
                if (split > 0) {
                    String property = line.substring(10, split).trim();
                    String value = line.substring(split + 1).trim();
                    if (property.endsWith("license_type")) drmType = value;
                    else if (property.endsWith("license_key")) drmKey = value;
                }
            } else if (!line.isEmpty() && !line.startsWith("#") && name != null) {
                LicenseData license = parseKodiLicenseKey(drmKey);
                String type = drmType.toLowerCase(Locale.US).contains("widevine") || !drmKey.isEmpty() ? "widevine" : "";
                channels.add(new Channel(name, line, id, group, logo, type, license.url, license.headers));
                name = null; id = ""; group = ""; logo = ""; drmType = ""; drmKey = "";
            }
        }
        return channels;
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
        Map<String, Channel> byId = new HashMap<>();
        for (Channel c : channels) if (!c.epgId.isEmpty()) byId.put(c.epgId, c);
        HttpURLConnection connection = openConnection(address);
        XmlPullParser parser = Xml.newPullParser();
        try (InputStream raw = connection.getInputStream();
             InputStream stream = guideStream(raw)) {
            parser.setInput(stream, "UTF-8");
            long now = System.currentTimeMillis();
            String channelId = null, title = null, start = null, stop = null;
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
                    Channel channel = byId.get(channelId);
                    long begin = xmlTime(start), end = xmlTime(stop);
                    if (channel != null && title != null && begin > 0 && end > begin) {
                        if (begin <= now && end > now && channel.now.isEmpty()) {
                            channel.now = title;
                            channel.nowStartMs = begin;
                            channel.nowEndMs = end;
                        }
                        else if (begin > now && channel.next.isEmpty()) channel.next = title;
                    }
                    title = null;
                }
            }
        } finally { connection.disconnect(); }
    }

    static InputStream guideStream(InputStream raw) throws Exception {
        BufferedInputStream buffered = new BufferedInputStream(raw);
        buffered.mark(2);
        int first = buffered.read();
        int second = buffered.read();
        buffered.reset();
        return first == 0x1f && second == 0x8b ? new GZIPInputStream(buffered) : buffered;
    }

    private static long xmlTime(String value) {
        if (value == null) return 0;
        try {
            String compact = value.trim().replace(" ", "");
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyyMMddHHmmssZ", java.util.Locale.US);
            if (compact.length() == 14) compact += "+0000";
            else if (compact.length() > 19) compact = compact.substring(0, 19);
            return format.parse(compact).getTime();
        } catch (Exception ignored) { return 0; }
    }

    private void saveCache(List<Channel> channels) throws Exception {
        JSONArray array = new JSONArray();
        for (Channel c : channels) {
            JSONObject o = new JSONObject();
            o.put("name", c.name); o.put("url", c.streamUrl); o.put("id", c.epgId);
            o.put("group", c.group); o.put("logo", c.logoUrl); o.put("drmType", c.drmType);
            o.put("drmLicenseUrl", c.drmLicenseUrl); o.put("drmHeaders", new JSONObject(c.drmHeaders));
            o.put("now", c.now); o.put("next", c.next);
            o.put("nowStartMs", c.nowStartMs); o.put("nowEndMs", c.nowEndMs);
            array.put(o);
        }
        AtomicFile file = new AtomicFile(new java.io.File(context.getFilesDir(), CACHE_FILE));
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(array.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception e) {
            if (output != null) file.failWrite(output);
            throw e;
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
