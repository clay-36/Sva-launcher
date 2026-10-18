package com.sva.launcher;

import android.app.Activity;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Simulates a network response arriving after its activity and executor have shut down. */
public final class LifecycleWorkTest extends InstrumentationTestCase {
    private Activity activity;
    private LifecycleWork work;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity = getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("lifecycleWork");
        field.setAccessible(true); work = (LifecycleWork) field.get(activity);
    }

    @Override protected void tearDown() throws Exception {
        work.close(); worker.shutdownNow();
        getInstrumentation().runOnMainSync(() -> { if (!activity.isDestroyed()) activity.finish(); });
        getInstrumentation().waitForIdleSync(); super.tearDown();
    }

    public void testLateResponseCannotRedrawOrSubmitAfterShutdown() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        AtomicInteger applied = new AtomicInteger();
        work.execute(worker, () -> {
            entered.countDown();
            boolean released = false;
            while (!released) {
                try { release.await(); released = true; }
                catch (InterruptedException ignored) { /* Network IO can outlive shutdownNow. */ }
            }
            work.post(() -> { applied.incrementAndGet(); work.execute(worker, applied::incrementAndGet); });
            finished.countDown();
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            getInstrumentation().runOnMainSync(() -> { activity.finish(); worker.shutdownNow(); });
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (!activity.isDestroyed() && android.os.SystemClock.uptimeMillis() < deadline) {
                android.os.SystemClock.sleep(20);
            }
            getInstrumentation().waitForIdleSync();
            assertTrue("Activity did not finish destroying", activity.isDestroyed());
            assertFalse(work.active());
        } finally { release.countDown(); }
        assertTrue(finished.await(5, TimeUnit.SECONDS)); getInstrumentation().waitForIdleSync();
        assertEquals(0, applied.get());
    }

    public void testQueuedUiResultChecksLifecycleWhenDelivered() {
        AtomicInteger applied = new AtomicInteger();
        getInstrumentation().runOnMainSync(() -> {
            work.post(applied::incrementAndGet);
            work.close();
        });
        getInstrumentation().waitForIdleSync(); assertEquals(0, applied.get());
    }

    public void testShutdownExecutorRejectsNoNewWork() {
        AtomicInteger applied = new AtomicInteger();
        worker.shutdownNow();
        work.execute(worker, applied::incrementAndGet);
        assertEquals(0, applied.get());
    }

    public void testLiveResultStillRuns() {
        AtomicInteger applied = new AtomicInteger();
        work.post(applied::incrementAndGet);
        getInstrumentation().waitForIdleSync(); assertEquals(1, applied.get());
    }
}
