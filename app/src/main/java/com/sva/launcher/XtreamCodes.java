package com.sva.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live-only Xtream Player API source and channel mapping. */
final class XtreamCodes {
    final String server, username, password;

    XtreamCodes(String address, String username, String password) {
        try {
            URI uri = new URI(address.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getPort() > 65535 || uri.getPort() == 0
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) throw new IllegalArgumentException();
            String base = uri.toASCIIString();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            this.server = base;
        } catch (Exception e) {
            throw new IllegalArgumentException(AppLanguage.text(R.string.ui_xtream_invalid_server, "Enter a valid HTTP or HTTPS server URL, including the port if required."));
        }
        if (username.trim().isEmpty() || password.isEmpty())
            throw new IllegalArgumentException(AppLanguage.text(R.string.ui_xtream_missing_credentials, "Enter both username and password."));
        this.username = username.trim();
        this.password = password;
    }

    String api(String action) {
        return server + "/player_api.php?" + credentials() + "&action=" + action;
    }
    String guide() { return server + "/xmltv.php?" + credentials(); }
    private String credentials() { return "username=" + encode(username) + "&password=" + encode(password); }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid account details."); }
    }

    static XtreamCodes fromSource(String source) {
        try {
            URI uri = new URI(source);
            if (!uri.getPath().endsWith("/player_api.php")) return null;
            Map<String, String> query = new LinkedHashMap<>();
            for (String pair : uri.getRawQuery().split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) query.put(parts[0], URLDecoder.decode(parts[1], "UTF-8"));
            }
            if (!"get_live_streams".equals(query.get("action"))) return null;
            String base = source.substring(0, source.lastIndexOf("/player_api.php"));
            return new XtreamCodes(base, query.getOrDefault("username", ""), query.getOrDefault("password", ""));
        } catch (Exception e) { return null; }
    }

    List<IptvRepository.Channel> channels(JSONArray streams, JSONArray categories) throws Exception {
        Map<String, String> names = new LinkedHashMap<>();
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category != null) names.put(category.optString("category_id"), category.optString("category_name"));
        }
        List<IptvRepository.Channel> channels = new ArrayList<>();
        for (int i = 0; i < streams.length(); i++) {
            JSONObject stream = streams.optJSONObject(i);
            if (stream == null) continue;
            String type = stream.optString("stream_type", "live");
            if (!"live".equals(type)) continue;
            long id = stream.optLong("stream_id", 0);
            String name = stream.optString("name", "").trim();
            if (id <= 0 || name.isEmpty()) continue;
            String category = stream.optString("category_id", "");
            String epg = stream.isNull("epg_channel_id") ? "" : stream.optString("epg_channel_id", "");
            String logo = stream.isNull("stream_icon") ? "" : stream.optString("stream_icon", "");
            String url = server + "/live/" + encode(username) + "/" + encode(password) + "/" + id + ".ts";
            channels.add(new IptvRepository.Channel(name, url, epg, names.getOrDefault(category, ""),
                    logo, "", "", Collections.emptyMap()));
        }
        return channels;
    }
}
