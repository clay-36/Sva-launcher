package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.test.InstrumentationTestCase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Pixel bounds verify alignment, including independent visibility and truncated text. */
public final class ScreensaverOverlayLayoutTest extends InstrumentationTestCase {
    public void testFixedBottomAnchorsAcrossThemesResolutionsAndVisibility() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("screensaver-overlay-layout-test", 0);
        final Throwable[] failure = {null};
        getInstrumentation().runOnMainSync(() -> {
            ScreensaverView view = new ScreensaverView(context, prefs, new ScreensaverPhotoRepository(context));
            try {
                Method draw = ScreensaverView.class.getDeclaredMethod("drawInformation", Canvas.class,
                        float.class, float.class, float.class, boolean.class);
                draw.setAccessible(true);
                for (int height : new int[]{720, 1080}) {
                    int width = height * 16 / 9;
                    view.layout(0, 0, width, height);
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    try {
                        for (int theme = 0; theme < ScreensaverPreferences.THEMES.length; theme++) {
                            set(view, "theme", theme);
                            for (boolean longLabels : new boolean[]{false, true}) {
                                for (int mask = 0; mask < 8; mask++) {
                                    prefs.edit().putBoolean(ScreensaverPreferences.CLOCK, (mask & 1) != 0)
                                            .putBoolean(ScreensaverPreferences.WEATHER, (mask & 2) != 0)
                                            .putBoolean(ScreensaverPreferences.EVENTS, (mask & 4) != 0).commit();
                                    set(view, "clockMinute", System.currentTimeMillis() / 60000L);
                                    set(view, "clockLabel", "10:16 AM"); set(view, "dateLabel", "Wed, 21 Oct");
                                    set(view, "weatherLabel", longLabels ? "Weather in a very long location that must fit in its own column" : "33°C Clear");
                                    set(view, "weatherDetail", "Surat, Gujarat");
                                    set(view, "eventLabel", longLabels ? "A very long upcoming family event title that must never overlap the weather" : "Family gathering");
                                    set(view, "eventDetail", "Up next · Thu, 22 Oct");
                                    bitmap.eraseColor(Color.TRANSPARENT);
                                    draw.invoke(view, new Canvas(bitmap), (float)width, (float)height, 0f, theme == 2 || theme == 7);
                                    int top = (int)(height * .83f), bottom = (int)(height * .96f);
                                    int[] pixels = new int[width * (bottom - top)];
                                    bitmap.getPixels(pixels, 0, width, 0, top, width, bottom - top);
                                    for (int slot = 0; slot < 3; slot++) {
                                        Rect bounds = bounds(pixels, width, bottom - top, slot);
                                        String description = "theme=" + theme + " mask=" + mask + " height=" + height + " slot=" + slot;
                                        if ((mask & (1 << slot)) == 0) {
                                            assertTrue("Disabled slot must stay empty: " + description, bounds.isEmpty());
                                            continue;
                                        }
                                        assertFalse("Overlay missing: " + description, bounds.isEmpty());
                                        float anchor = slot == 0 ? bounds.left : slot == 1 ? bounds.exactCenterX() : bounds.right;
                                        float expected = width * (slot == 0 ? .05f : slot == 1 ? .5f : .95f);
                                        assertEquals("Overlay must keep its bottom anchor: " + description, expected, anchor, 7f);
                                        assertTrue("Text must not overlap adjacent slots: " + description,
                                                bounds.width() <= Math.ceil(width * .275f));
                                    }
                                    android.text.TextPaint text = (android.text.TextPaint)get(view, "text");
                                    assertEquals("Right alignment must not leak into captions", Paint.Align.LEFT, text.getTextAlign());
                                }
                            }
                        }
                    } finally { bitmap.recycle(); }
                }
            } catch (Throwable error) { failure[0] = error; }
            finally { view.stop(); prefs.edit().clear().commit(); }
        });
        if (failure[0] != null) throw new AssertionError("Screensaver overlay alignment failed", failure[0]);
    }

    private Rect bounds(int[] pixels, int width, int height, int slot) {
        int start = width * slot / 3, end = width * (slot + 1) / 3;
        int left = width, right = -1, top = height, bottom = -1;
        for (int y = 0; y < height; y++) for (int x = start; x < end; x++) {
            if (Color.alpha(pixels[y * width + x]) == 0) continue;
            left = Math.min(left, x); right = Math.max(right, x);
            top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        return right < left ? new Rect() : new Rect(left, top, right + 1, bottom + 1);
    }

    private void set(ScreensaverView view, String name, Object value) throws Exception {
        Field field = ScreensaverView.class.getDeclaredField(name); field.setAccessible(true); field.set(view, value);
    }
    private Object get(ScreensaverView view, String name) throws Exception {
        Field field = ScreensaverView.class.getDeclaredField(name); field.setAccessible(true); return field.get(view);
    }
}
