package com.sva.launcher;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class IptvRepositoryTest {
    @Test public void parsesChannelLogoGroupAndIdentity() {
        List<IptvRepository.Channel> channels = IptvRepository.parseM3u(
                "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"news.one\" tvg-logo=\"https://img.test/news.png\" group-title=\"News\",News One\n" +
                "https://stream.test/news.m3u8\n");

        assertEquals(1, channels.size());
        IptvRepository.Channel channel = channels.get(0);
        assertEquals("https://img.test/news.png", channel.logoUrl);
        assertEquals("News", channel.group);
        assertEquals("id:news.one", channel.stableKey());
    }

    @Test public void preservesCommasInsideM3uMetadataAndChannelNames() {
        List<IptvRepository.Channel> channels = IptvRepository.parseM3u(
                "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"news.one\" group-title=\"News, India\",India, Now\n" +
                "https://stream.test/news.m3u8\n");

        assertEquals(1, channels.size());
        assertEquals("India, Now", channels.get(0).name);
        assertEquals("News, India", channels.get(0).group);
    }

    @Test public void rejectsPartialPlaylistInsteadOfReturningPartialChannelCache() {
        List<IptvRepository.Channel> channels = IptvRepository.parseM3u(
                "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"first\",First\n" +
                "https://stream.test/first.m3u8\n" +
                "#EXTINF:-1 tvg-id=\"second\",Second\n");

        assertTrue(channels.isEmpty());
    }

    @Test public void parsesKodiWidevineLicenseUrlAndEncodedHeaders() {
        List<IptvRepository.Channel> channels = IptvRepository.parseM3u(
                "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"movie.one\" group-title=\"Movies\",Movie One\n" +
                "#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha\n" +
                "#KODIPROP:inputstream.adaptive.license_key=https://license.test/wv|Authorization=Bearer%20abc%3D%3D%26Origin%3Dtv.test&Content-Type=|R{SSM}|\n" +
                "https://stream.test/movie.mpd\n");

        assertEquals(1, channels.size());
        IptvRepository.Channel channel = channels.get(0);
        assertEquals("widevine", channel.drmType);
        assertEquals("https://license.test/wv", channel.drmLicenseUrl);
        assertEquals("Bearer abc==", channel.drmHeaders.get("Authorization"));
        assertEquals("tv.test", channel.drmHeaders.get("Origin"));
        assertEquals("", channel.drmHeaders.get("Content-Type"));
    }

    @Test public void drmTagsDoNotLeakToFollowingChannel() {
        List<IptvRepository.Channel> channels = IptvRepository.parseM3u(
                "#EXTINF:-1 tvg-id=\"one\",Protected\n" +
                "#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha\n" +
                "#KODIPROP:inputstream.adaptive.license_key=https://license.test/wv||R{SSM}|\n" +
                "https://stream.test/one.mpd\n" +
                "#EXTINF:-1,Clear\n" +
                "https://stream.test/two.m3u8\n");

        assertEquals(2, channels.size());
        assertEquals("widevine", channels.get(0).drmType);
        assertFalse("widevine".equals(channels.get(1).drmType));
        assertTrue(channels.get(1).drmLicenseUrl.isEmpty());
    }

    @Test public void parsesGlobalHeadersIncludingBlankValues() {
        Map<String, String> headers = IptvRepository.parseHeaders("Authorization: Bearer token\nContent-Type:\n");

        assertEquals("Bearer token", headers.get("Authorization"));
        assertEquals("", headers.get("Content-Type"));
    }

    @Test public void readsPlainAndGzippedXmltvGuides() throws Exception {
        String xml = "<tv><programme channel=\"news.one\" /></tv>";
        byte[] plain = xml.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(plain);
        }

        for (byte[] input : new byte[][]{plain, compressed.toByteArray()}) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    IptvRepository.guideStream(new ByteArrayInputStream(input)), StandardCharsets.UTF_8))) {
                assertEquals(xml, reader.readLine());
            }
        }
    }

    @Test public void matchesJsonGuideByKeyAndUniqueNameAndUsesIndiaTime() throws Exception {
        IptvRepository.Channel keyed = channel("Pictures HD", "andpictureshd");
        IptvRepository.Channel named = channel("Star Plus", "unrelated.id");
        List<IptvRepository.Channel> channels = java.util.Arrays.asList(keyed, named);
        long now = ist("26-09-2026 12:15:00");
        List<Map<String, Object>> feed = java.util.Arrays.asList(
                entry("service_id", "3636", "unique_id", "11920_H_3636", "matching_key", "andpictureshd",
                        "channel", "&pictures HD", "schedule", java.util.Arrays.asList(
                                programme("Earlier", "26-09-2026 10:00:00", "26-09-2026 11:00:00"),
                                programme("Current show", "26-09-2026 11:00:00", "26-09-2026 12:30:00"),
                                programme("Next show", "26-09-2026 12:30:00", "26-09-2026 13:00:00"))),
                entry("channel", "Star Plus", "schedule", java.util.Collections.singletonList(
                        programme("Name match", "26-09-2026 12:00:00", "26-09-2026 13:00:00"))));

        IptvRepository.applyJsonGuideEntries(channels, feed, now);

        assertEquals("Current show", keyed.now);
        assertEquals("Next show", keyed.next);
        assertEquals(ist("26-09-2026 11:00:00"), keyed.nowStartMs);
        assertEquals(ist("26-09-2026 12:30:00"), keyed.nowEndMs);
        assertEquals("Name match", named.now);
    }

    @Test public void cachedScheduleAdvancesToTheProgrammeAiringNow() throws Exception {
        IptvRepository.Channel channel = channel("News", "newsid");
        List<Map<String, Object>> feed = java.util.Collections.singletonList(entry(
                "matching_key", "newsid", "channel", "News", "schedule", java.util.Arrays.asList(
                        programme("Two o'clock", "26-09-2026 14:00:00", "26-09-2026 14:30:00"),
                        programme("Half past two", "26-09-2026 14:30:00", "26-09-2026 15:00:00"),
                        programme("Three o'clock", "26-09-2026 15:00:00", "26-09-2026 15:30:00"))));

        IptvRepository.applyJsonGuideEntries(java.util.Collections.singletonList(channel), feed,
                ist("26-09-2026 14:10:00"));
        assertEquals("Two o'clock", channel.now);
        assertEquals("Half past two", channel.next);
        assertEquals(3, channel.programmes.size());

        IptvRepository.refreshProgrammeDisplay(channel, ist("26-09-2026 14:40:00"));
        assertEquals("Half past two", channel.now);
        assertEquals(ist("26-09-2026 14:30:00"), channel.nowStartMs);
        assertEquals(ist("26-09-2026 15:00:00"), channel.nowEndMs);
        assertEquals("Three o'clock", channel.next);
    }

    @Test public void cachedScheduleClearsAnExpiredProgrammeWhenThereIsNoNextShow() throws Exception {
        IptvRepository.Channel channel = channel("News", "newsid");
        List<Map<String, Object>> feed = java.util.Collections.singletonList(entry(
                "matching_key", "newsid", "channel", "News", "schedule",
                java.util.Collections.singletonList(programme("Old show", "26-09-2026 14:00:00", "26-09-2026 14:30:00"))));

        IptvRepository.applyJsonGuideEntries(java.util.Collections.singletonList(channel), feed,
                ist("26-09-2026 14:10:00"));
        IptvRepository.refreshProgrammeDisplay(channel, ist("26-09-2026 14:40:00"));

        assertEquals("", channel.now);
        assertEquals("", channel.next);
    }

    @Test public void jsonGuideRefreshClearsStaleProgrammeValues() throws Exception {
        IptvRepository.Channel channel = channel("Test Channel", "test.channel");
        channel.now = "Stale show";
        channel.next = "Stale next";
        channel.nowStartMs = 1;
        channel.nowEndMs = 2;
        List<Map<String, Object>> feed = java.util.Collections.singletonList(entry(
                "matching_key", "testchannel", "channel", "Test Channel", "schedule",
                java.util.Collections.singletonList(programme("Fresh show", "26-09-2026 12:00:00", "26-09-2026 13:00:00"))));

        IptvRepository.applyJsonGuideEntries(java.util.Collections.singletonList(channel), feed, ist("26-09-2026 12:15:00"));

        assertEquals("Fresh show", channel.now);
        assertEquals("", channel.next);
        assertEquals(ist("26-09-2026 13:00:00"), channel.nowEndMs);
    }

    @Test public void rejectsEmptyJsonGuide() throws Exception {
        try {
            IptvRepository.applyJsonGuideEntries(java.util.Collections.singletonList(channel("Test", "test")),
                    java.util.Collections.emptyList(), System.currentTimeMillis());
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("no channel"));
            return;
        }
        throw new AssertionError("Expected empty guide to be rejected");
    }

    @Test public void channelCardsWithDuplicateTvgIdsKeepDistinctIdentities() {
        IptvRepository.Channel first = channel("News HD", "news.shared");
        IptvRepository.Channel second = new IptvRepository.Channel("News SD", "https://stream.test/news-sd",
                "news.shared", "", "", "", "", new java.util.LinkedHashMap<>());
        IptvRepository.Channel updatedFirst = new IptvRepository.Channel("News HD", first.streamUrl,
                "news.shared", "", "", "", "", new java.util.LinkedHashMap<>());

        assertFalse(first.sameRenderIdentity(second));
        assertTrue(first.sameRenderIdentity(updatedFirst));
    }

    @Test public void channelRefreshPreservesGuidePerStreamWhenTvgIdsRepeat() {
        IptvRepository.Channel cachedHd = new IptvRepository.Channel("News HD", "https://stream.test/news-hd",
                "news.shared", "News", "", "", "", new java.util.LinkedHashMap<>());
        cachedHd.now = "HD programme";
        cachedHd.programmes.add(new IptvRepository.Programme("HD upcoming", 100L, 200L));
        IptvRepository.Channel cachedSd = new IptvRepository.Channel("News SD", "https://stream.test/news-sd",
                "news.shared", "News", "", "", "", new java.util.LinkedHashMap<>());
        cachedSd.now = "SD programme";
        IptvRepository.Channel freshSd = new IptvRepository.Channel("News SD", "https://stream.test/news-sd",
                "news.shared", "News", "", "", "", new java.util.LinkedHashMap<>());
        IptvRepository.Channel freshHd = new IptvRepository.Channel("News HD", "https://stream.test/news-hd",
                "news.shared", "News", "", "", "", new java.util.LinkedHashMap<>());
        List<IptvRepository.Channel> refreshed = java.util.Arrays.asList(freshSd, freshHd);

        IptvRepository.preserveGuideData(refreshed, java.util.Arrays.asList(cachedHd, cachedSd));

        assertEquals("SD programme", freshSd.now);
        assertEquals("HD programme", freshHd.now);
        assertEquals("HD upcoming", freshHd.programmes.get(0).title);
    }

    @Test public void unmatchedJsonGuideDoesNotClearExistingProgrammes() throws Exception {
        IptvRepository.Channel channel = channel("News", "news.id");
        channel.now = "Saved current";
        channel.next = "Saved next";
        List<Map<String, Object>> feed = java.util.Collections.singletonList(entry(
                "matching_key", "another.channel", "channel", "Another channel",
                "schedule", java.util.Collections.singletonList(programme(
                        "Unrelated show", "26-09-2026 12:00:00", "26-09-2026 13:00:00"))));

        try {
            IptvRepository.applyJsonGuideEntries(java.util.Collections.singletonList(channel), feed,
                    ist("26-09-2026 12:15:00"));
        } catch (IllegalArgumentException expected) {
            assertEquals("Saved current", channel.now);
            assertEquals("Saved next", channel.next);
            return;
        }
        throw new AssertionError("Expected a guide with no channel matches to be rejected");
    }

    @Test public void unusableJsonScheduleDoesNotClearExistingProgrammes() throws Exception {
        IptvRepository.Channel channel = channel("News", "news.id");
        channel.now = "Saved current";
        List<Map<String, Object>> feed = java.util.Collections.singletonList(entry(
                "matching_key", "newsid", "channel", "News", "schedule",
                java.util.Collections.singletonList(programme("Expired show", "25-09-2026 08:00:00", "25-09-2026 09:00:00"))));

        try {
            IptvRepository.applyJsonGuideEntries(java.util.Collections.singletonList(channel), feed,
                    ist("26-09-2026 12:15:00"));
        } catch (IllegalArgumentException expected) {
            assertEquals("Saved current", channel.now);
            return;
        }
        throw new AssertionError("Expected a guide without current or upcoming programmes to be rejected");
    }

    @Test public void guideRefreshWaitsADayAfterFailureOrSuccess() {
        long updated = 10_000_000L;
        assertFalse(IptvRepository.isGuideStale(updated + 59L * 60L * 1000L, 0, updated));
        assertFalse(IptvRepository.isGuideStale(updated + 23L * 60L * 60L * 1000L, 0, updated));
        assertTrue(IptvRepository.isGuideStale(updated + 24L * 60L * 60L * 1000L, 0, updated));
        assertFalse(IptvRepository.isGuideStale(updated + 23L * 60L * 60L * 1000L, updated, updated));
        assertTrue(IptvRepository.isGuideStale(updated + 24L * 60L * 60L * 1000L, updated, updated));
    }

    @Test public void guideRefreshBackfillsARecentLegacyCacheWithoutFullSchedule() {
        long updated = 10_000_000L;
        assertTrue(IptvRepository.isGuideStale(updated + 5_000L, updated, updated, true, false));
        assertFalse(IptvRepository.isGuideStale(updated + 5_000L, updated, updated + 1L, true, false));
        assertFalse(IptvRepository.isGuideStale(updated + 5_000L, updated, updated, true, true));
    }

    @Test public void channelRefreshWaitsADayAfterFailureOrSuccess() {
        long updated = 10_000_000L;
        assertFalse(IptvRepository.isChannelsStale(updated + 23L * 60L * 60L * 1000L, 0, updated));
        assertTrue(IptvRepository.isChannelsStale(updated + 24L * 60L * 60L * 1000L, 0, updated));
        assertFalse(IptvRepository.isChannelsStale(updated + 23L * 60L * 60L * 1000L, updated, updated));
        assertTrue(IptvRepository.isChannelsStale(updated + 24L * 60L * 60L * 1000L, updated, updated));
    }

    @Test public void customRefreshIntervalsAndManualModeAreHonored() {
        long updated = 1_000_000L;
        long sixHours = 6L * 60L * 60L * 1000L;
        assertFalse(IptvRepository.isChannelsStale(updated + sixHours - 1L, updated, updated, sixHours));
        assertTrue(IptvRepository.isChannelsStale(updated + sixHours, updated, updated, sixHours));
        assertFalse(IptvRepository.isChannelsStale(updated + 30L * SchedulePreferences.DAY,
                updated, updated, SchedulePreferences.MANUAL));
        assertFalse(IptvRepository.isGuideStale(updated + 30L * SchedulePreferences.DAY,
                updated, updated, true, false, SchedulePreferences.MANUAL));
        assertEquals(sixHours, IptvRepository.millisUntilRefresh(updated, updated, updated, sixHours));
        assertEquals(Long.MAX_VALUE, IptvRepository.millisUntilRefresh(
                updated, updated, updated, SchedulePreferences.MANUAL));
    }

    @Test public void cacheSourceMustMatchOnceItHasBeenRecorded() {
        assertTrue(IptvRepository.cacheMatchesSource("https://new.test/list.m3u", ""));
        assertTrue(IptvRepository.cacheMatchesSource("https://same.test/list.m3u",
                "https://same.test/list.m3u"));
        assertFalse(IptvRepository.cacheMatchesSource("https://new.test/list.m3u",
                "https://old.test/list.m3u"));
    }

    @Test public void channelCacheUsesBackupWhenPrimaryIsMissingOrEmpty() {
        IptvRepository.Channel saved = channel("Saved channel", "saved.id");
        List<IptvRepository.Channel> backup = java.util.Collections.singletonList(saved);

        assertEquals(backup, IptvRepository.selectCachedChannels(null, backup));
        assertEquals(backup, IptvRepository.selectCachedChannels(new java.util.ArrayList<>(), backup));
        assertTrue(IptvRepository.selectCachedChannels(null, null).isEmpty());
    }

    @Test public void guideRefreshCanRecoverFromAnEmptyDiskCacheUsingVisibleChannels() {
        IptvRepository.Channel visible = channel("News", "news.id");
        List<IptvRepository.Channel> recovered = IptvRepository.cacheOrFallback(
                java.util.Collections.emptyList(), java.util.Collections.singletonList(visible));

        assertEquals(1, recovered.size());
        assertFalse(visible == recovered.get(0));
        assertEquals("news.id", recovered.get(0).epgId);
    }

    @Test public void parsesAndNormalizesMultipleUrlsWithWhitespaceAndLineBreaks() {
        assertTrue(IptvRepository.parseUrls(null).isEmpty());
        assertTrue(IptvRepository.parseUrls("   \n\t\r\n  ").isEmpty());
        assertEquals("", IptvRepository.normalizeUrls(null));
        assertEquals("", IptvRepository.normalizeUrls("   \n\t  "));

        String raw = "  https://src1.test/list.m3u \r\n\r\n   https://src2.test/list.m3u\n https://src3.test/list.m3u  \n";
        List<String> parsed = IptvRepository.parseUrls(raw);
        assertEquals(3, parsed.size());
        assertEquals("https://src1.test/list.m3u", parsed.get(0));
        assertEquals("https://src2.test/list.m3u", parsed.get(1));
        assertEquals("https://src3.test/list.m3u", parsed.get(2));

        String normalized = IptvRepository.normalizeUrls(raw);
        assertEquals("https://src1.test/list.m3u\nhttps://src2.test/list.m3u\nhttps://src3.test/list.m3u", normalized);
    }

    @Test public void cacheMatchesMultiUrlSourcesRegardlessOfWhitespaceOrFormatting() {
        String configured = "https://one.test/list.m3u\r\nhttps://two.test/list.m3u\n";
        String cached = "  https://one.test/list.m3u  \n  https://two.test/list.m3u  ";
        assertTrue(IptvRepository.cacheMatchesSource(configured, cached));

        String different = "https://one.test/list.m3u\nhttps://other.test/list.m3u";
        assertFalse(IptvRepository.cacheMatchesSource(configured, different));
    }

    @Test public void combinesChannelsFromMultiplePlaylistsWithoutDeduplication() {
        List<IptvRepository.Channel> first = IptvRepository.parseM3u(
                "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"shared.id\",Channel One\nhttps://stream1.test/live.m3u8\n" +
                "#EXTINF:-1 tvg-id=\"chan2\",Channel Two\nhttps://stream2.test/live.m3u8\n");
        List<IptvRepository.Channel> second = IptvRepository.parseM3u(
                "#EXTM3U\n" +
                "#EXTINF:-1 tvg-id=\"shared.id\",Channel One Backup\nhttps://stream3.test/live.m3u8\n" +
                "#EXTINF:-1 tvg-id=\"chan4\",Channel Four\nhttps://stream4.test/live.m3u8\n");

        List<IptvRepository.Channel> combined = new java.util.ArrayList<>();
        combined.addAll(first);
        combined.addAll(second);

        assertEquals(4, combined.size());
        assertEquals("Channel One", combined.get(0).name);
        assertEquals("https://stream1.test/live.m3u8", combined.get(0).streamUrl);
        assertEquals("Channel Two", combined.get(1).name);
        assertEquals("Channel One Backup", combined.get(2).name);
        assertEquals("https://stream3.test/live.m3u8", combined.get(2).streamUrl);
        assertEquals("shared.id", combined.get(2).epgId);
        assertEquals("Channel Four", combined.get(3).name);
    }

    @Test public void multipleGuideSourcesAttachProgrammesToDifferentChannelsWithoutOverwriting() throws Exception {
        IptvRepository.Channel channel1 = channel("Channel One", "chan.one");
        IptvRepository.Channel channel2 = channel("Channel Two", "chan.two");
        List<IptvRepository.Channel> channels = java.util.Arrays.asList(channel1, channel2);
        long now = ist("26-09-2026 12:15:00");

        // Guide 1 matches Channel One
        List<Map<String, Object>> feed1 = java.util.Collections.singletonList(
                entry("matching_key", "chanone", "channel", "Channel One", "schedule",
                        java.util.Collections.singletonList(
                                programme("Source 1 Show", "26-09-2026 12:00:00", "26-09-2026 13:00:00"))));
        IptvRepository.applyJsonGuideEntries(channels, feed1, now);

        assertEquals("Source 1 Show", channel1.now);
        assertEquals("", channel2.now);

        // Guide 2 matches Channel Two
        List<Map<String, Object>> feed2 = java.util.Collections.singletonList(
                entry("matching_key", "chantwo", "channel", "Channel Two", "schedule",
                        java.util.Collections.singletonList(
                                programme("Source 2 Show", "26-09-2026 12:00:00", "26-09-2026 13:00:00"))));
        IptvRepository.applyJsonGuideEntries(channels, feed2, now);

        // Both channels now have their respective guide programmes attached
        assertEquals("Source 1 Show", channel1.now);
        assertEquals("Source 2 Show", channel2.now);
        assertEquals(1, channel1.programmes.size());
        assertEquals(1, channel2.programmes.size());
    }

    @Test public void multipleGuideSourcesMergeProgrammesForSameChannelChronologically() throws Exception {
        IptvRepository.Channel channel = channel("Sports", "sports.one");
        List<IptvRepository.Channel> channels = java.util.Collections.singletonList(channel);
        long now = ist("26-09-2026 10:15:00");

        // First guide provides 10:00-11:00 show
        List<Map<String, Object>> feed1 = java.util.Collections.singletonList(
                entry("matching_key", "sportsone", "channel", "Sports", "schedule",
                        java.util.Collections.singletonList(
                                programme("Morning Match", "26-09-2026 10:00:00", "26-09-2026 11:00:00"))));
        IptvRepository.applyJsonGuideEntries(channels, feed1, now);
        assertEquals("Morning Match", channel.now);
        assertEquals(1, channel.programmes.size());

        // Second guide provides 11:00-12:00 show AND duplicate 10:00-11:00 show
        List<Map<String, Object>> feed2 = java.util.Collections.singletonList(
                entry("matching_key", "sportsone", "channel", "Sports", "schedule", java.util.Arrays.asList(
                        programme("Morning Match Duplicate", "26-09-2026 10:00:00", "26-09-2026 11:00:00"),
                        programme("Afternoon Match", "26-09-2026 11:00:00", "26-09-2026 12:00:00"))));
        IptvRepository.applyJsonGuideEntries(channels, feed2, now);

        // No duplicate slot, non-duplicate slot appended, next show updated
        assertEquals(2, channel.programmes.size());
        assertEquals("Morning Match", channel.programmes.get(0).title);
        assertEquals("Afternoon Match", channel.programmes.get(1).title);
        assertEquals("Morning Match", channel.now);
        assertEquals("Afternoon Match", channel.next);
    }

    private static IptvRepository.Channel channel(String name, String id) {
        return new IptvRepository.Channel(name, "https://stream.test/" + id, id,
                "", "", "", "", new java.util.LinkedHashMap<>());
    }

    private static Map<String, Object> entry(Object... pairs) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    private static Map<String, String> programme(String title, String start, String end) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("title", title);
        result.put("start", start);
        result.put("end", end);
        return result;
    }

    private static long ist(String value) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        return format.parse(value).getTime();
    }
}
