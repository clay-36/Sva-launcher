package com.deo.launcher;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads image URLs from a publicly shared Google Photos album page. */
final class GooglePhotosAlbumRepository {
    private static final int MAX_PAGE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_IMAGES = 500;
    private static final Pattern IMAGE_URL = Pattern.compile(
            "https?://lh[0-9]+\\.googleusercontent\\.com/(?:pw/)?[A-Za-z0-9_-]{40,}",
            Pattern.CASE_INSENSITIVE);

    private GooglePhotosAlbumRepository() { }

    static List<String> fetchImageUrls(String shareUrl) throws Exception {
        URL requested = new URL(shareUrl);
        if (!isPhotosHost(requested.getHost())) throw new IllegalArgumentException("Enter a Google Photos shared album link.");

        HttpURLConnection connection = (HttpURLConnection) requested.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("Accept-Encoding", "identity");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("Google Photos returned HTTP " + status + ".");
            if (!isPhotosHost(connection.getURL().getHost())) throw new IllegalArgumentException("The link did not open a Google Photos album.");
            String html;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_PAGE_BYTES) throw new IllegalStateException("The Google Photos album page is too large to read.");
                    output.write(buffer, 0, count);
                }
                html = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            return extractImageUrls(html);
        } finally {
            connection.disconnect();
        }
    }

    static List<String> extractImageUrls(String html) {
        String normalized = normalizeEscapes(html);
        Matcher matcher = IMAGE_URL.matcher(normalized);
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        while (matcher.find() && urls.size() < MAX_IMAGES) {
            String baseUrl = matcher.group();
            urls.add(baseUrl + "=w1600-h900");
        }
        return new ArrayList<>(urls);
    }

    private static String normalizeEscapes(String input) {
        StringBuilder output = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '\\' && i + 1 < input.length()) {
                if (input.charAt(i + 1) == '/') {
                    output.append('/');
                    i++;
                    continue;
                }
                if (i + 5 < input.length() && input.charAt(i + 1) == 'u') {
                    char replacement = input.regionMatches(true, i + 2, "003a", 0, 4) ? ':'
                            : input.regionMatches(true, i + 2, "002f", 0, 4) ? '/'
                            : input.regionMatches(true, i + 2, "003d", 0, 4) ? '='
                            : input.regionMatches(true, i + 2, "0026", 0, 4) ? '&' : 0;
                    if (replacement != 0) {
                        output.append(replacement);
                        i += 5;
                        continue;
                    }
                }
            }
            if (current == '&' && input.regionMatches(i, "&amp;", 0, 5)) {
                output.append('&');
                i += 4;
            } else {
                output.append(current);
            }
        }
        return output.toString();
    }

    private static boolean isPhotosHost(String host) {
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return normalized.equals("photos.google.com") || normalized.endsWith(".photos.google.com")
                || normalized.equals("photos.app.goo.gl") || normalized.equals("goo.gl");
    }
}
