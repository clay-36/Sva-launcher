package com.sva.launcher;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/** Programme reminders delivered in-app while open and through notifications while closed. */
final class GuideReminders {
    static final String PREF_KEY = "programmeReminders";
    static final long LEAD_MS = 5L * 60L * 1000L;
    static final String ACTION_ALARM = "com.sva.launcher.PROGRAMME_REMINDER";
    static final String EXTRA_REMINDER_ID = "reminderId";
    static final String NOTIFICATION_CHANNEL = "programme-reminders";

    static final class Reminder {
        final String channelKey, streamUrl, channelName, title;
        final long startMs, endMs;

        Reminder(String channelKey, String streamUrl, String channelName, String title,
                 long startMs, long endMs) {
            this.channelKey = channelKey;
            this.streamUrl = streamUrl;
            this.channelName = channelName;
            this.title = title;
            this.startMs = startMs;
            this.endMs = endMs;
        }

        boolean sameProgramme(Reminder other) {
            return channelKey.equals(other.channelKey) && streamUrl.equals(other.streamUrl)
                    && startMs == other.startMs && title.equals(other.title);
        }
    }

    private final SharedPreferences preferences;
    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Activity foreground;
    private AlertDialog alert;
    private final Runnable tick = this::deliverDue;

    GuideReminders(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        rescheduleAlarms();
    }

    static long alertAt(Reminder reminder) { return reminder.startMs - LEAD_MS; }
    static boolean isDue(Reminder reminder, long now) {
        return now >= alertAt(reminder) && now < reminder.endMs;
    }

    boolean has(Reminder reminder) {
        for (Reminder saved : read()) if (saved.sameProgramme(reminder)) return true;
        return false;
    }

    boolean add(Reminder reminder) {
        if (reminder.startMs <= System.currentTimeMillis() || reminder.endMs <= reminder.startMs) return false;
        List<Reminder> saved = read();
        for (Reminder item : saved) if (item.sameProgramme(reminder)) return true;
        saved.add(reminder);
        write(saved);
        schedule();
        return true;
    }

    void remove(Reminder reminder) {
        List<Reminder> saved = read();
        if (saved.removeIf(item -> item.sameProgramme(reminder))) write(saved);
        schedule();
    }

    void reconcile(List<IptvRepository.Channel> channels, Set<String> hiddenKeys) {
        List<Reminder> saved = read();
        boolean changed = saved.removeIf(reminder -> {
            if (hiddenKeys != null && (hiddenKeys.contains(reminder.channelKey)
                    || hiddenKeys.contains("id:" + reminder.channelKey)
                    || hiddenKeys.contains("url:" + reminder.streamUrl)
                    || hiddenKeys.contains(reminder.streamUrl))) return true;
            IptvRepository.Channel channel = findChannel(channels, reminder);
            if (channel == null || HiddenChannels.isHidden(hiddenKeys, channel)) return true;
            if (channel.programmes.isEmpty()) return false;
            for (IptvRepository.Programme programme : channel.programmes) {
                if (programme.startMs == reminder.startMs && programme.title.equals(reminder.title)) return false;
            }
            return true;
        });
        if (changed) write(saved);
        rescheduleAlarms();
        schedule();
    }

    void onResumed(Activity activity) {
        foreground = activity;
        rescheduleAlarms();
        schedule();
    }

    void onPaused(Activity activity) {
        if (foreground != activity) return;
        foreground = null;
        handler.removeCallbacks(tick);
        if (alert != null) { alert.dismiss(); alert = null; }
    }

    private void schedule() {
        handler.removeCallbacks(tick);
        if (foreground == null || foreground.isFinishing() || foreground.isDestroyed() || alert != null) return;
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (Reminder reminder : read()) {
            if (reminder.endMs <= now) continue;
            next = Math.min(next, Math.max(now, alertAt(reminder)));
        }
        if (next != Long.MAX_VALUE) handler.postDelayed(tick, Math.max(0, next - now));
    }

    private void deliverDue() {
        Activity activity = foreground;
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || alert != null) return;
        long now = System.currentTimeMillis();
        List<Reminder> saved = read();
        Reminder due = null;
        boolean changed = false;
        for (int i = saved.size() - 1; i >= 0; i--) {
            Reminder reminder = saved.get(i);
            if (reminder.endMs <= now) { saved.remove(i); changed = true; }
            else if (isDue(reminder, now)) due = reminder;
        }
        if (due != null) { saved.remove(due); changed = true; }
        if (changed) write(saved);
        if (due == null) { schedule(); return; }

