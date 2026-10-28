package com.sva.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Delivers saved TV guide reminders and restores their alarms after system events. */
public final class GuideReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        GuideReminders reminders = ((SvaApplication) context.getApplicationContext()).guideReminders();
        if (GuideReminders.ACTION_ALARM.equals(intent.getAction())) {
            reminders.deliverAlarm(intent.getStringExtra(GuideReminders.EXTRA_REMINDER_ID));
        } else {
            reminders.rescheduleAlarms();
        }
    }
}
