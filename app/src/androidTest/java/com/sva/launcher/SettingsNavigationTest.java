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
        for (int attempt = 0; attempt < 100 && !dialog.getWindow().getDecorView().hasWindowFocus(); attempt++) {
            android.os.SystemClock.sleep(50);
            getInstrumentation().waitForIdleSync();
        }
        assertTrue("Settings window must receive remote events", dialog.getWindow().getDecorView().hasWindowFocus());
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
            for (int section = 0; section < ((Button[])field(screen, "tabs")).length; section++) {
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
                    View save = findText(detail, AppLanguage.get(R.string.ui_save_changes));
                    assertNotNull(save);
                    int[] content = new int[2], button = new int[2];
                    detail.getLocationOnScreen(content); save.getLocationOnScreen(button);
                    assertTrue("Save action clipped below the panel", button[1] + save.getHeight() <= content[1] + detail.getHeight());
                }
            }
        } finally { close(); }
    }

    public void testPressingUpInChildCategoryReturnsToParentCategory() throws Exception {
        try {
            open();
            Button[] tabs = (Button[]) field(screen, "tabs");
            for (int section = 0; section < tabs.length; section++) {
                select(section);
                assertEquals(section, ((Integer) field(screen, "section")).intValue());
                View first = (View) field(screen, "firstControl");
                assertNotNull("Section " + section + " should have a firstControl", first);
                assertTrue("Child control should have focus in section " + section, first.hasFocus());
                for (int attempt = 0; attempt < 5 && !tabs[section].hasFocus(); attempt++) {
                    getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
                    getInstrumentation().waitForIdleSync();
                }
                assertTrue("Parent tab " + section + " (" + tabs[section].getText() + ") must have focus after pressing UP",
                        tabs[section].hasFocus());
            }
        } finally { close(); }
    }

    public void testAppearanceUpFromBottomVisitsOffscreenControlsBeforeParent() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        String previous = prefs.getString(LiquidTheme.PREF_MODE, null);
        try {
            for (String theme : new String[]{LiquidTheme.DARK, LiquidTheme.LIGHT}) {
                prefs.edit().putString(LiquidTheme.PREF_MODE, theme).commit();
                open(); select(3);
                View first = (View)field(screen, "firstControl");
                View parent = first;
                while (!(parent instanceof android.widget.ScrollView)) parent = (View)parent.getParent();
                android.widget.ScrollView scroll = (android.widget.ScrollView)parent;
                java.util.ArrayList<View> controls = scroll.getChildAt(0).getFocusables(View.FOCUS_FORWARD);
                controls.removeIf(view -> !view.isEnabled() || !view.isFocusable());
                assertTrue("Appearance must contain a scrolling form", controls.size() > 6);
                assertSame(first, controls.get(0));
                for (int i = 1; i < controls.size(); i++) {
                    remote(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
                    assertSame("Down must visit each Appearance control", controls.get(i), dialog.getCurrentFocus());
                }
                assertTrue("Fixture must reach the bottom of the form", scroll.getScrollY() > 0);
                Button rail = ((Button[])field(screen, "tabs"))[3];
                for (int i = controls.size() - 2; i >= 0; i--) {
                    remote(android.view.KeyEvent.KEYCODE_DPAD_UP);
                    assertSame("Up must reveal the previous control instead of returning to the rail, index=" + i,
                            controls.get(i), dialog.getCurrentFocus());
                    assertFalse(rail.hasFocus());
                }
                remote(android.view.KeyEvent.KEYCODE_DPAD_UP);
                assertTrue("Only Up from the first control returns to Appearance", rail.hasFocus());
                close(); getInstrumentation().waitForIdleSync();
            }
        } finally {
            close();
            if (previous == null) prefs.edit().remove(LiquidTheme.PREF_MODE).commit();
            else prefs.edit().putString(LiquidTheme.PREF_MODE, previous).commit();
        }
    }

    public void testAllSectionsInLightTheme() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        String previous = prefs.getString(LiquidTheme.PREF_MODE, LiquidTheme.SYSTEM);
        try {
            prefs.edit().putString(LiquidTheme.PREF_MODE, LiquidTheme.LIGHT).commit();
            testEverySectionOpensWithVisibleFooter();
        } finally { prefs.edit().putString(LiquidTheme.PREF_MODE, previous).commit(); }
    }

    public void testSaveActionsReachableWithRemote() throws Exception {
        try {
            open();
            for (int section : new int[]{0, 5}) {
                select(section);
                View detail = (View) field(screen, "detail");
                View discard = findText(detail, AppLanguage.get(R.string.ui_discard_edits));
                View save = findText(detail, AppLanguage.get(R.string.ui_save_changes));
                getInstrumentation().runOnMainSync(discard::requestFocus);
                remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                getInstrumentation().waitForIdleSync();
                assertTrue("Left from Discard must reach Save; focus=" + focusDescription(), save.hasFocus());
                remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
                getInstrumentation().waitForIdleSync();
                assertTrue("Right from Save must reach Discard", discard.hasFocus());
            }
        } finally { close(); }
    }

    public void testRemoteRouteFromInputsToSaveAndBack() throws Exception {
        try {
            open();
            for (int section : new int[]{0, 5}) {
                for (boolean multiline : new boolean[]{false, true}) {
                    select(section);
                    EditText first = (EditText) field(screen, section == 0 ? "playlistField" : "licenseField");
                    EditText last = (EditText) field(screen, section == 0 ? "guideField" : "headersField");
                    getInstrumentation().runOnMainSync(() -> {
                        first.setText("https://example.invalid/source");
                        last.setText(multiline ? "first\nsecond\nthird\nfourth\nfifth\nsixth" : "single");
                        first.requestFocus(); first.setSelection(0);
                    });
                    View detail = (View) field(screen, "detail");
                    View save = findText(detail, AppLanguage.get(R.string.ui_save_changes));
                    View discard = findText(detail, AppLanguage.get(R.string.ui_discard_edits));
                    View refresh = findText(detail, AppLanguage.get(R.string.ui_refresh_channels));
                    for (int step = 0; step < 40 && dialog.getCurrentFocus() != save
                            && dialog.getCurrentFocus() != discard && dialog.getCurrentFocus() != refresh; step++) {
                        remote(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
                    }
                    View focused = dialog.getCurrentFocus();
                    assertTrue("Down must leave the inputs and reach the footer, section " + section + " multiline=" + multiline + " focus=" + focusDescription(),
                            focused == save || focused == discard || focused == refresh);
                    for (int step = 0; step < 2 && !save.hasFocus(); step++) remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                    assertTrue("Save must be reachable from the form", save.hasFocus());
                    remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
                    assertTrue(discard.hasFocus());
                    if (refresh != null) {
                        remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
                        assertTrue(refresh.hasFocus());
                        remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                        assertTrue(discard.hasFocus());
                    }
                    remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                    assertTrue(save.hasFocus());
                    remote(android.view.KeyEvent.KEYCODE_DPAD_UP);
                    assertTrue("Up must return to the form", isDescendant(dialog.getCurrentFocus(), detail)
                            && dialog.getCurrentFocus().getParent() != save.getParent());
                    getInstrumentation().runOnMainSync(save::requestFocus);
                    remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                    Button tab = ((Button[])field(screen, "tabs"))[section];
                    assertTrue("Left from Save must return to the sidebar", tab.hasFocus());
                    remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
                    assertTrue("Right must re-enter the form", first.hasFocus());
                }
            }
        } finally { close(); }
    }

    private void remote(int key) {
        // Horizontal routes below use LTR logical order; mirror their physical keys in RTL.
        if (AppLanguage.context().getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT) key = android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
            else if (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) key = android.view.KeyEvent.KEYCODE_DPAD_LEFT;
        }
        getInstrumentation().sendKeyDownUpSync(key);
        getInstrumentation().waitForIdleSync();
    }

    public void testRemoteRoutesInLightTheme() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        boolean existed = prefs.contains(LiquidTheme.PREF_MODE);
        String previous = prefs.getString(LiquidTheme.PREF_MODE, LiquidTheme.SYSTEM);
        try {
            prefs.edit().putString(LiquidTheme.PREF_MODE, LiquidTheme.LIGHT).commit();
            testRemoteRouteFromInputsToSaveAndBack();
        } finally {
            android.content.SharedPreferences.Editor editor = prefs.edit();
            if (existed) editor.putString(LiquidTheme.PREF_MODE, previous);
            else editor.remove(LiquidTheme.PREF_MODE);
            editor.commit();
        }
    }

    private String focusDescription() {
        View focus = dialog.getCurrentFocus();
        return focus == null ? "null" : focus.getClass().getSimpleName()
                + (focus instanceof Button ? ":" + ((Button)focus).getText() : "")
                + " windowFocus=" + dialog.getWindow().getDecorView().hasWindowFocus();
    }

    private boolean isDescendant(View child, View parent) {
        while (child != null) {
            if (child == parent) return true;
            child = child.getParent() instanceof View ? (View)child.getParent() : null;
        }
        return false;
    }

    public void testScreensaverRowsAndControllerCancellation() throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        ScreensaverSettingsActivity screensaver = (ScreensaverSettingsActivity)getInstrumentation().startActivitySync(
                new Intent(context, ScreensaverSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            getInstrumentation().waitForIdleSync();
            View decor = screensaver.getWindow().getDecorView();
            assertNotNull(findText(decor, AppLanguage.get(R.string.ui_photo_source)));
            assertNotNull(findText(decor, AppLanguage.get(R.string.ui_atmosphere)));
            assertNotNull(findText(decor, AppLanguage.get(R.string.ui_save_changes)));
            assertNotNull(findText(decor, AppLanguage.get(R.string.ui_preview)));
            assertFalse("Screensaver settings must not begin text editing on entry", screensaver.getCurrentFocus() instanceof EditText);
            View preview = findText(decor, AppLanguage.get(R.string.ui_preview));
            int[] location = new int[2]; preview.getLocationOnScreen(location);
            assertTrue("Preview action clipped", location[1] + preview.getHeight() <= decor.getHeight());
        } finally { getInstrumentation().runOnMainSync(screensaver::finish); }
        ControllerSettingsActivity controllers = (ControllerSettingsActivity)getInstrumentation().startActivitySync(
                new Intent(context, ControllerSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            getInstrumentation().waitForIdleSync();
            assertNotNull(findText(controllers.getWindow().getDecorView(), AppLanguage.get(R.string.ui_refresh_controllers)));
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
                    assertNotNull(findText(controllers.getWindow().getDecorView(), AppLanguage.get(R.string.ui_button_1_s_of_2_s_3_s, 1, ControllerProfile.NAMES.length, ControllerProfile.names()[0])));
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
                    View row = findDescription((View)field(screen, "detail"), AppLanguage.get(R.string.ui_label_state, AppLanguage.get(R.string.ui_show_games_in_navigation), AppLanguage.get(R.string.ui_on)));
                    assertNotNull(row); row.performClick();
                    assertEquals(0, ((Integer)field(activity, "activeTab")).intValue());
                    row = findDescription((View)field(screen, "detail"), AppLanguage.get(R.string.ui_label_state, AppLanguage.get(R.string.ui_show_events_in_navigation), AppLanguage.get(R.string.ui_on)));
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
                    findDescription((View)field(screen, "detail"), AppLanguage.get(R.string.ui_label_state, AppLanguage.get(R.string.ui_show_games_in_navigation), AppLanguage.get(R.string.ui_off))).performClick();
                    findDescription((View)field(screen, "detail"), AppLanguage.get(R.string.ui_label_state, AppLanguage.get(R.string.ui_show_events_in_navigation), AppLanguage.get(R.string.ui_off))).performClick();
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
        String[] keys = {NavigationPreferences.ORDER, "liveTvNavigationPosition", "hideGamesNavigation", "hideEventsNavigation", "hideLiveTvNavigation", "hideActivitiesNavigation"};
        try {
            android.content.SharedPreferences.Editor clean = prefs.edit();
            for (String key : keys) clean.remove(key);
            clean.commit();
            open(); select(13);
            chooseFirstPosition(4);
            assertEquals("saved,live_tv,games,events,news,activities", prefs.getString(NavigationPreferences.ORDER, ""));
            assertEquals(java.util.Arrays.asList(0, 4, 1, 2, 3, 5, 6), barOrder());
            View moved = ((View)field(screen, "detail")).findViewWithTag("navigation-order-4");
            assertTrue("Focus must follow the moved category", moved.hasFocus());
            getInstrumentation().runOnMainSync(() -> {
                try { findDescription((View)field(screen, "detail"), AppLanguage.get(R.string.ui_label_state, AppLanguage.get(R.string.ui_show_events_in_navigation), AppLanguage.get(R.string.ui_on))).performClick(); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            assertEquals(java.util.Arrays.asList(0, 4, 1, 2, 5, 6), barOrder());
            close(); open(); select(13);
            assertEquals("Order and visibility must survive reopening", java.util.Arrays.asList(0, 4, 1, 2, 5, 6), barOrder());
            chooseFirstPosition(3);
            assertEquals("events,saved,live_tv,games,news,activities", prefs.getString(NavigationPreferences.ORDER, ""));
            assertEquals("Moving a hidden item must not reveal it", java.util.Arrays.asList(0, 4, 1, 2, 5, 6), barOrder());
            getInstrumentation().runOnMainSync(() -> {
                try { findDescription((View)field(screen, "detail"), AppLanguage.get(R.string.ui_label_state, AppLanguage.get(R.string.ui_show_events_in_navigation), AppLanguage.get(R.string.ui_off))).performClick(); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            assertEquals(java.util.Arrays.asList(0, 3, 4, 1, 2, 5, 6), barOrder());
            getInstrumentation().runOnMainSync(() -> {
                try { findText((View)field(screen, "detail"), AppLanguage.get(R.string.ui_reset_item_order)).performClick(); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            assertEquals(java.util.Arrays.asList(0, 1, 2, 3, 4, 5, 6), barOrder());
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

    public void testMultipleCalendarFeedsSaveRefreshAndRemoteNavigation() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        java.util.Map<String, ?> previous = prefs.getAll();
        String[] keys = calendarKeys();
        try (CalendarHttpFixture feeds = new CalendarHttpFixture()) {
            android.content.SharedPreferences.Editor clean = prefs.edit();
            for (String key : keys) clean.remove(key);
            clean.putLong("scheduleCalendarMs", 0L).commit();
            open(); select(9);
            EditText input = (EditText)field(screen, "calendarUrlField");
            String urls = feeds.url("a") + "\n" + feeds.url("b");
            getInstrumentation().runOnMainSync(() -> { input.setText(urls + "\n" + feeds.url("a")); input.setSelection(input.length()); });
            View save = findText((View)field(screen, "detail"), AppLanguage.get(R.string.ui_save_calendar_and_refresh));
            for (int i = 0; i < 30 && !save.hasFocus(); i++) remote(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            assertTrue("Remote must leave multiline calendar input and reach Save", save.hasFocus());
            remote(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            waitForCalendarRefresh();
            assertEquals(urls, prefs.getString("publicCalendarUrl", ""));
            assertEquals(3, new org.json.JSONArray(prefs.getString("publicCalendarEvents", "[]")).length());
            select(4); select(9);
            EditText current = (EditText)field(screen, "calendarUrlField");
            assertEquals(urls, current.getText().toString());
            getInstrumentation().runOnMainSync(() -> current.setText(urls + "\nftp://example.com/invalid"));
            activateCalendarAction(AppLanguage.get(R.string.ui_save_calendar_and_refresh));
            assertEquals("Invalid lines must not change saved sources", urls, prefs.getString("publicCalendarUrl", ""));
            assertEquals(AppLanguage.get(R.string.ui_line_1_s_use_an_https_calendar_url_or_an_http_url_on_your_local, 3),
                    ((android.widget.TextView)field(screen, "calendarFeedback")).getText().toString());
            getInstrumentation().runOnMainSync(() -> current.setText(urls));
            feeds.failB = true; feeds.emptyA = true;
            activateCalendarAction(AppLanguage.get(R.string.ui_refresh_saved_calendar_now));
            waitForCalendarRefresh();
            assertEquals("Failed B retains its cache; empty A removes its old events", 2,
                    new org.json.JSONArray(prefs.getString("publicCalendarEvents", "[]")).length());
            assertTrue(((android.widget.TextView)field(screen, "calendarFeedback")).getText().toString().contains(AppLanguage.get(R.string.ui_source_1_s_2_s, 2, new PublicCalendarRepository.HttpFeedException(503).getMessage())));
            // Removing A keeps B's cache even while B still fails.
            getInstrumentation().runOnMainSync(() -> current.setText(feeds.url("b")));
            activateCalendarAction(AppLanguage.get(R.string.ui_save_calendar_and_refresh)); waitForCalendarRefresh();
            assertFalse(prefs.getString(CalendarFeedCache.KEY, "").contains(feeds.url("a")));
            assertEquals(2, new org.json.JSONArray(prefs.getString("publicCalendarEvents", "[]")).length());
            getInstrumentation().runOnMainSync(() -> current.setText(""));
            activateCalendarAction(AppLanguage.get(R.string.ui_save_calendar_and_refresh));
            assertFalse(prefs.contains("publicCalendarUrl")); assertFalse(prefs.contains(CalendarFeedCache.KEY));
            // Draft survives section switches, then existing Discard logic restores saved values.
            getInstrumentation().runOnMainSync(() -> current.setText(urls));
            select(4); select(9);
            assertEquals(urls, ((EditText)field(screen, "calendarUrlField")).getText().toString());
            getInstrumentation().runOnMainSync(() -> {
                try {
                    Method requestClose = screen.getClass().getDeclaredMethod("requestClose");
                    requestClose.setAccessible(true); requestClose.invoke(screen);
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            getInstrumentation().waitForIdleSync();
            boolean discarded = false;
            for (int attempt = 0; attempt < 100 && !discarded; attempt++) {
                android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    java.util.List<android.view.accessibility.AccessibilityNodeInfo> actions = root.findAccessibilityNodeInfosByText(AppLanguage.get(R.string.ui_discard));
                    for (android.view.accessibility.AccessibilityNodeInfo node : actions) {
                        if (AppLanguage.get(R.string.ui_discard).contentEquals(node.getText() == null ? "" : node.getText())) {
                            discarded = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK); break;
                        }
                    }
                }
                if (!discarded) android.os.SystemClock.sleep(50);
            }
            assertTrue("Discard prompt action must be available", discarded);
            getInstrumentation().waitForIdleSync();
            assertFalse(dialog.isShowing());
            close(); open(); select(9);
            assertEquals("Discarded feed drafts must not be saved", "", ((EditText)field(screen, "calendarUrlField")).getText().toString());
        } finally { close(); restoreCalendarPreferences(prefs, previous, keys); }
    }

    public void testMultipleCalendarFeedsInLightTheme() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        boolean existed = prefs.contains(LiquidTheme.PREF_MODE);
        String previous = prefs.getString(LiquidTheme.PREF_MODE, LiquidTheme.SYSTEM);
        try {
            prefs.edit().putString(LiquidTheme.PREF_MODE, LiquidTheme.LIGHT).commit();
            testMultipleCalendarFeedsSaveRefreshAndRemoteNavigation();
        } finally {
            android.content.SharedPreferences.Editor edit = prefs.edit();
            if (existed) edit.putString(LiquidTheme.PREF_MODE, previous); else edit.remove(LiquidTheme.PREF_MODE);
            edit.commit();
        }
    }

    public void testClearingCalendarsRejectsLateRefreshResult() throws Exception {
        checkLateCalendarRefresh(true);
    }

    public void testChangingCalendarsRejectsLateRefreshResult() throws Exception {
        checkLateCalendarRefresh(false);
    }

    private void checkLateCalendarRefresh(boolean clear) throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        java.util.Map<String, ?> previous = prefs.getAll();
        String[] keys = calendarKeys();
        try (CalendarHttpFixture feeds = new CalendarHttpFixture()) {
            android.content.SharedPreferences.Editor clean = prefs.edit();
            for (String key : keys) clean.remove(key);
            clean.putLong("scheduleCalendarMs", 0L).commit();
            open(); select(9); feeds.hold = true;
            EditText input = (EditText)field(screen, "calendarUrlField");
            getInstrumentation().runOnMainSync(() -> input.setText(feeds.url("a")));
            activateCalendarAction(AppLanguage.get(R.string.ui_save_calendar_and_refresh));
            assertTrue(feeds.entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            getInstrumentation().runOnMainSync(() -> input.setText(clear ? "" : feeds.url("b")));
            activateCalendarAction(AppLanguage.get(R.string.ui_save_calendar_and_refresh));
            feeds.release.countDown();
            assertTrue(feeds.completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
            // Drain the same executor: all earlier work must have posted its result first.
            java.util.concurrent.ExecutorService executor = (java.util.concurrent.ExecutorService)field(activity, "calendarWorker");
            executor.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
            getInstrumentation().waitForIdleSync();
            if (clear) {
                assertFalse(prefs.contains("publicCalendarUrl"));
                assertFalse(prefs.contains(CalendarFeedCache.KEY));
                assertFalse(prefs.contains("publicCalendarEvents"));
            } else {
                waitForCalendarRefresh();
                assertEquals(feeds.url("b"), prefs.getString("publicCalendarUrl", ""));
                assertFalse(prefs.getString(CalendarFeedCache.KEY, "").contains(feeds.url("a")));
                String events = prefs.getString("publicCalendarEvents", "[]");
                assertTrue(events.contains("Calendar B")); assertFalse(events.contains("Calendar A"));
            }
        } finally { close(); restoreCalendarPreferences(prefs, previous, keys); }
    }

    private void activateCalendarAction(String text) throws Exception {
        View action = findText((View)field(screen, "detail"), text);
        assertNotNull(action);
        getInstrumentation().runOnMainSync(action::requestFocus);
        remote(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
    }

    private void waitForCalendarRefresh() throws Exception {
        for (int i = 0; i < 200; i++) {
            getInstrumentation().waitForIdleSync();
            if (!((Boolean)field(activity, "calendarRefreshing"))) return;
            android.os.SystemClock.sleep(50);
        }
        fail("Calendar refresh timed out");
    }

    private String[] calendarKeys() {
        return new String[]{"publicCalendarUrl", "publicCalendarEvents", CalendarFeedCache.KEY,
                "publicCalendarUpdatedAt", "publicCalendarAttemptedAt", "publicCalendarCacheVersion",
                "publicCalendarAttemptedVersion", "scheduleCalendarMs"};
    }

    private void restoreCalendarPreferences(android.content.SharedPreferences prefs, java.util.Map<String, ?> previous, String[] keys) {
        android.content.SharedPreferences.Editor edit = prefs.edit();
        for (String key : keys) {
            Object value = previous.get(key);
            if (value instanceof String) edit.putString(key, (String)value);
            else if (value instanceof Long) edit.putLong(key, (Long)value);
            else if (value instanceof Integer) edit.putInt(key, (Integer)value);
            else edit.remove(key);
        }
        edit.commit();
    }

    public void testAppOrderArrowsNavigateWithinTheirOwnRow() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        java.util.Map<String, ?> previous = prefs.getAll();
        try {
            open(); select(2);
            for (int i = 0; i < 100 && field(screen, "availableApps") == null; i++) {
                android.os.SystemClock.sleep(50); getInstrumentation().waitForIdleSync();
            }
            java.util.List<?> apps = (java.util.List<?>)field(screen, "availableApps");
            assertNotNull(apps); assertTrue("Need three installed apps for ordering checks", apps.size() >= 3);
            java.util.List<String> selected = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) selected.add((String)field(apps.get(i), "packageName"));
            prefs.edit().putBoolean("customApps", true).putString("selectedApps", String.join("\n", selected)).commit();
            select(2);
            android.view.ViewGroup middle = appSettingsRow(1);
            View toggle = middle.getChildAt(0), up = middle.getChildAt(1), down = middle.getChildAt(2);
            getInstrumentation().runOnMainSync(down::requestFocus);
            remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
            assertTrue("Left from Down must reach Up in the same row", up.hasFocus());
            remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
            assertTrue("Left from Up must reach the app name", toggle.hasFocus());
            remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
            assertTrue("Only Left from the app name returns to the sidebar", ((Button[])field(screen, "tabs"))[2].hasFocus());
            getInstrumentation().runOnMainSync(toggle::requestFocus);
            remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); assertTrue(up.hasFocus());
            remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); assertTrue(down.hasFocus());
            remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); assertTrue("Right must not leave the row", down.hasFocus());
            remote(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            java.util.Collections.swap(selected, 1, 2);
            assertEquals("Reordering must still work", String.join("\n", selected), prefs.getString("selectedApps", ""));
            android.view.ViewGroup moved = appSettingsRow(2);
            View movedUp = moved.getChildAt(1), movedDown = moved.getChildAt(2);
            assertFalse("Last app cannot move down", movedDown.isEnabled());
            getInstrumentation().runOnMainSync(movedUp::requestFocus);
            remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
            assertTrue("Rebound rows must retain horizontal navigation", moved.getChildAt(0).hasFocus());
            remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); assertTrue(movedUp.hasFocus());
            remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT); assertTrue("Skip disabled Down at the row edge", movedUp.hasFocus());
            android.view.ViewGroup first = appSettingsRow(0);
            assertFalse(first.getChildAt(1).isEnabled());
            getInstrumentation().runOnMainSync(first.getChildAt(2)::requestFocus);
            remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
            assertTrue("Disabled Up must be skipped", first.getChildAt(0).hasFocus());
            remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
            assertTrue("Right must skip disabled Up", first.getChildAt(2).hasFocus());
            if (apps.size() > 3) {
                android.view.ViewGroup unselected = appSettingsRow(apps.size() - 1);
                assertEquals(View.GONE, unselected.getChildAt(1).getVisibility());
                assertEquals(View.GONE, unselected.getChildAt(2).getVisibility());
                getInstrumentation().runOnMainSync(unselected.getChildAt(0)::requestFocus);
                remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
                assertTrue("Hidden arrows must not receive focus", unselected.getChildAt(0).hasFocus());
                // Return after scrolling/recycling and repeat the original failure path.
                android.view.ViewGroup rebound = appSettingsRow(1);
                getInstrumentation().runOnMainSync(rebound.getChildAt(2)::requestFocus);
                remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
                assertTrue("Scrolled rows must stay in their own action row", rebound.getChildAt(1).hasFocus());
            }
        } finally {
            close();
            android.content.SharedPreferences.Editor edit = prefs.edit();
            if (previous.containsKey("customApps")) edit.putBoolean("customApps", (Boolean)previous.get("customApps"));
            else edit.remove("customApps");
            if (previous.containsKey("selectedApps")) edit.putString("selectedApps", (String)previous.get("selectedApps"));
            else edit.remove("selectedApps");
            edit.commit();
        }
    }

    private android.view.ViewGroup appSettingsRow(int index) throws Exception {
        androidx.recyclerview.widget.RecyclerView list = (androidx.recyclerview.widget.RecyclerView)field(screen, "appList");
        getInstrumentation().runOnMainSync(() -> list.scrollToPosition(index));
        for (int i = 0; i < 100; i++) {
            getInstrumentation().waitForIdleSync();
            androidx.recyclerview.widget.RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(index);
            if (holder != null && !list.isComputingLayout()) return (android.view.ViewGroup)holder.itemView;
            android.os.SystemClock.sleep(20);
        }
        throw new AssertionError("App row did not appear: " + index);
    }

    public void testYouTubeSavedShortcutsTitleNavigation() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        String previous = prefs.getString("youtubeCards", null);
        try {
            prefs.edit().putString("youtubeCards", "[{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\",\"title\":\"Test Video\"}]").commit();
            open();
            select(8); // YouTube
            Button[] tabs = (Button[]) field(screen, "tabs");
            View detail = (View) field(screen, "detail");
            View titleBtn = findText(detail, AppLanguage.get(com.sva.launcher.R.string.ui_title));
            assertNotNull("Title button should exist in saved shortcuts", titleBtn);
            View addBtn = (View) field(screen, "youtubeAddButton");
            assertNotNull("Add to saved button should exist", addBtn);

            getInstrumentation().runOnMainSync(() -> addBtn.requestFocus());
            getInstrumentation().waitForIdleSync();
            assertTrue(addBtn.hasFocus());

            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            getInstrumentation().waitForIdleSync();
            assertTrue("Title button should receive focus when pressing DOWN from Add button", titleBtn.hasFocus());

            remote(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
            getInstrumentation().waitForIdleSync();
            View artBtn = findText(detail, AppLanguage.get(com.sva.launcher.R.string.ui_art));
            assertNotNull(artBtn);
            assertTrue("Art button should receive focus when pressing RIGHT from Title", artBtn.hasFocus());

            remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
            getInstrumentation().waitForIdleSync();
            assertTrue("Title button should receive focus when pressing LEFT from Art", titleBtn.hasFocus());

            remote(android.view.KeyEvent.KEYCODE_DPAD_LEFT);
            getInstrumentation().waitForIdleSync();
            assertTrue("Parent YouTube tab should receive focus when pressing LEFT from Title", tabs[8].hasFocus());
        } finally {
            if (previous != null) prefs.edit().putString("youtubeCards", previous).commit();
            else prefs.edit().remove("youtubeCards").commit();
            close();
        }
    }

    public void testEveryNavigationCategoryHasVisibilityAndOrder() throws Exception {
        android.content.SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        java.util.Map<String, ?> previous = prefs.getAll();
        java.util.List<String> keys = new java.util.ArrayList<>();
        keys.add(NavigationPreferences.ORDER); keys.add("liveTvNavigationPosition");
        for (int tab = 1; tab <= 6; tab++) keys.add(NavigationPreferences.hiddenKey(tab));
        try {
            android.content.SharedPreferences.Editor clean = prefs.edit();
            for (String key : keys) clean.remove(key);
            clean.commit();
            open(); select(13);
            for (int tab = 1; tab <= 6; tab++) {
                final int selected = tab;
                View toggle = ((View) field(screen, "detail")).findViewWithTag("navigation-visible-" + tab);
                assertNotNull("Every category needs a visibility control", toggle);
                getInstrumentation().runOnMainSync(() -> {
                    try {
                        Method change = MainActivity.class.getDeclaredMethod("selectTab", int.class, boolean.class);
                        change.setAccessible(true); change.invoke(activity, selected, false);
                        toggle.performClick();
                    } catch (Exception e) { throw new AssertionError(e); }
                });
                assertTrue(prefs.getBoolean(NavigationPreferences.hiddenKey(tab), false));
                assertFalse(barOrder().contains(tab));
                assertEquals(0, ((Integer) field(activity, "activeTab")).intValue());
                assertEquals("Hidden categories must keep their position", 6,
                        NavigationPreferences.order(prefs.getString(NavigationPreferences.ORDER, null), 0).size());
                getInstrumentation().runOnMainSync(toggle::performClick);
                assertTrue(barOrder().contains(tab));
            }
            chooseFirstPosition(6); chooseFirstPosition(5);
            assertEquals("news,activities,live_tv,games,events,saved", prefs.getString(NavigationPreferences.ORDER, ""));
            for (int tab = 4; tab <= 6; tab++) {
                View toggle = ((View) field(screen, "detail")).findViewWithTag("navigation-visible-" + tab);
                getInstrumentation().runOnMainSync(toggle::performClick);
            }
            close(); open(); select(13);
            assertEquals(java.util.Arrays.asList(0, 1, 2, 3), barOrder());
            for (int tab = 4; tab <= 6; tab++) assertTrue(prefs.getBoolean(NavigationPreferences.hiddenKey(tab), false));
            assertEquals("news,activities,live_tv,games,events,saved", prefs.getString(NavigationPreferences.ORDER, ""));
        } finally {
            close();
            android.content.SharedPreferences.Editor restore = prefs.edit();
            for (String key : keys) {
                Object value = previous.get(key);
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else restore.remove(key);
            }
            restore.commit();
        }
    }

    private void chooseFirstPosition(int tab) throws Exception {
        View row = ((View)field(screen, "detail")).findViewWithTag("navigation-order-" + tab);
        getInstrumentation().runOnMainSync(() -> { row.requestFocus(); row.performClick(); });
        getInstrumentation().waitForIdleSync();
        for (int attempt = 0; attempt < 100 && dialog.getWindow().getDecorView().hasWindowFocus(); attempt++) {
            android.os.SystemClock.sleep(20);
            getInstrumentation().waitForIdleSync();
        }
        for (int i = 0; i < 6; i++) getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
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