        Reminder shown = due;
        showReminderDialog(activity, shown);
    }

    private void showReminderDialog(Activity activity, Reminder shown) {
        String time = DateFormat.getTimeInstance(DateFormat.SHORT, AppLanguage.locale()).format(new Date(shown.startMs));
        alert = new AlertDialog.Builder(activity)
                .setTitle(AppLanguage.get(R.string.ui_starting_soon_1_s, shown.title))
                .setMessage(AppLanguage.get(R.string.ui_1_s_starts_at_2_s, shown.channelName, time))
                .setPositiveButton(AppLanguage.get(R.string.ui_watch), (dialog, which) -> {
                    Intent intent = new Intent(activity, MainActivity.class);
                    intent.setAction(MainActivity.ACTION_WATCH_REMINDER);
                    intent.putExtra(MainActivity.EXTRA_REMINDER_CHANNEL_KEY, shown.channelKey);
                    intent.putExtra(MainActivity.EXTRA_REMINDER_STREAM_URL, shown.streamUrl);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                })
                .setNegativeButton(AppLanguage.get(R.string.ui_dismiss), null)
                .create();
        alert.setOnDismissListener(dialog -> { alert = null; rescheduleAlarms(); schedule(); });
        Motion.dialog(alert); alert.show();
    }

    void deliverAlarm(String id) {
        if (id == null || id.isEmpty()) return;
        Reminder target = null;
        for (Reminder reminder : read()) {
            if (id.equals(reminderId(reminder))) { target = reminder; break; }
        }
        if (target == null) return;
        final Reminder selected = target;
        long now = System.currentTimeMillis();
        if (selected.endMs <= now) {
            List<Reminder> saved = read();
            saved.removeIf(item -> item.sameProgramme(selected));
            write(saved);
            return;
        }
        if (!isDue(selected, now)) { scheduleAlarm(selected); return; }

        Activity activity = foreground;
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            if (alert == null) deliverDue();
            return;
        }
        if (!notificationsEnabled()) return;
        List<Reminder> saved = read();
        saved.removeIf(item -> item.sameProgramme(selected));
        write(saved);
        postNotification(selected);
    }

    void rescheduleAlarms() {
        long now = System.currentTimeMillis();
        for (Reminder reminder : read()) {
            if (reminder.endMs <= now
                    || (foreground == null && !notificationsEnabled() && isDue(reminder, now))) {
                cancelAlarm(reminder);
            } else {
                scheduleAlarm(reminder);
            }
        }
    }

    boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager manager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    boolean notificationsEnabled() {
        if (Build.VERSION.SDK_INT >= 33
                && appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.areNotificationsEnabled();
    }

    private void scheduleAlarm(Reminder reminder) {
        AlarmManager manager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long triggerAt = Math.max(System.currentTimeMillis(), alertAt(reminder));
        PendingIntent operation = alarmPendingIntent(reminder);
        try {
            if (canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        } catch (SecurityException ignored) {
            try { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation); }
            catch (SecurityException ignoredAgain) { }
        }
    }

    private void cancelAlarm(Reminder reminder) {
        AlarmManager manager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(alarmPendingIntent(reminder));
    }

    private PendingIntent alarmPendingIntent(Reminder reminder) {
        String id = reminderId(reminder);
        Intent intent = new Intent(appContext, GuideReminderReceiver.class).setAction(ACTION_ALARM)
                .setData(Uri.parse("sva-reminder://alarm/" + id)).putExtra(EXTRA_REMINDER_ID, id);
        return PendingIntent.getBroadcast(appContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void postNotification(Reminder reminder) {
        NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !notificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL,
                    appContext.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(appContext, MainActivity.class);
        open.setAction(MainActivity.ACTION_WATCH_REMINDER);
        open.putExtra(MainActivity.EXTRA_REMINDER_CHANNEL_KEY, reminder.channelKey);
        open.putExtra(MainActivity.EXTRA_REMINDER_STREAM_URL, reminder.streamUrl);
        open.setData(Uri.parse("sva-reminder://watch/" + reminderId(reminder)));
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content = PendingIntent.getActivity(appContext, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String time = DateFormat.getTimeInstance(DateFormat.SHORT, AppLanguage.locale()).format(new Date(reminder.startMs));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(appContext, NOTIFICATION_CHANNEL) : new Notification.Builder(appContext);
        Notification notification = builder.setSmallIcon(R.drawable.ic_tv_input)
                .setContentTitle(AppLanguage.get(R.string.ui_starting_soon_1_s, reminder.title))
                .setContentText(AppLanguage.get(R.string.ui_1_s_starts_at_2_s, reminder.channelName, time))
                .setContentIntent(content).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).build();
        manager.notify(notificationId(reminder), notification);
    }

    private static int notificationId(Reminder reminder) { return reminderId(reminder).hashCode() & 0x7fffffff; }

    private static String reminderId(Reminder reminder) {
        String identity = reminder.channelKey + '\u0000' + reminder.streamUrl + '\u0000'
                + reminder.startMs + '\u0000' + reminder.title;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (Exception ignored) { return Integer.toHexString(identity.hashCode()); }
    }

    private static IptvRepository.Channel findChannel(List<IptvRepository.Channel> channels, Reminder reminder) {
        for (IptvRepository.Channel channel : channels) {
            if (channel.stableKey().equals(reminder.channelKey)
                    && channel.streamUrl.equals(reminder.streamUrl)) return channel;
        }
        return null;
    }

    private List<Reminder> read() {
        ArrayList<Reminder> reminders = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(preferences.getString(PREF_KEY, "[]"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                String key = value.optString("channelKey", "");
                String url = value.optString("streamUrl", "");
                String name = value.optString("channelName", "");
                String title = value.optString("title", "");
                long start = value.optLong("startMs", 0);
                long end = value.optLong("endMs", 0);
                if (!key.isEmpty() && !url.isEmpty() && !title.isEmpty() && end > start && start > 0)
                    reminders.add(new Reminder(key, url, name, title, start, end));
            }
        } catch (Exception ignored) { }
        return reminders;
    }

    private void write(List<Reminder> reminders) {
        List<Reminder> previous = read();
        JSONArray values = new JSONArray();
        for (Reminder reminder : reminders) {
            JSONObject value = new JSONObject();
            try {
                value.put("channelKey", reminder.channelKey);
                value.put("streamUrl", reminder.streamUrl);
                value.put("channelName", reminder.channelName);
                value.put("title", reminder.title);
                value.put("startMs", reminder.startMs);
                value.put("endMs", reminder.endMs);
                values.put(value);
            } catch (Exception ignored) { }
        }
        preferences.edit().putString(PREF_KEY, values.toString()).apply();
        for (Reminder reminder : previous) cancelAlarm(reminder);
        rescheduleAlarms();
    }
}
