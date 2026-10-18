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
    private final android.text.TextPaint text = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private float gradientHeight = -1f;
    private Shader gradientShader;
    private final List<String> urls = new ArrayList<>();
    private Bitmap current, previous;
    private final LinkedHashMap<String, Bitmap> photoWindow = new LinkedHashMap<>();
    private List<String> sceneKeys = new ArrayList<>();
    private ScreensaverArt.Scene artScene, outgoingScene;
    private ScreensaverArt art;
    private float outgoingAge, outgoingTime;
    private String currentKey = "";
    private final Runnable releaseOutgoing = this::releaseOutgoing;
    private long changedAt, startedAt;
    private int index, frame, theme;
    private boolean stopped, loading, started;
    private String status = "", caption;
    private long clockMinute = -1;
    private String clockLabel = "", dateLabel = "";
    private String weatherLabel = "", weatherDetail = "", eventLabel = "", eventDetail = "";
    private final Runnable next = this::loadNext;
    private final Runnable redraw = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            invalidate();
            main.postDelayed(this, android.animation.ValueAnimator.areAnimatorsEnabled() ? 40 : 1000);
        }
    };

    ScreensaverView(Context context) {
        this(context, context.getSharedPreferences("launcher", Context.MODE_PRIVATE), new ScreensaverPhotoRepository(context));
    }

    ScreensaverView(Context context, SharedPreferences preferences, ScreensaverPhotoRepository repository) {
        super(context);
        prefs = preferences;
        photos = repository;
        theme = ScreensaverPreferences.themeIndex(prefs.getString(ScreensaverPreferences.THEME, "cinema"));
        caption = prefs.getString(ScreensaverPreferences.CAPTION, "").trim();
        text.setTypeface(LiquidTheme.font(context));
        art = new ScreensaverArt(LiquidTheme.font(context), caption);
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
            LinkedHashMap<String, Bitmap> saved = new LinkedHashMap<>();
            int desired = collageTheme() ? 3 : 1;
            for (String url : new LinkedHashSet<>(cached)) {
                Bitmap bitmap = photos.loadCached(url);
                if (bitmap != null) saved.put(url, bitmap);
                if (saved.size() >= desired || Thread.currentThread().isInterrupted()) break;
            }
            final boolean hasSavedPhoto = !saved.isEmpty();
            if (!hasSavedPhoto) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap fallback = BitmapFactory.decodeResource(getResources(), R.drawable.mountain_wallpaper, options);
                if (fallback != null) saved.put("__fallback__", fallback);
            }
            main.post(() -> {
                if (stopped) { for (Bitmap bitmap : saved.values()) bitmap.recycle(); return; }
                photoWindow.putAll(saved);
                sceneKeys = new ArrayList<>(saved.keySet());
                if (sceneKeys.isEmpty()) return;
                currentKey = sceneKeys.get(0);
                current = photoWindow.get(currentKey);
                artScene = makeScene();
                changedAt = SystemClock.uptimeMillis();
                status = hasSavedPhoto ? "" : album.isEmpty()
                        ? "Add a Google Photos album in Screensaver settings" : "Loading your photographs…";
                if (hasSavedPhoto) frame = 1;
                if (!cached.isEmpty()) {
                    setUrls(cached);
                    if (hasSavedPhoto) scheduleNext(); else loadNext();
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
    private boolean collageTheme() { return theme == 1 || theme == 3 || theme == 7 || theme == 8; }

    private ScreensaverArt.Scene makeScene() {
        List<Bitmap> images = new ArrayList<>();
        for (String key : sceneKeys) images.add(photoWindow.get(key));
        return new ScreensaverArt.Scene(images);
    }

    private void setUrls(List<String> source) {
        urls.clear(); urls.addAll(new LinkedHashSet<>(source));
        Collections.shuffle(urls);
        index = 0;
    }

    private void scheduleNext() {
        main.removeCallbacks(next);
        int target = collageTheme() ? Math.min(3, urls.size()) : 1;
        long delay = sceneKeys.size() < target ? 2000L
                : ScreensaverPreferences.interval(prefs.getInt(ScreensaverPreferences.INTERVAL, 20)) * 1000L;
        main.postDelayed(next, delay);
    }

    private void loadNext() {
        if (stopped || loading || urls.isEmpty()) return;
        List<String> candidates = ScreensaverPhotoWindow.candidates(urls, photoWindow.keySet(), currentKey, index);
        if (candidates.isEmpty()) { scheduleNext(); return; }
        releaseOutgoing();
        String first = candidates.get(0);
        if (photoWindow.containsKey(first)) {
            switchTo(first, photoWindow.get(first));
            return;
        }
        List<String> downloads = new ArrayList<>();
        for (String url : candidates) if (!photoWindow.containsKey(url)) downloads.add(url);
        loading = true;
        worker.execute(() -> {
            Bitmap bitmap = null;
            String loadedKey = "";
            // Existing photos need no worker decode; try only new candidates in this batch.
            for (String url : downloads) {
                bitmap = photos.load(url);
                if (bitmap != null) { loadedKey = url; break; }
                if (Thread.currentThread().isInterrupted()) break;
            }
            Bitmap loaded = bitmap;
            String key = loadedKey;
            main.post(() -> {
                loading = false;
                if (stopped) { if (loaded != null) loaded.recycle(); return; }
                if (loaded != null) switchTo(key, loaded);
                else {
                    index = (index + candidates.size()) % urls.size();
                    if (frame == 0) status = "Photos unavailable · showing landscape wallpaper";
                    scheduleNext();
                }
            });
        });
    }

    private void switchTo(String key, Bitmap bitmap) {
        releaseOutgoing();
        long now = SystemClock.uptimeMillis();
        outgoingAge = (now - changedAt) / 1000f;
        outgoingTime = (now - startedAt) / 1000f;
        outgoingScene = artScene;
        previous = current;
        photoWindow.put(key, bitmap);
        currentKey = key;
        current = bitmap;
        List<String> companions = new ArrayList<>(sceneKeys);
        companions.remove("__fallback__");
        sceneKeys = ScreensaverPhotoWindow.sceneKeys(key, companions, collageTheme() ? 3 : 1);
        artScene = makeScene();
        changedAt = now;
        frame++;
        status = "";
        int position = urls.indexOf(key);
        if (position >= 0) index = (position + 1) % urls.size();
        main.postDelayed(releaseOutgoing, android.animation.ValueAnimator.areAnimatorsEnabled() ? 1850 : 0);
        scheduleNext();
    }

    private void releaseOutgoing() {
        main.removeCallbacks(releaseOutgoing);
        outgoingScene = null;
        previous = null;
        Iterator<Map.Entry<String, Bitmap>> iterator = photoWindow.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Bitmap> entry = iterator.next();
            if (!sceneKeys.contains(entry.getKey())) { entry.getValue().recycle(); iterator.remove(); }
        }
    }

    void stop() {
        if (stopped) return;
        stopped = true;
        // Worker result callbacks must still run their stopped branch to recycle decoded photos.
        main.removeCallbacks(redraw);
        main.removeCallbacks(next);
        main.removeCallbacks(releaseOutgoing);
        worker.shutdownNow();
        for (Bitmap bitmap : photoWindow.values()) bitmap.recycle();
        photoWindow.clear();
        current = previous = null;
        artScene = outgoingScene = null;
        art.dispose();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (current == null || stopped) return;
        float w = getWidth(), h = getHeight();
        float age = (SystemClock.uptimeMillis() - changedAt) / 1000f;
        float time = (SystemClock.uptimeMillis() - startedAt) / 1000f;
        float fade = android.animation.ValueAnimator.areAnimatorsEnabled() ? Math.min(1f, age / 1.8f) : 1f;
        if (previous != null && fade < 1) {
            if (theme == 0) scene(canvas, previous, w, h, outgoingAge, outgoingTime, Math.max(0, frame - 1));
            else art.draw(canvas, theme, outgoingScene, w, h, outgoingAge, outgoingTime,
                    Math.max(0, frame - 1), android.animation.ValueAnimator.areAnimatorsEnabled());
        }
        int layer = canvas.saveLayerAlpha(0, 0, w, h, Math.round(255 * fade));
        if (theme == 0) scene(canvas, current, w, h, age, time, frame);
        else art.draw(canvas, theme, artScene, w, h, age, time, frame,
                android.animation.ValueAnimator.areAnimatorsEnabled());
        canvas.restoreToCount(layer);
        boolean paperTheme = theme == 2 || theme == 7;
        if (theme == 0) {
            if (gradientHeight != h || gradientShader == null) {
                gradientHeight = h;
                gradientShader = new LinearGradient(0, h * .7f, 0, h, Color.TRANSPARENT,
                        0x99000000, Shader.TileMode.CLAMP);
            }
            paint.setShader(gradientShader);
            canvas.drawRect(0, h * .7f, w, h, paint); paint.setShader(null);
        }
        drawInformation(canvas, w, h, time, paperTheme);
        if (!status.isEmpty()) write(canvas, status, w * .05f, h * (theme == 0 ? .96f : .985f), h * .021f, paperTheme ? 0xCC3D3931 : 0xCCFFFFFF, Typeface.NORMAL);
    }
    private void drawInformation(Canvas canvas, float w, float h, float time, boolean paperTheme) {
        boolean showClock = prefs.getBoolean(ScreensaverPreferences.CLOCK, true);
        boolean showWeather = prefs.getBoolean(ScreensaverPreferences.WEATHER, false);
        boolean showEvents = prefs.getBoolean(ScreensaverPreferences.EVENTS, false);
        int count = (showClock ? 1 : 0) + (showWeather ? 1 : 0) + (showEvents ? 1 : 0);
        if (count == 0) return;
        long now = System.currentTimeMillis();
        long minute = now / 60_000L;
        if (minute != clockMinute) {
            clockMinute = minute;
            clockLabel = new SimpleDateFormat(android.text.format.DateFormat.is24HourFormat(getContext())
                    ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date(now));
            dateLabel = new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date(now));
            updateInformation(now);
        }
        float drift = (float) Math.sin(time / 35) * w * .008f;
        float columnWidth = w * .9f / count;
        float x = w * .05f + drift;
        int foreground = paperTheme ? 0xEE3D3931 : 0xEEF4F7F5;
        int secondary = paperTheme ? 0xCC3D3931 : 0xCCDCE4E1;
        if (showClock) {
            informationColumn(canvas, clockLabel,
                    dateLabel,
                    x, h, columnWidth, foreground, secondary);
            x += columnWidth;
        }
        if (showWeather) {
            informationColumn(canvas, weatherLabel, weatherDetail, x, h, columnWidth, foreground, secondary);
            x += columnWidth;
        }
        if (showEvents) informationColumn(canvas, eventLabel, eventDetail, x, h, columnWidth, foreground, secondary);
    }

    private void informationColumn(Canvas canvas, String title, String detail, float x, float h,
                                   float width, int foreground, int secondary) {
        canvas.save();
        canvas.clipRect(x, h * .83f, x + width - getWidth() * .025f, h * (theme == 0 ? .935f : .96f));
        // Ellipsize within each column so long locations and event titles remain separate.
        float right = x + width - getWidth() * .025f;
        informationText(canvas, title, x, h * (theme == 0 ? .885f : .9f), h * .033f, foreground, right - x);
        informationText(canvas, detail, x, h * (theme == 0 ? .925f : .945f), h * .021f, secondary, right - x);
        canvas.restore();
    }

    private void informationText(Canvas canvas, String value, float x, float y, float size, int color, float width) {
        text.setTextSize(size);
        text.setColor(color);
        text.setTypeface(LiquidTheme.font(getContext()));
        String fitted = android.text.TextUtils.ellipsize(value, text,
                Math.max(1, width), android.text.TextUtils.TruncateAt.END).toString();
        canvas.drawText(fitted, x, y, text);
    }

    private void updateInformation(long now) {
        weatherLabel = "Weather unavailable";
        weatherDetail = "Set up weather in Launcher Settings";
        try {
            WeatherRepository.Forecast forecast = WeatherRepository.Forecast.fromJson(
                    new org.json.JSONObject(prefs.getString("weatherCache", "")));
            if (forecast != null) {
                boolean fahrenheit = prefs.getBoolean("weatherFahrenheit", false);
                double temperature = fahrenheit ? WeatherRepository.fahrenheit(forecast.temperature) : forecast.temperature;
                weatherLabel = Math.round(temperature) + (fahrenheit ? "°F" : "°C")
                        + " · " + WeatherRepository.condition(forecast.code);
                weatherDetail = forecast.location;
            }
        } catch (Exception ignored) { }
        eventLabel = "No upcoming events";
        eventDetail = "Your calendar";
        try {
            org.json.JSONArray events = new org.json.JSONArray(prefs.getString("publicCalendarEvents", "[]"));
            org.json.JSONObject nextEvent = null;
            long earliest = Long.MAX_VALUE;
            for (int i = 0; i < events.length(); i++) {
                org.json.JSONObject event = events.optJSONObject(i);
                if (event == null || event.optLong("end", 0) <= now) continue;
                long start = event.optLong("start", Long.MAX_VALUE);
                if (start < earliest) { earliest = start; nextEvent = event; }
            }
            if (nextEvent != null) {
                eventLabel = nextEvent.optString("title", "Calendar event").replaceAll("\\s+", " ");
                boolean allDay = nextEvent.optBoolean("allDay", false);
                eventDetail = (earliest <= now ? "Now · " : "Up next · ")
                        + new SimpleDateFormat(allDay ? "EEE, d MMM" : "EEE · h:mm a", Locale.getDefault())
                        .format(new Date(earliest)) + (allDay ? " · All day" : "");
            }
        } catch (Exception ignored) { }
    }

    private void scene(Canvas c, Bitmap photo, float w, float h, float age, float time, int number) {
        boolean motion = android.animation.ValueAnimator.areAnimatorsEnabled();
        float zoom = motion ? 1f + Math.min(.06f, age * .002f) : 1f;
        RectF full = new RectF(0, 0, w, h);
        paint.setColor(Color.WHITE); paint.setAlpha(255); paint.setColorFilter(null);
        if (theme == 0) {
            picture(c, photo, full, true, zoom, null);
            cinemaEdgeShadow(c, w, h);
            if (!caption.isEmpty()) write(c, caption, w * .05f, h * .8f, h * .045f, Color.WHITE, Typeface.NORMAL);
        }
    }
    private void cinemaEdgeShadow(Canvas canvas, float width, float height) {
        // Independent straight edge fades keep the centre clear without an oval vignette.
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        paint.setShader(new LinearGradient(0, 0, width, 0,
                new int[]{0x99000000, Color.TRANSPARENT, Color.TRANSPARENT, 0x99000000},
                new float[]{0f, .22f, .78f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{0x77000000, Color.TRANSPARENT, Color.TRANSPARENT, 0x55000000},
                new float[]{0f, .2f, .8f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
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
}
