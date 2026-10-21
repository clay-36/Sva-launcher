package com.sva.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public final class CalendarFeedsTest {
    private static final long NOW = Instant.parse("2026-10-21T10:00:00Z").toEpochMilli();
    private static final String A = "https://calendar.example/a.ics";
    private static final String B = "https://calendar.example/b.ics";
    private static final String EMPTY = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";

    private static String event(String uid, String title) {
        return "BEGIN:VEVENT\r\n" + (uid.isEmpty() ? "" : "UID:" + uid + "\r\n") + "SUMMARY:" + title
                + "\r\nDTSTART:20261021T100000Z\r\nDTEND:20261021T110000Z\r\nEND:VEVENT\r\n";
    }
    private static String feed(String events) { return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events + "END:VCALENDAR\r\n"; }
    private static CalendarFeedCache cache() { return CalendarFeedCache.load("", "", "[]", 0); }

    @Test public void multilineNormalizesAndDeduplicatesWithoutChangingTokens() {
        assertEquals(Arrays.asList("https://example.com/a%40b.ics?key=a%2Fb", "http://192.168.1.20:8080/tv.ics"),
                PublicCalendarRepository.normalizeUrls(" \r\nwebcal://EXAMPLE.com/a%40b.ics?key=a%2Fb\r\n"
                        + "https://example.com:443/a%40b.ics?key=a%2Fb#ignored\n\nhttp://192.168.1.20:8080/tv.ics\n"));
        assertTrue(PublicCalendarRepository.normalizeUrls(" \n\r\n").isEmpty());
        assertEquals(Collections.singletonList(A), PublicCalendarRepository.normalizeUrls(A));
    }

    @Test public void publishedProviderFeedSupportsFoldedTextAndTimezone() {
        String published = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Nextcloud//Calendar//EN\r\n"
                + "BEGIN:VEVENT\r\nUID:provider@example.com\r\nSUMMARY:Team\r\n  meeting\r\n"
                + "DTSTART;TZID=Europe/London:20261021T110000\r\nDTEND;TZID=Europe/London:20261021T120000\r\n"
                + "END:VEVENT\r\nEND:VCALENDAR\r\n";
        PublicCalendarRepository.Event event = PublicCalendarRepository.parse(published, NOW).get(0);
        assertEquals("Team meeting", event.title); assertEquals(NOW, event.startMs);
        assertEquals("provider@example.com", event.uid);
        assertEquals("https://example.com/a.ics", PublicCalendarRepository.normalizeUrl("http://example.com:80/a.ics"));
    }

    @Test public void invalidLineReportsItsNumberWithoutLeakingUrl() {
        try {
            PublicCalendarRepository.normalizeUrls(A + "\n\nhttps://user:secret@example.com/calendar.ics");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().startsWith("Line 3:"));
            assertFalse(expected.getMessage().contains("secret"));
        }
        for (String value : Arrays.asList("ftp://example.com/a.ics", "file:///a.ics", "https:///a.ics", "https://x:65536/a", "https://x:0/a", "not a url"))
            assertNull(value, PublicCalendarRepository.normalizeUrl(value));
    }

    @Test public void localAddressRulesAndPublicHttpUpgrade() throws Exception {
        for (String host : Arrays.asList("10.1.2.3", "172.16.0.1", "172.31.255.254", "192.168.0.2", "169.254.1.2", "127.0.0.1", "localhost", "tv.local", "[::1]", "[fd12::1]", "[fe80::1]"))
            assertEquals(host, "http://" + host + "/tv.ics", PublicCalendarRepository.normalizeUrl("http://" + host + "/tv.ics"));
        for (String host : Arrays.asList("8.8.8.8", "172.15.1.2", "172.32.0.1", "192.169.0.1", "example.com", "tv.local.example.com", "[2001:4860:4860::8888]", "0.0.0.0"))
            assertEquals(host, "https://" + host + "/tv.ics", PublicCalendarRepository.normalizeUrl("http://" + host + "/tv.ics"));
        assertFalse(PublicCalendarRepository.isLocalAddress(InetAddress.getByName("8.8.8.8")));
    }

    @Test public void redirectsResolveRelativeLinksAndRejectDowngradesOrCredentials() throws Exception {
        assertEquals("https://calendar.example/next.ics", PublicCalendarRepository.redirectUrl(A, "next.ics"));
        assertEquals("https://other.example/feed", PublicCalendarRepository.redirectUrl(A, "https://other.example/feed"));
        for (String to : Arrays.asList("http://192.168.1.1/a", "http://public.example/a", "ftp://example.com/a", "https://user:secret@example.com/a")) {
            try { PublicCalendarRepository.redirectUrl(A, to); fail(to); }
            catch (IOException expected) { assertFalse(expected.getMessage().contains("secret")); }
        }
    }

    private static final class Response extends HttpURLConnection {
        final int status;
        final String location;
        final byte[] body;
        boolean closed;
        Response(URL url, int status, String location, byte[] body) { super(url); this.status=status; this.location=location; this.body=body; }
        @Override public int getResponseCode() { return status; }
        @Override public String getHeaderField(String name) { return "Location".equals(name) ? location : null; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
        @Override public void disconnect() { closed = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }

    @Test public void downloaderFollowsValidatedRedirectsAndPreservesLimits() throws Exception {
        List<Response> connections = new ArrayList<>();
        String result = PublicCalendarRepository.download(A, url -> {
            Response response = new Response(url, connections.isEmpty() ? 302 : 200, "/next.ics", EMPTY.getBytes("UTF-8"));
            connections.add(response); return response;
        }, InetAddress::getAllByName);
        assertEquals(EMPTY, result);
        assertEquals(2, connections.size());
        assertEquals("/next.ics", connections.get(1).getURL().getPath());
        for (Response response : connections) {
            assertTrue(response.closed); assertFalse(response.getInstanceFollowRedirects());
            assertEquals(12000, response.getConnectTimeout()); assertEquals(20000, response.getReadTimeout());
        }
        for (int mode = 0; mode < 4; mode++) {
            final int scenario = mode;
            List<Response> failed = new ArrayList<>();
            try {
                PublicCalendarRepository.download(A, url -> {
                    byte[] body = scenario == 1 ? new byte[6 * 1024 * 1024 + 1] : "<html>Login</html>".getBytes("UTF-8");
                    Response response = new Response(url, scenario == 2 ? 404 : scenario == 3 ? 302 : 200, "/loop", body);
                    failed.add(response); return response;
                }, InetAddress::getAllByName);
                fail("scenario " + mode);
            } catch (IOException expected) {
                assertTrue(failed.stream().allMatch(r -> r.closed));
                if (mode == 3) assertEquals(6, failed.size());
            }
        }
    }

    @Test public void localDnsIsCheckedAndConnectionUsesCheckedAddress() throws Exception {
        final URL[] connected = {null};
        assertEquals(EMPTY, PublicCalendarRepository.download("http://tv.local:8080/tv.ics", url -> {
            connected[0] = url; return new Response(url, 200, null, EMPTY.getBytes("UTF-8"));
        }, host -> new InetAddress[]{InetAddress.getByName("192.168.1.20")}));
        assertEquals("192.168.1.20", connected[0].getHost());
        assertEquals(8080, connected[0].getPort());
        try {
            PublicCalendarRepository.download("http://tv.local/tv.ics", url -> { fail("must not connect"); return null; },
                    host -> new InetAddress[]{InetAddress.getByName("192.168.1.20"), InetAddress.getByName("8.8.8.8")});
            fail();
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("local network")); }
    }

    @Test public void sourcesMergeByIdentityRatherThanTitleAndSourceOrderWins() {
        CalendarFeedCache cache = cache();
        List<String> urls = Arrays.asList(A, B);
        cache.refresh(urls, NOW, url -> feed(url.equals(A) ? event("shared", "First") + event("a", "Same title") + event("", "No UID")
                : event("shared", "Second") + event("b", "Same title") + event("", "No UID")));
        List<PublicCalendarRepository.Event> merged = cache.merged(urls, NOW);
        assertEquals(5, merged.size()); assertEquals("First", merged.get(0).title);
        assertEquals("Second", cache.merged(Arrays.asList(B, A), NOW).get(0).title);
    }

    @Test public void recurringInstancesAndAllDayDatesSurviveSerialization() {
        String recurring = "BEGIN:VEVENT\nUID:weekly\nSUMMARY:Weekly\nDTSTART:20261021T100000Z\nRRULE:FREQ=DAILY;COUNT=3\nEND:VEVENT\n";
        String allDay = "BEGIN:VEVENT\nUID:day\nSUMMARY:All day\nDTSTART;VALUE=DATE:20261022\nEND:VEVENT\n";
        CalendarFeedCache cache = cache();
        List<String> urls = Arrays.asList(A, B);
        cache.refresh(urls, NOW, url -> feed(recurring + allDay));
        assertEquals(4, cache.merged(urls, NOW).size());
        CalendarFeedCache restored = CalendarFeedCache.load(cache.encode(), String.join("\n", urls), "[]", 0);
        List<PublicCalendarRepository.Event> events = restored.merged(urls, NOW);
        assertEquals(4, events.size());
        assertEquals(3, events.stream().filter(e -> e.uid.equals("weekly")).map(e -> e.instance).distinct().count());
        assertEquals(1, events.stream().filter(e -> e.allDay).count());
    }

    @Test public void recurrenceIdUsesOriginalInstanceEvenWhenMoved() {
        String moved = feed("BEGIN:VEVENT\nUID:weekly\nRECURRENCE-ID:20261021T100000Z\nDTSTART:20261021T120000Z\nSUMMARY:Moved\nEND:VEVENT\n");
        PublicCalendarRepository.Event event = PublicCalendarRepository.parse(moved, NOW).get(0);
        assertEquals("weekly", event.uid); assertEquals(Long.toString(NOW), event.instance);
        assertEquals(NOW + 2 * 60 * 60 * 1000, event.startMs);
    }

    @Test public void partialFailureKeepsOnlyFailedSourcesOldEventsAndTimestamp() throws Exception {
        CalendarFeedCache cache = cache(); List<String> urls = Arrays.asList(A, B);
        cache.refresh(urls, NOW, url -> feed(event(url, "Old")));
        CalendarFeedCache.RefreshResult result = cache.refresh(urls, NOW + 1000, url -> {
            if (url.equals(B)) throw new java.net.SocketTimeoutException("https://secret.example/token");
            return feed(event(url, "New"));
        });
        assertEquals(1, result.succeeded); assertEquals(1, result.errors.size());
        assertFalse(result.message(2).contains("secret")); assertTrue(result.message(2).contains("Source 2"));
        assertEquals("New", cache.merged(urls, NOW).get(0).title);
        assertEquals("Old", cache.merged(urls, NOW).get(1).title);
        JSONArray snapshots = new JSONObject(cache.encode()).getJSONArray("sources");
        assertEquals(NOW + 1000, snapshots.getJSONObject(0).getLong("updatedAt"));
        assertEquals(NOW, snapshots.getJSONObject(1).getLong("updatedAt"));
        String before = cache.encode();
        CalendarFeedCache.RefreshResult totalFailure = cache.refresh(urls, NOW + 2000, url -> { throw new IOException("https://private-token"); });
        assertEquals(0, totalFailure.succeeded); assertEquals(before, cache.encode());
        assertFalse(totalFailure.message(2).contains("private-token"));
    }

    @Test public void emptyFeedReplacesSnapshotAndRemovedSourcesDoNotReturn() {
        CalendarFeedCache cache = cache(); List<String> urls = Arrays.asList(A, B);
        cache.refresh(urls, NOW, url -> feed(event(url, "Old")));
        cache.refresh(Collections.singletonList(A), NOW, url -> EMPTY);
        assertTrue(cache.merged(urls, NOW).isEmpty());
        assertFalse(cache.encode().contains(B));
    }

    @Test public void migratesSingleCalendarCacheButNeverAssignsItToMultipleSources() throws Exception {
        String legacy = new JSONArray().put(new JSONObject().put("title", "Legacy").put("start", NOW).put("end", NOW + 1000)).toString();
        CalendarFeedCache cache = CalendarFeedCache.load("", A, legacy, NOW);
        assertEquals("Legacy", cache.merged(Collections.singletonList(A), NOW).get(0).title);
        assertEquals(NOW, new JSONObject(cache.encode()).getJSONArray("sources").getJSONObject(0).getLong("updatedAt"));
        assertTrue(CalendarFeedCache.load("", A + "\n" + B, legacy, NOW).merged(Arrays.asList(A, B), NOW).isEmpty());
    }

    @Test public void mergedEventLimitAppliesAcrossSourcesAndStaleEventsExpire() {
        CalendarFeedCache cache = cache(); List<String> urls = Arrays.asList(A, B);
        StringBuilder events = new StringBuilder();
        for (int i = 0; i < 6000; i++) events.append(event("", "Event " + i));
        cache.refresh(urls, NOW, url -> feed(events.toString()));
        assertEquals(PublicCalendarRepository.MAX_EVENTS, cache.merged(urls, NOW).size());
        assertTrue(cache.merged(urls, NOW + 500L * 24 * 60 * 60 * 1000).isEmpty());
    }
}
