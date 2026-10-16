package com.sva.launcher;

import android.graphics.*;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.util.*;

/** Photo-led art directions. All coordinates use a 1600 × 900 design space. */
final class ScreensaverArt {
    static final class Scene {
        final Bitmap[] photos;
        final int color;
        Scene(List<Bitmap> images) {
            photos = images.toArray(new Bitmap[0]);
            long r = 0, g = 0, b = 0;
            Bitmap photo = photos[0];
            for (int y = 1; y <= 6; y++) for (int x = 1; x <= 6; x++) {
                int pixel = photo.getPixel(photo.getWidth() * x / 7, photo.getHeight() * y / 7);
                r += Color.red(pixel); g += Color.green(pixel); b += Color.blue(pixel);
            }
            color = Color.rgb((int)r / 36, (int)g / 36, (int)b / 36);
        }
    }
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint type = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF dst = new RectF();
    private final ColorMatrixColorFilter warm, mono;
    private final Bitmap paper;
    private final Shader paperShader, prismLight, edgeLight;
    private final Map<String, StaticLayout> captions = new HashMap<>();
    private final Typeface sans, serif = Typeface.create("serif", Typeface.NORMAL);
    private final Typeface italic = Typeface.create("serif", Typeface.ITALIC);
    private final String caption;
    private float photoPan;
    private int photoMat = 0xFF161A1A;
    private int frameNumber = -1;
    private String frameLabel = "";
    private static final float[][][] WEAVE_LAYOUTS = {
        {{70,64,1010,720},{1026,64,1530,380},{1026,396,1530,720}},
        {{70,64,574,380},{70,396,574,720},{590,64,1530,720}},
        {{70,64,790,720},{806,64,1530,448},{806,464,1530,720}}
    };
    private final DashPathEffect routeDash = new DashPathEffect(new float[]{5, 10}, 0);

