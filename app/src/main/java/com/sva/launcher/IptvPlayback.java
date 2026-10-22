package com.sva.launcher;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/** Per-player factories prevent channel credentials or keys reaching the next channel. */
@UnstableApi
final class IptvPlayback {
    private IptvPlayback() { }

    static MediaItem mediaItem(PlaybackRequest request) {
        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(request.url));
        if (!request.mimeType.isEmpty()) item.setMimeType(request.mimeType);
        if (!request.drmType.isEmpty()) {
            boolean clearKey = "clearkey".equals(request.drmType);
            if (clearKey && StreamOptions.HLS.equals(request.mimeType))
                throw new IllegalArgumentException(AppLanguage.text(R.string.ui_clearkey_playback_requires_a_dash_stream, "ClearKey playback requires a DASH stream."));
            if (clearKey && request.clearKeyData.isEmpty() && request.licenseUrl.isEmpty())
                throw new IllegalArgumentException(AppLanguage.text(R.string.ui_clearkey_needs_inline_keys_or_a_license_url, "ClearKey needs inline keys or a license URL."));
            if (clearKey && !request.clearKeyData.isEmpty()) StreamOptions.clearKeyResponse(request.clearKeyData);
            MediaItem.DrmConfiguration.Builder drm = new MediaItem.DrmConfiguration.Builder(
                    clearKey ? C.CLEARKEY_UUID : C.WIDEVINE_UUID);
            if (!request.licenseUrl.isEmpty()) drm.setLicenseUri(Uri.parse(request.licenseUrl));
            if (!request.licenseHeaders.isEmpty()) drm.setLicenseRequestHeaders(request.licenseHeaders);
            item.setDrmConfiguration(drm.build());
        }
        return item.build();
    }

    static ExoPlayer create(Context context, PlaybackRequest request) {
        MediaItem item = mediaItem(request);
        ExoPlayer.Builder player = new ExoPlayer.Builder(context);
        boolean localKeys = "clearkey".equals(request.drmType) && !request.clearKeyData.isEmpty();
        if (!request.streamHeaders.isEmpty() || localKeys) {
            DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(context);
            if (!request.streamHeaders.isEmpty()) {
                DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(request.streamHeaders);
                sources.setDataSourceFactory(new DefaultDataSource.Factory(context, http));
            }
            if (localKeys) {
                byte[] response = StreamOptions.clearKeyResponse(request.clearKeyData);
                sources.setDrmSessionManagerProvider(ignored -> new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(new LocalMediaDrmCallback(response)));
            }
            player.setMediaSourceFactory(sources);
        }
        ExoPlayer result = player.build();
        result.setMediaItem(item);
        return result;
    }
}
