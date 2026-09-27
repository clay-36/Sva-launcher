package com.deo.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SchedulePreferencesTest {
    @Test public void invalidValuesFallBackToExistingDefaults() {
        assertEquals(SchedulePreferences.DEFAULT_SLIDESHOW, SchedulePreferences.sanitize(
                123L, SchedulePreferences.SLIDESHOW_VALUES, SchedulePreferences.DEFAULT_SLIDESHOW));
        assertEquals(SchedulePreferences.DEFAULT_GUIDE, SchedulePreferences.sanitize(
                -99L, SchedulePreferences.GUIDE_VALUES, SchedulePreferences.DEFAULT_GUIDE));
    }

    @Test public void presetCyclingWrapsAndRepairsInvalidValues() {
        assertEquals(5_000L, SchedulePreferences.next(0L,
                SchedulePreferences.SLIDESHOW_VALUES, SchedulePreferences.DEFAULT_SLIDESHOW));
        assertEquals(0L, SchedulePreferences.next(60_000L,
                SchedulePreferences.SLIDESHOW_VALUES, SchedulePreferences.DEFAULT_SLIDESHOW));
        assertEquals(10_000L, SchedulePreferences.next(123L,
                SchedulePreferences.SLIDESHOW_VALUES, SchedulePreferences.DEFAULT_SLIDESHOW));
    }

    @Test public void labelsSpecialModesAndDurations() {
        assertEquals("Paused", SchedulePreferences.label(0L, true, false));
        assertEquals("Manual only", SchedulePreferences.label(0L, false, false));
        assertEquals("Every launch", SchedulePreferences.label(-1L, false, true));
        assertEquals("6 hours", SchedulePreferences.label(6L * SchedulePreferences.HOUR, false, false));
        assertEquals("1 day", SchedulePreferences.label(SchedulePreferences.DAY, false, false));
        assertEquals("Every launch", SchedulePreferences.label(SchedulePreferences.DEFAULT_WEATHER, false, true));
    }
}
