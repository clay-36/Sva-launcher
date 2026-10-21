package com.sva.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source snapshots are separate from the merged display cache so failures preserve good data. */
final class CalendarFeedCache {
    static final String KEY = "publicCalendarSources";
    private static final int VERSION = 1;
    private final Map<String, Snapshot> sources = new LinkedHashMap<>();

    interface Downloader { String download(String url) throws Exception; }

    static CalendarFeedCache load(String encoded, String savedUrls, String legacyEvents, long legacyUpdated) {
        CalendarFeedCache cache = new CalendarFeedCache();
        try {
            JSONObject root = new JSONObject(encoded);
            if (root.optInt("version") == VERSION) {
                JSONArray entries = root.optJSONArray("sources");
                if (entries != null) for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.optJSONObject(i);
                    if (entry == null) continue;
                    String url = PublicCalendarRepository.normalizeUrl(entry.optString("url"));
                    if (url != null) cache.sources.put(url, new Snapshot(
                            decodeEvents(entry.optJSONArray("events")), entry.optLong("updatedAt")));
                }
            }
        } catch (Exception ignored) { }
        List<String> urls = PublicCalendarRepository.normalizeUrls(savedUrls);
        if (cache.sources.isEmpty() && urls.size() == 1) {
            try {
                cache.sources.put(urls.get(0), new Snapshot(decodeEvents(new JSONArray(legacyEvents)), legacyUpdated));
            } catch (Exception ignored) { }
        }
        cache.retain(urls);
        return cache;
    }

    void retain(List<String> urls) { sources.keySet().retainAll(urls); }

    RefreshResult refresh(List<String> urls, long now, Downloader downloader) {
        retain(urls);
        int succeeded = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            if (Thread.currentThread().isInterrupted()) break;
            String url = urls.get(i);
            try {
                List<PublicCalendarRepository.Event> events = PublicCalendarRepository.parse(downloader.download(url), now);
                sources.put(url, new Snapshot(events, now));
                succeeded++;
            } catch (Exception failure) {
                // Exception messages can include private tokens/URLs; expose only a safe category.
                errors.add("Source " + (i + 1) + ": " + safeError(failure));
            }
        }
        return new RefreshResult(succeeded, errors);
    }

    private static String safeError(Exception error) {
        if (error instanceof java.net.SocketTimeoutException) return "connection timed out";
        if (error instanceof java.net.UnknownHostException) return "host unavailable";
        if (error instanceof javax.net.ssl.SSLException) return "secure connection failed";
        String message = error.getMessage();
        if (message != null && (message.matches("Calendar feed returned HTTP [0-9]{3}\\.")
                || message.equals("Calendar feed is larger than 6 MB.")
                || message.equals("This link did not return an iCalendar feed.")
                || message.equals("HTTP calendars must stay on your local network.")
                || message.equals("Calendar redirect is not allowed.")
                || message.equals("Calendar redirect limit exceeded or destination missing."))) return message;
        return "could not read this calendar";
    }

    List<PublicCalendarRepository.Event> merged(List<String> urls, long now) {
        List<PublicCalendarRepository.Event> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        long window = 400L * 24 * 60 * 60 * 1000;
        for (String url : urls) {
            Snapshot snapshot = sources.get(url);
            if (snapshot == null) continue;
            for (PublicCalendarRepository.Event event : snapshot.events) {
                if (event.endMs <= now - window || event.startMs > now + window) continue;
                String key = event.uid.length() + ":" + event.uid + ":" + event.instance;
                if (event.uid.isEmpty() || seen.add(key)) result.add(event);
            }
        }
        result.sort(Comparator.comparingLong(event -> event.startMs));
        return result.size() <= PublicCalendarRepository.MAX_EVENTS ? result
                : new ArrayList<>(result.subList(0, PublicCalendarRepository.MAX_EVENTS));
    }

    String encode() {
        try {
            JSONArray entries = new JSONArray();
            for (Map.Entry<String, Snapshot> entry : sources.entrySet()) {
                entries.put(new JSONObject().put("url", entry.getKey()).put("updatedAt", entry.getValue().updatedAt)
                        .put("events", encodeEvents(entry.getValue().events)));
            }
            return new JSONObject().put("version", VERSION).put("sources", entries).toString();
        } catch (Exception failure) { throw new IllegalStateException("Could not cache calendars.", failure); }
    }

    static JSONArray encodeEvents(List<PublicCalendarRepository.Event> events) throws Exception {
        JSONArray result = new JSONArray();
        for (PublicCalendarRepository.Event event : events) {
            result.put(new JSONObject().put("title", event.title).put("start", event.startMs).put("end", event.endMs)
                    .put("allDay", event.allDay).put("uid", event.uid).put("instance", event.instance));
        }
        return result;
    }

    static List<PublicCalendarRepository.Event> decodeEvents(JSONArray encoded) {
        List<PublicCalendarRepository.Event> result = new ArrayList<>();
        if (encoded == null) return result;
        for (int i = 0; i < encoded.length() && result.size() < PublicCalendarRepository.MAX_EVENTS; i++) {
            JSONObject event = encoded.optJSONObject(i);
            if (event == null || event.optLong("end") <= event.optLong("start")) continue;
            result.add(new PublicCalendarRepository.Event(event.optString("title", "Calendar event"),
                    event.optLong("start"), event.optLong("end"), event.optBoolean("allDay"),
                    event.optString("uid"), event.optString("instance")));
        }
        return result;
    }

    static final class RefreshResult {
        final int succeeded;
        final List<String> errors;
        RefreshResult(int succeeded, List<String> errors) { this.succeeded = succeeded; this.errors = errors; }
        String message(int eventCount) {
            return succeeded + " sources refreshed · " + errors.size() + " failed · " + eventCount + " events cached"
                    + (errors.isEmpty() ? "." : ".\n" + String.join("\n", errors));
        }
    }

    private static final class Snapshot {
        final List<PublicCalendarRepository.Event> events;
        final long updatedAt;
        Snapshot(List<PublicCalendarRepository.Event> events, long updatedAt) {
            this.events = events; this.updatedAt = updatedAt;
        }
    }
}
