package com.sva.launcher;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public final class HiddenChannelsTest {

    private IptvRepository.Channel channel(String name, String id, String url, String group) {
        return new IptvRepository.Channel(name, url, id, group, "", "", "", Collections.emptyMap());
    }

    @Test
    public void isHiddenIdentifiesChannelsByStableKeyAndDirectKey() {
        IptvRepository.Channel withId = channel("STAR Plus", "ts550", "https://stream.test/1", "Hindi");
        IptvRepository.Channel noId = channel("Custom Stream", "", "https://stream.test/custom.m3u8", "Custom");
        IptvRepository.Channel other = channel("Colors", "ts543", "https://stream.test/2", "Hindi");

        Set<String> hidden = new HashSet<>();
        hidden.add(withId.stableKey()); // "id:ts550"
        hidden.add(noId.stableKey());   // "url:https://stream.test/custom.m3u8"

        assertTrue(HiddenChannels.isHidden(hidden, withId));
        assertTrue(HiddenChannels.isHidden(hidden, noId));
        assertFalse(HiddenChannels.isHidden(hidden, other));
        assertFalse(HiddenChannels.isHidden(null, withId));
        assertFalse(HiddenChannels.isHidden(hidden, null));
    }

    @Test
    public void isHiddenMatchesRawIdAndRawUrl() {
        IptvRepository.Channel withId = channel("STAR Plus", "ts550", "https://stream.test/1", "Hindi");
        IptvRepository.Channel noId = channel("Custom Stream", "", "https://stream.test/custom.m3u8", "Custom");

        Set<String> hiddenWithRawId = Collections.singleton("ts550");
        assertTrue(HiddenChannels.isHidden(hiddenWithRawId, withId));

        Set<String> hiddenWithRawUrl = Collections.singleton("https://stream.test/custom.m3u8");
        assertTrue(HiddenChannels.isHidden(hiddenWithRawUrl, noId));
    }

    @Test
    public void isHiddenStrictlyMatchesChannelIdAndUrlNotChannelName() {
        IptvRepository.Channel sd = channel("STAR Plus", "ts550", "https://stream.test/sd.m3u8", "Hindi");
        IptvRepository.Channel hd = channel("STAR Plus HD", "ts8", "https://stream.test/hd.m3u8", "Hindi");

        Set<String> hidden = Collections.singleton(sd.stableKey()); // only sd hidden
        assertTrue(HiddenChannels.isHidden(hidden, sd));
        assertFalse("HD channel with different ID/URL must not be hidden", HiddenChannels.isHidden(hidden, hd));
    }

    @Test
    public void toggleAddsAndRemovesHiddenChannel() {
        IptvRepository.Channel ch = channel("STAR Plus", "ts550", "https://stream.test/1", "Hindi");
        Set<String> hidden = new HashSet<>();

        boolean nowHidden = HiddenChannels.toggle(hidden, ch);
        assertTrue(nowHidden);
        assertTrue(HiddenChannels.isHidden(hidden, ch));
        assertTrue(hidden.contains(ch.stableKey()));

        boolean stillHidden = HiddenChannels.toggle(hidden, ch);
        assertFalse(stillHidden);
        assertFalse(HiddenChannels.isHidden(hidden, ch));
        assertFalse(hidden.contains(ch.stableKey()));
    }

    @Test
    public void filterVisibleExcludesHiddenChannels() {
        IptvRepository.Channel ch1 = channel("STAR Plus HD", "ts8", "https://stream.test/hd", "Hindi");
        IptvRepository.Channel ch2 = channel("Star Plus", "ts550", "https://stream.test/sd", "Hindi");
        IptvRepository.Channel ch3 = channel("Sony SAB", "ts559", "https://stream.test/sab", "Hindi");

        List<IptvRepository.Channel> all = Arrays.asList(ch1, ch2, ch3);
        Set<String> hidden = Collections.singleton(ch2.stableKey());

        List<IptvRepository.Channel> visible = HiddenChannels.filterVisible(all, hidden);
        assertEquals(2, visible.size());
        assertEquals("STAR Plus HD", visible.get(0).name);
        assertEquals("Sony SAB", visible.get(1).name);
    }

    @Test
    public void backupTypesIncludeHiddenChannels() throws Exception {
        org.json.JSONObject preferences = new org.json.JSONObject();
        org.json.JSONObject hiddenObj = new org.json.JSONObject();
        hiddenObj.put("type", "string-set");
        org.json.JSONArray array = new org.json.JSONArray();
        array.put("id:ts550");
        hiddenObj.put("value", array);
        preferences.put("hiddenChannels", hiddenObj);

        java.util.Map<String, Object> decoded = LauncherBackup.decodePreferences(preferences);
        assertTrue(decoded.containsKey("hiddenChannels"));
        @SuppressWarnings("unchecked")
        Set<String> decodedSet = (Set<String>) decoded.get("hiddenChannels");
        assertNotNull(decodedSet);
        assertTrue(decodedSet.contains("id:ts550"));
    }
}
