package com.sva.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;

/** Delayed work must not flash indicators, steal focus, or revive detached UI. */
public final class TaskProgressTest extends InstrumentationTestCase {
    private GameLibraryActivity activity;
    private LinearLayout host;
    private TaskProgress progress;
    private Button focus;

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity = (GameLibraryActivity) getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(), GameLibraryActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Field buttonField = GameLibraryActivity.class.getDeclaredField("addFiles");
        buttonField.setAccessible(true);
        focus = (Button) buttonField.get(activity);
        getInstrumentation().runOnMainSync(() -> {
            host = new LinearLayout(activity);
            host.setOrientation(LinearLayout.VERTICAL);
            progress = new TaskProgress(activity, false);
            host.addView(progress);
            activity.addContentView(host, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            focus.requestFocus();
        });
        getInstrumentation().waitForIdleSync();
    }

    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(activity::finish);
        getInstrumentation().waitForIdleSync(); super.tearDown();
    }

    private boolean visible() {
        boolean[] shown = new boolean[1];
        getInstrumentation().runOnMainSync(() -> shown[0] = progress.getChildAt(0).isShown());
        return shown[0];
    }

    private void waitForDelay() {
        SystemClock.sleep(450);
        getInstrumentation().waitForIdleSync();
    }

    public void testFastCompletionNeverRevealsAndKeepsFootprint() {
        int height = progress.getHeight();
        getInstrumentation().runOnMainSync(() -> {
            progress.setBusy(true, "Working");
            assertFalse(progress.getChildAt(0).isShown());
            progress.setBusy(false, "Done");
        });
        waitForDelay();
        assertFalse(visible());
        assertEquals(height, progress.getHeight());
        getInstrumentation().runOnMainSync(() -> assertSame(focus, activity.getCurrentFocus()));
    }

    public void testLongTaskShowsWithoutTakingFocusAndClearsOnCompletion() {
        getInstrumentation().runOnMainSync(() -> progress.setBusy(true, "Working"));
        waitForDelay(); assertTrue(visible());
        getInstrumentation().runOnMainSync(() -> {
            assertSame(focus, activity.getCurrentFocus());
            progress.setBusy(false, "Done");
            assertFalse(progress.getChildAt(0).isShown());
        });
    }

    public void testHiddenAncestorStopsIndicatorAndReopeningShowsActiveWork() {
        getInstrumentation().runOnMainSync(() -> progress.setBusy(true, "Working"));
        waitForDelay(); assertTrue(visible());
        getInstrumentation().runOnMainSync(() -> {
            host.setVisibility(View.GONE);
            assertEquals(View.INVISIBLE, progress.getChildAt(0).getVisibility());
        });
        waitForDelay(); assertFalse(visible());
        getInstrumentation().runOnMainSync(() -> host.setVisibility(View.VISIBLE));
        assertTrue(visible());
    }

    public void testDetachCancelsPendingRevealAndReattachRestoresCurrentState() {
        getInstrumentation().runOnMainSync(() -> {
            progress.setBusy(true, "Working");
            host.removeView(progress);
        });
        waitForDelay(); assertFalse(visible());
        getInstrumentation().runOnMainSync(() -> host.addView(progress));
        getInstrumentation().waitForIdleSync(); assertTrue(visible());
        getInstrumentation().runOnMainSync(() -> {
            host.removeView(progress);
            progress.setBusy(false, "Done");
            host.addView(progress);
        });
        waitForDelay(); assertFalse(visible());
    }

    public void testCompletionOfOneTaskDoesNotAffectAnother() {
        TaskProgress[] other = new TaskProgress[1];
        getInstrumentation().runOnMainSync(() -> {
            other[0] = new TaskProgress(activity, true);
            host.addView(other[0]);
            other[0].setBusy(true, "Other task");
            progress.setBusy(true, "Working");
        });
        waitForDelay();
        getInstrumentation().runOnMainSync(() -> {
            progress.setBusy(false, "Done");
            assertTrue(other[0].getChildAt(0).isShown());
        });
    }

    public void testGameImportKeepsStatusUntilReloadAndRejectsDuplicateAction() throws Exception {
        Context target = getInstrumentation().getTargetContext();
        File workspace = new File(target.getCacheDir(), "loader-import-" + System.nanoTime());
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return workspace; }
        };
        GameRepository repository = new GameRepository(isolated);
        Field repositoryField = GameLibraryActivity.class.getDeclaredField("repository");
        repositoryField.setAccessible(true);
        Field busyField = GameLibraryActivity.class.getDeclaredField("importing"); busyField.setAccessible(true);
        Field progressField = GameLibraryActivity.class.getDeclaredField("importProgress"); progressField.setAccessible(true);
        // An unreadable fixture fails safely without modifying the real game catalogue.
        Intent result = TvFilePickerActivity.localFilesResult(Collections.singletonList(new File(workspace, "missing.nes")));
        try {
            synchronized (repository) {
                getInstrumentation().runOnMainSync(() -> {
                    try {
                        repositoryField.set(activity, repository);
                        activity.onActivityResult(7101, Activity.RESULT_OK, result);
                        activity.onActivityResult(7101, Activity.RESULT_OK, result);
                        assertTrue(busyField.getBoolean(activity));
                        progress = (TaskProgress) progressField.get(activity);
                    } catch (Exception e) { throw new AssertionError(e); }
                });
                waitForDelay(); assertTrue(visible());
            }
            long deadline = SystemClock.uptimeMillis() + 5000;
            boolean[] busy = {true};
            while (busy[0] && SystemClock.uptimeMillis() < deadline) {
                getInstrumentation().runOnMainSync(() -> {
                    try { busy[0] = busyField.getBoolean(activity); }
                    catch (Exception e) { throw new AssertionError(e); }
                });
                SystemClock.sleep(20);
            }
            assertFalse("Import must finish and reload even when a file cannot be read", busy[0]);
            assertFalse(visible());
        } finally { delete(workspace); }
    }

    private void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
