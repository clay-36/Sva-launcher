package com.sva.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/** Foreground-only programme alerts. The preference is included in launcher backups. */
final class GuideReminders {
    static final String PREF_KEY = "programmeReminders";
    static final long LEAD_MS = 5L * 60L * 1000L;

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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Activity foreground;
    private AlertDialog alert;
    private final Runnable tick = this::deliverDue;

    GuideReminders(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
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
        schedule();
    }

    void onResumed(Activity activity) {
        foreground = activity;
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
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(shown.startMs));
        alert = new AlertDialog.Builder(activity)
                .setTitle("Starting soon · " + shown.title)
                .setMessage(shown.channelName + " starts at " + time + ".")
                .setPositiveButton("Watch", (dialog, which) -> {
                    Intent intent = new Intent(activity, MainActivity.class);
                    intent.setAction(MainActivity.ACTION_WATCH_REMINDER);
                    intent.putExtra(MainActivity.EXTRA_REMINDER_CHANNEL_KEY, shown.channelKey);
                    intent.putExtra(MainActivity.EXTRA_REMINDER_STREAM_URL, shown.streamUrl);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                })
                .setNegativeButton("Dismiss", null)
                .create();
        alert.setOnDismissListener(dialog -> { alert = null; schedule(); });
        Motion.dialog(alert); alert.show();
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
    }
}
