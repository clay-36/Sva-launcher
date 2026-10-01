package com.sva.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Crisp rim and specular highlight over a wallpaper-aligned frosted surface. */
final class GlassDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final Context context;
    private final GlassBackdrop backdrop;
    private final int tint, border;
    private final float radius, stroke;
    private int alpha = 255;
    private final int[] location = new int[2];

    GlassDrawable(Context context, GlassBackdrop backdrop, int tint, int border, int stroke, int radius) {
        this.context = context;
        this.backdrop = backdrop;
        this.tint = tint;
        this.border = border;
        this.stroke = LiquidTheme.dp(context, stroke);
        this.radius = LiquidTheme.dp(context, radius);
        if (backdrop != null) backdrop.register(this);
    }

    @Override public void draw(Canvas canvas) {
        rect.set(getBounds());
        rect.inset(Math.max(1, stroke) / 2f, Math.max(1, stroke) / 2f);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        Drawable.Callback owner = getCallback();
        while (owner instanceof Drawable) owner = ((Drawable) owner).getCallback();
        if (backdrop != null && owner instanceof View) {
            View view = (View) owner;
            view.getLocationOnScreen(location);
            backdrop.draw(canvas, paint, location[0], location[1],
                    context.getResources().getDisplayMetrics().widthPixels,
                    context.getResources().getDisplayMetrics().heightPixels);
        }
        paint.setColor(tint);
        paint.setAlpha(Color.alpha(tint) * alpha / 255);
        canvas.drawRect(rect, paint);
        paint.setAlpha(alpha);
        paint.setShader(new LinearGradient(0, rect.top, rect.right, rect.bottom,
                new int[]{0x26FFFFFF, 0x03FFFFFF, 0x080B1525}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(rect, paint);
        canvas.restoreToCount(save);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, stroke));
        paint.setColor(border);
        paint.setAlpha(Color.alpha(border) * alpha / 255);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{0xB3FFFFFF, 0x08FFFFFF, 0x30FFFFFF}, null, Shader.TileMode.CLAMP));
        paint.setStrokeWidth(LiquidTheme.dp(context, .7f));
        paint.setAlpha(alpha);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);
    }

    @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), radius); }
    @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
