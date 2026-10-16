package com.sva.launcher;

import android.content.Context;
import android.graphics.*;
import android.test.InstrumentationTestCase;
import java.io.*;
import java.util.*;

/** Device render checks and reviewable PNGs, using cached album photos when available. */
public final class ScreensaverArtTest extends InstrumentationTestCase {
    public void testAllArtDirectionsAndPhotoCounts() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        ScreensaverPhotoRepository repository = new ScreensaverPhotoRepository(context);
        List<Bitmap> photos = new ArrayList<>();
        for (String url : new LinkedHashSet<>(repository.cached(ScreensaverPreferences.album(
                context.getSharedPreferences("launcher", Context.MODE_PRIVATE))))) {
            Bitmap bitmap = repository.loadCached(url);
            if (bitmap != null) photos.add(bitmap);
            if (photos.size() == 3) break;
        }
        while (photos.size() < 3) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.mountain_wallpaper, options);
            assertNotNull(bitmap); photos.add(bitmap);
        }
        File directory = new File(context.getCacheDir(), "screensaver-art-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap target = null;
        try {
            for (int height : new int[]{720, 1080}) {
                target = Bitmap.createBitmap(height * 16 / 9, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(target);
                for (String caption : new String[]{"", "The little moments we keep. A beautiful day, together."}) {
                    ScreensaverArt art = new ScreensaverArt(LiquidTheme.font(context), caption);
                    try {
                        for (int count = 1; count <= 3; count++) {
                            ScreensaverArt.Scene scene = new ScreensaverArt.Scene(photos.subList(0, count));
                            for (int theme = 1; theme <= 8; theme++) {
                                for (float age : new float[]{0, .8f, 2, 28}) {
                                    art.draw(canvas, theme, scene, target.getWidth(), height, age, 15 + age, 7, true);
                                    assertEquals(255, Color.alpha(target.getPixel(0, 0)));
                                }
                                art.draw(canvas, theme, scene, target.getWidth(), height, 0, 0, 7, false);
                                if (height == 720 && count == 3 && !caption.isEmpty()) {
                                    try (FileOutputStream out = new FileOutputStream(new File(directory,
                                            ScreensaverPreferences.THEMES[theme] + ".png"))) {
                                        assertTrue(target.compress(Bitmap.CompressFormat.PNG, 100, out));
                                    }
                                }
                            }
                        }
                    } finally { art.dispose(); }
                }
                target.recycle(); target = null;
            }
        } finally {
            if (target != null) target.recycle();
            for (Bitmap photo : photos) photo.recycle();
        }
    }
    public void testPortraitPhotosAndLongCaptions() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        Bitmap portrait = Bitmap.createBitmap(360, 640, Bitmap.Config.RGB_565);
        Canvas source = new Canvas(portrait);
        source.drawColor(0xFF59696C);
        Paint paint = new Paint(); paint.setColor(0xFFE9C7A1);
        source.drawCircle(180, 190, 85, paint);
        source.drawRect(70, 300, 290, 620, paint);
        Bitmap output = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
        ScreensaverArt art = new ScreensaverArt(LiquidTheme.font(context),
                "A very long caption, with memories from many years, should wrap and end gracefully without covering the clock, weather, or events. "
                + "Together through the seasons, and every little moment in between. ");
        try {
            ScreensaverArt.Scene scene = new ScreensaverArt.Scene(Collections.singletonList(portrait));
            for (int theme = 1; theme <= 8; theme++) {
                art.draw(new Canvas(output), theme, scene, 1280, 720, 3, 15, 2, true);
                assertEquals(255, Color.alpha(output.getPixel(0, 0)));
            }
        } finally { art.dispose(); portrait.recycle(); output.recycle(); }
    }
}
