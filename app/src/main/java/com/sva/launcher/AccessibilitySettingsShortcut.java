package com.sva.launcher;

import android.content.ActivityNotFoundException;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/** Opens the best available settings screen without assuming an OEM menu layout. */
final class AccessibilitySettingsShortcut {
    enum Destination { ACCESSIBILITY, GENERAL_SETTINGS, UNAVAILABLE }

    interface Launcher { void start(String action); }

    private AccessibilitySettingsShortcut() { }

    static Destination open(Launcher launcher, TextView feedback) {
        if (tryStart(launcher, Settings.ACTION_ACCESSIBILITY_SETTINGS)) {
            feedback.setText("");
            feedback.setVisibility(View.GONE);
            return Destination.ACCESSIBILITY;
        }
        if (tryStart(launcher, Settings.ACTION_SETTINGS)) {
            String instructions = "Find Accessibility in your TV’s settings and enable Sva Launcher.";
            feedback.setText(instructions);
            feedback.setVisibility(View.VISIBLE);
            Toast.makeText(feedback.getContext(), instructions, Toast.LENGTH_LONG).show();
            return Destination.GENERAL_SETTINGS;
        }
        feedback.setText("Open your TV’s Settings manually, find Accessibility, and enable Sva Launcher.");
        feedback.setVisibility(View.VISIBLE);
        return Destination.UNAVAILABLE;
    }

    private static boolean tryStart(Launcher launcher, String action) {
        try {
            launcher.start(action);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
}
