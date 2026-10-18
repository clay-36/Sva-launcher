package com.sva.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.test.InstrumentationTestCase;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;

/** Exercises interruptions and cleanup, not the exact shape of intermediate animation frames. */
public final class MotionTest extends InstrumentationTestCase {
    private Activity activity;
    private FrameLayout parent, panel;
    private Button control;
    private String originalScale;
    private boolean originalEnabled;
    @Override protected void setUp() throws Exception {
        super.setUp();
        originalScale = Settings.Global.getString(getInstrumentation().getTargetContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE);
        originalEnabled = Motion.enabled();
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS");
        scale("1", true);
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().runOnMainSync(() -> {
            parent = (FrameLayout)activity.getWindow().getDecorView();
            panel = new FrameLayout(activity); control = new Button(activity); control.setText("Initial");
            panel.addView(control, new FrameLayout.LayoutParams(300, 100));
            parent.addView(panel, new FrameLayout.LayoutParams(400, 180)); control.requestFocus();
        }); getInstrumentation().waitForIdleSync();
    }
    @Override protected void tearDown() throws Exception {
        try {
            if (activity != null) getInstrumentation().runOnMainSync(activity::finish);
            scale(originalScale, originalEnabled);
        } finally { getInstrumentation().getUiAutomation().dropShellPermissionIdentity(); super.tearDown(); }
    }
    private void scale(String value, boolean expected) {
        Settings.Global.putString(getInstrumentation().getTargetContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, value);
        long end = SystemClock.uptimeMillis() + 5000;
        while (Motion.enabled() != expected && SystemClock.uptimeMillis() < end) SystemClock.sleep(20);
        assertEquals("System animation preference was not observed", expected, Motion.enabled());
    }
    private void settled() { SystemClock.sleep(Motion.SCREEN + 120); getInstrumentation().waitForIdleSync(); }
    private void stopped(View view) throws Exception {
        Object state = view.getTag(R.id.motion_state); if (state == null) return;
        for (String name : new String[]{"fade", "ghostAnimation", "ghost", "overlay"}) {
            Field field = state.getClass().getDeclaredField(name); field.setAccessible(true); assertNull(name + " retained after cleanup", field.get(state));
        }
    }
    public void testInterruptedHideKeepsLatestVisibleControlAndFocus() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            Motion.hide(panel); Motion.show(panel, true); Motion.hide(panel); Motion.show(panel, true);
            Motion.reveal(panel); Motion.show(panel, true); control.requestFocus();
        }); settled();
        assertEquals(View.VISIBLE, panel.getVisibility()); assertEquals(1f, panel.getAlpha(), .001f); assertTrue(control.hasFocus()); stopped(panel);
        getInstrumentation().runOnMainSync(() -> Motion.hide(panel)); settled();
        assertEquals(View.GONE, panel.getVisibility()); assertEquals(1f, panel.getAlpha(), .001f); stopped(panel);
    }
    public void testCrossfadeUpdatesImmediatelyAndRemovesOldLayers() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            Motion.crossfade(panel, () -> control.setText("First"));
            Motion.crossfade(panel, () -> control.setText("Latest"));
            assertEquals("Latest", control.getText().toString());
        }); settled(); stopped(panel); assertEquals(1f, panel.getAlpha(), .001f);
        getInstrumentation().runOnMainSync(() -> {
            Motion.crossfade(panel, () -> control.setText("Detached")); parent.removeView(panel);
        }); stopped(panel); assertEquals(1f, panel.getAlpha(), .001f);
        getInstrumentation().runOnMainSync(() -> { parent.addView(panel, new FrameLayout.LayoutParams(400, 180)); control.requestFocus(); });
        assertTrue(control.hasFocus()); assertEquals("Detached", control.getText().toString());
    }
    public void testDisabledAnimationsFinishImmediately() throws Exception {
        scale("0", false);
        getInstrumentation().runOnMainSync(() -> {
            Motion.hide(panel); assertEquals(View.GONE, panel.getVisibility());
            Motion.show(panel, true); assertEquals(View.VISIBLE, panel.getVisibility());
            Motion.crossfade(panel, () -> control.setText("No motion")); Motion.reveal(panel);
            assertEquals("No motion", control.getText().toString()); assertEquals(1f, panel.getAlpha(), .001f);
            Motion.interaction(control, true); control.requestFocus();
        }); stopped(panel);
    }
    public void testRapidTabSwitchingLeavesOneVisibleFocusedPage() throws Exception {
        Method select = MainActivity.class.getDeclaredMethod("selectTab", int.class, boolean.class); select.setAccessible(true);
        Field pagesField = MainActivity.class.getDeclaredField("tabPages"); pagesField.setAccessible(true);
        getInstrumentation().runOnMainSync(() -> {
            try { for (int tab : new int[]{1,2,3,4,0,1,4,0}) select.invoke(activity, tab, true); }
            catch (Exception e) { throw new AssertionError(e); }
        }); settled();
        View[] pages = (View[])pagesField.get(activity);
        for (int i = 0; i < pages.length; i++) {
            assertEquals(i == 0 ? View.VISIBLE : View.GONE, pages[i].getVisibility());
            assertEquals(1f, pages[i].getAlpha(), .001f); stopped(pages[i]);
        }
        assertNotNull(activity.getCurrentFocus());
    }
}
