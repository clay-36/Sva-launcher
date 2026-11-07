package com.sva.launcher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

/** Shared aerial sources; wallpaper and screensaver are independently opt-in. */
public final class AerialSettingsActivity extends MotionActivity {
    private View wallpaperToggle, screensaverToggle;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BackNavigation.install(this, this::finish);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        SharedPreferences prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        LinearLayout shell = SettingsUi.page(this, getString(R.string.aerial_title),
                getString(R.string.aerial_description), this::finish);
        LinearLayout page = SettingsUi.scroll(shell);
        wallpaperToggle = SettingsUi.toggle(this, getString(R.string.aerial_wallpaper),
                prefs.getBoolean(AerialCatalog.WALLPAPER, false),
                enabled -> prefs.edit().putBoolean(AerialCatalog.WALLPAPER, enabled).apply());
        SettingsUi.add(page, wallpaperToggle);
        screensaverToggle = SettingsUi.toggle(this, getString(R.string.aerial_screensaver),
                prefs.getBoolean(AerialCatalog.SCREENSAVER, false),
                enabled -> prefs.edit().putBoolean(AerialCatalog.SCREENSAVER, enabled).apply());
        SettingsUi.add(page, screensaverToggle);
        SettingsUi.add(page, SettingsUi.choice(this, getString(R.string.aerial_source),
                new String[]{getString(R.string.aerial_all), "Apple", "Jetson Creative", "Robin Fourcade", "Amazon"},
                AerialCatalog.providerIndex(prefs.getString(AerialCatalog.PROVIDER, "all")),
                index -> prefs.edit().putString(AerialCatalog.PROVIDER, AerialCatalog.PROVIDERS[index]).apply()));
        SettingsUi.hint(page, getString(R.string.aerial_streaming_hint));
        SettingsUi.add(page, SettingsUi.action(this, getString(R.string.aerial_preview), () ->
                startActivity(new Intent(this, ScreensaverPreviewActivity.class).putExtra("aerialPreview", true))));
        SettingsUi.add(page, SettingsUi.action(this, getString(R.string.ui_sva_memories_screensaver_settings), () ->
                startActivity(new Intent(this, ScreensaverSettingsActivity.class))));
        SettingsUi.heading(page, getString(R.string.aerial_credits));
        SettingsUi.hint(page, getString(R.string.aerial_credit_text));
        SettingsUi.add(page, SettingsUi.action(this, getString(R.string.aerial_license), () -> {
            try {
                java.io.InputStream input = getAssets().open("aerialviews-license.txt");
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                input.close();
                android.widget.TextView text = SettingsUi.text(this,
                        new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8), 13, false);
                int padding = LiquidTheme.dp(this, 20); text.setPadding(padding, padding, padding, padding);
                android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(text);
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this, LiquidTheme.alertDialogTheme(this))
                        .setTitle(R.string.aerial_license).setView(scroll).setPositiveButton(android.R.string.ok, null).create();
                Motion.dialog(dialog); dialog.show();
            } catch (java.io.IOException ignored) { }
        }));
        setContentView(shell);
    }
    @Override protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        if (wallpaperToggle != null) SettingsUi.refreshToggle(wallpaperToggle, prefs.getBoolean(AerialCatalog.WALLPAPER, false));
        if (screensaverToggle != null) SettingsUi.refreshToggle(screensaverToggle, prefs.getBoolean(AerialCatalog.SCREENSAVER, false));
    }

}
