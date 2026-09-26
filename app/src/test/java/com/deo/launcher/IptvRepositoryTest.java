package com.deo.launcher;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
}
