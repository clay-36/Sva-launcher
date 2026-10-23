package com.sva.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Structural performance checks use bounded views and unchanged snapshots, not timing thresholds. */
public final class PerformanceUiTest extends InstrumentationTestCase {
    private Activity activity;
    private SharedPreferences prefs;
    private Map<String, ?> previous;
    @Override protected void setUp() throws Exception {
        super.setUp();
        prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        previous = prefs.getAll();
        prefs.edit().putLong("schedulePlaylistMs", 0).putLong("scheduleGuideMs", 0)
                .putLong("scheduleCalendarMs", 0).putLong("scheduleWeatherMs", 0).commit();
    }
    @Override protected void tearDown() throws Exception {
        if (activity != null) getInstrumentation().runOnMainSync(activity::finish);
        getInstrumentation().waitForIdleSync();
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : previous.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) edit.putString(key, (String)value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean)value);
            else if (value instanceof Long) edit.putLong(key, (Long)value);
            else if (value instanceof Integer) edit.putInt(key, (Integer)value);
            else if (value instanceof Float) edit.putFloat(key, (Float)value);
            else if (value instanceof java.util.Set) edit.putStringSet(key, (java.util.Set<String>)value);
        }
        edit.commit(); super.tearDown();
    }
    private Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private void invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            try { Method m = object.getClass().getDeclaredMethod(name, types); m.setAccessible(true); m.invoke(object, args); }
            catch (Exception e) { throw new AssertionError(e); }
        });
        getInstrumentation().waitForIdleSync();
    }
    private void open(Class<? extends Activity> type, String mode) {
        Intent intent = new Intent(getInstrumentation().getTargetContext(), type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (mode != null) intent.putExtra(TvFilePickerActivity.EXTRA_MODE, mode);
        activity = getInstrumentation().startActivitySync(intent);
        getInstrumentation().setInTouchMode(false); getInstrumentation().waitForIdleSync();
    }
    private void select(int tab) throws Exception {
        invoke(activity, "selectTab", new Class<?>[]{int.class, boolean.class}, tab, false);
    }
    public void testLazyTabsAndCalendarSnapshots() throws Exception {
        open(MainActivity.class, null);
        assertNull(field(activity, "channelRows")); assertNull(field(activity, "gameGrid"));
        assertNull(field(activity, "calendarMonthGrid")); assertNull(field(activity, "youtubeRow"));
        Method cached = MainActivity.class.getDeclaredMethod("cachedCalendarEvents"); cached.setAccessible(true);
        getInstrumentation().runOnMainSync(() -> {
            try {
                prefs.edit().putString("publicCalendarEvents", "[]").commit();
                Object first = cached.invoke(activity);
                assertSame(first, cached.invoke(activity));
                long now = System.currentTimeMillis();
                prefs.edit().putString("publicCalendarEvents", new JSONArray().put(new JSONObject()
                        .put("title", "Changed").put("start", now).put("end", now + 3600000)).toString()).commit();
                Object changed = cached.invoke(activity);
                assertNotSame(first, changed); assertEquals(1, ((List<?>)changed).size());
            } catch (Exception e) { throw new AssertionError(e); }
        });
        for (int tab : new int[]{1, 2, 3, 4, 0, 4, 3, 2, 1, 0}) select(tab);
        assertNotNull(field(activity, "calendarMonthGrid"));
        assertTrue(field(activity, "youtubeRow") instanceof RecyclerView);
    }
    public void testHomeTickStopsWhilePausedAndResumesOnce() throws Exception {
        open(MainActivity.class, null);
        android.os.Handler handler = (android.os.Handler)field(activity, "handler");
        Runnable tick = (Runnable)field(activity, "clockTick");
        getInstrumentation().runOnMainSync(() -> assertTrue(handler.hasCallbacks(tick)));
        getInstrumentation().runOnMainSync(() -> getInstrumentation().callActivityOnPause(activity));
        getInstrumentation().runOnMainSync(() -> assertFalse(handler.hasCallbacks(tick)));
        getInstrumentation().runOnMainSync(() -> getInstrumentation().callActivityOnResume(activity));
        getInstrumentation().runOnMainSync(() -> assertTrue(handler.hasCallbacks(tick)));
    }
    public void testSavedCollectionUsesBoundedViewsAndKeepsUnchangedCards() throws Exception {
        JSONArray cards = new JSONArray();
        for (int i = 0; i < 250; i++) cards.put(new JSONObject()
                .put("url", "https://www.youtube.com/watch?v=a" + String.format(java.util.Locale.US, "%010d", i))
                .put("title", "Saved " + i).put("customThumbnail", true).put("thumbnailUrl", "")
                .put("metadataCheckedAt", System.currentTimeMillis()));
        prefs.edit().putString("youtubeCards", cards.toString()).putBoolean("youtubeArtworkFeedFallbackTried", true).commit();
        open(MainActivity.class, null); select(4);
        RecyclerView list = (RecyclerView)field(activity, "youtubeRow");
        awaitCount(list, 250);
        assertTrue(list.getChildCount() < 12);
        capture("saved");
        assertEquals("Saved must stay active after its first layout", 4, field(activity, "activeTab"));
        View first = list.getChildAt(0);
        invoke(activity, "renderYouTubeCards", new Class<?>[0]);
        assertSame(first, list.getChildAt(0));
        remoteAcross(list, 10, android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue("Remote must reach recycled Saved cards", focusedPosition(list) >= 5);
        getInstrumentation().runOnMainSync(() -> list.scrollToPosition(249));
        getInstrumentation().waitForIdleSync();
        assertTrue(list.getChildCount() < 12);
        assertNotNull(list.findViewHolderForAdapterPosition(249));
        android.app.Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        Activity original = activity;
        try {
            getInstrumentation().runOnMainSync(original::recreate);
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (SystemClock.uptimeMillis() < deadline) {
                Activity candidate = monitor.getLastActivity();
                if (candidate != null && candidate != original && !candidate.isDestroyed()) { activity = candidate; break; }
                SystemClock.sleep(25);
            }
            assertNotSame(original, activity);
            RecyclerView restored = (RecyclerView)field(activity, "youtubeRow"); awaitCount(restored, 250);
            assertTrue("Saved position must survive recreation",
                    ((androidx.recyclerview.widget.LinearLayoutManager)restored.getLayoutManager()).findFirstVisibleItemPosition() > 200);
        } finally { getInstrumentation().removeMonitor(monitor); }
    }
    public void testLargeGameLibraryUsesBoundedViews() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        File root = new File(context.getFilesDir(), "games"), roms = new File(root, "roms"); roms.mkdirs();
        File catalog = new File(root, "catalog-v1.json");
        byte[] original = catalog.isFile() ? Files.readAllBytes(catalog.toPath()) : null;
        List<File> fixtures = new ArrayList<>();
        try {
            JSONArray games = new JSONArray();
            for (int i = 0; i < 100; i++) {
                String id = String.format(java.util.Locale.US, "%064x", 700000 + i);
                File rom = new File(roms, id + ".nes");
                assertFalse(rom.exists()); Files.write(rom.toPath(), new byte[]{'N','E','S',26}); fixtures.add(rom);
                games.put(new GameRecord(id, "Game " + i, rom.getName(), null, 0, 0).toJson());
            }
            Files.write(catalog.toPath(), new JSONObject().put("games", games).toString().getBytes(StandardCharsets.UTF_8));
            open(GameLibraryActivity.class, null);
            RecyclerView grid = (RecyclerView)field(activity, "grid"); awaitCount(grid, 100);
            assertTrue(grid.getChildCount() < 30);
            android.widget.FrameLayout host = (android.widget.FrameLayout)grid.getChildAt(0);
            android.widget.LinearLayout card = (android.widget.LinearLayout)host.getChildAt(0);
            assertNotNull("Game cards must bind their initial fallback artwork",
                    ((android.widget.ImageView)card.getChildAt(0)).getDrawable());
            capture("games");
            remoteAcross(grid, 5, android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            assertTrue("Remote must reach offscreen game rows", focusedPosition(grid) >= 20);
            getInstrumentation().runOnMainSync(() -> grid.scrollToPosition(99)); getInstrumentation().waitForIdleSync();
            assertNotNull(grid.findViewHolderForAdapterPosition(99)); assertTrue(grid.getChildCount() < 30);
            open(MainActivity.class, null); select(2);
            RecyclerView tabGrid = (RecyclerView)field(activity, "gameGrid"); awaitCount(tabGrid, 100);
            assertTrue(tabGrid.getChildCount() < 30);
            capture("games-tab");
            remoteAcross(tabGrid, 5, android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            assertTrue("Games tab must navigate through recycled rows; position=" + focusedPosition(tabGrid)
                    + " activeTab=" + field(activity, "activeTab"), focusedPosition(tabGrid) >= 20);
        } finally {
            if (activity != null) { getInstrumentation().runOnMainSync(activity::finish); activity = null; }
            if (original == null) catalog.delete(); else Files.write(catalog.toPath(), original);
            for (File file : fixtures) file.delete();
        }
    }
    public void testDirectoryNavigationRejectsOldResultsAndRecyclesRows() throws Exception {
        File base = new File(getInstrumentation().getTargetContext().getCacheDir(), "performance-picker");
        File large = new File(base, "large"), latest = new File(base, "latest"); large.mkdirs(); latest.mkdirs();
        try {
            for (int i = 0; i < 1000; i++) new File(large, "fixture-" + i + ".nes").createNewFile();
            new File(latest, "latest.nes").createNewFile();
            open(TvFilePickerActivity.class, TvFilePickerActivity.MODE_NES_FILES);
            invoke(activity, "navigate", new Class<?>[]{File.class}, large);
            RecyclerView rows = (RecyclerView)field(activity, "rows"); awaitCount(rows, 1000);
            assertTrue(rows.getChildCount() < 25);
            capture("files");
            remoteAcross(rows, 20, android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            assertTrue("Remote must scroll through recycled files", focusedPosition(rows) >= 15);
            invoke(activity, "navigate", new Class<?>[]{File.class}, large);
            invoke(activity, "navigate", new Class<?>[]{File.class}, latest);
            awaitCount(rows, 1);
            assertEquals(latest, field(activity, "directory"));
            assertEquals("latest.nes", ((TextView)rows.getChildAt(0)).getText().toString());
        } finally {
            for (File dir : new File[]{large, latest}) { File[] files = dir.listFiles(); if (files != null) for (File file : files) file.delete(); dir.delete(); }
            base.delete();
        }
    }
    public void testChannelSearchKeepsOnlyLatestQuery() throws Exception {
        open(MainActivity.class, null);
        List<IptvRepository.Channel> fixtures = new ArrayList<>();
        for (int i = 0; i < 5000; i++) fixtures.add(new IptvRepository.Channel(
                (i == 4321 ? "Needle " : "Station ") + i, "https://example.invalid/" + i,
                "fixture-" + i, "Test", "", "", "", java.util.Collections.emptyMap()));
        getInstrumentation().runOnMainSync(() -> {
            try { List<IptvRepository.Channel> channels = (List<IptvRepository.Channel>)field(activity, "channels");
                channels.clear(); channels.addAll(fixtures);
            } catch (Exception e) { throw new AssertionError(e); }
        });
        invoke(activity, "showSettings", new Class<?>[0]);
        Object settings = field(activity, "settingsScreen");
        android.widget.Button[] tabs = (android.widget.Button[])field(settings, "tabs");
        getInstrumentation().runOnMainSync(() -> tabs[1].performClick()); getInstrumentation().waitForIdleSync();
        Object adapter = field(settings, "channelAdapter");
        for (String query : new String[]{"Station", "Needle", "Needle 4321"})
            invoke(adapter, "setQuery", new Class<?>[]{String.class}, query);
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            getInstrumentation().waitForIdleSync();
            List<?> visible = (List<?>)field(adapter, "visible");
            if (visible.size() == 1) break;
            SystemClock.sleep(25);
        }
        List<?> visible = (List<?>)field(adapter, "visible");
        assertEquals(1, visible.size()); assertSame(fixtures.get(4321), visible.get(0));
        invoke(adapter, "setQuery", new Class<?>[]{String.class}, "Station");
        getInstrumentation().runOnMainSync(() -> tabs[4].performClick()); getInstrumentation().waitForIdleSync();
        assertEquals(4, field(settings, "section"));
    }
    private void capture(String name) throws Exception {
        SystemClock.sleep(300);
        getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(getInstrumentation().getTargetContext().getCacheDir(), "performance-qa");
        directory.mkdirs();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        } finally { bitmap.recycle(); }
    }
    private void remoteAcross(RecyclerView list, int count, int key) {
        if (list.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            if (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) key = android.view.KeyEvent.KEYCODE_DPAD_LEFT;
            else if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT) key = android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        getInstrumentation().runOnMainSync(() -> list.getChildAt(0).requestFocus());
        for (int i = 0; i < count; i++) {
            getInstrumentation().sendKeyDownUpSync(key); getInstrumentation().waitForIdleSync();
        }
    }
    private int focusedPosition(RecyclerView list) {
        View focus = list.findFocus();
        while (focus != null && focus.getParent() != list)
            focus = focus.getParent() instanceof View ? (View)focus.getParent() : null;
        return focus == null ? RecyclerView.NO_POSITION : list.getChildAdapterPosition(focus);
    }
    private void awaitCount(RecyclerView list, int count) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            getInstrumentation().waitForIdleSync();
            if (list.getAdapter().getItemCount() == count && list.getChildCount() > 0 && !list.isComputingLayout()) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        assertEquals(count, list.getAdapter().getItemCount()); fail("List did not finish layout");
    }
}
