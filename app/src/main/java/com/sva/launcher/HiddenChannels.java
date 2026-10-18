package com.sva.launcher;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HiddenChannels {
    public static final String PREF_HIDDEN = "hiddenChannels";

    private HiddenChannels() { }

    public static Set<String> load(SharedPreferences preferences) {
        if (preferences == null) return new HashSet<>();
        return new HashSet<>(preferences.getStringSet(PREF_HIDDEN, Collections.emptySet()));
    }

    public static void save(SharedPreferences preferences, Set<String> hiddenKeys) {
        if (preferences == null) return;
        preferences.edit().putStringSet(PREF_HIDDEN, new HashSet<>(hiddenKeys)).apply();
    }

    public static boolean isHidden(Set<String> hiddenKeys, IptvRepository.Channel channel) {
        if (channel == null || hiddenKeys == null || hiddenKeys.isEmpty()) return false;
        if (hiddenKeys.contains(channel.stableKey())) return true;
        if (!channel.epgId.isEmpty()) {
            if (hiddenKeys.contains("id:" + channel.epgId) || hiddenKeys.contains(channel.epgId)) return true;
        }
        if (!channel.streamUrl.isEmpty()) {
            if (hiddenKeys.contains("url:" + channel.streamUrl) || hiddenKeys.contains(channel.streamUrl)) return true;
        }
        return false;
    }

    public static boolean toggle(Set<String> hiddenKeys, IptvRepository.Channel channel) {
        if (channel == null || hiddenKeys == null) return false;
        String key = channel.stableKey();
        if (isHidden(hiddenKeys, channel)) {
            hiddenKeys.remove(key);
            if (!channel.epgId.isEmpty()) {
                hiddenKeys.remove("id:" + channel.epgId);
                hiddenKeys.remove(channel.epgId);
            }
            if (!channel.streamUrl.isEmpty()) {
                hiddenKeys.remove("url:" + channel.streamUrl);
                hiddenKeys.remove(channel.streamUrl);
            }
            return false;
        } else {
            hiddenKeys.add(key);
            return true;
        }
    }

    public static List<IptvRepository.Channel> filterVisible(List<IptvRepository.Channel> channels,
                                                             Set<String> hiddenKeys) {
        List<IptvRepository.Channel> visible = new ArrayList<>();
        if (channels == null) return visible;
        for (IptvRepository.Channel channel : channels) {
            if (!isHidden(hiddenKeys, channel)) {
                visible.add(channel);
            }
        }
        return visible;
    }
}
