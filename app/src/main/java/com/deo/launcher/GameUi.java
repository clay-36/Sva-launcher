package com.deo.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

final class GameUi {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService ARTWORK_WORKER = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "game-artwork");
        thread.setDaemon(true);
        return thread;
    });
    private static final LruCache<String, Bitmap> ARTWORK_CACHE = new LruCache<String, Bitmap>(16 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return Math.max(1, value.getByteCount() / 1024); }
    };

    private GameUi() { }

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static TextView text(Activity activity, String value, float size, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value); text.setTextColor(Color.WHITE); text.setTextSize(size);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    static Button button(Activity activity, String value) {
        Button button = new Button(activity);
        button.setText(value); button.setTextColor(Color.WHITE); button.setTextSize(15);
        button.setAllCaps(false); button.setFocusable(true); button.setFocusableInTouchMode(false);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(0xff33495f, 0xffbce7ff, 3, 15));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(0xff33495f, 0xffbce7ff, 3, 15));
        states.addState(new int[]{}, shape(0xff182838, 0xff5f7488, 1, 15));
        button.setBackground(states);
        button.setOnFocusChangeListener((view, focused) -> view.animate().scaleX(focused ? 1.04f : 1f)
                .scaleY(focused ? 1.04f : 1f).setDuration(110).start());
        return button;
    }

    static LinearLayout card(Activity activity, GameRecord game, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        card.setFocusable(true); card.setClickable(true); card.setOnClickListener(click);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(0xff253a4c, 0xffbce7ff, 3, 14));
        states.addState(new int[]{}, shape(0xff132231, 0xff526779, 1, 14));
        card.setBackground(states);
        card.setOnFocusChangeListener((view, focused) -> view.animate().scaleX(focused ? 1.035f : 1f)
                .scaleY(focused ? 1.035f : 1f).setDuration(110).start());
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        File art = new GameRepository(activity).artworkFile(game);
        if (art.isFile()) loadArtwork(image, art, dp(activity, 220));
        else {
            image.setImageResource(R.drawable.duo_image);
            image.setBackgroundColor(0xff091421);
            image.setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 24));
        }
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(activity, 142)));
        TextView title = text(activity, game.title, 14, true);
        title.setGravity(Gravity.CENTER); title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(activity, 48));
        titleParams.topMargin = dp(activity, 6); card.addView(title, titleParams);
        return card;
    }

    static void loadArtwork(ImageView image, File file, int targetPx) {
        String key = file.getAbsolutePath() + ':' + file.lastModified() + ':' + targetPx;
        image.setTag(key);
        Bitmap cached = ARTWORK_CACHE.get(key);
        if (cached != null) { image.setPadding(0, 0, 0, 0); image.setImageBitmap(cached); return; }
        image.setImageResource(R.drawable.duo_image);
        ARTWORK_WORKER.execute(() -> {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return;
            ARTWORK_CACHE.put(key, bitmap);
            MAIN.post(() -> {
                if (!key.equals(image.getTag())) return;
                image.setPadding(0, 0, 0, 0);
                image.setImageBitmap(bitmap);
            });
        });
    }

    static GradientDrawable shape(int color, int stroke, int width, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color);
        shape.setStroke(width, stroke); shape.setCornerRadius(radius); return shape;
    }
}
