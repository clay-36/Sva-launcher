package com.sva.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Reserves its footprint; only the indicator animates, after a short wait on a visible screen. */
final class TaskProgress extends LinearLayout {
    private final LinearLayout content;
    private final TextView label;
    private boolean busy;
    private long startedAt;
    private final Runnable reveal = this::updateVisibility;

    TaskProgress(Context context) { this(context, false); }

    TaskProgress(Context context, boolean compact) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        content = new LinearLayout(context);
        content.setOrientation(compact ? HORIZONTAL : VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setVisibility(INVISIBLE);
        content.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        label = new TextView(context);
        label.setTextSize(12);
        label.setTextColor(LiquidTheme.secondaryText(context));
        label.setTypeface(LiquidTheme.font(context));
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ProgressBar indicator = new ProgressBar(context, null, compact
                ? android.R.attr.progressBarStyleSmall : android.R.attr.progressBarStyleHorizontal);
        indicator.setIndeterminate(true);
        indicator.setIndeterminateTintList(ColorStateList.valueOf(LiquidTheme.accent(context)));
        indicator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (!compact) content.addView(label, new LayoutParams(-1, dp(22)));
        content.addView(indicator, new LayoutParams(compact ? dp(14) : -1, compact ? dp(14) : dp(4)));
        addView(content, new LayoutParams(compact ? dp(20) : -1, compact ? dp(16) : dp(32)));
    }

    void setBusy(boolean value, String message) {
        label.setText(message);
        content.setContentDescription(message);
        if (value && !busy) startedAt = SystemClock.uptimeMillis();
        busy = value;
        updateVisibility();
    }

    private int dp(int value) { return LiquidTheme.dp(getContext(), value); }

    private void updateVisibility() {
        if (content == null) return;
        removeCallbacks(reveal);
        if (!busy || !isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE) {
            content.setVisibility(INVISIBLE);
            return;
        }
        long remaining = 300 - (SystemClock.uptimeMillis() - startedAt);
        if (remaining > 0) {
            content.setVisibility(INVISIBLE);
            postDelayed(reveal, remaining);
        } else content.setVisibility(VISIBLE);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateVisibility(); }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(reveal);
        content.setVisibility(INVISIBLE);
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); updateVisibility();
    }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility); updateVisibility();
    }
}
