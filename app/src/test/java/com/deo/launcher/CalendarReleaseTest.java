package com.sva.launcher;

import org.junit.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.Assert.*;

public final class CalendarReleaseTest {
    private static final long NOW = Instant.parse("2026-06-01T12:00:00Z").toEpochMilli();
    @Test public void allDayEventWithoutEndLastsOneDay() {
        List<PublicCalendarRepository.Event> events = PublicCalendarRepository.parse(
                "BEGIN:VCALENDAR\nBEGIN:VEVENT\nDTSTART;VALUE=DATE:20260601\nSUMMARY:Day\nEND:VEVENT\nEND:VCALENDAR",NOW);
        assertEquals(1,events.size()); assertTrue(events.get(0).allDay);
        assertEquals(24*60*60*1000L,events.get(0).endMs-events.get(0).startMs);
    }
    @Test public void oldUnboundedAnniversariesStillAppear() {
        List<PublicCalendarRepository.Event> events = PublicCalendarRepository.parse(
                "BEGIN:VEVENT\nDTSTART;VALUE=DATE:19000101\nRRULE:FREQ=YEARLY\nSUMMARY:Anniversary\nEND:VEVENT",NOW);
        assertTrue(events.stream().anyMatch(e -> Instant.ofEpochMilli(e.startMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString().equals("2026-01-01")));
    }
    @Test public void recurrenceExpansionCannotGrowWithoutLimit() {
        String event = "BEGIN:VEVENT\nDTSTART;VALUE=DATE:20260601\nRRULE:FREQ=DAILY\nSUMMARY:Daily\nEND:VEVENT\n";
        StringBuilder feed = new StringBuilder("BEGIN:VCALENDAR\n");
        for(int i=0;i<100;i++)feed.append(event);
        feed.append("END:VCALENDAR");
        assertEquals(PublicCalendarRepository.MAX_EVENTS,PublicCalendarRepository.parse(feed.toString(),NOW).size());
    }
}
