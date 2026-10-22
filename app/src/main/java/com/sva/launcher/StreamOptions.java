package com.sva.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/** Playlist option parsing, independent of Android and the player. */
final class StreamOptions {
    static final String HLS = "application/x-mpegURL";
    static final String DASH = "application/dash+xml";
    private StreamOptions() { }

    static Map<String, String> headers() { return new TreeMap<>(String.CASE_INSENSITIVE_ORDER); }

    static void putHeader(Map<String, String> headers, String name, String value) {
        if (name == null || value == null) return;
        name = name.trim();
        if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) return;
        headers.put(name, value);
    }

    static Map<String, String> encodedHeaders(String encoded) {
        Map<String, String> result = headers();
        for (String pair : encoded.split("&")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) continue;
            try {
                putHeader(result, URLDecoder.decode(pair.substring(0, equals), "UTF-8"),
                        URLDecoder.decode(pair.substring(equals + 1), "UTF-8"));
            } catch (IllegalArgumentException | java.io.UnsupportedEncodingException ignored) { }
        }
        return result;
    }

    static Map<String, String> jsonHeaders(JSONObject json) {
        Map<String, String> result = headers();
        if (json != null) {
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next(); putHeader(result, key, json.optString(key));
            }
        }
        return result;
    }

    static String mimeType(String hint) {
        if (hint == null) return "";
        switch (hint.trim().toLowerCase(java.util.Locale.US)) {
            case "hls": case "m3u8": case "application/x-mpegurl": case "application/vnd.apple.mpegurl": return HLS;
            case "mpd": case "dash": case "application/dash+xml": return DASH;
            default: return "";
        }
    }

    static final class Url {
        final String url;
        final Map<String, String> headers;
        Url(String source) {
            int pipe = source.indexOf('|');
            Map<String, String> parsed = pipe < 0 ? StreamOptions.headers() : encodedHeaders(source.substring(pipe + 1));
            url = parsed.isEmpty() ? source : source.substring(0, pipe);
            headers = parsed;
        }
    }

    static boolean isClearKey(String type) {
        return "clearkey".equalsIgnoreCase(type) || "org.w3.clearkey".equalsIgnoreCase(type);
    }

    static boolean isHttp(String url) {
        return url.regionMatches(true, 0, "https://", 0, 8) || url.regionMatches(true, 0, "http://", 0, 7);
    }

    /** Canonical, bounded JWK response; exceptions deliberately contain no supplied keys. */
    static byte[] clearKeyResponse(String config) {
        try {
            if (config == null || config.trim().isEmpty() || config.length() > 65536) throw new IllegalArgumentException();
            String value = config.trim();
            JSONArray keys = new JSONArray();
            if (value.startsWith("{")) {
                JSONArray source = new JSONObject(value).getJSONArray("keys");
                if (source.length() == 0 || source.length() > 64) throw new IllegalArgumentException();
                for (int i = 0; i < source.length(); i++) {
                    JSONObject key = source.getJSONObject(i);
                    if (!"oct".equals(key.getString("kty"))) throw new IllegalArgumentException();
                    keys.put(jwk(decodeKey(key.getString("kid")), decodeKey(key.getString("k"))));
                }
            } else {
                String[] pairs = value.split(",", -1);
                if (pairs.length > 64) throw new IllegalArgumentException();
                for (String pair : pairs) {
                    String[] parts = pair.trim().split(":", -1);
                    if (parts.length != 2) throw new IllegalArgumentException();
                    keys.put(jwk(hex(parts[0].trim()), hex(parts[1].trim())));
                }
            }
            return new JSONObject().put("keys", keys).put("type", "temporary").toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            throw new IllegalArgumentException(AppLanguage.text(R.string.ui_invalid_clearkey_configuration_supply_valid_128_bit_kid_key_pair, "Invalid ClearKey configuration. Supply valid 128-bit KID/key pairs or a JWK key set."));
        }
    }

    private static byte[] hex(String value) {
        if (!value.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException();
        byte[] bytes = new byte[16];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    private static byte[] decodeKey(String value) {
        byte[] decoded = Base64.getUrlDecoder().decode(value.replace('+', '-').replace('/', '_'));
        if (decoded.length != 16) throw new IllegalArgumentException();
        return decoded;
    }

    private static JSONObject jwk(byte[] kid, byte[] key) throws Exception {
        return new JSONObject().put("kty", "oct")
                .put("kid", Base64.getUrlEncoder().withoutPadding().encodeToString(kid))
                .put("k", Base64.getUrlEncoder().withoutPadding().encodeToString(key));
    }
}
