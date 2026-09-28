package com.deo.launcher;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

final class CalendarDates {
    private CalendarDates() { }

    static boolean includes(PublicCalendarRepository.Event event, LocalDate date, ZoneId zone) {
        long start = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return event.startMs < end && event.endMs > start;
    }

    static boolean inRange(YearMonth month, YearMonth today) {
        return !month.isBefore(today.minusMonths(12)) && !month.isAfter(today.plusMonths(12));
    }
}
