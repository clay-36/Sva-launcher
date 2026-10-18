package com.sva.launcher;

import android.app.Dialog;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Checks remote activation and drafts without saving or changing user preferences. */
public final class SettingsNavigationTest extends InstrumentationTestCase {
    private MainActivity activity;
    private Object screen;
    private Dialog dialog;

    private Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }

    private void open() throws Exception {
        activity = (MainActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().setInTouchMode(false);
        getInstrumentation().runOnMainSync(() -> {
            try {
                Method show = MainActivity.class.getDeclaredMethod("showSettings");
                show.setAccessible(true); show.invoke(activity);
            } catch (Exception e) { throw new AssertionError(e); }
        });
        getInstrumentation().waitForIdleSync();
        screen = field(activity, "settingsScreen"); dialog = (Dialog)field(screen, "dialog");
    }

    private void select(int index) throws Exception {
        Button[] tabs = (Button[])field(screen, "tabs");
        getInstrumentation().runOnMainSync(() -> { tabs[index].requestFocus(); tabs[index].performClick(); });
        getInstrumentation().waitForIdleSync();
    }

    public void testFocusDoesNotOpenSectionAndDraftSurvives() throws Exception {
        try {
            open();
            Button[] tabs = (Button[])field(screen, "tabs");
            getInstrumentation().runOnMainSync(() -> tabs[3].requestFocus());
            assertEquals("Focus must not switch the content pane", 0, ((Integer)field(screen, "section")).intValue());
            select(0);
            EditText playlist = (EditText)field(screen, "playlistField");
            getInstrumentation().runOnMainSync(() -> playlist.setText("https://example.invalid/draft.m3u"));
            select(4);
            assertEquals(4, ((Integer)field(screen, "section")).intValue());
            View first = (View)field(screen, "firstControl");
            assertTrue("Opening with OK must enter the content", first.hasFocus());
            select(0);
            assertEquals("https://example.invalid/draft.m3u", ((EditText)field(screen, "playlistField")).getText().toString());
        } finally { close(); }
    }

    public void testEverySectionOpensWithVisibleFooter() throws Exception {
        try {
            open();
            for (int section = 0; section < 14; section++) {
                select(section);
                assertEquals(section, ((Integer)field(screen, "section")).intValue());
                View detail = (View)field(screen, "detail");
                assertTrue("Content needs positive width", detail.getWidth() > 0);
                assertTrue("Content needs positive height", detail.getHeight() > 0);
                Button tab = ((Button[])field(screen, "tabs"))[section];
                android.text.Layout text = tab.getLayout();
                assertNotNull(text);
                assertEquals("Section label truncated", 0, text.getEllipsisCount(text.getLineCount() - 1));
                assertTrue("Section label clipped", text.getHeight() <= tab.getHeight() - tab.getCompoundPaddingTop() - tab.getCompoundPaddingBottom());
                // These pages contain fixed Save/Discard actions below their scrolling form.
                if (section == 0 || section == 5) {
                    View save = findText(detail, "Save changes");
                    assertNotNull(save);
                    int[] content = new int[2], button = new int[2];
                    detail.getLocationOnScreen(content); save.getLocationOnScreen(button);
                    assertTrue("Save action clipped below the panel", button[1] + save.getHeight() <= content[1] + detail.getHeight());
                }
            }
        } finally { close(); }
    }

    public void testAllSectionsInLightTheme() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        String previous = prefs.getString(LiquidTheme.PREF_MODE, LiquidTheme.SYSTEM);
        try {
            prefs.edit().putString(LiquidTheme.PREF_MODE, LiquidTheme.LIGHT).commit();
            testEverySectionOpensWithVisibleFooter();
        } finally { prefs.edit().putString(LiquidTheme.PREF_MODE, previous).commit(); }
    }

    public void testScreensaverRowsAndControllerCancellation() throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        ScreensaverSettingsActivity screensaver = (ScreensaverSettingsActivity)getInstrumentation().startActivitySync(
                new Intent(context, ScreensaverSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            getInstrumentation().waitForIdleSync();
            View decor = screensaver.getWindow().getDecorView();
            assertNotNull(findText(decor, "Photo source"));
            assertNotNull(findText(decor, "Atmosphere"));
            assertNotNull(findText(decor, "Save changes"));
            assertNotNull(findText(decor, "Preview"));
            assertFalse("Screensaver settings must not begin text editing on entry", screensaver.getCurrentFocus() instanceof EditText);
            View preview = findText(decor, "Preview");
            int[] location = new int[2]; preview.getLocationOnScreen(location);
            assertTrue("Preview action clipped", location[1] + preview.getHeight() <= decor.getHeight());
        } finally { getInstrumentation().runOnMainSync(screensaver::finish); }
        ControllerSettingsActivity controllers = (ControllerSettingsActivity)getInstrumentation().startActivitySync(
                new Intent(context, ControllerSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            getInstrumentation().waitForIdleSync();
            assertNotNull(findText(controllers.getWindow().getDecorView(), "Refresh controllers"));
            android.view.InputDevice device = android.view.InputDevice.getDevice(-1);
            if (device == null) {
                int[] ids = android.view.InputDevice.getDeviceIds();
                if (ids.length > 0) device = android.view.InputDevice.getDevice(ids[0]);
            }
            assertNotNull("An input device is needed for the cancellation fixture", device);
            android.view.InputDevice selected = device;
            getInstrumentation().runOnMainSync(() -> {
                try {
                    Method begin = ControllerSettingsActivity.class.getDeclaredMethod("begin", android.view.InputDevice.class);
                    begin.setAccessible(true); begin.invoke(controllers, selected);
                    assertNotNull(findText(controllers.getWindow().getDecorView(), "Button 1 of " + ControllerProfile.NAMES.length + "\n\n" + ControllerProfile.NAMES[0]));
                    Method back = ControllerSettingsActivity.class.getDeclaredMethod("handleBack");
                    back.setAccessible(true); back.invoke(controllers);
                    assertNull(field(controllers, "mappingDevice"));
                } catch (Exception e) { throw new AssertionError(e); }
            });
        } finally { getInstrumentation().runOnMainSync(controllers::finish); }
    }

    public void testNavigationVisibilityAndCalendarAccess() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        String[] keys = {"hideGamesNavigation", "hideEventsNavigation"};
        java.util.Map<String, ?> previous = prefs.getAll();
        try {
            prefs.edit().remove(keys[0]).remove(keys[1]).commit();
            open(); select(13);
            android.widget.LinearLayout bar = (android.widget.LinearLayout)field(activity, "tabRow");
            Button[] buttons = (Button[])field(activity, "tabButtons");
            assertTrue(bar.indexOfChild(buttons[2]) >= 0);
            assertTrue(bar.indexOfChild(buttons[3]) >= 0);
            getInstrumentation().runOnMainSync(() -> {
                try {
                    Method select = MainActivity.class.getDeclaredMethod("selectTab", int.class, boolean.class);
                    select.setAccessible(true); select.invoke(activity, 2, false);
                    View row = findDescription((View)field(screen, "detail"), "Show Games in navigation, On");
                    assertNotNull(row); row.performClick();
                    assertEquals(0, ((Integer)field(activity, "activeTab")).intValue());
                    row = findDescription((View)field(screen, "detail"), "Show Events in navigation, On");
                    assertNotNull(row); row.performClick();
                } catch (Exception e) { throw new AssertionError(e); }
            });
            getInstrumentation().waitForIdleSync();
            assertEquals(-1, bar.indexOfChild(buttons[2]));
            assertEquals(-1, bar.indexOfChild(buttons[3]));
            assertTrue(prefs.getBoolean(keys[0], false)); assertTrue(prefs.getBoolean(keys[1], false));
            getInstrumentation().runOnMainSync(() -> {
                try {
                    View homeCalendar = (View) field(activity, "homeCalendarCard");
                    assertFalse("The Home calendar card is non-clickable", homeCalendar.isClickable());
                    assertFalse("The Home calendar card is non-focusable", homeCalendar.isFocusable());
                    homeCalendar.performClick();
                } catch (Exception e) { throw new AssertionError(e); }
            });
            getInstrumentation().waitForIdleSync();
            assertEquals("The Home calendar card does not switch tabs", 0, ((Integer)field(activity, "activeTab")).intValue());
            // Rebuild from saved preferences, then show both tabs again using the settings controls.
            getInstrumentation().runOnMainSync(() -> {
                try {
                    Method rebuild = MainActivity.class.getDeclaredMethod("rebuildNavigationButtons");
                    rebuild.setAccessible(true); rebuild.invoke(activity);
                    findDescription((View)field(screen, "detail"), "Show Games in navigation, Off").performClick();
                    findDescription((View)field(screen, "detail"), "Show Events in navigation, Off").performClick();
                } catch (Exception e) { throw new AssertionError(e); }
            });
            assertTrue(bar.indexOfChild(buttons[2]) >= 0); assertTrue(bar.indexOfChild(buttons[3]) >= 0);
        } finally {
            close();
            android.content.SharedPreferences.Editor edit = prefs.edit();
            for (String key : keys) {
                if (previous.containsKey(key)) edit.putBoolean(key, (Boolean)previous.get(key)); else edit.remove(key);
            }
            edit.commit();
        }
    }

    private View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description); if (found != null) return found;
            }
        }
        return null;
    }

    public void testCustomNavigationOrderPersistsWithHiddenItems() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        java.util.Map<String, ?> previous = prefs.getAll();
        String[] keys = {NavigationPreferences.ORDER, "liveTvNavigationPosition", "hideGamesNavigation", "hideEventsNavigation", "hideLiveTvNavigation"};
        try {
            android.content.SharedPreferences.Editor clean = prefs.edit();
            for (String key : keys) clean.remove(key);
            clean.commit();
            open(); select(13);
            chooseFirstPosition(4);
            assertEquals("saved,live_tv,games,events", prefs.getString(NavigationPreferences.ORDER, ""));
            assertEquals(java.util.Arrays.asList(0, 4, 1, 2, 3), barOrder());
            View moved = ((View)field(screen, "detail")).findViewWithTag("navigation-order-4");
            assertTrue("Focus must follow the moved category", moved.hasFocus());
            getInstrumentation().runOnMainSync(() -> {
                try { findDescription((View)field(screen, "detail"), "Show Events in navigation, On").performClick(); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            assertEquals(java.util.Arrays.asList(0, 4, 1, 2), barOrder());
            close(); open(); select(13);
            assertEquals("Order and visibility must survive reopening", java.util.Arrays.asList(0, 4, 1, 2), barOrder());
            chooseFirstPosition(3);
            assertEquals("events,saved,live_tv,games", prefs.getString(NavigationPreferences.ORDER, ""));
            assertEquals("Moving a hidden item must not reveal it", java.util.Arrays.asList(0, 4, 1, 2), barOrder());
            getInstrumentation().runOnMainSync(() -> {
                try { findDescription((View)field(screen, "detail"), "Show Events in navigation, Off").performClick(); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            assertEquals(java.util.Arrays.asList(0, 3, 4, 1, 2), barOrder());
            getInstrumentation().runOnMainSync(() -> {
                try { findText((View)field(screen, "detail"), "Reset item order").performClick(); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            assertEquals(java.util.Arrays.asList(0, 1, 2, 3, 4), barOrder());
            assertFalse(prefs.contains(NavigationPreferences.ORDER));
        } finally {
            close();
            android.content.SharedPreferences.Editor restore = prefs.edit();
            for (String key : keys) {
                Object value = previous.get(key);
                if (value instanceof String) restore.putString(key, (String)value);
                else if (value instanceof Integer) restore.putInt(key, (Integer)value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean)value);
                else restore.remove(key);
            }
            restore.commit();
        }
    }

    private void chooseFirstPosition(int tab) throws Exception {
        View row = ((View)field(screen, "detail")).findViewWithTag("navigation-order-" + tab);
        getInstrumentation().runOnMainSync(() -> { row.requestFocus(); row.performClick(); });
        getInstrumentation().waitForIdleSync();
        for (int i = 0; i < 3; i++) getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
        getInstrumentation().waitForIdleSync();
    }

    private java.util.List<Integer> barOrder() throws Exception {
        android.widget.LinearLayout bar = (android.widget.LinearLayout)field(activity, "tabRow");
        Button[] buttons = (Button[])field(activity, "tabButtons");
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < bar.getChildCount(); i++) for (int tab = 0; tab < buttons.length; tab++)
            if (bar.getChildAt(i) == buttons[tab]) order.add(tab);
        return order;
    }

    private View findText(View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView)view).getText())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text); if (found != null) return found;
            }
        }
        return null;
    }

    private void close() {
        if (activity != null) getInstrumentation().runOnMainSync(() -> {
            if (dialog != null) dialog.dismiss(); activity.finish();
        });
    }
}