    ScreensaverArt(Typeface sans, String caption) {
        this.sans = sans;
        this.caption = caption;
        ColorMatrix gray = new ColorMatrix(); gray.setSaturation(0);
        mono = new ColorMatrixColorFilter(gray);
        // Retain skin tones and most original color instead of applying full sepia.
        warm = new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                1.03f,.025f,0,0,3, .015f,.96f,.015f,0,1, 0,.025f,.88f,0,0, 0,0,0,1,0}));
        paper = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        Random random = new Random(41);
        for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++)
            paper.setPixel(x, y, Color.argb(3 + random.nextInt(11), 72, 50, 28));
        paperShader = new BitmapShader(paper, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        prismLight = new LinearGradient(0, 0, 1200, 550,
                new int[]{0x0031C7B5, 0x5531C7B5, 0x337F7BE3, 0x007F7BE3}, null, Shader.TileMode.CLAMP);
        edgeLight = new LinearGradient(0, 0, 220, 0,
                new int[]{0x00FFC078, 0x33FFB35E, 0x00FFC078}, null, Shader.TileMode.CLAMP);
    }

    void dispose() { paper.recycle(); captions.clear(); }

    void draw(Canvas canvas, int theme, Scene scene, float width, float height,
              float age, float time, int number, boolean motion) {
        int save = canvas.save();
        canvas.scale(width / 1600f, height / 900f);
        p.reset(); p.setAntiAlias(true); p.setFilterBitmap(true);
        photoMat = theme == 2 ? 0xFFF0EDE5 : theme == 7 ? 0xFFF7F3E9
                : theme == 3 ? 0xFFF4F0E7 : theme == 6 ? 0xFF0B0C0D : 0xFF161A1A;
        float drift = motion ? (float)Math.sin(time * .075f) : 0;
        photoPan = theme == 2 ? drift * 3 : 0;
        float progress = motion ? smooth(Math.min(1, age / 1.8f)) : 1;
        switch (theme) {
            case 1: darkroom(canvas, scene, drift, time, number, motion); break;
            case 2: editorial(canvas, scene, drift, progress, number); break;
            case 3: prints(canvas, scene, drift, time, progress, motion); break;
            case 4: monolith(canvas, scene, drift); break;
            case 5: prism(canvas, scene, drift, time, motion); break;
            case 6: silhouette(canvas, scene, drift, progress); break;
            case 7: journal(canvas, scene, drift, progress); break;
            case 8: weave(canvas, scene, drift, progress, number); break;
            default: break;
        }
        canvas.restoreToCount(save);
    }

    private void darkroom(Canvas c, Scene s, float drift, float time, int number, boolean motion) {
        c.drawColor(0xFF131311);
        rule(c, 66, 66, 1534, 66, 0xFF806D50, 1);
        number(c, number, 72, 49, 0xFFBAA17B);
        if (s.photos.length == 1) {
            frame(c, s.photos[0], 180, 100, 1420, 716, 9, 0xFF26251F, warm, false);
        } else {
            float shift = drift * 5;
            frame(c, s.photos[0], 70 + shift, 100, 1070 + shift, 716, 8, 0xFF343128, warm, false);
            float bottom = s.photos.length == 3 ? 393 : 716;
            frame(c, s.photos[1], 1104, 100, 1530, bottom, 7, 0xFF343128, warm, true);
            if (s.photos.length == 3) frame(c, s.photos[2], 1104, 418, 1530, 716, 7, 0xFF343128, warm, true);
        }
        for (int y = 110; y < 710; y += 35) {
            rect(c, 46, y, 53, y + 15, 0xFF685C48);
            rect(c, 1547, y, 1554, y + 15, 0xFF685C48);
        }
        if (motion) {
            c.save(); c.translate((time * 23 % 2100) - 250, 0);
            p.setShader(edgeLight); c.drawRect(0, 80, 220, 732, p); p.setShader(null); c.restore();
        }
        caption(c, 80, 737, 1380, 27, 0xFFD7C3A4, sans, 1);
    }

    private void editorial(Canvas c, Scene s, float drift, float progress, int number) {
        c.drawColor(0xFFF0EDE5);
        texture(c);
        if (caption.isEmpty()) {
            photo(c, s.photos[0], 66, 64, 1534, 724, false, 1, null);
            rule(c, 66, 747, 1534, 747, 0xFF9C9B91, 1);
            number(c, number, 1490, 778, 0xFF64665E);
        } else {
            photo(c, s.photos[0], 56, 52, 1080, 730, false, 1, null);
            float offset = (1 - progress) * 22;
            rule(c, 1131, 82, 1532, 82, 0xFF333A35, 2);
            number(c, number, 1131, 122, 0xFF777F74);
            caption(c, 1131 + offset, 193, 385, 53, 0xFF252E29, serif, 7);
            rule(c, 1131, 693, 1250, 693, 0xFF333A35, 2);
        }
    }

    private void prints(Canvas c, Scene s, float drift, float time, float progress, boolean motion) {
        c.drawColor(tint(s.color, .55f, 35));
        texture(c);
        c.save(); c.translate(0, (1 - progress) * 18);
        if (s.photos.length == 1) {
            print(c, s.photos[0], 800, 390, 1060, 603, -2 + drift * .6f);
        } else if (s.photos.length == 2) {
            print(c, s.photos[1], 490, 387, 665, 506, -7 + drift);
            print(c, s.photos[0], 1090, 377, 665, 506, 6 - drift);
        } else {
            float lift = motion ? (float)Math.sin(time * .1f + 1.4f) * 9 : 0;
            print(c, s.photos[2], 360, 386 + lift, 520, 448, -9 + drift);
            print(c, s.photos[1], 1240, 367 - lift, 520, 448, 8 - drift);
            print(c, s.photos[0], 800, 391, 660, 546, -2 + drift * .5f);
        }
        c.restore();
        caption(c, 220, 735, 1160, 30, 0xFFF1EBDF, italic, 1);
    }

    private void monolith(Canvas c, Scene s, float drift) {
        c.drawColor(tint(s.color, .25f, 6));
        // Large architectural planes give depth without surrounding the image with a vignette.
        rect(c, 0, 0, 400, 900, tint(s.color, .35f, 10));
        rect(c, 1180, 0, 1600, 900, tint(s.color, .19f, 3));
        float x = 800 + drift * 11, y = 380 - drift * 5;
        float fit = Math.min(1152f / s.photos[0].getWidth(), 596f / s.photos[0].getHeight());
        float hw = s.photos[0].getWidth() * fit / 2, hh = s.photos[0].getHeight() * fit / 2;
        shadow(c, x - hw - 14, y - hh - 14, x + hw + 14, y + hh + 14, 30);
        rect(c, x - hw - 12, y - hh - 12, x + hw + 12, y + hh + 12, tint(s.color, .7f, 10));
        photo(c, s.photos[0], x - hw, y - hh, x + hw, y + hh, false, 1, null);
        rule(c, 84, 736, 225, 736, 0xFFBBAE91, 2);
        caption(c, 256, 715, 1255, 30, 0xFFEAE7DE, sans, 1);
    }

    private void prism(Canvas c, Scene s, float drift, float time, boolean motion) {
        c.drawColor(0xFF0B1420);
        polygon(c, 0x223A9290, -80, 120, 600, 10, 1080, 810, 340, 930);
        polygon(c, 0x225A5193, 1150, -60, 1630, 220, 1550, 850, 900, 650);
        c.save(); c.translate(drift * 35, drift * -12);
        path.reset(); path.moveTo(-100, 640);
        path.cubicTo(420, 70 + drift * 40, 1000, 810, 1700, 140);
        path.lineTo(1700, 250); path.cubicTo(970, 890, 400, 190, -100, 720); path.close();
        p.setShader(prismLight); c.drawPath(path, p); p.setShader(null); c.restore();
        float lift = motion ? (float)Math.sin(time * .09f) * 7 : 0;
        float fit = Math.min(1140f / s.photos[0].getWidth(), 594f / s.photos[0].getHeight());
        float hw = s.photos[0].getWidth() * fit / 2, hh = s.photos[0].getHeight() * fit / 2;
        float cy = 392 + lift;
        shadow(c, 799 - hw, cy - hh - 1, 801 + hw, cy + hh + 1, 26);
        rect(c, 797 - hw, cy - hh - 3, 803 + hw, cy + hh + 3, 0xFF769C9F);
        photo(c, s.photos[0], 800 - hw, cy - hh, 800 + hw, cy + hh, false, 1, null);
        rule(c, 230, 714, 355, 714, 0xFF74CCCB, 2);
        caption(c, 388, 710, 980, 29, 0xFFE2F0F1, sans, 1);
    }

    private void silhouette(Canvas c, Scene s, float drift, float progress) {
        c.drawColor(0xFF0B0C0D);
        // Each slice samples the same fitted image, preserving one continuous portrait.
        for (int i = 0; i < 4; i++) {
            float left = 68 + i * 366;
            float reveal = smooth(Math.max(0, Math.min(1, progress * 1.55f - i * .15f)));
            c.save();
            c.clipRect(left, 62, left + 350, 62 + 648 * reveal);
            c.translate(drift * (i % 2 == 0 ? 3 : -3), 0);
            photo(c, s.photos[0], 68, 62, 1516, 710, false, 1.015f, mono);
            c.restore();
        }
        rule(c, 68, 748, 263, 748, 0xFFAAB3B9, 1);
        caption(c, 302, 723, 1210, 37, 0xFFF4F3ED, serif, 1);
    }

    private void journal(Canvas c, Scene s, float drift, float progress) {
        c.drawColor(0xFFE8DECB); texture(c);
        rule(c, 80, 62, 1520, 62, 0x887B806C, 1);
        // Abstract routes are ornament, not claims about where a photo was taken.
        path.reset(); path.moveTo(54, 650);
        path.cubicTo(390, 560, 400, 165, 820, 190);
        path.cubicTo(1200, 210, 1200, 700, 1550, 610);
        p.setColor(0x557B806C); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
        p.setPathEffect(routeDash);
        int route = c.save(); c.clipRect(0, 0, 1600 * progress, 800); c.drawPath(path, p); c.restoreToCount(route);
        p.setPathEffect(null); p.setStyle(Paint.Style.FILL);
        if (s.photos.length == 1) {
            journalPrint(c, s.photos[0], 800, 374, 1040, 580, -1 + drift * .4f);
        } else {
            journalPrint(c, s.photos[1], 459, 376, 650, 550, -4 + drift * .6f);
            journalPrint(c, s.photos[0], 1125, 384, 650, 550, 4 - drift * .6f);
        }
        caption(c, 150, 724, 1300, 32, 0xFF514C3D, italic, 1);
    }

    private void weave(Canvas c, Scene s, float drift, float progress, int number) {
        c.drawColor(tint(s.color, .25f, 4));
        if (s.photos.length == 1) {
            photo(c, s.photos[0], 70, 64, 1530, 720, false, 1, null);
        } else {
            // Adjacent layouts interpolate from the preceding composition, never randomize per frame.
            int layout = Math.floorMod(number, 3), old = Math.floorMod(number - 1, 3);
            if (s.photos.length == 2) {
                float split = 960 + drift * 12;
                photo(c, s.photos[0], 70, 64, split, 720, true, 1, null);
                photo(c, s.photos[1], split + 16, 64, 1530, 720, true, 1, null);
            } else for (int i = 0; i < 3; i++) {
                float[] a = WEAVE_LAYOUTS[old][i], b = WEAVE_LAYOUTS[layout][i];
                photo(c, s.photos[i], lerp(a[0],b[0],progress), lerp(a[1],b[1],progress),
                        lerp(a[2],b[2],progress), lerp(a[3],b[3],progress), true, 1, null);
            }
        }
        caption(c, 70, 741, 1460, 28, 0xFFE7ECE7, sans, 1);
    }

    private void photo(Canvas c, Bitmap photo, float l, float t, float r, float b,
                       boolean crop, float zoom, ColorMatrixColorFilter filter) {
        int save = c.save(); c.clipRect(l,t,r,b);
        rect(c,l,t,r,b,photoMat);
        float scale = (crop ? Math.max((r-l)/photo.getWidth(), (b-t)/photo.getHeight())
                : Math.min((r-l)/photo.getWidth(), (b-t)/photo.getHeight())) * zoom;
        float hw = photo.getWidth()*scale*.5f, hh = photo.getHeight()*scale*.5f;
        dst.set((l+r)*.5f-hw+photoPan,(t+b)*.5f-hh,(l+r)*.5f+hw+photoPan,(t+b)*.5f+hh);
        p.setColor(Color.WHITE); p.setColorFilter(filter);
        c.drawBitmap(photo, null, dst, p); p.setColorFilter(null); c.restoreToCount(save);
    }
    private void frame(Canvas c, Bitmap photo, float l,float t,float r,float b,float border,int color,
                       ColorMatrixColorFilter filter, boolean crop) {
        rect(c,l-border,t-border,r+border,b+border,color);
        photo(c,photo,l,t,r,b,crop,1,filter);
    }
    private void print(Canvas c, Bitmap photo,float x,float y,float w,float h,float angle) {
        float fit = Math.min((w - 32) / photo.getWidth(), (h - 62) / photo.getHeight());
        w = photo.getWidth() * fit + 32; h = photo.getHeight() * fit + 62;
        c.save(); c.rotate(angle,x,y);
        float l=x-w/2,t=y-h/2,r=x+w/2,b=y+h/2;
        shadow(c,l,t,r,b,16); rect(c,l,t,r,b,0xFFF4F0E7);
        photo(c,photo,l+16,t+16,r-16,b-46,false,1,null); c.restore();
    }
    private void journalPrint(Canvas c,Bitmap photo,float x,float y,float w,float h,float angle) {
        float fit = Math.min((w - 28) / photo.getWidth(), (h - 36) / photo.getHeight());
        w = photo.getWidth() * fit + 28; h = photo.getHeight() * fit + 36;
        c.save(); c.rotate(angle,x,y);
        float l=x-w/2,t=y-h/2,r=x+w/2,b=y+h/2;
        shadow(c,l,t,r,b,11); rect(c,l,t,r,b,0xFFF7F3E9);
        photo(c,photo,l+14,t+14,r-14,b-22,false,1,null);
        polygon(c,0xFFB0A58B,l-5,t-5,l+43,t-5,l-5,t+43);
        polygon(c,0xFFB0A58B,r+5,b+5,r-43,b+5,r+5,b-43); c.restore();
    }
    private void shadow(Canvas c,float l,float t,float r,float b,int radius) {
        // Layered rectangular shadows remain inexpensive on TV hardware.
        for (int i=radius;i>0;i-=3) rect(c,l-i,t-i*.4f,r+i,b+i,0x05000000);
        rect(c,l+8,t+12,r+8,b+12,0x26000000);
    }
    private void rect(Canvas c,float l,float t,float r,float b,int color) {
        p.setColor(color); c.drawRect(l,t,r,b,p);
    }
    private void rule(Canvas c,float x,float y,float endX,float endY,int color,float width) {
        p.setColor(color); p.setStrokeWidth(width); c.drawLine(x,y,endX,endY,p);
    }
    private void polygon(Canvas c,int color,float... xy) {
        path.reset(); path.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]); path.close();
        p.setColor(color); c.drawPath(path,p);
    }
    private void texture(Canvas c) {
        p.setColor(Color.WHITE); p.setShader(paperShader); c.drawRect(0,0,1600,900,p); p.setShader(null);
    }
    private void caption(Canvas c,float x,float y,float width,float size,int color,Typeface face,int lines) {
        if(caption.isEmpty())return;
        String key = width+":"+size+":"+lines+":"+face.toString();
        StaticLayout layout=captions.get(key);
        if(layout==null) {
            type.setTypeface(face); type.setTextSize(size); type.setColor(Color.WHITE);
            layout=StaticLayout.Builder.obtain(caption,0,caption.length(),new TextPaint(type),(int)width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(lines)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END).setLineSpacing(3,1).build();
            if(captions.size()>12)captions.clear();
            captions.put(key,layout);
        }
        layout.getPaint().setColor(color);
        c.save(); c.translate(x,y); layout.draw(c); c.restore();
    }
    private void number(Canvas c,int number,float x,float y,int color) {
        type.setTypeface(sans); type.setTextSize(19); type.setLetterSpacing(.16f); type.setColor(color);
        if (frameNumber != number) { frameNumber = number; frameLabel = String.format(Locale.ROOT,"%03d",number+1); }
        c.drawText(frameLabel,x,y,type); type.setLetterSpacing(0);
    }
    private static int tint(int color,float factor,int lift) {
        return Color.rgb(Math.min(255,(int)(Color.red(color)*factor)+lift),
                Math.min(255,(int)(Color.green(color)*factor)+lift),Math.min(255,(int)(Color.blue(color)*factor)+lift));
    }
    private static float smooth(float t) { return t*t*(3-2*t); }
    private static float lerp(float a,float b,float t) { return a+(b-a)*t; }
}
