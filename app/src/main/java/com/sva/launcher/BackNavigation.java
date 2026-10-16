package com.sva.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.os.Build;
import android.window.OnBackInvokedDispatcher;

/** Modern system Back plus each screen's existing TV remote/pre-33 fallback. */
final class BackNavigation {
    private BackNavigation() { }
    static void install(Activity activity, Runnable action) {
        if (Build.VERSION.SDK_INT >= 33) activity.getOnBackInvokedDispatcher()
                .registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, action::run);
    }
    static void install(Dialog dialog, Runnable action) {
        if (Build.VERSION.SDK_INT >= 33) dialog.getOnBackInvokedDispatcher()
                .registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, action::run);
    }
}
