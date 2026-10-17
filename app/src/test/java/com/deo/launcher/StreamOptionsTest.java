package com.sva.launcher;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public final class StreamOptionsTest {
    private static final String PAIR = "000102030405060708090a0b0c0d0e0f:00112233445566778899aabbccddeeff";

    private IptvRepository.Channel channel(String options, String url) {
        return IptvRepository.parseM3u("#EXTM3U\n#EXTINF:-1 tvg-id=\"one\",One\n" + options + url + "\n").get(0);
    }

    @Test public void headersDecodeIndividuallyWithoutInventingOtherHeaders() {
        Map<String, String> headers = StreamOptions.encodedHeaders("Authorization=Bearer%20a%26b%3Dc&Cookie=a%3Db%2Bc&Empty=");
        assertEquals("Bearer a&b=c", headers.get("authorization"));
        assertEquals("a=b+c", headers.get("cookie"));
        assertEquals("", headers.get("empty")); assertEquals(3, headers.size());
    }

    @Test public void streamHeadersHaveDeterministicCaseInsensitivePrecedence() {
        IptvRepository.Channel c = channel("#EXTVLCOPT:http-user-agent=VLC Agent\n"
                + "#EXTVLCOPT:http-referrer=https://origin.test/page\n"
                + "#EXTVLCOPT:http-origin=https://origin.test\n"
                + "#KODIPROP:inputstream.adaptive.stream_headers=user-agent=Kodi%20Agent&Authorization=Bearer%20stream\n",
                "https://stream.test/live|USER-AGENT=URL%20Agent");
        PlaybackRequest request = PlaybackRequest.resolve(c, "", Collections.emptyMap());
        assertEquals("https://stream.test/live", request.url);
        assertEquals("URL Agent", request.streamHeaders.get("User-Agent"));
        assertEquals("https://origin.test/page", request.streamHeaders.get("Referer"));
        assertEquals("Bearer stream", request.streamHeaders.get("authorization"));
        assertTrue(request.licenseHeaders.isEmpty());
        assertEquals("id:one", c.stableKey());
    }

    @Test public void rejectsHeaderControlCharactersAndMalformedEncoding() {
        Map<String, String> headers = StreamOptions.encodedHeaders("Good=yes&Bad=one%0D%0AX-Evil%3Ayes&Invalid%20Name=no&Broken=%zz");
        assertEquals(Collections.singletonMap("Good", "yes"), headers);
    }

    @Test public void onlyRecognizedHintsOverrideFormatInference() {
        assertEquals(StreamOptions.HLS, channel("#KODIPROP:mimetype=application/vnd.apple.mpegurl\n", "https://test/live").mimeType);
        assertEquals(StreamOptions.DASH, channel("#KODIPROP:inputstream.adaptive.manifest_type=mpd\n", "https://test/live").mimeType);
        assertEquals("", StreamOptions.mimeType("unknown"));
        assertEquals("", channel("", "https://test/live.m3u8").mimeType);
    }

    @Test public void newOptionsNeverLeakToTheFollowingChannel() {
        List<IptvRepository.Channel> channels = IptvRepository.parseM3u("#EXTINF:-1,First\n"
                + "#EXTVLCOPT:http-user-agent=Private\n#KODIPROP:inputstream.adaptive.manifest_type=mpd\n"
                + "#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey\n"
                + "#KODIPROP:inputstream.adaptive.license_key=" + PAIR + "\nhttps://test/one\n"
                + "#EXTINF:-1,Second\nhttps://test/two.m3u8\n");
        assertEquals(2, channels.size());
        IptvRepository.Channel second = channels.get(1);
        assertTrue(second.streamHeaders.isEmpty()); assertEquals("", second.mimeType);
        assertEquals("", second.drmType); assertEquals("", second.clearKeyData);
    }

    @Test public void inlineClearKeyOverridesGlobalWidevineWithoutUsingItsCredentials() throws Exception {
        IptvRepository.Channel c = channel("#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey\n"
                + "#KODIPROP:inputstream.adaptive.license_key=" + PAIR + "\n", "https://test/clear.mpd");
        PlaybackRequest request = PlaybackRequest.resolve(c, "https://license.test/widevine", Collections.singletonMap("Authorization", "private"));
        assertEquals("clearkey", request.drmType); assertEquals("", request.licenseUrl);
        assertEquals(PAIR, request.clearKeyData); assertTrue(request.licenseHeaders.isEmpty());
        JSONObject response = new JSONObject(new String(StreamOptions.clearKeyResponse(PAIR), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("AAECAwQFBgcICQoLDA0ODw", response.getJSONArray("keys").getJSONObject(0).getString("kid"));
        assertEquals("ABEiM0RVZneImaq7zN3u_w", response.getJSONArray("keys").getJSONObject(0).getString("k"));
    }

    @Test public void supportsMultipleClearKeysAndJwkJson() throws Exception {
        byte[] pairs = StreamOptions.clearKeyResponse(PAIR + "," + PAIR);
        JSONObject response = new JSONObject(new String(pairs, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(2, response.getJSONArray("keys").length());
        assertArrayEquals(pairs, StreamOptions.clearKeyResponse(response.toString()));
    }

    @Test public void clearKeyEndpointHeadersAreSeparateFromStreamHeaders() {
        IptvRepository.Channel c = channel("#KODIPROP:inputstream.adaptive.license_type=clearkey\n"
                + "#KODIPROP:inputstream.adaptive.license_key=https://license.test/keys|Authorization=Bearer%20license%26token\n"
                + "#EXTVLCOPT:http-user-agent=Stream Agent\n", "https://test/movie.mpd");
        PlaybackRequest request = PlaybackRequest.resolve(c, "https://wrong.test", Collections.emptyMap());
        assertEquals("https://license.test/keys", request.licenseUrl);
        assertEquals("Bearer license&token", request.licenseHeaders.get("Authorization"));
        assertFalse(request.streamHeaders.containsKey("Authorization")); assertEquals("", request.clearKeyData);
    }

    @Test public void malformedClearKeyDoesNotRejectThePlaylistOrExposeTheValue() {
        IptvRepository.Channel c = channel("#KODIPROP:inputstream.adaptive.license_type=clearkey\n"
                + "#KODIPROP:inputstream.adaptive.license_key=secret-invalid-key\n", "https://test/live.mpd");
        assertEquals("clearkey", c.drmType);
        try { StreamOptions.clearKeyResponse(c.clearKeyData); fail("Invalid key accepted"); }
        catch (IllegalArgumentException e) { assertFalse(e.getMessage().contains("secret-invalid-key")); }
    }

    @Test public void oldWidevineAndGlobalDefaultsKeepTheirExistingResolution() {
        IptvRepository.Channel plain = channel("", "https://test/live.m3u8");
        PlaybackRequest clear = PlaybackRequest.resolve(plain, "", Collections.emptyMap());
        assertEquals("", clear.drmType); assertEquals(plain.streamUrl, clear.url);
        PlaybackRequest global = PlaybackRequest.resolve(plain, "https://global.test/license", Collections.singletonMap("Token", "global"));
        assertEquals("widevine", global.drmType); assertEquals("global", global.licenseHeaders.get("Token"));
        IptvRepository.Channel own = channel("#KODIPROP:inputstream.adaptive.license_key=https://own.test/license|Token=own\n", "https://test/live.mpd");
        PlaybackRequest request = PlaybackRequest.resolve(own, "https://global.test/license", Collections.singletonMap("Token", "global"));
        assertEquals("widevine", request.drmType); assertEquals("https://own.test/license", request.licenseUrl);
        assertEquals("own", request.licenseHeaders.get("Token"));
    }

    @Test public void initialAndZapRequestsUseTheSameSerializableConfiguration() throws Exception {
        IptvRepository.Channel c = channel("#KODIPROP:inputstream.adaptive.manifest_type=hls\n"
                + "#EXTVLCOPT:http-user-agent=Agent\n", "https://test/opaque");
        PlaybackRequest first = PlaybackRequest.resolve(c, "", Collections.emptyMap());
        PlaybackRequest zap = PlaybackRequest.fromJson(first.toJson());
        assertEquals(first.url, zap.url); assertEquals(first.mimeType, zap.mimeType);
        assertEquals(first.streamHeaders, zap.streamHeaders); assertEquals(first.drmType, zap.drmType);
        assertEquals(first.licenseHeaders, zap.licenseHeaders);
    }
}
