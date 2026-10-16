package com.sva.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import android.view.Gravity;
import android.view.View;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScreensaverSettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout page;
    private EditText album, caption;
    private TextView source, feedback;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(LiquidTheme.scenicBackground(this));
        scroll.setFillViewport(true);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(48), dp(28), dp(48), dp(32));
        scroll.addView(page); setContentView(scroll);
        title("Screensaver", 30);
        title("Your photographs, with a little atmosphere. Press any remote key to leave the preview.", 14);
        source = title("", 14); updateSource();
        album = input("Separate Google Photos album (optional)", prefs.getString(ScreensaverPreferences.ALBUM, ""));
        title("Leave empty to follow the Home album automatically. Albums must be shared with anyone who has the link.", 12);
        caption = input("A caption or dedication (optional)", prefs.getString(ScreensaverPreferences.CAPTION, ""));
        feedback = title("", 13);
        LinearLayout actions = row();
        Button save = button(actions, "Save album & caption", v -> save());
        button(actions, "Use Home album", v -> {
            album.setText(""); prefs.edit().remove(ScreensaverPreferences.ALBUM).apply();
            updateSource(); feedback.setText("Screensaver now follows the Home album.");
        });
        button(actions, "Check album", v -> checkAlbum());
        title("Choose an atmosphere", 22);
        LinearLayout themes = null;
        Button[] themeButtons = new Button[ScreensaverPreferences.THEMES.length];
        for (int i = 0; i < themeButtons.length; i++) {
            if (i % 3 == 0) themes = row();
            final int selected = i;
            themeButtons[i] = button(themes, ScreensaverPreferences.NAMES[i], v -> {
                prefs.edit().putString(ScreensaverPreferences.THEME, ScreensaverPreferences.THEMES[selected]).apply();
                for (int n = 0; n < themeButtons.length; n++) themeButtons[n].setText((n == selected ? "✓ " : "") + ScreensaverPreferences.NAMES[n]);
                feedback.setText(ScreensaverPreferences.DESCRIPTIONS[selected]);
            });
        }
        int current = ScreensaverPreferences.themeIndex(prefs.getString(ScreensaverPreferences.THEME, "cinema"));
        themeButtons[current].setText("✓ " + ScreensaverPreferences.NAMES[current]);
        feedback.setText(ScreensaverPreferences.DESCRIPTIONS[current]);
        LinearLayout options = row();
        int seconds = ScreensaverPreferences.interval(prefs.getInt(ScreensaverPreferences.INTERVAL, 20));
        button(options, "Change photo · " + seconds + " seconds", v -> {
            int old = ScreensaverPreferences.interval(prefs.getInt(ScreensaverPreferences.INTERVAL, 20));
            int next = 20;
            for (int i = 0; i < ScreensaverPreferences.INTERVALS.length; i++)
                if (ScreensaverPreferences.INTERVALS[i] == old) next = ScreensaverPreferences.INTERVALS[(i + 1) % ScreensaverPreferences.INTERVALS.length];
            prefs.edit().putInt(ScreensaverPreferences.INTERVAL, next).apply();
            ((Button)v).setText("Change photo · " + next + " seconds");
        });
        button(options, "Clock · " + (prefs.getBoolean(ScreensaverPreferences.CLOCK, true) ? "On" : "Off"), v -> {
            boolean enabled = !prefs.getBoolean(ScreensaverPreferences.CLOCK, true);
            prefs.edit().putBoolean(ScreensaverPreferences.CLOCK, enabled).apply();
            ((Button)v).setText("Clock · " + (enabled ? "On" : "Off"));
        });
        LinearLayout idle = row();
        int idleMinutes = ScreensaverPreferences.idleMinutes(prefs.getInt(ScreensaverPreferences.IDLE, 0));
        button(idle, "Start while Sva is idle · " + (idleMinutes == 0 ? "Off" : idleMinutes + " min"), v -> {
            int old = ScreensaverPreferences.idleMinutes(prefs.getInt(ScreensaverPreferences.IDLE, 0));
            int next = 0;
            for (int i = 0; i < ScreensaverPreferences.IDLE_MINUTES.length; i++)
                if (old == ScreensaverPreferences.IDLE_MINUTES[i])
                    next = ScreensaverPreferences.IDLE_MINUTES[(i + 1) % ScreensaverPreferences.IDLE_MINUTES.length];
            prefs.edit().putInt(ScreensaverPreferences.IDLE, next).apply();
            ((Button)v).setText("Start while Sva is idle · " + (next == 0 ? "Off" : next + " min"));
        });
        title("The idle option works while Sva is open, including TVs that restrict custom system screensavers. Playback and settings stay uninterrupted.", 12);
        LinearLayout launch = row();
        button(launch, "Preview screensaver", v -> {
            if (save()) startActivity(new Intent(this, ScreensaverPreviewActivity.class));
        });
        button(launch, "Choose in Android TV settings", v -> openScreensaverSettings());
        button(launch, "Back", v -> finish());
        title("Select Sva Memories in your TV's screensaver settings and choose its idle timeout there. Offline, previously downloaded photos remain available; without an album, the preview uses Sva's landscape wallpaper.", 12);
        save.requestFocus();
    }

    private void openScreensaverSettings() {
        Intent[] candidates = new Intent[] {
            new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity")),
            new Intent(Settings.ACTION_DREAM_SETTINGS),
            new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.amazon.tv.settings", "com.amazon.tv.settings.SettingsActivity")),
            new Intent("com.google.android.tv.settings.ambient"),
            new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")),
            new Intent(Settings.ACTION_SETTINGS)
        };
        for (Intent intent : candidates) {
            try {
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    return;
                }
            } catch (Exception ignored) { }
        }
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception unavailable) {
            feedback.setText("This TV restricts system screensaver settings. Use Start while Sva is idle above, or open the TV's Screensaver / Ambient mode settings.");
        }
    }
    private boolean save() {
        String value = album.getText().toString().trim();
        if (!ScreensaverPreferences.validAlbum(value)) { feedback.setText("Enter a Google Photos shared album link, or leave empty to use Home."); return false; }
        prefs.edit().putString(ScreensaverPreferences.ALBUM, value)
                .putString(ScreensaverPreferences.CAPTION, caption.getText().toString().trim()).apply();
        updateSource(); feedback.setText("Saved."); return true;
    }
    private void checkAlbum() {
        if (!save()) return;
        String selected = ScreensaverPreferences.album(prefs);
        if (selected.isEmpty()) { feedback.setText("Add an album on Home or paste a separate album above."); return; }
        feedback.setText("Checking shared album…");
        worker.execute(() -> {
            String result;
            try { result = "Album ready · " + new ScreensaverPhotoRepository(this).refresh(selected, true).size() + " photos"; }
            catch (Exception e) { result = "Could not read album. Check the link, sharing settings and connection."; }
            String message = result;
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) feedback.setText(message); });
        });
    }
    private void updateSource() {
        boolean home = prefs.getString(ScreensaverPreferences.ALBUM, "").isEmpty();
        source.setText("Photo source · " + (home ? "Home album" : "Separate album")
                + (ScreensaverPreferences.album(prefs).isEmpty() ? " · no link configured yet" : ""));
    }
    private int dp(int n) { return LiquidTheme.dp(this, n); }
    private TextView title(String value, int size) {
        TextView t = GameUi.text(this, value, size, true);
        t.setPadding(0, dp(8), 0, dp(6)); page.addView(t); return t;
    }
    private EditText input(String hint, String value) {
        EditText edit = new EditText(this); edit.setText(value); edit.setHint(hint);
        edit.setTextColor(LiquidTheme.text(this)); edit.setHintTextColor(LiquidTheme.secondaryText(this));
        edit.setTextSize(14); edit.setSingleLine(true); edit.setSelectAllOnFocus(true);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        edit.setPadding(dp(12), dp(6), dp(12), dp(6));
        page.addView(edit, new LinearLayout.LayoutParams(-1, dp(46))); return edit;
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6)); page.addView(row); return row;
    }
    private Button button(LinearLayout parent, String value, View.OnClickListener listener) {
        Button b = GameUi.button(this, value); b.setTextSize(13); b.setPadding(dp(12), 0, dp(12), 0);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1); params.rightMargin = dp(8);
        parent.addView(b, params); return b;
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
