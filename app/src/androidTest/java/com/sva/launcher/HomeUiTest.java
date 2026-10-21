package com.sva.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.RectF;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.lang.reflect.Field;

public final class HomeUiTest extends InstrumentationTestCase {
    public void testMissingAppearanceUsesDarkEvenOnLightSystem() {
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("launcher", 0);
        String previous = prefs.getString(LiquidTheme.PREF_MODE, null);
        Configuration light = new Configuration(context.getResources().getConfiguration());
        light.uiMode = (light.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        Context lightSystem = context.createConfigurationContext(light);
        try {
            prefs.edit().remove(LiquidTheme.PREF_MODE).commit();
            assertFalse("New installations must default to dark", LiquidTheme.isLight(lightSystem));
            prefs.edit().putString(LiquidTheme.PREF_MODE, LiquidTheme.SYSTEM).commit();
            assertTrue("Explicit system appearance must still follow the TV", LiquidTheme.isLight(lightSystem));
            prefs.edit().putString(LiquidTheme.PREF_MODE, LiquidTheme.LIGHT).commit();
            assertTrue("Explicit light appearance must be preserved", LiquidTheme.isLight(lightSystem));
        } finally {
            if (previous == null) prefs.edit().remove(LiquidTheme.PREF_MODE).commit();
            else prefs.edit().putString(LiquidTheme.PREF_MODE, previous).commit();
        }
    }

    public void testFirstAndLastFocusedAppStayInsideScrollViewport() throws Exception {
        MainActivity activity = (MainActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().setInTouchMode(false);
        try {
            Field field = MainActivity.class.getDeclaredField("appRow"); field.setAccessible(true);
            LinearLayout row = (LinearLayout)field.get(activity);
            long deadline = SystemClock.uptimeMillis() + 10000;
            while ((row.getChildCount() == 0 || row.getChildAt(0).getTag() == null) && SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(30);
            assertTrue("Home apps did not load", row.getChildCount() > 0 && row.getChildAt(0).getTag() != null);
            HorizontalScrollView scroll = (HorizontalScrollView)row.getParent();
            for (int index : new int[]{0, row.getChildCount() - 1, 0}) {
                View tile = row.getChildAt(index);
                getInstrumentation().runOnMainSync(tile::requestFocus);
                SystemClock.sleep(500); getInstrumentation().waitForIdleSync();
                final AssertionError[] failure = new AssertionError[1];
                getInstrumentation().runOnMainSync(() -> {
                    try {
                    assertTrue(tile.hasFocus());
                    RectF bounds = new RectF(0, 0, tile.getWidth(), tile.getHeight());
                    tile.getMatrix().mapRect(bounds);
                    bounds.offset(tile.getLeft(), tile.getTop());
                    assertTrue("Focus ring clipped by content at left", bounds.left >= 0);
                    assertTrue("Focus ring clipped by content at right", bounds.right <= row.getWidth());
                    assertTrue("Focus ring clipped by content vertically", bounds.top >= 0 && bounds.bottom <= row.getHeight());
                    bounds.offset(row.getLeft() - scroll.getScrollX(), row.getTop() - scroll.getScrollY());
                    assertTrue("Focused app clipped by viewport at left", bounds.left >= 0);
                    assertTrue("Focused app clipped by viewport at right", bounds.right <= scroll.getWidth());
                    assertTrue("Focused app clipped by viewport vertically", bounds.top >= 0 && bounds.bottom <= scroll.getHeight());
                    } catch (AssertionError error) { failure[0] = error; }
                });
                if (failure[0] != null) throw failure[0];
            }
        } finally { getInstrumentation().runOnMainSync(activity::finish); }
    }
    public void testTvInputIconBesideNetworkAndRemoteNavigationInBothThemes() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("launcher", 0);
        String previous = prefs.getString(LiquidTheme.PREF_MODE, null);
        try {
            for (String theme : new String[]{LiquidTheme.DARK, LiquidTheme.LIGHT}) {
                prefs.edit().putString(LiquidTheme.PREF_MODE, theme).commit();
                MainActivity activity = startHome();
                try {
                    LinearLayout shortcuts = shortcuts(activity);
                    assertEquals(4, shortcuts.getChildCount());
                    View network = shortcuts.getChildAt(0), input = shortcuts.getChildAt(1);
                    assertEquals("Network", network.getContentDescription().toString());
                    assertEquals("TV Input", input.getContentDescription().toString());
                    assertNotNull(((android.widget.ImageView)input).getDrawable());
                    getInstrumentation().runOnMainSync(network::requestFocus);
                    getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
                    getInstrumentation().waitForIdleSync();
                    assertTrue("Right from Wi-Fi must reach TV Input", input.hasFocus());
                    getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
                    getInstrumentation().waitForIdleSync();
                    assertTrue(shortcuts.getChildAt(2).hasFocus());
                    getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                    getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                    getInstrumentation().waitForIdleSync();
                    assertTrue("Left must return through TV Input to Wi-Fi", network.hasFocus());
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    assertTrue(input.getGlobalVisibleRect(bounds));
                    assertEquals("Icon must fit the navigation dock", input.getWidth(), bounds.width());
                    assertEquals(input.getHeight(), bounds.height());
                } finally { getInstrumentation().runOnMainSync(activity::finish); getInstrumentation().waitForIdleSync(); }
            }
        } finally {
            if (previous == null) prefs.edit().remove(LiquidTheme.PREF_MODE).commit();
            else prefs.edit().putString(LiquidTheme.PREF_MODE, previous).commit();
        }
    }

    public void testTvInputRemoteActivationShowsSourcesOrHelpfulEmptyState() throws Exception {
        MainActivity activity = startHome();
        try {
            getInstrumentation().runOnMainSync(shortcuts(activity).getChildAt(1)::requestFocus);
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            getInstrumentation().waitForIdleSync();
            android.view.accessibility.AccessibilityNodeInfo root = null;
            for (int i = 0; i < 100; i++) {
                root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if (root != null && !root.findAccessibilityNodeInfosByText("TV Input").isEmpty()) break;
                SystemClock.sleep(50);
            }
            assertNotNull(root);
            assertFalse("OK must open TV Input", root.findAccessibilityNodeInfosByText("TV Input").isEmpty());
            android.media.tv.TvInputManager manager = (android.media.tv.TvInputManager)activity.getSystemService(Context.TV_INPUT_SERVICE);
            boolean hasInputs = false;
            if (manager != null) for (android.media.tv.TvInputInfo input : manager.getTvInputList()) {
                if (!input.isHidden(activity)) hasInputs = true;
            }
            if (!hasInputs) assertFalse("Devices without HDMI inputs need clear instructions",
                    root.findAccessibilityNodeInfosByText("No TV inputs are available to Sva").isEmpty());
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            getInstrumentation().waitForIdleSync();
            assertTrue("Dismissing input selection must return focus to its icon", shortcuts(activity).getChildAt(1).hasFocus());
        } finally { getInstrumentation().runOnMainSync(activity::finish); }
    }

    private MainActivity startHome() throws Exception {
        MainActivity activity = (MainActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().setInTouchMode(false);
        for (int i = 0; i < 100 && !activity.hasWindowFocus(); i++) {
            SystemClock.sleep(50); getInstrumentation().waitForIdleSync();
        }
        assertTrue("Home window must receive remote input", activity.hasWindowFocus());
        return activity;
    }

    private LinearLayout shortcuts(MainActivity activity) throws Exception {
        Field field = MainActivity.class.getDeclaredField("systemShortcuts"); field.setAccessible(true);
        return (LinearLayout)field.get(activity);
    }

}
