package com.sva.launcher;

import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** One per-channel request used for both initial playback and channel zapping. */
final class PlaybackRequest {
    final String url, mimeType, drmType, licenseUrl, clearKeyData;
    final Map<String, String> streamHeaders, licenseHeaders;

    PlaybackRequest(String url, String mimeType, String drmType, String licenseUrl, String clearKeyData,
                    Map<String, String> streamHeaders, Map<String, String> licenseHeaders) {
        StreamOptions.Url stream = new StreamOptions.Url(url);
        this.url = stream.url; this.mimeType = StreamOptions.mimeType(mimeType);
        this.drmType = drmType; this.licenseUrl = licenseUrl; this.clearKeyData = clearKeyData;
        this.streamHeaders = StreamOptions.headers();
        for (Map.Entry<String, String> header : streamHeaders.entrySet())
            StreamOptions.putHeader(this.streamHeaders, header.getKey(), header.getValue());
        this.streamHeaders.putAll(stream.headers);
        this.licenseHeaders = new LinkedHashMap<>(licenseHeaders);
    }

    static PlaybackRequest resolve(IptvRepository.Channel channel, String defaultLicense, Map<String, String> defaultHeaders) {
        if (StreamOptions.isClearKey(channel.drmType))
            return new PlaybackRequest(channel.streamUrl, channel.mimeType, "clearkey", channel.drmLicenseUrl,
                    channel.clearKeyData, channel.streamHeaders, channel.drmHeaders);
        // Retain the existing per-channel/global Widevine fallback exactly.
        boolean own = !channel.drmLicenseUrl.isEmpty() || !channel.drmHeaders.isEmpty();
        String license = own ? channel.drmLicenseUrl : defaultLicense;
        String type = "widevine".equals(channel.drmType) || !license.isEmpty() ? "widevine" : "";
        return new PlaybackRequest(channel.streamUrl, channel.mimeType, type, license, "",
                channel.streamHeaders, own ? channel.drmHeaders : defaultHeaders);
    }

    JSONObject toJson() throws Exception {
        return new JSONObject().put("url", url).put("mimeType", mimeType).put("drmType", drmType)
                .put("licenseUrl", licenseUrl).put("clearKeyData", clearKeyData)
                .put("streamHeaders", new JSONObject(streamHeaders)).put("licenseHeaders", new JSONObject(licenseHeaders));
    }

    static PlaybackRequest fromJson(JSONObject json) {
        return new PlaybackRequest(json.optString("url"), json.optString("mimeType"), json.optString("drmType"),
                json.optString("licenseUrl"), json.optString("clearKeyData"),
                StreamOptions.jsonHeaders(json.optJSONObject("streamHeaders")),
                StreamOptions.jsonHeaders(json.optJSONObject("licenseHeaders")));
    }
}
