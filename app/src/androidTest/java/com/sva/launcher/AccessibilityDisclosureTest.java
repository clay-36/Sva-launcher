package com.sva.launcher;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.widget.TextView;
import java.lang.reflect.Method;

/** Checks that dismissal and decline cannot grant consent, and agreeing can. */
public final class AccessibilityDisclosureTest extends InstrumentationTestCase {
    public void testTranslatedDisclosureLayoutAndRemoteDecline() throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("launcher", 0);
        String language = AppLanguage.selected(context);
        String appearance = prefs.getString(LiquidTheme.PREF_MODE, null);
        boolean shown = prefs.getBoolean("accessibilityDisclosureShownV1", false);
        boolean consent = prefs.getBoolean("accessibilityConsentV1", false);
        boolean hadShown = prefs.contains("accessibilityDisclosureShownV1");
        boolean hadConsent = prefs.contains("accessibilityConsentV1");
        getInstrumentation().setInTouchMode(false);
        try {
            for (String mode : new String[]{LiquidTheme.DARK, LiquidTheme.LIGHT}) {
                prefs.edit().putString(LiquidTheme.PREF_MODE, mode).commit();
                for (String tag : AppLanguage.TAGS) {
                    if (tag.isEmpty()) continue;
                    getInstrumentation().runOnMainSync(() -> AppLanguage.select(context, tag));
                    MainActivity home = (MainActivity) TestActivities.start(getInstrumentation(), new Intent(context,
                            MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    AlertDialog[] dialog = new AlertDialog[1];
                    try {
                        getInstrumentation().runOnMainSync(() -> {
                            try {
                                Method method = MainActivity.class.getDeclaredMethod("showAccessibilityDisclosure", TextView.class);
                                method.setAccessible(true); dialog[0] = (AlertDialog) method.invoke(home, new Object[]{null});
                            } catch (Exception e) { throw new AssertionError(e); }
                        });
                        getInstrumentation().waitForIdleSync();
                        android.os.SystemClock.sleep(200);
                        Throwable[] layoutFailure = new Throwable[1];
                        getInstrumentation().runOnMainSync(() -> {
                            try {
                                TextView title = dialog[0].getWindow().getDecorView().findViewWithTag("accessibility-disclosure-title");
                                assertNotNull(title);
                                assertEquals(mode + ": disclosure title must use readable theme text", LiquidTheme.text(home), title.getCurrentTextColor());
                                TextView message = dialog[0].findViewById(android.R.id.message);
                                assertNotNull(message);
                                assertEquals(mode + ": disclosure must use readable theme text", LiquidTheme.text(home), message.getCurrentTextColor());
                                for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
                                    android.widget.Button button = dialog[0].getButton(which);
                                    android.graphics.Rect rect = new android.graphics.Rect();
                                    assertTrue(tag + ": consent button must be visible", button.getGlobalVisibleRect(rect));
                                    // Focus styling scales buttons up, so global bounds may exceed local height.
                                    assertTrue(tag + ": consent button must fit vertically", rect.height() >= button.getHeight() - 1);
                                    assertNotNull(button.getLayout());
                                    for (int line = 0; line < button.getLineCount(); line++)
                                        assertEquals(tag + ": consent label must not be truncated", 0, button.getLayout().getEllipsisCount(line));
                                    assertTrue(tag + ": consent text must fit the button", button.getLayout().getHeight()
                                            <= button.getHeight() - button.getCompoundPaddingTop() - button.getCompoundPaddingBottom());
                                }
                                assertTrue(tag + ": No thanks must receive initial remote focus",
                                        dialog[0].getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus());
                            } catch (Throwable failure) { layoutFailure[0] = failure; }
                        });
                        if (layoutFailure[0] != null) throw new AssertionError(layoutFailure[0]);
                        android.graphics.Bitmap image = getInstrumentation().getUiAutomation().takeScreenshot();
                        assertNotNull(image);
                        java.io.File directory = new java.io.File(context.getCacheDir(), "disclosure-screenshots");
                        directory.mkdirs();
                        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, mode + "-" + tag + ".png"))) {
                            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
                        } finally { image.recycle(); }
                        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
                        getInstrumentation().waitForIdleSync();
                        assertFalse(tag + ": default remote confirmation must decline", prefs.getBoolean("accessibilityConsentV1", false));
                    } finally {
                        getInstrumentation().runOnMainSync(() -> { if (dialog[0] != null) dialog[0].dismiss(); home.finish(); });
                        getInstrumentation().waitForIdleSync();
                    }
                }
            }
        } finally {
            getInstrumentation().runOnMainSync(() -> AppLanguage.select(context, language));
            SharedPreferences.Editor editor = prefs.edit();
            if (appearance == null) editor.remove(LiquidTheme.PREF_MODE); else editor.putString(LiquidTheme.PREF_MODE, appearance);
            if (hadShown) editor.putBoolean("accessibilityDisclosureShownV1", shown); else editor.remove("accessibilityDisclosureShownV1");
            if (hadConsent) editor.putBoolean("accessibilityConsentV1", consent); else editor.remove("accessibilityConsentV1");
            editor.commit();
        }
    }

    public void testFirstLaunchBackAndSettingsReentry() throws Exception {
        SharedPreferences prefs = getInstrumentation().getTargetContext().getSharedPreferences("launcher", 0);
        boolean hadShown = prefs.contains("accessibilityDisclosureShownV1");
        boolean shown = prefs.getBoolean("accessibilityDisclosureShownV1", false);
        boolean hadConsent = prefs.contains("accessibilityConsentV1");
        boolean consent = prefs.getBoolean("accessibilityConsentV1", false);
        prefs.edit().remove("accessibilityDisclosureShownV1").remove("accessibilityConsentV1").commit();
        getInstrumentation().setInTouchMode(false);
        MainActivity activity = (MainActivity) getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            assertDisclosureVisible(true);
            assertTrue(prefs.getBoolean("accessibilityDisclosureShownV1", false));
            assertFalse(prefs.getBoolean("accessibilityConsentV1", false));
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            getInstrumentation().waitForIdleSync();
            assertDisclosureVisible(false);
            assertFalse(prefs.getBoolean("accessibilityConsentV1", false));
            // Follow the actual settings button, rather than invoking the disclosure directly.
            getInstrumentation().runOnMainSync(() -> {
                try {
                    Method show = MainActivity.class.getDeclaredMethod("showSettings");
                    show.setAccessible(true); show.invoke(activity);
                    java.lang.reflect.Field screenField = MainActivity.class.getDeclaredField("settingsScreen");
                    screenField.setAccessible(true); Object screen = screenField.get(activity);
                    java.lang.reflect.Field tabsField = screen.getClass().getDeclaredField("tabs");
                    tabsField.setAccessible(true);
                    ((android.widget.Button[]) tabsField.get(screen))[6].performClick();
                    java.lang.reflect.Field detail = screen.getClass().getDeclaredField("detail");
                    detail.setAccessible(true);
                    android.view.View button = findButton((android.view.View) detail.get(screen),
                            AppLanguage.get(R.string.ui_open_accessibility_settings));
                    assertNotNull(button); button.performClick();
                } catch (Exception e) { throw new AssertionError(e); }
            });
            assertDisclosureVisible(true);
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            getInstrumentation().waitForIdleSync();
            assertDisclosureVisible(false);
            assertFalse("Default remote choice must decline", prefs.getBoolean("accessibilityConsentV1", false));
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);
            SharedPreferences.Editor editor = prefs.edit();
            if (hadShown) editor.putBoolean("accessibilityDisclosureShownV1", shown);
            else editor.remove("accessibilityDisclosureShownV1");
            if (hadConsent) editor.putBoolean("accessibilityConsentV1", consent);
            else editor.remove("accessibilityConsentV1");
            editor.commit();
        }
    }

    private android.view.View findButton(android.view.View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View found = findButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void assertDisclosureVisible(boolean expected) {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        boolean visible;
        do {
            android.view.accessibility.AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            visible = root != null && !root.findAccessibilityNodeInfosByText(
                    AppLanguage.get(R.string.accessibility_disclosure_title)).isEmpty();
            if (visible == expected) return;
            android.os.SystemClock.sleep(100);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        assertEquals("Disclosure visibility", expected, visible);
    }

    public void testConsentRequiresAgreeAndCanBeWithdrawn() throws Exception {
        SharedPreferences prefs = getInstrumentation().getTargetContext()
                .getSharedPreferences("launcher", 0);
        boolean hadShown = prefs.contains("accessibilityDisclosureShownV1");
        boolean shown = prefs.getBoolean("accessibilityDisclosureShownV1", false);
        boolean hadConsent = prefs.contains("accessibilityConsentV1");
        boolean consent = prefs.getBoolean("accessibilityConsentV1", false);
        prefs.edit().putBoolean("accessibilityDisclosureShownV1", true)
                .putBoolean("accessibilityConsentV1", false).commit();
        getInstrumentation().setInTouchMode(false);
        MainActivity activity = (MainActivity) getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            Method show = MainActivity.class.getDeclaredMethod("showAccessibilityDisclosure", TextView.class);
            show.setAccessible(true);
            AlertDialog[] dialog = new AlertDialog[1];
            Runnable open = () -> {
                try { dialog[0] = (AlertDialog) show.invoke(activity, new Object[]{null}); }
                catch (Exception e) { throw new AssertionError(e); }
            };
            getInstrumentation().runOnMainSync(open);
            assertFalse(prefs.getBoolean("accessibilityConsentV1", false));
            getInstrumentation().runOnMainSync(() -> dialog[0].cancel());
            getInstrumentation().waitForIdleSync();
            assertFalse(prefs.getBoolean("accessibilityConsentV1", false));
            getInstrumentation().runOnMainSync(open);
            getInstrumentation().runOnMainSync(() -> dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            getInstrumentation().waitForIdleSync();
            assertTrue(prefs.getBoolean("accessibilityConsentV1", false));
            getInstrumentation().runOnMainSync(open);
            getInstrumentation().runOnMainSync(() -> dialog[0].getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
            getInstrumentation().waitForIdleSync();
            assertFalse(prefs.getBoolean("accessibilityConsentV1", true));
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);
            SharedPreferences.Editor editor = prefs.edit();
            if (hadShown) editor.putBoolean("accessibilityDisclosureShownV1", shown);
            else editor.remove("accessibilityDisclosureShownV1");
            if (hadConsent) editor.putBoolean("accessibilityConsentV1", consent);
            else editor.remove("accessibilityConsentV1");
            editor.commit();
        }
    }
}
