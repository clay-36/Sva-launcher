package com.sva.launcher;

import android.app.Activity;
import android.os.Bundle;

/** Window transitions are limited to UI screens; video and game surfaces retain their own lifecycle. */
public abstract class MotionActivity extends LocalizedActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(state == null && Motion.enabled() ? R.anim.motion_enter : 0, 0);
    }
    @Override public void finish() {
        super.finish();
        overridePendingTransition(0, Motion.enabled() ? R.anim.motion_exit : 0);
    }
    @Override protected void onDestroy() {
        Motion.cancelTree(getWindow().getDecorView()); super.onDestroy();
    }
}
