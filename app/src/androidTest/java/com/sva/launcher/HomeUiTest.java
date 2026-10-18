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
}
