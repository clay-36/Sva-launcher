package com.sva.launcher;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import android.view.accessibility.AccessibilityEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Exercises the actual service callback without changing system accessibility settings. */
public final class LauncherOverrideServiceTest extends InstrumentationTestCase {
    public void testConsentFilteringThrottleAndRevocation() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("launcher", 0);
        boolean existed = prefs.contains("accessibilityConsentV1");
        boolean previous = prefs.getBoolean("accessibilityConsentV1", false);
        List<Intent> launches = new ArrayList<>();
        LauncherOverrideService service = new LauncherOverrideService();
        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
        attach.setAccessible(true);
        attach.invoke(service, new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) { launches.add(intent); }
        });
        try {
            prefs.edit().remove("accessibilityConsentV1").commit();
            send(service, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.google.android.tvlauncher");
            assertEquals("Previously enabled services must stay inactive without app consent", 0, launches.size());
            prefs.edit().putBoolean("accessibilityConsentV1", true).commit();
            service.onAccessibilityEvent(null);
            send(service, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, null);
            send(service, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.android.settings");
            send(service, AccessibilityEvent.TYPE_VIEW_CLICKED, "com.google.android.tvlauncher");
            assertEquals(0, launches.size());
            send(service, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.google.android.tvlauncher");
            assertEquals(1, launches.size());
            assertEquals(MainActivity.class.getName(), launches.get(0).getComponent().getClassName());
            assertTrue((launches.get(0).getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
            send(service, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.google.android.tvlauncher");
            assertEquals("Repeated events must not create a launch loop", 1, launches.size());
            // Reset only the timing field so revocation is checked independently of throttling.
            java.lang.reflect.Field last = LauncherOverrideService.class.getDeclaredField("lastReopenAt");
            last.setAccessible(true); last.setLong(service, 0);
            prefs.edit().putBoolean("accessibilityConsentV1", false).commit();
            send(service, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.google.android.apps.tv.launcherx");
            assertEquals("Revocation must take effect without restarting the service", 1, launches.size());
            prefs.edit().putBoolean("accessibilityConsentV1", true).commit();
            for (String name : new String[]{"com.google.android.apps.tv.launcherx",
                    "com.google.android.leanbacklauncher", "com.google.android.leanbacklauncher.recommendations"}) {
                last.setLong(service, 0);
                send(service, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, name);
            }
            assertEquals(4, launches.size());
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (existed) editor.putBoolean("accessibilityConsentV1", previous);
            else editor.remove("accessibilityConsentV1");
            editor.commit();
        }
    }

    private void send(LauncherOverrideService service, int type, String name) {
        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setPackageName(name);
        try { service.onAccessibilityEvent(event); }
        finally { event.recycle(); }
    }
}
