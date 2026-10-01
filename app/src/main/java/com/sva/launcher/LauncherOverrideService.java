package com.sva.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/** Reopens Sva when the TV's built-in launcher handles the Home button. */
public final class LauncherOverrideService extends AccessibilityService {
    private static final long REOPEN_THROTTLE_MS = 1200L;
    private static final String[] STOCK_LAUNCHERS = {
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            "com.google.android.leanbacklauncher.recommendations"
    };
    private long lastReopenAt;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence packageName = event.getPackageName();
        if (packageName == null || !isStockLauncher(packageName.toString())) return;

        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastReopenAt < REOPEN_THROTTLE_MS) return;
        lastReopenAt = now;
        Intent home = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
    }

    private boolean isStockLauncher(String packageName) {
        for (String candidate : STOCK_LAUNCHERS) {
            if (candidate.equals(packageName)) return true;
        }
        return false;
    }

    @Override public void onInterrupt() { }
}
