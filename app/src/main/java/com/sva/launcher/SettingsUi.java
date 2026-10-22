package com.sva.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Shared, remote-first controls used only by Sva's settings screens. */
final class SettingsUi {
    private SettingsUi() { }
    static int dp(Context c, int n) { return LiquidTheme.dp(c, n); }
    static int background(Context c) { return LiquidTheme.isLight(c) ? 0xFFF2F4F7 : 0xFF11151C; }
    static int surface(Context c) { return LiquidTheme.isLight(c) ? Color.WHITE : 0xFF1B212B; }
    static int raised(Context c) { return LiquidTheme.isLight(c) ? 0xFFE5EDF4 : 0xFF293443; }
    static int text(Context c) { return LiquidTheme.isLight(c) ? 0xFF17212F : 0xFFF2F5FA; }
    static int muted(Context c) { return LiquidTheme.isLight(c) ? 0xFF546273 : 0xFFAFBAC9; }
    static Drawable shape(Context c, int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill);
        d.setCornerRadius(dp(c, radius)); d.setStroke(dp(c, width), stroke); return d;
    }
    static Drawable states(Context c, boolean selected) {
        return states(c, selected, false);
    }
    static Drawable navigationStates(Context c, boolean selected) {
        return states(c, selected, true);
    }
    private static Drawable states(Context c, boolean selected, boolean navigation) {
        StateListDrawable d = new Motion.States();
        d.addState(new int[]{android.R.attr.state_focused}, shape(c, raised(c), LiquidTheme.accent(c), 2, 12));
        d.addState(new int[]{android.R.attr.state_pressed}, shape(c, raised(c), LiquidTheme.accent(c), 2, 12));
        d.addState(new int[]{}, shape(c, selected ? raised(c) : navigation ? Color.TRANSPARENT : surface(c), Color.TRANSPARENT, 0, 12));
        return d;
    }
    static TextView text(Context c, String value, int size, boolean secondary) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size);
        t.setTypeface(LiquidTheme.font(c)); t.setTextColor(secondary ? muted(c) : text(c));
        return t;
    }
    static void styleButton(Button b) {
        Context c = b.getContext(); b.setAllCaps(false); b.setTextSize(16);
        b.setTypeface(LiquidTheme.font(c)); b.setTextColor(text(c));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(c, 56)); b.setMinimumHeight(dp(c, 56));
        b.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END); b.setBackground(states(c, false));
        b.setFocusable(true); b.setFocusableInTouchMode(false); b.setOnFocusChangeListener(null);
        b.setElevation(0); Motion.interaction(b, true);
    }
    static Button action(Context c, String label, Runnable click) {
        return action(c, label, false, click);
    }
    static Button action(Context c, String label, boolean destructive, Runnable click) {
        Button b = new Button(c); styleButton(b); b.setText(label);
        if (click != null) b.setOnClickListener(v -> click.run());
        if (destructive)
            b.setTextColor(LiquidTheme.isLight(c) ? 0xFFAA2635 : 0xFFFFA8AB);
        return b;
    }
    static void add(LinearLayout parent, View child) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(parent.getContext(), 8), dp(parent.getContext(), 3), dp(parent.getContext(), 8), dp(parent.getContext(), 5));
        parent.addView(child, p);
    }
    static void heading(LinearLayout parent, String title) {
        TextView t = text(parent.getContext(), title, 18, false);
        t.setPadding(dp(parent.getContext(), 10), dp(parent.getContext(), 20), dp(parent.getContext(), 10), dp(parent.getContext(), 10));
        parent.addView(t);
    }
    static void hint(LinearLayout parent, String value) {
        TextView t = text(parent.getContext(), value, 14, true);
        t.setLineSpacing(dp(parent.getContext(), 3), 1);
        t.setPadding(dp(parent.getContext(), 10), dp(parent.getContext(), 4), dp(parent.getContext(), 10), dp(parent.getContext(), 12));
        parent.addView(t);
    }
    static void styleInput(EditText field, boolean multiline) {
        Context c = field.getContext(); field.setTextSize(16); field.setTypeface(LiquidTheme.font(c));
        field.setTextColor(text(c)); field.setHintTextColor(muted(c)); field.setBackground(states(c, false));
        field.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        field.setMinHeight(dp(c, multiline ? 112 : 56));
        int variation = field.getInputType() & android.text.InputType.TYPE_MASK_VARIATION;
        if (multiline || variation == android.text.InputType.TYPE_TEXT_VARIATION_URI) {
            field.setTextDirection(View.TEXT_DIRECTION_LTR);
            field.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }
    }
    static View toggle(Context c, String title, boolean checked, java.util.function.Consumer<Boolean> save) {
        LinearLayout row = row(c); TextView label = text(c, title, 16, false);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(c); toggle.setChecked(checked);
        toggle.setFocusable(false); toggle.setClickable(false); toggle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        toggle.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{LiquidTheme.accent(c), muted(c)}));
        toggle.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{LiquidTheme.accent(c) & 0x66FFFFFF, muted(c) & 0x55FFFFFF}));
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        row.setContentDescription(AppLanguage.get(R.string.ui_label_state, title, checked ? AppLanguage.get(R.string.ui_on) : AppLanguage.get(R.string.ui_off)));
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Switch.class.getName()); info.setCheckable(true); info.setChecked(toggle.isChecked());
            }
        });
        row.setOnClickListener(v -> {
            boolean next = !toggle.isChecked(); toggle.setChecked(next); save.accept(next);
            row.setContentDescription(AppLanguage.get(R.string.ui_label_state, title, next ? AppLanguage.get(R.string.ui_on) : AppLanguage.get(R.string.ui_off)));
        });
        return row;
    }
    static View choice(Context c, String title, String[] options, int selected, IntConsumer save) {
        LinearLayout row = row(c); TextView label = text(c, title, 16, false);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(c, options[selected], 14, true);
        value.setGravity(Gravity.END); value.setMaxLines(2);
        row.addView(value, new LinearLayout.LayoutParams(dp(c, 160), -2));
        row.setContentDescription(title + ", " + options[selected]);
        row.setOnClickListener(v -> {
            int current = 0;
            for (int i = 0; i < options.length; i++) if (options[i].contentEquals(value.getText())) current = i;
            AlertDialog dialog = new AlertDialog.Builder(c, LiquidTheme.alertDialogTheme(c))
                    .setTitle(title).setSingleChoiceItems(options, current, (d, which) -> {
                        value.setText(options[which]); row.setContentDescription(title + ", " + options[which]);
                        save.accept(which); d.dismiss(); row.requestFocus();
                    }).setNegativeButton(AppLanguage.get(R.string.ui_cancel), null).create();
            dialog.setOnDismissListener(d -> row.requestFocus()); Motion.dialog(dialog); dialog.show();
            dialog.getListView().setSelection(current);
        });
        return row;
    }
    private static LinearLayout row(Context c) {
        LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        row.setMinimumHeight(dp(c, 64)); row.setBackground(states(c, false));
        Motion.interaction(row, true);
        row.setFocusable(true); row.setClickable(true); row.setDescendantFocusability(LinearLayout.FOCUS_BLOCK_DESCENDANTS);
        return row;
    }
    static LinearLayout page(Activity a, String title, String subtitle, Runnable back) {
        LinearLayout page = new LinearLayout(a); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(a, 40), dp(a, 24), dp(a, 40), dp(a, 24)); page.setBackgroundColor(background(a));
        LinearLayout header = new LinearLayout(a); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false); header.setClipToPadding(false);
        header.addView(text(a, title, 28, false), new LinearLayout.LayoutParams(0, -2, 1));
        Button backButton = action(a, AppLanguage.get(R.string.ui_back), back);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(a, 112), dp(a, 52));
        backParams.setMargins(dp(a, 4), dp(a, 4), dp(a, 4), dp(a, 4));
        header.addView(backButton, backParams);
        page.addView(header); hint(page, subtitle); return page;
    }
    static LinearLayout scroll(LinearLayout parent) {
        ScrollView scroll = new ScrollView(parent.getContext()); scroll.setFillViewport(false);
        scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = new LinearLayout(parent.getContext()); content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false); content.setClipToPadding(false);
        content.setPadding(dp(parent.getContext(), 8), dp(parent.getContext(), 2), dp(parent.getContext(), 8), dp(parent.getContext(), 8));
        scroll.addView(content); parent.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); return content;
    }
    /** Small code-drawn category marks replace font-dependent symbol glyphs. */
    static Drawable icon(Context c, int category) {
        return new Drawable() {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas) {
                canvas.save(); canvas.translate(getBounds().left, getBounds().top);
                canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
                p.setColor(muted(c)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f);
                switch (category) {
                    case 0: case 8:
                        canvas.drawRoundRect(2, 5, 22, 19, 3, 3, p);
                        canvas.drawLine(10, 9, 16, 12, p); canvas.drawLine(16, 12, 10, 15, p); canvas.drawLine(10, 15, 10, 9, p); break;
                    case 1: case 13:
                        for (int y = 6; y <= 18; y += 6) {
                            canvas.drawCircle(4, y, 1, p); canvas.drawLine(9, y, 21, y, p);
                        } break;
                    case 2:
                        canvas.drawRoundRect(3, 3, 10, 10, 2, 2, p); canvas.drawRoundRect(14, 3, 21, 10, 2, 2, p);
                        canvas.drawRoundRect(3, 14, 10, 21, 2, 2, p); canvas.drawRoundRect(14, 14, 21, 21, 2, 2, p); break;
                    case 3:
                        canvas.drawRoundRect(3, 3, 21, 21, 3, 3, p); canvas.drawCircle(8, 8, 1.5f, p);
                        canvas.drawLine(4, 18, 11, 11, p); canvas.drawLine(11, 11, 20, 19, p); break;
                    case 4:
                        canvas.drawCircle(12, 12, 9, p); canvas.drawLine(12, 6, 12, 12, p); canvas.drawLine(12, 12, 16, 15, p); break;
                    case 5:
                        canvas.drawRoundRect(5, 10, 19, 21, 2, 2, p); canvas.drawArc(8, 2, 16, 16, 180, 180, false, p);
                        canvas.drawCircle(12, 15, 1, p); break;
                    case 6:
                        canvas.drawLine(2, 11, 12, 3, p); canvas.drawLine(12, 3, 22, 11, p);
                        canvas.drawLine(5, 9, 5, 21, p); canvas.drawLine(5, 21, 19, 21, p); canvas.drawLine(19, 21, 19, 9, p); break;
                    case 7:
                        canvas.drawLine(7, 19, 7, 4, p); canvas.drawLine(3, 8, 7, 4, p); canvas.drawLine(7, 4, 11, 8, p);
                        canvas.drawLine(17, 5, 17, 20, p); canvas.drawLine(13, 16, 17, 20, p); canvas.drawLine(17, 20, 21, 16, p); break;
                    case 9:
                        canvas.drawRoundRect(3, 5, 21, 21, 2, 2, p); canvas.drawLine(3, 10, 21, 10, p);
                        canvas.drawLine(8, 2, 8, 8, p); canvas.drawLine(16, 2, 16, 8, p); break;
                    case 10:
                        canvas.drawArc(4, 4, 20, 20, 35, 290, false, p);
                        canvas.drawLine(20, 5, 20, 11, p); canvas.drawLine(20, 11, 14, 11, p); break;
                    case 11:
                        canvas.drawRoundRect(2, 6, 22, 18, 5, 5, p); canvas.drawLine(6, 12, 12, 12, p);
                        canvas.drawLine(9, 9, 9, 15, p); canvas.drawCircle(17, 11, 1, p); break;
                    case 12:
                        canvas.drawCircle(12, 12, 4, p);
                        for (int i = 0; i < 8; i++) {
                            double angle = i * Math.PI / 4;
                            canvas.drawLine(12 + (float)Math.cos(angle) * 7, 12 + (float)Math.sin(angle) * 7,
                                    12 + (float)Math.cos(angle) * 10, 12 + (float)Math.sin(angle) * 10, p);
                        } break;
                    case 14:
                        canvas.drawCircle(12, 12, 9, p);
                        canvas.drawOval(8, 3, 16, 21, p);
                        canvas.drawLine(3, 12, 21, 12, p);
                        break;
                }
                canvas.restore();
            }
            @Override public void setAlpha(int a) { p.setAlpha(a); }
            @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
            @Override public int getIntrinsicWidth() { return dp(c, 20); }
            @Override public int getIntrinsicHeight() { return dp(c, 20); }
        };
    }
}
