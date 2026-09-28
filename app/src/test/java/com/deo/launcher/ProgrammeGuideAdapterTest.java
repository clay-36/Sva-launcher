package com.deo.launcher;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class ProgrammeGuideAdapterTest {
    @Test public void picksCurrentAndSoonestFutureProgrammeEvenWhenFeedIsUnsorted() {
        IptvRepository.Channel channel = new IptvRepository.Channel("News", "https://example.test/live",
                "news", "News", "", "", "", Collections.emptyMap());
        channel.programmes.add(new IptvRepository.Programme("Later", 400, 500));
        channel.programmes.add(new IptvRepository.Programme("Current", 200, 300));
        channel.programmes.add(new IptvRepository.Programme("Next", 300, 400));
        assertEquals("Current", ProgrammeGuideAdapter.current(channel, 250).title);
        assertEquals("Next", ProgrammeGuideAdapter.next(channel, 250).title);
        assertEquals("Next", ProgrammeGuideAdapter.current(channel, 300).title);
        assertEquals("Later", ProgrammeGuideAdapter.next(channel, 300).title);
        assertNull(ProgrammeGuideAdapter.current(channel, 500));
        assertNull(ProgrammeGuideAdapter.next(channel, 500));
    }
}
