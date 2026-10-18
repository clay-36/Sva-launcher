package com.sva.launcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class ProgrammeGuideAdapterTest {
    @Test public void picksCurrentAndSoonestFutureProgrammeEvenWhenFeedIsUnsorted() {
        IptvRepository.Channel channel = new IptvRepository.Channel("News", "https://example.test/live",
                "news", "News", "", "", "", Collections.emptyMap());
        channel.programmes.add(new IptvRepository.Programme("Later", 400, 500));
        channel.programmes.add(new IptvRepository.Programme("Current", 200, 300));
        channel.programmes.add(new IptvRepository.Programme("Next", 300, 400));
        assertEquals("Current", GuideTimeline.current(channel, 250).title);
        assertEquals("Next", GuideTimeline.next(channel, 250).title);
        assertEquals("Next", GuideTimeline.current(channel, 300).title);
        assertEquals("Later", GuideTimeline.next(channel, 300).title);
        assertNull(GuideTimeline.current(channel, 500));
        assertNull(GuideTimeline.next(channel, 500));
    }

    @Test public void includesEveryCurrentAndUpcomingProgrammeInHorizontalScheduleOrder() {
        IptvRepository.Channel news = new IptvRepository.Channel("News", "https://example.test/news",
                "news", "News", "", "", "", Collections.emptyMap());
        news.programmes.add(new IptvRepository.Programme("Later", 400, 500));
        news.programmes.add(new IptvRepository.Programme("Ended", 100, 200));
        news.programmes.add(new IptvRepository.Programme("Now", 200, 300));
        news.programmes.add(new IptvRepository.Programme("Next", 300, 400));

        List<IptvRepository.Programme> schedule = GuideTimeline.upcomingProgrammes(news, 250);
        assertEquals(3, schedule.size());
        assertEquals(Arrays.asList("Now", "Next", "Later"), Arrays.asList(
                schedule.get(0).title, schedule.get(1).title, schedule.get(2).title));
    }
}
