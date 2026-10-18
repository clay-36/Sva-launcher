package com.sva.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Crisp rim and specular highlight over a wallpaper-aligned frosted surface. */
final class GlassDrawable extends Drawable {
    private static final int[] SPECULAR_COLORS = new int[]{0x26FFFFFF, 0x03FFFFFF, 0x080B1525};
    private static final int[] RIM_COLORS = new int[]{0xB3FFFFFF, 0x08FFFFFF, 0x30FFFFFF};
    private static java.lang.reflect.Field sCallbackField;
    private static boolean sCallbackFieldAttempted;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final Context context;
    private final GlassBackdrop backdrop;
    private final int tint, border;
    private final float radius, stroke, rimStroke;
    private final int[] location = new int[2];
    private LinearGradient specularShader;
    private LinearGradient rimShader;
    private float lastWidth = -1f, lastHeight = -1f;
    private java.lang.ref.WeakReference<View> cachedView;
    private int alpha = 255;

    GlassDrawable(Context context, GlassBackdrop backdrop, int tint, int border, int stroke, int radius) {
        this.context = context;
        this.backdrop = backdrop;
        this.tint = tint;
        this.border = border;
        this.stroke = LiquidTheme.dp(context, stroke);
        this.radius = LiquidTheme.dp(context, radius);
        this.rimStroke = Math.max(1f, LiquidTheme.dp(context, .7f));
        if (backdrop != null) backdrop.register(this);
    }

    private View resolveView() {
        if (cachedView != null) {
            View v = cachedView.get();
            if (v != null) return v;
        }
        Drawable.Callback owner = getCallback();
        while (owner != null) {
            if (owner instanceof View) {
                View v = (View) owner;
                cachedView = new java.lang.ref.WeakReference<>(v);
                return v;
            }
            if (owner instanceof Drawable) {
                owner = ((Drawable) owner).getCallback();
            } else {
                if (!sCallbackFieldAttempted) {
                    sCallbackFieldAttempted = true;
                    try {
                        sCallbackField = owner.getClass().getDeclaredField("mCallback");
                        sCallbackField.setAccessible(true);
                    } catch (Throwable ignored) {
                        sCallbackField = null;
                    }
                }
                if (sCallbackField != null) {
                    try {
                        owner = (Drawable.Callback) sCallbackField.get(owner);
                    } catch (Throwable ignored) {
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        return null;
    }

    private static void getStableLocation(View view, int[] loc) {
        loc[0] = 0;
        loc[1] = 0;
        View v = view;
        while (v != null) {
            loc[0] += v.getLeft() - v.getScrollX();
            loc[1] += v.getTop() - v.getScrollY();
            android.view.ViewParent parent = v.getParent();
            v = (parent instanceof View) ? (View) parent : null;
        }
    }

    private void updateBounds(Rect bounds) {
        rect.set(bounds);
        rect.inset(Math.max(1, stroke) / 2f, Math.max(1, stroke) / 2f);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        float w = rect.width(), h = rect.height();
        if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight)) {
            lastWidth = w;
            lastHeight = h;
            specularShader = new LinearGradient(0, rect.top, rect.right, rect.bottom,
                    SPECULAR_COLORS, null, Shader.TileMode.CLAMP);
            rimShader = new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    RIM_COLORS, null, Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        updateBounds(bounds);
    }

    @Override public void draw(Canvas canvas) {
        if (lastWidth < 0f || rect.isEmpty()) {
            updateBounds(getBounds());
        }

        if (backdrop != null) {
            int save = canvas.save();
            canvas.clipPath(clip);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            View view = resolveView();
            if (view != null) {
                getStableLocation(view, location);
                backdrop.draw(canvas, paint, location[0], location[1],
                        context.getResources().getDisplayMetrics().widthPixels,
                        context.getResources().getDisplayMetrics().heightPixels);
            }
            canvas.restoreToCount(save);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(tint);
        paint.setAlpha(Color.alpha(tint) * alpha / 255);
        canvas.drawRoundRect(rect, radius, radius, paint);

        if (specularShader != null) {
            paint.setAlpha(alpha);
            paint.setShader(specularShader);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, stroke));
        paint.setColor(border);
        paint.setAlpha(Color.alpha(border) * alpha / 255);
        canvas.drawRoundRect(rect, radius, radius, paint);

        if (rimShader != null) {
            paint.setShader(rimShader);
            paint.setStrokeWidth(rimStroke);
            paint.setAlpha(alpha);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
        }
    }

    @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), radius); }
    @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
