package com.sva.launcher;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class NewsRepository {
    static final String FEEDS = "newsFeeds";
    static final String CACHE = "newsCache";
    static final String DEFAULTS = "https://feeds.bbci.co.uk/news/world/rss.xml\nhttps://www.theguardian.com/world/rss";
    private static final int MAX_STORIES = 100;
    static final class Story {
        String title, link, image, source;
        long publishedAt;
    }
    private NewsRepository() { }
    static List<Story> fetch(String feeds) throws Exception {
        ArrayList<Story> result = new ArrayList<>();
        Set<String> seenLinks = new HashSet<>();
        Exception lastFailure = null;
        for (String raw : feeds.split("\\R")) {
            String feedUrl = raw.trim();
            if (!feedUrl.startsWith("https://") && !feedUrl.startsWith("http://")) continue;
            try {
                String currentUrl = feedUrl;
                for (int redirects = 0; redirects <= 5; redirects++) {
                    HttpURLConnection c = (HttpURLConnection)new URL(currentUrl).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(10000); c.setInstanceFollowRedirects(false);
                    c.setRequestProperty("User-Agent", "SvaLauncher/1.0");
                    try {
                        int status = c.getResponseCode();
                        if (status >= 300 && status < 400) {
                            String location = c.getHeaderField("Location");
                            if (location == null || redirects == 5) throw new IOException("Too many or invalid feed redirects");
                            currentUrl = new URL(new URL(currentUrl), location).toString();
                            continue;
                        }
                        if (status < 200 || status >= 300) throw new IOException("News feed returned HTTP " + status);
                        try (InputStream in = c.getInputStream()) {
                            for (Story story : parse(in, new URL(feedUrl).getHost())) {
                                String link = story.link.trim();
                                if (seenLinks.add(link)) result.add(story);
                            }
                        }
                        break;
                    } finally {
                        c.disconnect();
                    }
                }
            } catch (Exception failure) {
                lastFailure = failure;
            }
        }
        if (result.isEmpty() && lastFailure != null) throw lastFailure;
        sortByTime(result);
        return new ArrayList<>(result.subList(0, Math.min(MAX_STORIES, result.size())));
    }

    static List<Story> parse(InputStream in, String source) throws Exception {
        ArrayList<Story> result = new ArrayList<>();
        XmlPullParser p = Xml.newPullParser(); p.setInput(in, null);
        Story item = null; String tag = ""; StringBuilder value = new StringBuilder();
        for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT; e = p.nextToken()) {
            if (e == XmlPullParser.START_TAG) {
                tag = localName(p.getName()); value.setLength(0);
                if (tag.equals("item") || tag.equals("entry")) { item = new Story(); item.source = source; }
                if (item == null) continue;
                if (tag.equals("link")) {
                    String href = p.getAttributeValue(null, "href"), rel = p.getAttributeValue(null, "rel");
                    if (href != null && (rel == null || rel.equals("alternate"))) item.link = href;
                }
                if (tag.equals("enclosure") || tag.equals("content") || tag.equals("thumbnail")) {
                    String image = p.getAttributeValue(null, "url");
                    if (image != null && item.image == null) item.image = image;
                }
            } else if ((e == XmlPullParser.TEXT || e == XmlPullParser.CDSECT) && item != null) {
                value.append(p.getText());
            } else if (e == XmlPullParser.END_TAG && item != null) {
                String end = localName(p.getName()), text = value.toString().trim();
                if (end.equals("title")) item.title = text;
                else if (end.equals("link") && item.link == null) item.link = text;
                else if (end.equals("pubdate") || end.equals("published") || end.equals("date")) {
                    long time = parseTime(text); if (time > 0) item.publishedAt = time;
                } else if (end.equals("updated") && item.publishedAt == 0) item.publishedAt = parseTime(text);
                else if (end.equals("item") || end.equals("entry")) {
                    if (item.title != null && item.link != null && item.link.startsWith("http")) result.add(item);
                    item = null;
                    if (result.size() >= MAX_STORIES) break;
                }
                value.setLength(0);
            }
        }
        return result;
    }
    private static String localName(String name) {
        return name.substring(name.indexOf(':') + 1).toLowerCase(Locale.ROOT);
    }
    static long parseTime(String value) {
        try { return Instant.parse(value).toEpochMilli(); } catch (DateTimeParseException ignored) { }
        try { return OffsetDateTime.parse(value).toInstant().toEpochMilli(); } catch (DateTimeParseException ignored) { }
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); } catch (DateTimeParseException ignored) { }
        return 0;
    }
    static void sortByTime(List<Story> stories) {
        // Stable ordering for equal/missing dates; undated items follow dated stories.
        stories.sort((a, b) -> Long.compare(b.publishedAt, a.publishedAt));
    }
    static String encode(List<Story> stories) {
        JSONArray a = new JSONArray();
        for (Story s : stories) {
            JSONObject o = new JSONObject();
            try { o.put("t", s.title); o.put("l", s.link); o.put("i", s.image); o.put("s", s.source); o.put("publishedAt", s.publishedAt); a.put(o); } catch (Exception ignored) { }
        }
        return a.toString();
    }
    static List<Story> decode(String json) {
        ArrayList<Story> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i); Story s = new Story();
                s.title = o.optString("t"); s.link = o.optString("l"); s.image = o.optString("i", null); s.source = o.optString("s");
                s.publishedAt = o.optLong("publishedAt", 0);
                if (s.link.startsWith("http")) out.add(s);
            }
        } catch (Exception ignored) { }
        sortByTime(out);
        return out;
    }
}
