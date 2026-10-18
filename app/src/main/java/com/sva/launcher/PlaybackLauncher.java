package com.sva.launcher;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import androidx.media3.common.util.UnstableApi;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Shared playback configuration for channel cards and the full-screen guide. */
@UnstableApi
final class PlaybackLauncher {
    private PlaybackLauncher() { }

    static Intent intent(Activity activity, IptvRepository.Channel channel,
                         List<IptvRepository.Channel> channels) throws Exception {
        IptvRepository repository = new IptvRepository(activity);
        Set<String> hidden = HiddenChannels.load(activity.getSharedPreferences("launcher", 0));
        Set<String> favorites = FavoriteChannels.load(activity.getSharedPreferences("launcher", 0));
        IptvRepository.refreshProgrammeDisplay(channels, System.currentTimeMillis());
        Intent payload = new Intent(activity, PlaybackActivity.class)
                .putExtra("name", channel.name).putExtra("now", channel.now).putExtra("next", channel.next)
                .putExtra("nowStartMs", channel.nowStartMs).putExtra("nowEndMs", channel.nowEndMs)
                .putExtra("stableKey", channel.stableKey())
                .putExtra("favorite", favorites.contains(channel.stableKey()))
                .putExtra("programmes", IptvRepository.serializeProgrammes(channel.programmes).toString())
                .putExtra("playbackRequest", PlaybackRequest.resolve(channel, repository.drmLicenseUrl(), repository.drmHeaders()).toJson().toString());
        JSONArray zap = new JSONArray();
        int selected = 0;
        for (IptvRepository.Channel item : channels) {
            if (HiddenChannels.isHidden(hidden, item)) continue;
            if (item.sameRenderIdentity(channel)) selected = zap.length();
            zap.put(new JSONObject().put("name", item.name).put("now", item.now).put("next", item.next)
                    .put("nowStartMs", item.nowStartMs).put("nowEndMs", item.nowEndMs)
                    .put("stableKey", item.stableKey())
                    .put("favorite", favorites.contains(item.stableKey()))
                    .put("playbackRequest", PlaybackRequest.resolve(item, repository.drmLicenseUrl(), repository.drmHeaders()).toJson()));
        }
        return PlaybackSession.createIntent(activity, payload.putExtra("zapChannels", zap.toString()).putExtra("zapIndex", selected));
    }

    static void watch(Activity activity, IptvRepository.Channel channel, List<IptvRepository.Channel> channels) {
        try { activity.startActivity(intent(activity, channel, channels)); }
        catch (Exception ignored) { Toast.makeText(activity, "Could not open playback. Please try again.", Toast.LENGTH_LONG).show(); }
    }
}
