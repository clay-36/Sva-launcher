package com.deo.launcher;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A small wallpaper-only blur, shared by surfaces; never reads the window or video. */
final class GlassBackdrop {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<WeakReference<GlassDrawable>> surfaces = new ArrayList<>();
    private Bitmap current, outgoing;
    private ValueAnimator transition;
    private float fraction = 1f;
    private int generation;
    private boolean closed;

    void register(GlassDrawable drawable) { surfaces.add(new WeakReference<>(drawable)); }

    void prepare(Bitmap source, boolean animate, Runnable ready) {
        int request = ++generation;
        worker.execute(() -> {
            Bitmap blurred = blur(source);
            main.post(() -> {
                if (closed || request != generation) { blurred.recycle(); return; }
                if (transition != null) transition.cancel();
                outgoing = current;
                current = blurred;
                boolean moving = animate && outgoing != null && ValueAnimator.areAnimatorsEnabled();
                fraction = moving ? 0f : 1f;
                ready.run();
                if (moving) {
                    transition = ValueAnimator.ofFloat(0f, 1f);
                    transition.setDuration(900);
                    transition.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                    transition.addUpdateListener(value -> {
                        fraction = (float) value.getAnimatedValue();
                        if (fraction == 1f) recycleOutgoing();
                        invalidate();
                    });
                    transition.start();
                } else {
                    recycleOutgoing();
                    invalidate();
                }
            });
        });
    }

    void draw(Canvas canvas, Paint paint, int x, int y, int width, int height) {
        if (outgoing != null && fraction < 1f) drawBitmap(canvas, paint, outgoing, x, y, width, height, 255);
        if (current != null) drawBitmap(canvas, paint, current, x, y, width, height, Math.round(255 * fraction));
        paint.setAlpha(255);
    }

    private void drawBitmap(Canvas canvas, Paint paint, Bitmap image, int x, int y, int width, int height, int alpha) {
        float scale = Math.max((float) width / image.getWidth(), (float) height / image.getHeight());
        float left = (width - image.getWidth() * scale) / 2f - x;
        float top = (height - image.getHeight() * scale) * .30f - y;
        paint.setAlpha(alpha);
        canvas.drawBitmap(image, null, new RectF(left, top, left + image.getWidth() * scale,
                top + image.getHeight() * scale), paint);
    }

    void invalidate() {
        for (int i = surfaces.size() - 1; i >= 0; i--) {
            GlassDrawable drawable = surfaces.get(i).get();
            if (drawable == null) surfaces.remove(i);
            else drawable.invalidateSelf();
        }
    }

    void close() {
        closed = true;
        generation++;
        if (transition != null) transition.cancel();
        worker.shutdownNow();
        if (current != null) current.recycle();
        if (outgoing != null && outgoing != current) outgoing.recycle();
        current = outgoing = null;
        surfaces.clear();
    }

    private void recycleOutgoing() {
        if (outgoing != null && outgoing != current) outgoing.recycle();
        outgoing = null;
    }

    private static Bitmap blur(Bitmap source) {
        float scale = Math.min(1f, 192f / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap small = Bitmap.createScaledBitmap(source, width, height, true);
        int[] pixels = new int[width * height];
        int[] temp = new int[pixels.length];
        small.getPixels(pixels, 0, width, 0, 0, width, height);
        // Three separable box passes approximate a Gaussian with bounded memory.
        for (int pass = 0; pass < 3; pass++) {
            box(pixels, temp, width, height, true);
            box(temp, pixels, width, height, false);
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static void box(int[] source, int[] target, int width, int height, boolean horizontal) {
        int radius = 3;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int r = 0, g = 0, b = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int sx = horizontal ? Math.max(0, Math.min(width - 1, x + offset)) : x;
                int sy = horizontal ? y : Math.max(0, Math.min(height - 1, y + offset));
                int color = source[sy * width + sx];
                r += (color >> 16) & 255; g += (color >> 8) & 255; b += color & 255;
            }
            target[y * width + x] = 0xFF000000 | ((r / 7) << 16) | ((g / 7) << 8) | (b / 7);
        }
    }
}
