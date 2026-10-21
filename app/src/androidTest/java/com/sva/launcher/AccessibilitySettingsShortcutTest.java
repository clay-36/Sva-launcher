package com.sva.launcher;

import android.content.ActivityNotFoundException;
import android.provider.Settings;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Exercises missing and restricted settings activities without changing system packages. */
public final class AccessibilitySettingsShortcutTest extends InstrumentationTestCase {
    public void testDirectLaunchDoesNotFallBackAndClearsPreviousInstructions() {
        getInstrumentation().runOnMainSync(() -> {
            TextView feedback = feedback();
            feedback.setText("Previous failure");
            List<String> actions = new ArrayList<>();
            assertEquals(AccessibilitySettingsShortcut.Destination.ACCESSIBILITY,
                    AccessibilitySettingsShortcut.open(actions::add, feedback));
            assertEquals(Collections.singletonList(Settings.ACTION_ACCESSIBILITY_SETTINGS), actions);
            assertEquals("", feedback.getText().toString());
            assertEquals(View.GONE, feedback.getVisibility());
        });
    }

    public void testMissingAccessibilityActivityFallsBackToGeneralSettings() {
        checkFallback(false);
    }

    public void testRestrictedAccessibilityActivityFallsBackToGeneralSettings() {
        checkFallback(true);
    }

    private void checkFallback(boolean restricted) {
        getInstrumentation().runOnMainSync(() -> {
            TextView feedback = feedback();
            List<String> actions = new ArrayList<>();
            assertEquals(AccessibilitySettingsShortcut.Destination.GENERAL_SETTINGS,
                    AccessibilitySettingsShortcut.open(action -> {
                        actions.add(action);
                        if (Settings.ACTION_ACCESSIBILITY_SETTINGS.equals(action)) throw unavailable(restricted);
                    }, feedback));
            assertEquals(Arrays.asList(Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS), actions);
            assertEquals(View.VISIBLE, feedback.getVisibility());
            assertEquals("Find Accessibility in your TV’s settings and enable Sva Launcher.", feedback.getText().toString());
        });
    }

    public void testBothLaunchesFailLeavesPersistentManualInstructions() {
        getInstrumentation().runOnMainSync(() -> {
            for (boolean firstRestricted : new boolean[]{false, true}) {
                for (boolean secondRestricted : new boolean[]{false, true}) {
                    TextView feedback = feedback();
                    List<String> actions = new ArrayList<>();
                    assertEquals(AccessibilitySettingsShortcut.Destination.UNAVAILABLE,
                            AccessibilitySettingsShortcut.open(action -> {
                                actions.add(action);
                                throw unavailable(actions.size() == 1 ? firstRestricted : secondRestricted);
                            }, feedback));
                    assertEquals(Arrays.asList(Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS), actions);
                    assertEquals(View.VISIBLE, feedback.getVisibility());
                    assertEquals("Open your TV’s Settings manually, find Accessibility, and enable Sva Launcher.", feedback.getText().toString());
                    assertFalse("Instructions must not interrupt D-pad navigation", feedback.isFocusable());
                }
            }
        });
    }

    private TextView feedback() {
        TextView feedback = new TextView(getInstrumentation().getTargetContext());
        feedback.setVisibility(View.GONE);
        return feedback;
    }

    private RuntimeException unavailable(boolean restricted) {
        return restricted ? new SecurityException("Restricted by test") : new ActivityNotFoundException("Missing in test");
    }
}
