package com.sva.launcher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

/** Unrelated UI tests start after onboarding; disclosure tests exercise onboarding separately. */
final class TestActivities {
    private TestActivities() { }

    static Activity start(Instrumentation instrumentation, Intent intent) {
        if (intent.getComponent() != null && MainActivity.class.getName().equals(intent.getComponent().getClassName())) {
            instrumentation.getTargetContext().getSharedPreferences("launcher", 0).edit()
                    .putBoolean("accessibilityDisclosureShownV1", true).commit();
        }
        return instrumentation.startActivitySync(intent);
    }
}
