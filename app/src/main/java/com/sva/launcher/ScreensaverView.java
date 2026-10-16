package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared renderer for the Android dream and full-screen preview. */
final class ScreensaverView extends View {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScreensaverPhotoRepository photos;
    private final SharedPreferences prefs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<String> urls = new ArrayList<>();
    private Bitmap current, previous;
    private long changedAt, startedAt;
    private int index, frame, theme;
    private boolean stopped, loading, started;
    private final Shader vignette = new RadialGradient(0, 0, 1,
            new int[]{0x00000000, 0x00000000, 0x66000000, 0xCC000000},
            new float[]{0f, .48f, .82f, 1f}, Shader.TileMode.CLAMP);
    private String status = "", caption;
    private long clockMinute = -1;
    private String clockLabel = "";
    private final Runnable next = this::loadNext;
    private final Runnable redraw = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            invalidate();
            main.postDelayed(this, android.animation.ValueAnimator.areAnimatorsEnabled() ? 40 : 1000);
        }
    };

    ScreensaverView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        photos = new ScreensaverPhotoRepository(context);
        theme = ScreensaverPreferences.themeIndex(prefs.getString(ScreensaverPreferences.THEME, "cinema"));
        caption = prefs.getString(ScreensaverPreferences.CAPTION, "").trim();
        text.setTypeface(LiquidTheme.font(context));
        setBackgroundColor(Color.BLACK);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void start() {
        if (started || stopped) return;
        started = true;
        startedAt = SystemClock.uptimeMillis();
        main.post(redraw);
        String album = ScreensaverPreferences.album(prefs);
        worker.execute(() -> {
            List<String> cached = photos.cached(album);
            Collections.shuffle(cached);
            Bitmap saved = null;
            for (String url : cached) {
                saved = photos.loadCached(url);
                if (saved != null || Thread.currentThread().isInterrupted()) break;
            }
            final boolean hasSavedPhoto = saved != null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap fallback = hasSavedPhoto ? saved : BitmapFactory.decodeResource(getResources(), R.drawable.mountain_wallpaper, options);
            main.post(() -> {
                if (stopped) { if (fallback != null) fallback.recycle(); return; }
                current = fallback;
                changedAt = SystemClock.uptimeMillis();
                status = hasSavedPhoto ? "" : album.isEmpty()
                        ? "Add a Google Photos album in Screensaver settings" : "Loading your photographs…";
                if (hasSavedPhoto) frame = 1;
                if (!cached.isEmpty()) {
                    setUrls(cached);
                    if (hasSavedPhoto) main.postDelayed(next,
                            ScreensaverPreferences.interval(prefs.getInt(ScreensaverPreferences.INTERVAL, 20)) * 1000L);
                    else loadNext();
                }
            });
            if (album.isEmpty()) return;
            try {
                List<String> fresh = photos.refresh(album, false);
                main.post(() -> {
                    if (stopped) return;
                    setUrls(fresh);
                    if (!loading && frame == 0) loadNext();
                });
            } catch (Exception ignored) {
                main.post(() -> {
                    if (!stopped) status = frame > 0 ? "" : "Album unavailable · showing landscape wallpaper";
                });
            }
        });
    }
    private void setUrls(List<String> source) {
        urls.clear(); urls.addAll(source);
        Collections.shuffle(urls);
        index = 0;
    }
    private void loadNext() {
        if (stopped || loading || urls.isEmpty()) return;
        loading = true;
        // Try a bounded set so a broken image cannot stall the whole album.
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < Math.min(3, urls.size()); i++) candidates.add(urls.get((index + i) % urls.size()));
        worker.execute(() -> {
            Bitmap bitmap = null;
            int attempts = 0;
            for (String url : candidates) {
                attempts++;
                bitmap = photos.load(url);
                if (bitmap != null || Thread.currentThread().isInterrupted()) break;
            }
            Bitmap loaded = bitmap;
            int consumed = attempts;
            main.post(() -> {
                loading = false;
                if (stopped) { if (loaded != null) loaded.recycle(); return; }
                index = (index + consumed) % Math.max(1, urls.size());
                if (loaded != null) {
                    if (previous != null) previous.recycle();
                    previous = current;
                    current = loaded;
                    changedAt = SystemClock.uptimeMillis();
                    frame++;
                    status = "";
                } else if (frame == 0) status = "Photos unavailable · showing landscape wallpaper";
                main.removeCallbacks(next);
                main.postDelayed(next, ScreensaverPreferences.interval(prefs.getInt(ScreensaverPreferences.INTERVAL, 20)) * 1000L);
            });
        });
    }
    void stop() {
        stopped = true;
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        if (current != null) current.recycle();
        if (previous != null) previous.recycle();
        current = previous = null;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (current == null || stopped) return;
        float w = getWidth(), h = getHeight();
        float age = (SystemClock.uptimeMillis() - changedAt) / 1000f;
        float time = (SystemClock.uptimeMillis() - startedAt) / 1000f;
        float fade = android.animation.ValueAnimator.areAnimatorsEnabled() ? Math.min(1f, age / 1.8f) : 1f;
        if (previous != null && fade < 1) scene(canvas, previous, w, h, 0, time, Math.max(0, frame - 1));
        int layer = canvas.saveLayerAlpha(0, 0, w, h, Math.round(255 * fade));
        scene(canvas, current, w, h, age, time, frame);
        canvas.restoreToCount(layer);
        boolean paperTheme = theme == 3 || theme == 7;
        paint.setShader(new LinearGradient(0, h * .7f, 0, h, Color.TRANSPARENT,
                paperTheme ? 0x11000000 : 0x99000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * .7f, w, h, paint); paint.setShader(null);
        float drift = (float) Math.sin(time / 35) * w * .008f;
        if (prefs.getBoolean(ScreensaverPreferences.CLOCK, true)) {
            long minute = System.currentTimeMillis() / 60_000L;
            if (minute != clockMinute) {
                clockMinute = minute;
                clockLabel = new SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(getContext())
                        ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date());
            }
            write(canvas, clockLabel, w * .05f + drift, h * .91f, h * .036f, paperTheme ? 0xDD3D3931 : 0xDDEEF4EF, Typeface.NORMAL);
        }
        if (!status.isEmpty()) write(canvas, status, w * .05f, h * .96f, h * .021f, paperTheme ? 0xCC3D3931 : 0xCCFFFFFF, Typeface.NORMAL);
    }
    private void scene(Canvas c, Bitmap photo, float w, float h, float age, float time, int number) {
        boolean motion = android.animation.ValueAnimator.areAnimatorsEnabled();
        float zoom = motion ? 1f + Math.min(.06f, age * .002f) : 1f;
        RectF full = new RectF(0, 0, w, h);
        paint.setColor(Color.WHITE); paint.setAlpha(255); paint.setColorFilter(null);
        if (theme == 0) {
            picture(c, photo, full, true, zoom, null);
            surroundingShadow(c, w, h);
            if (!caption.isEmpty()) write(c, caption, w * .05f, h * .8f, h * .045f, Color.WHITE, Typeface.NORMAL);
        } else if (theme == 1) {
            c.drawColor(0xFF18120C);
            ColorMatrix sepia = new ColorMatrix(new float[]{.393f,.769f,.189f,0,0, .349f,.686f,.168f,0,0, .272f,.534f,.131f,0,0, 0,0,0,1,0});
            picture(c, photo, new RectF(w * .045f, h * .07f, w * .955f, h * .9f), true, zoom, new ColorMatrixColorFilter(sepia));
            paint.setColor(0xBB090705); c.drawRect(0, 0, w, h * .07f, paint); c.drawRect(0, h * .9f, w, h, paint);
            paint.setColor(0xFFAC9B79);
            for (int i = 0; i < 22; i++) {
                float x = w * (i + .3f) / 22;
                c.drawRoundRect(x, h * .02f, x + w * .022f, h * .05f, 3, 3, paint);
                c.drawRoundRect(x, h * .92f, x + w * .022f, h * .95f, 3, 3, paint);
            }
            // Fixed grain pattern with a very gentle projector flicker.
            paint.setColor(0x0CFFE4A6);
            Random grain = new Random(19);
            for (int i = 0; i < 380; i++) c.drawCircle(grain.nextFloat() * w, grain.nextFloat() * h, .7f, paint);
            paint.setColor(Color.argb((int)(4 + 3 * Math.sin(time * 3)), 255, 226, 176)); c.drawRect(full, paint);
            write(c, caption.isEmpty() ? "THE GOOD OLD DAYS" : caption, w * .08f, h * .84f, h * .028f, 0xFFFFE7BC, Typeface.NORMAL);
            write(c, String.format(Locale.ROOT, "%03d", number + 1), w * .9f, h * .98f, h * .019f, 0xFFBBAA87, Typeface.NORMAL);
        } else if (theme == 2) {
            c.drawColor(0xFF0E1319);
            ColorMatrix mono = new ColorMatrix(); mono.setSaturation(.12f);
            RectF portrait = new RectF(w * .36f, h * .1f, w * .94f, h * .84f);
            picture(c, photo, portrait, false, zoom, new ColorMatrixColorFilter(mono));
            for (int i = 0; i < 16; i++) {
                float x = w * ((i * .173f) % 1f);
                float y = h * (1 - ((i * .137f + time * .006f) % 1f));
                paint.setColor(0x22F5D8A3); c.drawCircle(x, y, h * (.002f + i % 3 * .002f), paint);
            }
            write(c, "MEMORY LANE", w * .065f, h * .33f, h * .023f, 0xFFCEB88E, Typeface.NORMAL);
            wrapped(c, caption.isEmpty() ? "Always close.\nForever cherished." : caption,
                    w * .065f, h * .41f, w * .26f, h * .047f, 0xFFF3EEE5);
        } else if (theme == 3) {
            c.drawColor(0xFFE5D9BF);
            paint.setColor(0x227A6147);
            for (int i = 0; i < 30; i++) c.drawLine(0, h * i / 30, w, h * i / 30, paint);
            print(c, previous == null ? photo : previous, new RectF(w * .07f, h * .12f, w * .49f, h * .82f), -7, "little moments");
            print(c, photo, new RectF(w * .43f, h * .1f, w * .92f, h * .85f), 5, caption.isEmpty() ? "a day to remember" : caption);
        } else if (theme == 4) {
            int color = photo.getPixel(photo.getWidth() / 2, photo.getHeight() / 2);
            c.drawColor(Color.rgb(Color.red(color) / 4, Color.green(color) / 4, Color.blue(color) / 4));
            RectF mat = new RectF(w * .14f, h * .08f, w * .86f, h * .84f);
            paint.setColor(0xFFEBE9E0); c.drawRect(mat, paint);
            picture(c, photo, new RectF(mat.left + w * .025f, mat.top + h * .035f, mat.right - w * .025f, mat.bottom - h * .055f), false, 1f, null);
            if (!caption.isEmpty()) write(c, caption, w * .14f, h * .9f, h * .025f, 0xFFE5E7DE, Typeface.NORMAL);
        } else if (theme == 5) {
            c.drawColor(0xFF070F20);
            float sway = motion ? (float)Math.sin(time * .08f) : 0;
            paint.setShader(new RadialGradient(w * (.24f + sway * .08f), h * .25f, w * .62f,
                    new int[]{0xBB226D67, 0x00226D67}, null, Shader.TileMode.CLAMP));
            c.drawRect(full, paint);
            paint.setShader(new RadialGradient(w * (.8f - sway * .06f), h * .7f, w * .55f,
                    new int[]{0xBB695194, 0x00695194}, null, Shader.TileMode.CLAMP));
            c.drawRect(full, paint); paint.setShader(null);
            float lift = motion ? (float)Math.sin(time * .12f) * h * .009f : 0;
            RectF frameBox = new RectF(w * .18f, h * .09f + lift, w * .82f, h * .84f + lift);
            for (int i = 6; i > 0; i--) {
                paint.setColor(Color.argb(8, 160, 225, 229));
                c.drawRoundRect(frameBox.left - i * 3, frameBox.top - i * 3,
                        frameBox.right + i * 3, frameBox.bottom + i * 3, 8, 8, paint);
            }
            picture(c, photo, frameBox, false, 1, null);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f); paint.setColor(0x99C6EFEB);
            c.drawRect(frameBox, paint); paint.setStyle(Paint.Style.FILL);
            if (!caption.isEmpty()) write(c, caption, w * .18f, h * .9f, h * .028f, 0xFFE4EEFA, Typeface.NORMAL);
        } else if (theme == 6) {
            ColorMatrix mono = new ColorMatrix(); mono.setSaturation(0);
            picture(c, photo, full, true, zoom, new ColorMatrixColorFilter(mono));
            surroundingShadow(c, w, h);
            paint.setColor(0xFF06080A);
            c.drawRect(0, 0, w, h * .075f, paint); c.drawRect(0, h * .925f, w, h, paint);
            paint.setShader(new LinearGradient(0, 0, w * .65f, 0, 0xAA000000, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(full, paint); paint.setShader(null);
            if (!caption.isEmpty()) wrapped(c, caption, w * .08f, h * .63f, w * .45f, h * .055f, 0xFFF5F4EE);
        } else if (theme == 7) {
            c.drawColor(0xFFD5CBB8);
            RectF book = new RectF(w * .06f, h * .08f, w * .94f, h * .85f);
            paint.setColor(0x33746148); c.drawRect(book.left + 8, book.top + 10, book.right + 8, book.bottom + 10, paint);
            paint.setColor(0xFFF6F0E2); c.drawRect(book, paint);
            picture(c, photo, new RectF(w * .09f, h * .12f, w * .48f, h * .79f), false, 1, null);
            paint.setShader(new LinearGradient(w * .485f, 0, w * .52f, 0,
                    new int[]{0x007A684C, 0x337A684C, 0x007A684C}, null, Shader.TileMode.CLAMP));
            c.drawRect(w * .485f, book.top, w * .52f, book.bottom, paint); paint.setShader(null);
            write(c, "A MOMENT TO KEEP", w * .56f, h * .28f, h * .018f, 0xFF8C7350, Typeface.NORMAL);
            wrapped(c, caption.isEmpty() ? "Some days\nstay with us." : caption,
                    w * .56f, h * .34f, w * .3f, h * .055f, 0xFF3D3931);
            paint.setColor(0x447E715A); c.drawRect(w * .56f, h * .71f, w * .85f, h * .712f, paint);
            write(c, String.format(Locale.ROOT, "%02d", number + 1), w * .84f, h * .77f, h * .02f, 0xFF8C7350, Typeface.NORMAL);
        } else if (theme == 8) {
            c.drawColor(0xFF101B22);
            for (int i = 0; i < 3; i++) {
                float lift = motion ? (float)Math.sin(time * .1f + i * 1.5f) * h * .012f : 0;
                float left = w * (.07f + i * .3f);
                float top = h * (i == 1 ? .1f : .19f) + lift;
                RectF panel = new RectF(left, top, left + w * .26f, top + h * .6f);
                paint.setColor(0x77000000); c.drawRect(panel.left + 6, panel.top + 9, panel.right + 6, panel.bottom + 9, paint);
                // The centre anchors the current photo; the left panel carries the previous one.
                Bitmap companion = i == 0 && previous != null ? previous : photo;
                picture(c, companion, panel, true, i == 1 ? 1 : 1.12f, null);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); paint.setColor(0x4486B6C0);
                c.drawRect(panel, paint); paint.setStyle(Paint.Style.FILL);
            }
            if (!caption.isEmpty()) write(c, caption, w * .07f, h * .87f, h * .03f, 0xFFE1ECEA, Typeface.NORMAL);
        }
    }
    private void surroundingShadow(Canvas canvas, float width, float height) {
        canvas.save();
        canvas.translate(width / 2, height / 2);
        canvas.scale(width / 2, height / 2);
        paint.setColor(Color.WHITE); paint.setShader(vignette);
        canvas.drawRect(-1, -1, 1, 1, paint);
        paint.setShader(null);
        canvas.restore();
    }
    private void print(Canvas c, Bitmap bitmap, RectF box, float rotation, String label) {
        c.save(); c.rotate(rotation, box.centerX(), box.centerY());
        paint.setColor(0x33000000); c.drawRect(box.left + 7, box.top + 9, box.right + 7, box.bottom + 9, paint);
        paint.setColor(0xFFFFFCF1); c.drawRect(box, paint);
        float margin = box.width() * .045f;
        picture(c, bitmap, new RectF(box.left + margin, box.top + margin, box.right - margin, box.bottom - box.height() * .17f), true, 1, null);
        text.setTextSize(box.height() * .037f);
        text.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        text.setColor(0xFF564A3B);
        String fitted = android.text.TextUtils.ellipsize(label, new android.text.TextPaint(text),
                box.width() - margin * 2, android.text.TextUtils.TruncateAt.END).toString();
        c.drawText(fitted, box.left + margin, box.bottom - box.height() * .055f, text);
        c.restore();
    }
    private void picture(Canvas c, Bitmap bitmap, RectF target, boolean crop, float zoom, ColorMatrixColorFilter filter) {
        c.save(); c.clipRect(target);
        paint.setColor(theme == 7 ? 0xFFF6F0E2 : 0xFF12191B); c.drawRect(target, paint);
        float scale = (crop ? Math.max(target.width()/bitmap.getWidth(), target.height()/bitmap.getHeight())
                : Math.min(target.width()/bitmap.getWidth(), target.height()/bitmap.getHeight())) * zoom;
        RectF destination = new RectF(target.centerX() - bitmap.getWidth()*scale/2, target.centerY() - bitmap.getHeight()*scale/2,
                target.centerX() + bitmap.getWidth()*scale/2, target.centerY() + bitmap.getHeight()*scale/2);
        paint.setColor(Color.WHITE); paint.setColorFilter(filter);
        c.drawBitmap(bitmap, null, destination, paint); paint.setColorFilter(null); c.restore();
    }
    private void write(Canvas c, String value, float x, float y, float size, int color, int style) {
        text.setTextSize(size); text.setColor(color);
        text.setTypeface(style == Typeface.ITALIC ? Typeface.create("serif", style) : LiquidTheme.font(getContext()));
        String fitted = android.text.TextUtils.ellipsize(value, new android.text.TextPaint(text), Math.max(1, getWidth() - x - getWidth() * .05f), android.text.TextUtils.TruncateAt.END).toString();
        c.drawText(fitted, x, y, text);
    }
    private void wrapped(Canvas c, String value, float x, float y, float width, float size, int color) {
        android.text.TextPaint p = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create("serif", Typeface.NORMAL)); p.setColor(color); p.setTextSize(size);
        android.text.StaticLayout layout = android.text.StaticLayout.Builder.obtain(value, 0, value.length(), p, (int)width)
                .setMaxLines(5).setEllipsize(android.text.TextUtils.TruncateAt.END).setLineSpacing(size * .18f, 1).build();
        c.save(); c.translate(x,y); layout.draw(c); c.restore();
    }
}
