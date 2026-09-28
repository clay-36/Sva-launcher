package com.deo.launcher;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public final class DuoApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private GuideReminders guideReminders;

    @Override public void onCreate() {
        super.onCreate();
        guideReminders = new GuideReminders(this);
        registerActivityLifecycleCallbacks(this);
    }

    GuideReminders guideReminders() { return guideReminders; }

    @Override public void onActivityResumed(Activity activity) { guideReminders.onResumed(activity); }
    @Override public void onActivityPaused(Activity activity) { guideReminders.onPaused(activity); }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
