package com.sva.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Synthetic settings and local feeds preserve the installed app's preferences. */
public final class TaskLoaderUiTest extends InstrumentationTestCase {
    private MainActivity activity;
    private SharedPreferences prefs, iptv;
    private Map<String, ?> previous, previousIptv;
    private String previousLanguage;

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences("launcher", 0);
        iptv = context.getSharedPreferences("iptv", 0);
        previous = prefs.getAll(); previousIptv = iptv.getAll();
        previousLanguage = AppLanguage.selected(context);
        prefs.edit().clear().putLong("schedulePlaylistMs", 0).putLong("scheduleGuideMs", 0)
                .putLong("scheduleCalendarMs", 0).putLong("scheduleWeatherMs", 0)
                .putLong("scheduleGooglePhotosMs", 0).putBoolean("youtubeArtworkFeedFallbackTried", true).commit();
        iptv.edit().clear().commit();
    }

    @Override protected void tearDown() throws Exception {
        finish();
        getInstrumentation().runOnMainSync(() -> AppLanguage.select(getInstrumentation().getTargetContext(), previousLanguage));
        restore(prefs, previous); restore(iptv, previousIptv);
        super.tearDown();
    }

    private void restore(SharedPreferences target, Map<String, ?> values) {
        SharedPreferences.Editor edit = target.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof String) edit.putString(key, (String)value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean)value);
            else if (value instanceof Long) edit.putLong(key, (Long)value);
            else if (value instanceof Integer) edit.putInt(key, (Integer)value);
            else if (value instanceof Float) edit.putFloat(key, (Float)value);
            else if (value instanceof java.util.Set) edit.putStringSet(key, (java.util.Set<String>)value);
        }
        edit.commit();
    }

    private void open() {
        activity = (MainActivity) getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        getInstrumentation().waitForIdleSync();
    }

    private void finish() {
        if (activity == null) return;
        getInstrumentation().runOnMainSync(activity::finish);
        getInstrumentation().waitForIdleSync(); activity = null;
    }

    private Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }

    private void set(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }

    private void call(Object object, String name, Class<?>[] types, Object... args) {
        getInstrumentation().runOnMainSync(() -> {
            try {
                Method method = object.getClass().getDeclaredMethod(name, types);
                method.setAccessible(true); method.invoke(object, args);
            } catch (Exception e) { throw new AssertionError(e); }
        });
        getInstrumentation().waitForIdleSync();
    }

    private void flag(String name, boolean value) {
        getInstrumentation().runOnMainSync(() -> {
            try { set(activity, name, value); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        call(activity, "updateTaskProgress", new Class<?>[0]);
    }

    private Object settings(int section) throws Exception {
        call(activity, "showSettings", new Class<?>[0]);
        Object settings = field(activity, "settingsScreen");
        getInstrumentation().runOnMainSync(() -> {
            try { set(settings, "section", section); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        call(settings, "renderSection", new Class<?>[0]);
        return settings;
    }

    private boolean indicator(Object owner, String name) throws Exception {
        TaskProgress progress = (TaskProgress)field(owner, name);
        assertNotNull(progress);
        boolean[] visible = {false};
        getInstrumentation().runOnMainSync(() -> visible[0] = progress.getChildAt(0).getVisibility() == View.VISIBLE);
        return visible[0];
    }

    private void delay() { SystemClock.sleep(450); getInstrumentation().waitForIdleSync(); }

    public void testLazySectionsReflectConcurrentRefreshAndKeepOtherTaskVisible() throws Exception {
        open();
        flag("channelsRefreshing", true); flag("guideRefreshing", true);
        flag("weatherRefreshing", true); flag("calendarRefreshing", true);
        call(activity, "selectTab", new Class<?>[]{int.class, boolean.class}, 1, false);
        delay(); assertTrue(indicator(activity, "liveProgress"));
        flag("channelsRefreshing", false); assertTrue(indicator(activity, "liveProgress"));
        Object settings = settings(0); delay(); assertTrue(indicator(settings, "taskProgress"));
        flag("guideRefreshing", false); assertFalse(indicator(settings, "taskProgress"));
        settings = settings(12); delay(); assertTrue(indicator(settings, "taskProgress"));
        flag("weatherRefreshing", false); assertFalse(indicator(settings, "taskProgress"));
        settings = settings(9); delay(); assertTrue(indicator(settings, "taskProgress"));
        flag("calendarRefreshing", false); assertFalse(indicator(settings, "taskProgress"));
    }

    public void testRestoreDisablesEditsAcrossSectionsAndFailureReenablesActions() throws Exception {
        open(); Object settings = settings(7);
        flag("restoringBackup", true); delay();
        assertTrue(indicator(settings, "taskProgress"));
        assertFalse(((Button)field(settings, "backupExportButton")).isEnabled());
        assertFalse(((Button)field(settings, "backupImportButton")).isEnabled());
        settings = settings(4); delay(); assertTrue(indicator(settings, "taskProgress"));
        assertNoEnabledAction((View)field(settings, "detail"));
        flag("restoringBackup", false);
        settings = settings(7);
        assertTrue(((Button)field(settings, "backupImportButton")).isEnabled());
        File missing = new File(getInstrumentation().getTargetContext().getCacheDir(), "missing-loader-backup.json");
        assertFalse(missing.exists());
        call(activity, "importBackup", new Class<?>[]{Uri.class}, Uri.fromFile(missing));
        awaitIdle("restoringBackup");
        assertFalse(indicator(settings, "taskProgress"));
        assertTrue(((Button)field(settings, "backupImportButton")).isEnabled());
    }

    private void assertNoEnabledAction(View view) {
        if (view.isClickable() || view instanceof android.widget.EditText) assertFalse(view.isEnabled());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) assertNoEnabledAction(group.getChildAt(i));
        }
    }

    public void testStaleCalendarResponseCannotClearNewRefresh() throws Exception {
        open();
        try (CalendarHttpFixture first = new CalendarHttpFixture(); CalendarHttpFixture second = new CalendarHttpFixture()) {
            first.hold = true; second.hold = true;
            prefs.edit().putString("publicCalendarUrl", first.url("a")).commit();
            call(activity, "refreshPublicCalendar", new Class<?>[]{String.class, boolean.class}, first.url("a"), true);
            assertTrue(first.entered.await(5, TimeUnit.SECONDS));
            call(activity, "cancelCalendarRefresh", new Class<?>[0]);
            prefs.edit().putString("publicCalendarUrl", second.url("a")).commit();
            call(activity, "refreshPublicCalendar", new Class<?>[]{String.class, boolean.class}, second.url("a"), true);
            first.release.countDown();
            assertTrue(second.entered.await(5, TimeUnit.SECONDS));
            delay(); assertTrue(indicator(activity, "homeCalendarProgress"));
            second.release.countDown(); awaitIdle("calendarRefreshing");
            assertFalse(indicator(activity, "homeCalendarProgress"));
        }
    }

    private void awaitIdle(String name) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] busy = {true};
        while (busy[0] && SystemClock.uptimeMillis() < deadline) {
            getInstrumentation().runOnMainSync(() -> {
                try { busy[0] = (Boolean)field(activity, name); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            SystemClock.sleep(20);
        }
        assertFalse(name + " did not finish", busy[0]);
    }

    public void testRemovingWeatherClearsBothWidgetAndSettingsIndicators() throws Exception {
        prefs.edit().putString("weatherLocationLabel", "Test city")
                .putLong("weatherLatitude", Double.doubleToRawLongBits(18.5))
                .putLong("weatherLongitude", Double.doubleToRawLongBits(73.8)).commit();
        open(); flag("weatherRefreshing", true);
        Object settings = settings(12); delay();
        assertTrue(indicator(settings, "taskProgress"));
        View remove = findText((View)field(settings, "detail"), AppLanguage.get(R.string.ui_remove_weather));
        assertNotNull(remove);
        getInstrumentation().runOnMainSync(remove::performClick);
        assertFalse(indicator(activity, "weatherProgress"));
        assertFalse(indicator(settings, "taskProgress"));
    }

    private View findText(View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView)view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text); if (found != null) return found;
            }
        }
        return null;
    }

    public void testAlbumEditInvalidatesPendingCheckWithoutNetwork() throws Exception {
        ScreensaverSettingsActivity screen = (ScreensaverSettingsActivity)getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(), ScreensaverSettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService worker = (java.util.concurrent.ExecutorService)field(screen, "worker");
        worker.submit(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            android.widget.EditText album = (android.widget.EditText)field(screen, "album");
            getInstrumentation().runOnMainSync(() -> album.setText("https://photos.app.goo.gl/LoaderFixture"));
            call(screen, "checkAlbum", new Class<?>[0]); delay();
            assertTrue(indicator(screen, "albumProgress"));
            assertFalse(((Button)field(screen, "checkAlbumButton")).isEnabled());
            getInstrumentation().runOnMainSync(() -> album.setText(""));
            assertFalse(indicator(screen, "albumProgress"));
            assertTrue(((Button)field(screen, "checkAlbumButton")).isEnabled());
        } finally {
            getInstrumentation().runOnMainSync(screen::finish);
            getInstrumentation().waitForIdleSync(); release.countDown();
        }
    }

    public void testBackupExportCompletesAndBusyOperationRejectsOverlaps() throws Exception {
        open(); Object settings = settings(7);
        File exported = new File(getInstrumentation().getTargetContext().getCacheDir(), "loader-export-" + System.nanoTime() + ".json");
        File overlap = new File(getInstrumentation().getTargetContext().getCacheDir(), "loader-overlap-" + System.nanoTime() + ".json");
        try {
            call(activity, "exportBackup", new Class<?>[]{Uri.class}, Uri.fromFile(exported));
            awaitIdle("exportingBackup");
            assertTrue(exported.length() > 0);
            assertFalse(indicator(settings, "backupExportProgress"));
            flag("exportingBackup", true); delay();
            assertTrue(indicator(settings, "backupExportProgress"));
            call(activity, "exportBackup", new Class<?>[]{Uri.class}, Uri.fromFile(overlap));
            call(activity, "importBackup", new Class<?>[]{Uri.class}, Uri.fromFile(exported));
            assertFalse(overlap.exists()); assertEquals(false, field(activity, "restoringBackup"));
            assertFalse(((Button)field(settings, "backupExportButton")).isEnabled());
            flag("exportingBackup", false);
        } finally { exported.delete(); overlap.delete(); }
    }

    public void testThemeAndLanguageLayoutsWithSyntheticLoadingState() throws Exception {
        WeatherRepository.Forecast forecast = new WeatherRepository.Forecast("Test city", System.currentTimeMillis(),
                24, 25, 10, 0, true, java.util.Collections.emptyList());
        prefs.edit().putString("weatherCache", forecast.toJson().toString())
                .putString("weatherLocationLabel", "Test city")
                .putLong("weatherLatitude", Double.doubleToRawLongBits(18.5))
                .putLong("weatherLongitude", Double.doubleToRawLongBits(73.8)).commit();
        for (String language : new String[]{"en", "hi", "ar"}) {
            getInstrumentation().runOnMainSync(() -> AppLanguage.select(getInstrumentation().getTargetContext(), language));
            for (String theme : new String[]{LiquidTheme.LIGHT, LiquidTheme.DARK}) {
                prefs.edit().putString(LiquidTheme.PREF_MODE, theme).commit(); open();
                flag("weatherRefreshing", true); flag("calendarRefreshing", true);
                delay();
                assertTrue(indicator(activity, "weatherProgress"));
                assertTrue(indicator(activity, "homeCalendarProgress"));
                capture(language + "-" + theme + "-home");
                flag("channelsRefreshing", true);
                Object settings = settings(0); delay();
                assertTrue(indicator(settings, "taskProgress"));
                capture(language + "-" + theme + "-settings");
                finish();
            }
        }
    }

    private void capture(String name) throws Exception {
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        File directory = new File(getInstrumentation().getTargetContext().getCacheDir(), "loader-qa"); directory.mkdirs();
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }
}
