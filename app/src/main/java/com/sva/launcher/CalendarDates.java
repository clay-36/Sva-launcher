package com.sva.launcher;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class CalendarDates {
    private static final long KNOWN_NEW_MOON_MS = 947_182_440_000L;
    private static final double SYNODIC_MONTH_MS = 29.53058867 * 24d * 60d * 60d * 1000d;
    private static final String[] MOON_SYMBOLS = {"🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘"};
    private static final String[] MOON_NAMES = {
            "New moon", "", "", "",
            "Full moon", "", "", ""
    };

    private CalendarDates() { }

    static boolean includes(PublicCalendarRepository.Event event, LocalDate date, ZoneId zone) {
        long start = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return event.startMs < end && event.endMs > start;
    }

    static boolean inRange(YearMonth month, YearMonth today) {
        return !month.isBefore(today.minusMonths(12)) && !month.isAfter(today.plusMonths(12));
    }

    static MoonPhase moonPhase(LocalDate date) {
        long noonUtc = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
        double cycle = (noonUtc - KNOWN_NEW_MOON_MS) / SYNODIC_MONTH_MS;
        double fraction = cycle - Math.floor(cycle);
        int phase = (int) Math.floor(fraction * 8d + .5d) % 8;
        int illumination = (int) Math.round((1d - Math.cos(fraction * Math.PI * 2d)) * 50d);
        return new MoonPhase(MOON_SYMBOLS[phase], MOON_NAMES[phase], illumination);
    }

    static final class MoonPhase {
        final String symbol;
        final String name;
        final int illumination;

        MoonPhase(String symbol, String name, int illumination) {
            this.symbol = symbol;
            this.name = name;
            this.illumination = illumination;
        }
    }
}
