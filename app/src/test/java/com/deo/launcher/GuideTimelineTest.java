package com.sva.launcher;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class GuideTimelineTest {
    private IptvRepository.Channel channel() {
        return new IptvRepository.Channel("Test", "https://example.test", "test", "", "", "", "", Collections.emptyMap());
    }
    @Test public void gapsAndOverlapsProduceContinuousNonOverlappingTimeGeometry() {
        IptvRepository.Channel c = channel();
        c.programmes.add(new IptvRepository.Programme("Earlier", 100, 400));
        c.programmes.add(new IptvRepository.Programme("Later start wins", 200, 300));
        c.programmes.add(new IptvRepository.Programme("Future", 500, 600));
        c.programmes.add(new IptvRepository.Programme("Invalid", 700, 650));
        List<GuideTimeline.Slot> slots = GuideTimeline.slots(c, 0, 700);
        assertEquals(7, slots.size());
        long cursor = 0;
        for (GuideTimeline.Slot slot : slots) { assertEquals(cursor, slot.start); assertTrue(slot.end > slot.start); cursor = slot.end; }
        assertEquals(700, cursor);
        assertEquals("Later start wins", slots.get(GuideTimeline.at(slots, 250)).programme.title);
        assertNull(slots.get(GuideTimeline.at(slots, 450)).programme);
        assertEquals("Future", slots.get(GuideTimeline.at(slots, 500)).programme.title);
    }
    @Test public void missingSchedulesRemainFocusableAndWatchable() {
        List<GuideTimeline.Slot> slots = GuideTimeline.slots(channel(), 10, 100);
        assertEquals(1, slots.size()); assertNull(slots.get(0).programme);
        assertEquals(0, GuideTimeline.at(slots, 40));
        assertEquals(0, GuideTimeline.nearestProgramme(slots, 40));
    }
    @Test public void verticalNavigationChoosesNearestProgrammeWhenAnotherChannelHasAGap() {
        IptvRepository.Channel c = channel();
        c.programmes.add(new IptvRepository.Programme("Earlier", 100, 200));
        c.programmes.add(new IptvRepository.Programme("Later", 500, 600));
        List<GuideTimeline.Slot> slots = GuideTimeline.slots(c, 0, 700);
        assertEquals("Earlier", slots.get(GuideTimeline.nearestProgramme(slots, 250)).programme.title);
        assertEquals("Later", slots.get(GuideTimeline.nearestProgramme(slots, 450)).programme.title);
    }
    @Test public void positionsReflectDurationAndClipAcrossMidnight() {
        IptvRepository.Channel c = channel(); long midnight = 24 * 60 * 60_000L;
        c.programmes.add(new IptvRepository.Programme("Late movie", midnight - 60_000, midnight + 60_000));
        List<GuideTimeline.Slot> slots = GuideTimeline.slots(c, midnight, midnight + GuideTimeline.WINDOW);
        assertEquals(midnight, slots.get(0).start); assertEquals(midnight + 60_000, slots.get(0).end);
        assertEquals(1f / 180, GuideTimeline.fraction(midnight + 60_000, midnight), .0001f);
        assertEquals(.5f, GuideTimeline.fraction(midnight + GuideTimeline.WINDOW / 2, midnight), .0001f);
    }
    @Test public void halfHourRulerUsesLocalTimeIncludingQuarterHourZones() {
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"));
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(GuideTimeline.floor(1735689600123L));
            assertEquals(0, c.get(Calendar.MINUTE) % 30); assertEquals(0, c.get(Calendar.SECOND)); assertEquals(0, c.get(Calendar.MILLISECOND));
        } finally { TimeZone.setDefault(saved); }
    }
    @Test public void emptySlotsAndNullChannelSafelyHandled() {
        assertEquals(0, GuideTimeline.at(Collections.emptyList(), 12345));
        assertEquals(0, GuideTimeline.at(null, 12345));
        assertEquals(0, GuideTimeline.nearestProgramme(Collections.emptyList(), 12345));
        assertEquals(0, GuideTimeline.nearestProgramme(null, 12345));
        assertTrue(GuideTimeline.upcomingProgrammes(null, 100).isEmpty());
        assertNull(GuideTimeline.current(null, 100));
        assertNull(GuideTimeline.next(null, 100));
        IptvRepository.Channel empty = channel(); empty.programmes.clear();
        assertTrue(GuideTimeline.upcomingProgrammes(empty, 100).isEmpty());
        assertNull(GuideTimeline.current(empty, 100));
        assertNull(GuideTimeline.next(empty, 100));
    }
    @Test public void hiddenChannelsFilteredFromTimelineChannels() {
        IptvRepository.Channel shown = new IptvRepository.Channel("Shown", "https://example.test/1", "ts1", "General", "", "", "", Collections.emptyMap());
        IptvRepository.Channel hidden = new IptvRepository.Channel("Hidden", "https://example.test/2", "ts2", "General", "", "", "", Collections.emptyMap());
        List<IptvRepository.Channel> all = Arrays.asList(shown, hidden);
        Set<String> hiddenKeys = Collections.singleton("id:ts2");
        List<IptvRepository.Channel> visible = HiddenChannels.filterVisible(all, hiddenKeys);
        assertEquals(1, visible.size());
        assertEquals("Shown", visible.get(0).name);
    }
}
