package com.sva.launcher;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.widget.TextView;
import java.lang.reflect.Method;

/** Checks that dismissal and decline cannot grant consent, and agreeing can. */
public final class AccessibilityDisclosureTest extends InstrumentationTestCase {
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
