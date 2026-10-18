package com.sva.launcher;

import android.app.Activity;
import android.os.Handler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/** Gates activity-owned work and results; shutdown cannot make a late result revive the UI. */
final class LifecycleWork {
    private final Activity activity;
    private final Handler handler;
    private volatile boolean closed;

    LifecycleWork(Activity activity, Handler handler) {
        this.activity = activity;
        this.handler = handler;
    }

    boolean active() { return !closed && !activity.isFinishing() && !activity.isDestroyed(); }

    void execute(ExecutorService executor, Runnable task) {
        if (!active() || executor.isShutdown()) return;
        try {
            executor.execute(() -> { if (active()) task.run(); });
        } catch (RejectedExecutionException failure) {
            // A shutdown between the check and submission is expected; other rejection is a bug.
            if (active() && !executor.isShutdown()) throw failure;
        }
    }

    void post(Runnable result) {
        if (active()) handler.post(() -> { if (active()) result.run(); });
    }

    void close() { closed = true; }
}
