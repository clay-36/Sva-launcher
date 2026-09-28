package com.deo.launcher;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class PublicCalendarRepositoryTest {
    @Test public void normalizesWebcalPublicGoogleCalendarLink() {
        assertEquals("https://calendar.google.com/calendar/ical/demo%40group.calendar.google.com/public/basic.ics",
                PublicCalendarRepository.normalizeUrl("webcal://calendar.google.com/calendar/ical/demo%40group.calendar.google.com/public/basic.ics"));
        assertNull(PublicCalendarRepository.normalizeUrl("https://example.com/calendar.ics"));
    }

    @Test public void parsesCurrentEventAndUnescapesTitle() {
        long now = Instant.parse("2026-09-26T09:30:00Z").toEpochMilli();
        List<PublicCalendarRepository.Event> events = PublicCalendarRepository.parse(
                "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:a\r\nSUMMARY:Movie\\, night\r\n"
                        + "DTSTART:20260926T090000Z\r\nDTEND:20260926T100000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n", now);

        assertEquals(1, events.size());
        assertEquals("Movie, night", events.get(0).title);
        assertTrue(events.get(0).startMs <= now);
        assertTrue(now < events.get(0).endMs);
    }

    @Test public void expandsWeeklyRecurringEventsForCurrentOccurrence() {
        long now = Instant.parse("2026-09-28T09:30:00Z").toEpochMilli(); // Monday
        List<PublicCalendarRepository.Event> events = PublicCalendarRepository.parse(
                "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:weekly\r\nSUMMARY:Weekly check-in\r\n"
                        + "DTSTART:20260907T090000Z\r\nDTEND:20260907T100000Z\r\n"
                        + "RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=8\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n", now);

        boolean currentFound = false;
        for (PublicCalendarRepository.Event event : events) {
            if (event.startMs <= now && now < event.endMs) currentFound = true;
        }
        assertTrue(currentFound);
        assertFalse(events.isEmpty());
    }

    @Test public void parsesAllDayEventAsLocalDayWindow() {
        long now = Instant.parse("2026-09-26T12:00:00Z").toEpochMilli();
        List<PublicCalendarRepository.Event> events = PublicCalendarRepository.parse(
                "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:day\nSUMMARY:Festival\n"
                        + "DTSTART;VALUE=DATE:20260926\nDTEND;VALUE=DATE:20260927\nEND:VEVENT\nEND:VCALENDAR\n", now);

        assertEquals(1, events.size());
        assertEquals("Festival", events.get(0).title);
        assertTrue(events.get(0).startMs <= now);
        assertTrue(now < events.get(0).endMs);
        assertTrue(events.get(0).allDay);
    }

    @Test public void retainsUpcomingYearlyAllDayEventWithinOneYear() {
        long now = Instant.parse("2026-09-27T08:00:00Z").toEpochMilli();
        List<PublicCalendarRepository.Event> events = PublicCalendarRepository.parse(
                "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Ravi's Birthday\n"
                        + "DTSTART;VALUE=DATE:20270403\nDTEND;VALUE=DATE:20270404\n"
                        + "RRULE:FREQ=YEARLY\nEND:VEVENT\nEND:VCALENDAR\n", now);

        assertEquals(1, events.size());
        assertEquals("Ravi's Birthday", events.get(0).title);
        assertTrue(events.get(0).startMs > now);
        assertTrue(events.get(0).allDay);
    }

    @Test public void retainsEventsFromEarlierMonthInPreviousYear() {
        long now = Instant.parse("2026-09-28T12:00:00Z").toEpochMilli();
        List<PublicCalendarRepository.Event> events = PublicCalendarRepository.parse(
                "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Past appointment\n"
                        + "DTSTART:20250904T100000Z\nDTEND:20250904T110000Z\n"
                        + "END:VEVENT\nEND:VCALENDAR\n", now);
        assertEquals(1, events.size());
        assertEquals("Past appointment", events.get(0).title);
    }
}
