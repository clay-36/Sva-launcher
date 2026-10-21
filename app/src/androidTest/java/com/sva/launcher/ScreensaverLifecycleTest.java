package com.sva.launcher;

import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import java.lang.reflect.*;
import java.util.*;

/** Exercises async ownership, overlay combinations and renderer shutdown without network or user preference edits. */
public final class ScreensaverLifecycleTest extends InstrumentationTestCase {
    private ScreensaverView view;
    private SharedPreferences prefs;
    private FakePhotos photos;

    private static final class FakePhotos extends ScreensaverPhotoRepository {
        final List<String> urls;
        final List<Bitmap> allocated = Collections.synchronizedList(new ArrayList<>());
        boolean fail;
        FakePhotos(Context context, int count) {
            super(context); urls = new ArrayList<>();
            for (int i = 0; i < count; i++) urls.add("https://fixture/" + i);
        }
        @Override List<String> cached(String album) { return new ArrayList<>(urls); }
        @Override List<String> refresh(String album, boolean force) { return new ArrayList<>(urls); }
        @Override Bitmap loadCached(String url) { return load(url); }
        @Override Bitmap load(String url) {
            if (fail) return null;
            int index = urls.indexOf(url);
            Bitmap bitmap = Bitmap.createBitmap(index % 2 == 0 ? 320 : 180,
                    index % 2 == 0 ? 180 : 320, Bitmap.Config.RGB_565);
            bitmap.eraseColor(Color.rgb(70 + index * 20, 90, 120));
            allocated.add(bitmap); return bitmap;
        }
    }
    private Object field(String name) throws Exception {
        Field field = ScreensaverView.class.getDeclaredField(name); field.setAccessible(true); return field.get(view);
    }
    private void onMain(Runnable runnable) { getInstrumentation().runOnMainSync(runnable); }
    private void create(int count, String theme) throws Exception {
        Context context = getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences("screensaver-render-tests", Context.MODE_PRIVATE);
        prefs.edit().clear().putString(ScreensaverPreferences.THEME, theme)
                .putString(ScreensaverPreferences.ALBUM, "https://photos.google.com/share/test")
                .putString(ScreensaverPreferences.CAPTION, "A long caption that wraps gracefully. Together, always.")
                .putInt(ScreensaverPreferences.INTERVAL, 60).commit();
        photos = new FakePhotos(context, count);
        onMain(() -> {
            view = new ScreensaverView(context, prefs, photos); view.layout(0,0,1280,720); view.start();
        });
        awaitReady();
    }
    private void awaitReady() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] ready = {false};
            onMain(() -> {
                try { ready[0] = field("current") != null && !(Boolean)field("loading"); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            if (ready[0]) return;
            SystemClock.sleep(30);
        }
        fail("Slideshow did not become ready");
    }
    private void advance() throws Exception {
        onMain(() -> {
            try {
                Method method = ScreensaverView.class.getDeclaredMethod("loadNext");
                method.setAccessible(true); method.invoke(view);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        awaitReady();
    }
    public void testBoundedWindowTransitionsAndCleanup() throws Exception {
        create(8, "photo_weave");
        Bitmap output = Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);
        try {
            for (int iteration = 0; iteration < 12; iteration++) {
                advance();
                onMain(() -> {
                    try {
                        assertTrue(((Map<?,?>)field("photoWindow")).size() <= 4);
                        assertEquals(3, ((List<?>)field("sceneKeys")).size());
                        view.draw(new Canvas(output));
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                SystemClock.sleep(1900);
                onMain(() -> {
                    try { assertTrue(((Map<?,?>)field("photoWindow")).size() <= 3); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
            }
            photos.fail = true;
            advance(); // Failed downloads retain a drawable scene.
            onMain(() -> view.draw(new Canvas(output)));
        } finally { output.recycle(); shutdown(); }
    }
    public void testOverlayCombinationsAcrossAllThemes() throws Exception {
        create(3, "editorial");
        Bitmap output = Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);
        try {
            for (int theme = 0; theme <= 8; theme++) {
                final int selected = theme;
                for (int mask = 0; mask < 8; mask++) {
                    prefs.edit().putBoolean(ScreensaverPreferences.CLOCK,(mask&1)!=0)
                            .putBoolean(ScreensaverPreferences.WEATHER,(mask&2)!=0)
                            .putBoolean(ScreensaverPreferences.EVENTS,(mask&4)!=0).commit();
                    onMain(() -> {
                        try {
                            Field field = ScreensaverView.class.getDeclaredField("theme");
                            field.setAccessible(true); field.setInt(view,selected);
                            Field changed = ScreensaverView.class.getDeclaredField("changedAt");
                            changed.setAccessible(true); changed.setLong(view, SystemClock.uptimeMillis() - 3000L);
                            view.draw(new Canvas(output));
                        } catch (Exception e) { throw new RuntimeException(e); }
                    });
                    assertEquals(255,Color.alpha(output.getPixel(0,0)));
                    if (mask == 7) {
                        java.io.File directory = new java.io.File(getInstrumentation().getTargetContext().getCacheDir(), "screensaver-overlay-qa");
                        assertTrue(directory.isDirectory() || directory.mkdirs());
                        try (java.io.FileOutputStream file = new java.io.FileOutputStream(new java.io.File(directory,
                                ScreensaverPreferences.THEMES[theme] + ".png"))) {
                            assertTrue(output.compress(Bitmap.CompressFormat.PNG, 100, file));
                        }
                    }
                }
            }
        } finally { output.recycle(); shutdown(); }
    }
    public void testSinglePhotoDoesNotLoadDuplicatesAndStopDuringLoad() throws Exception {
        create(1,"floating_prints");
        try {
            advance(); advance();
            assertEquals(1,photos.allocated.size());
        } finally { shutdown(); }
        create(8,"darkroom");
        onMain(() -> {
            try {
                Method next = ScreensaverView.class.getDeclaredMethod("loadNext"); next.setAccessible(true); next.invoke(view);
                view.stop();
            } catch(Exception e) { throw new RuntimeException(e); }
        });
        SystemClock.sleep(300);
        shutdown();
    }
    public void testOfflineFallbackAndPendingStartupCleanup() throws Exception {
        create(0,"prism");
        Bitmap fallback = (Bitmap)field("current");
        shutdown();
        assertTrue(fallback.isRecycled());

        Context context = getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences("screensaver-render-tests", Context.MODE_PRIVATE);
        prefs.edit().clear().putString(ScreensaverPreferences.THEME,"floating_prints").commit();
        photos = new FakePhotos(context,3);
        onMain(() -> {
            view = new ScreensaverView(context,prefs,photos);
            view.start();
            // Keep the UI thread occupied until a worker-owned startup result is queued.
            long deadline = SystemClock.uptimeMillis() + 2000;
            while (photos.allocated.size() < 3 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(5);
            SystemClock.sleep(30);
            view.stop();
        });
        SystemClock.sleep(100);
        shutdown();
    }
    private void shutdown() throws Exception {
        if(view != null) {
            onMain(() -> { view.stop(); view.stop(); });
            assertTrue(((Map<?,?>)field("photoWindow")).isEmpty());
            SystemClock.sleep(100);
            synchronized(photos.allocated) { for(Bitmap bitmap:photos.allocated)assertTrue(bitmap.isRecycled()); }
            view = null;
        }
        if(prefs != null)prefs.edit().clear().commit();
    }
}
