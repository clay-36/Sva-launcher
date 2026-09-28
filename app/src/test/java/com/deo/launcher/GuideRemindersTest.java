package com.deo.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GuideRemindersTest {
    @Test public void reminderOpensFiveMinutesBeforeStartAndExpiresAtEnd() {
        long start = 1_000_000L;
        GuideReminders.Reminder reminder = new GuideReminders.Reminder("id:news", "https://example.test/live",
                "News", "Evening bulletin", start, start + 1_800_000L);
        assertEquals(start - 300_000L, GuideReminders.alertAt(reminder));
        assertFalse(GuideReminders.isDue(reminder, start - 300_001L));
        assertTrue(GuideReminders.isDue(reminder, start - 300_000L));
        assertTrue(GuideReminders.isDue(reminder, start + 10_000L));
        assertFalse(GuideReminders.isDue(reminder, reminder.endMs));
    }

    @Test public void sameTitleOnAnotherChannelIsASeparateReminder() {
        GuideReminders.Reminder first = new GuideReminders.Reminder("id:news", "https://example.test/one",
                "News", "Bulletin", 1_000_000L, 2_000_000L);
        GuideReminders.Reminder same = new GuideReminders.Reminder("id:news", "https://example.test/one",
                "News HD", "Bulletin", 1_000_000L, 2_000_000L);
        GuideReminders.Reminder other = new GuideReminders.Reminder("id:news", "https://example.test/two",
                "News", "Bulletin", 1_000_000L, 2_000_000L);
        assertTrue(first.sameProgramme(same));
        assertFalse(first.sameProgramme(other));
    }
}
