package com.deo.launcher;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Parsed public YouTube link saved as a launcher home card. */
final class YouTubeCard {
    static final String VIDEO = "video";
    static final String PLAYLIST = "playlist";

    final String url;
    final String type;
    final String videoId;
    final String playlistId;
    String title;
    String autoTitle;
    boolean customTitle;
    String thumbnailUrl;
    long metadataCheckedAt;

    private YouTubeCard(String url, String type, String videoId, String playlistId) {
        this.url = url;
        this.type = type;
        this.videoId = videoId;
        this.playlistId = playlistId;
        this.title = VIDEO.equals(type) ? "YouTube video" : "YouTube playlist";
        this.autoTitle = this.title;
        if (VIDEO.equals(type) && videoId != null) {
            this.thumbnailUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
        }
    }

    static YouTubeCard parse(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;
        try {
            String value = rawUrl.trim();
            if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || host == null || !isYouTubeHost(host) || uri.getUserInfo() != null) return null;

            String path = uri.getPath() == null ? "" : uri.getPath();
            String videoId = queryParameter(uri.getRawQuery(), "v");
            String playlistId = queryParameter(uri.getRawQuery(), "list");
            if ("youtu.be".equalsIgnoreCase(host)) {
                videoId = firstPathSegment(path);
            } else if (videoId == null) {
                String[] segments = path.split("/");
                if (segments.length >= 3 && ("shorts".equals(segments[1]) || "live".equals(segments[1])
                        || "embed".equals(segments[1]))) videoId = segments[2];
            }

            if (isVideoId(videoId)) {
                String canonical = "https://www.youtube.com/watch?v=" + videoId;
                if (isPlaylistId(playlistId)) canonical += "&list=" + playlistId;
                return new YouTubeCard(canonical, VIDEO, videoId, isPlaylistId(playlistId) ? playlistId : null);
            }
            if (isPlaylistId(playlistId)) {
                return new YouTubeCard("https://www.youtube.com/playlist?list=" + playlistId,
                        PLAYLIST, null, playlistId);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean isYouTubeHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("youtube.com") || normalized.endsWith(".youtube.com")
                || normalized.equals("youtu.be");
    }

    private static boolean isVideoId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{11}");
    }

    private static boolean isPlaylistId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{8,}");
    }

    private static String firstPathSegment(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) return null;
        String[] segments = path.split("/");
        for (String segment : segments) if (!segment.isEmpty()) return segment;
        return null;
    }

    private static String queryParameter(String rawQuery, String key) {
        if (rawQuery == null) return null;
        for (String part : rawQuery.split("&")) {
            int equals = part.indexOf('=');
            if (equals < 0) continue;
            try {
                String name = URLDecoder.decode(part.substring(0, equals), StandardCharsets.UTF_8.name());
                if (key.equals(name)) return URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8.name());
            } catch (Exception ignored) { }
        }
        return null;
    }
}
