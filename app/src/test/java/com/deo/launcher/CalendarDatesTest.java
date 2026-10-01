package com.sva.launcher;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CalendarDatesTest {
    @Test public void includesEventsOverlappingLocalDayAndUsesExclusiveEnd() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate day = LocalDate.of(2026, 9, 28);
        long beforeMidnight = day.minusDays(1).atTime(23, 30).atZone(zone).toInstant().toEpochMilli();
        long afterMidnight = day.atTime(0, 30).atZone(zone).toInstant().toEpochMilli();
        PublicCalendarRepository.Event crossing = new PublicCalendarRepository.Event(
                "Late show", beforeMidnight, afterMidnight);
        assertTrue(CalendarDates.includes(crossing, day, zone));
        assertTrue(CalendarDates.includes(crossing, day.minusDays(1), zone));

        long tomorrow = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        PublicCalendarRepository.Event allDay = new PublicCalendarRepository.Event(
                "Festival", day.atStartOfDay(zone).toInstant().toEpochMilli(), tomorrow, true);
        assertTrue(CalendarDates.includes(allDay, day, zone));
        assertFalse(CalendarDates.includes(allDay, day.plusDays(1), zone));
    }

    @Test public void monthNavigationStopsAtTwelveMonthsEitherSide() {
        YearMonth current = YearMonth.of(2026, 9);
        assertTrue(CalendarDates.inRange(current.minusMonths(12), current));
        assertTrue(CalendarDates.inRange(current.plusMonths(12), current));
        assertFalse(CalendarDates.inRange(current.minusMonths(13), current));
        assertFalse(CalendarDates.inRange(current.plusMonths(13), current));
    }
}
