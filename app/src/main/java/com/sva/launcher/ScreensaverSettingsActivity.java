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

public final class ScreensaverSettingsActivity extends MotionActivity {
    private SharedPreferences prefs;
    private LinearLayout page;
    private EditText album, caption;
    private TextView source, feedback;
    private TaskProgress albumProgress;
    private Button checkAlbumButton;
    private boolean checkingAlbum;
    private int albumGeneration;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LifecycleWork lifecycleWork = new LifecycleWork(this, new android.os.Handler(android.os.Looper.getMainLooper()));
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BackNavigation.install(this, this::requestClose);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout shell = SettingsUi.page(this, AppLanguage.get(R.string.ui_screensaver), AppLanguage.get(R.string.ui_sva_memories_personalize_your_idle_screen), this::requestClose);
        page = SettingsUi.scroll(shell);
        SettingsUi.heading(page, AppLanguage.get(R.string.ui_photo_source));
        source = SettingsUi.text(this, "", 14, true); page.addView(source); updateSource();
        album = input(AppLanguage.get(R.string.ui_separate_google_photos_album_optional), true, state == null
                ? prefs.getString(ScreensaverPreferences.ALBUM, "") : state.getString("album", ""));
        album.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (checkingAlbum) { albumGeneration++; checkingAlbum = false; updateAlbumProgress(); }
            }
            @Override public void afterTextChanged(android.text.Editable value) { }
        });
        SettingsUi.hint(page, AppLanguage.get(R.string.ui_leave_empty_to_follow_the_home_album_use_an_album_shared_with_an));
        caption = input(AppLanguage.get(R.string.ui_caption_optional), false, state == null
                ? prefs.getString(ScreensaverPreferences.CAPTION, "") : state.getString("caption", ""));
        feedback = SettingsUi.text(this, "", 14, true); SettingsUi.add(page, feedback);
        SettingsUi.add(page, SettingsUi.action(this, AppLanguage.get(R.string.ui_use_home_album), () -> {
            albumGeneration++; checkingAlbum = false; updateAlbumProgress();
            album.setText(""); prefs.edit().remove(ScreensaverPreferences.ALBUM).apply();
            updateSource(); feedback.setText(AppLanguage.get(R.string.ui_screensaver_now_follows_the_home_album));
        }));
        albumProgress = new TaskProgress(this, false);
        SettingsUi.add(page, albumProgress);
        checkAlbumButton = SettingsUi.action(this, AppLanguage.get(R.string.ui_check_album), this::checkAlbum);
        SettingsUi.add(page, checkAlbumButton);
        SettingsUi.heading(page, AppLanguage.get(R.string.ui_appearance));
        int atmosphere = ScreensaverPreferences.themeIndex(prefs.getString(ScreensaverPreferences.THEME, "cinema"));
        TextView atmosphereDescription = SettingsUi.text(this, ScreensaverPreferences.descriptions()[atmosphere], 14, true);
        SettingsUi.add(page, SettingsUi.choice(this, AppLanguage.get(R.string.ui_atmosphere), ScreensaverPreferences.names(),
                atmosphere, index -> {
                    prefs.edit().putString(ScreensaverPreferences.THEME, ScreensaverPreferences.THEMES[index]).apply();
                    atmosphereDescription.setText(ScreensaverPreferences.descriptions()[index]);
                }));
        SettingsUi.add(page, atmosphereDescription);
        int interval = ScreensaverPreferences.interval(prefs.getInt(ScreensaverPreferences.INTERVAL, 20));
        String[] intervals = new String[ScreensaverPreferences.INTERVALS.length];
        int selected = 0;
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = AppLanguage.quantity(R.plurals.duration_seconds, ScreensaverPreferences.INTERVALS[i]);
            if (ScreensaverPreferences.INTERVALS[i] == interval) selected = i;
        }
        SettingsUi.add(page, SettingsUi.choice(this, AppLanguage.get(R.string.ui_change_photo), intervals, selected,
                index -> prefs.edit().putInt(ScreensaverPreferences.INTERVAL, ScreensaverPreferences.INTERVALS[index]).apply()));
        SettingsUi.heading(page, AppLanguage.get(R.string.ui_overlays));
        overlayToggle(AppLanguage.get(R.string.ui_clock), ScreensaverPreferences.CLOCK, true);
        overlayToggle(AppLanguage.get(R.string.ui_weather), ScreensaverPreferences.WEATHER, false);
        overlayToggle(AppLanguage.get(R.string.ui_events), ScreensaverPreferences.EVENTS, false);
        SettingsUi.hint(page, AppLanguage.get(R.string.ui_weather_and_events_use_your_home_location_and_calendar));
        SettingsUi.heading(page, AppLanguage.get(R.string.ui_activation));
        String[] idleOptions = new String[ScreensaverPreferences.IDLE_MINUTES.length];
        int idle = ScreensaverPreferences.idleMinutes(prefs.getInt(ScreensaverPreferences.IDLE, 0));
        selected = 0;
        for (int i = 0; i < idleOptions.length; i++) {
            int minutes = ScreensaverPreferences.IDLE_MINUTES[i];
            idleOptions[i] = minutes == 0 ? AppLanguage.get(R.string.ui_off) : AppLanguage.quantity(R.plurals.duration_minutes, minutes);
            if (idle == minutes) selected = i;
        }
        SettingsUi.add(page, SettingsUi.choice(this, AppLanguage.get(R.string.ui_start_while_sva_is_idle), idleOptions, selected,
                index -> prefs.edit().putInt(ScreensaverPreferences.IDLE, ScreensaverPreferences.IDLE_MINUTES[index]).apply()));
        SettingsUi.hint(page, AppLanguage.get(R.string.ui_runs_while_sva_is_open_playback_and_settings_stay_uninterrupted));
        SettingsUi.add(page, SettingsUi.action(this, AppLanguage.get(R.string.ui_choose_in_android_tv_settings), this::openScreensaverSettings));
        SettingsUi.hint(page, AppLanguage.get(R.string.ui_choose_sva_memories_in_android_tv_settings_for_a_system_wide_scr));
        LinearLayout footer = new LinearLayout(this);
        footer.setClipChildren(false);
        footer.setClipToPadding(false);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.setMargins(0, dp(10), 0, dp(12));
        Button save = SettingsUi.action(this, AppLanguage.get(R.string.ui_save_changes), this::save);
        save.setTextColor(LiquidTheme.accent(this));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, -2, 1);
        saveParams.setMargins(dp(4), dp(4), dp(6), dp(4));
        footer.addView(save, saveParams);
        Button preview = SettingsUi.action(this, AppLanguage.get(R.string.ui_preview), () -> {
            if (save()) startActivity(new Intent(this, ScreensaverPreviewActivity.class));
        });
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(0, -2, 1);
        previewParams.setMargins(dp(6), dp(4), dp(4), dp(4));
        footer.addView(preview, previewParams);
        shell.addView(footer, footerParams);
        setContentView(shell); save.requestFocus();
        if (state != null) page.post(() -> ((ScrollView) page.getParent()).scrollTo(0, state.getInt("scroll", 0)));
    }

    private void overlayToggle(String label, String key, boolean fallback) {
        SettingsUi.add(page, SettingsUi.toggle(this, label, prefs.getBoolean(key, fallback),
                enabled -> prefs.edit().putBoolean(key, enabled).apply()));
    }

    private void requestClose() {
        boolean dirty = !album.getText().toString().trim().equals(prefs.getString(ScreensaverPreferences.ALBUM, ""))
                || !caption.getText().toString().trim().equals(prefs.getString(ScreensaverPreferences.CAPTION, ""));
        if (!dirty) { finish(); return; }
        android.app.AlertDialog prompt = new android.app.AlertDialog.Builder(this, LiquidTheme.alertDialogTheme(this))
                .setTitle(AppLanguage.get(R.string.ui_save_screensaver_changes))
                .setMessage(AppLanguage.get(R.string.ui_your_album_and_caption_have_unsaved_changes))
                .setPositiveButton(AppLanguage.get(R.string.ui_save), (d, w) -> { if (save()) finish(); })
                .setNegativeButton(AppLanguage.get(R.string.ui_discard), (d, w) -> finish()).setNeutralButton(AppLanguage.get(R.string.ui_keep_editing), null).create();
        Motion.dialog(prompt); prompt.show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("album", album.getText().toString());
        state.putString("caption", caption.getText().toString());
        state.putInt("scroll", ((ScrollView)page.getParent()).getScrollY());
        super.onSaveInstanceState(state);
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { requestClose(); }

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
            feedback.setText(AppLanguage.get(R.string.ui_this_tv_restricts_system_screensaver_settings_use_start_while_sv));
        }
    }
    private boolean save() {
        String value = album.getText().toString().trim();
        if (!ScreensaverPreferences.validAlbum(value)) { feedback.setText(AppLanguage.get(R.string.ui_enter_a_google_photos_shared_album_link_or_leave_empty_to_use_ho)); return false; }
        prefs.edit().putString(ScreensaverPreferences.ALBUM, value)
                .putString(ScreensaverPreferences.CAPTION, caption.getText().toString().trim()).apply();
        updateSource(); feedback.setText(AppLanguage.get(R.string.ui_saved)); return true;
    }
    private void checkAlbum() {
        if (checkingAlbum || !save()) return;
        String selected = ScreensaverPreferences.album(prefs);
        if (selected.isEmpty()) { feedback.setText(AppLanguage.get(R.string.ui_add_an_album_on_home_or_paste_a_separate_album_above)); return; }
        int generation = ++albumGeneration;
        checkingAlbum = true; updateAlbumProgress();
        feedback.setText(AppLanguage.get(R.string.ui_checking_shared_album));
        lifecycleWork.execute(worker, () -> {
            String result;
            try { result = AppLanguage.get(R.string.ui_album_ready_1_s_photos, new ScreensaverPhotoRepository(this).refresh(selected, true).size()); }
            catch (Exception e) { result = AppLanguage.get(R.string.ui_could_not_read_album_check_the_link_sharing_settings_and_connect); }
            String message = result;
            lifecycleWork.post(() -> {
                if (generation != albumGeneration) return;
                checkingAlbum = false; updateAlbumProgress();
                if (selected.equals(ScreensaverPreferences.album(prefs))) feedback.setText(message);
            });
        });
    }
    private void updateAlbumProgress() {
        if (albumProgress != null) albumProgress.setBusy(checkingAlbum, AppLanguage.get(R.string.ui_checking_shared_album));
        if (checkAlbumButton != null) checkAlbumButton.setEnabled(!checkingAlbum);
    }
    private void updateSource() {
        boolean home = prefs.getString(ScreensaverPreferences.ALBUM, "").isEmpty();
        source.setText(AppLanguage.get(R.string.ui_photo_source_1_s_2_s, (home ? AppLanguage.get(R.string.ui_home_album) : AppLanguage.get(R.string.ui_separate_album)), (ScreensaverPreferences.album(prefs).isEmpty() ? AppLanguage.get(R.string.ui_no_link_configured_yet) : "")));
    }
    private int dp(int n) { return LiquidTheme.dp(this, n); }
    private EditText input(String label, boolean uri, String value) {
        SettingsUi.hint(page, label);
        EditText edit = new EditText(this); edit.setText(value); edit.setSingleLine(true);
        edit.setSelectAllOnFocus(true);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (uri
                ? android.text.InputType.TYPE_TEXT_VARIATION_URI : android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        edit.setHint(uri ? "https://photos.app.goo.gl/…" : AppLanguage.get(R.string.ui_add_a_personal_caption));
        SettingsUi.styleInput(edit, false); SettingsUi.add(page, edit); return edit;
    }
    @Override protected void onDestroy() { lifecycleWork.close(); worker.shutdownNow(); super.onDestroy(); }
}
