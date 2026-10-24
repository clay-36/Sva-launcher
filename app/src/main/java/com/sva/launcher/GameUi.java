package com.sva.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
        text.setText(value); text.setTextColor(LiquidTheme.text(activity)); text.setTextSize(size);
        text.setTypeface(LiquidTheme.font(activity));
        return text;
    }

    static Button button(Activity activity, String value) {
        Button button = new Button(activity);
        button.setTypeface(LiquidTheme.font(activity));
        button.setText(value); button.setTextColor(LiquidTheme.text(activity)); button.setTextSize(15);
        button.setAllCaps(false); button.setFocusable(true); button.setFocusableInTouchMode(false);
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, shape(LiquidTheme.raisedSurface(activity), LiquidTheme.accent(activity), 3, 13));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(LiquidTheme.raisedSurface(activity), LiquidTheme.accent(activity), 3, 13));
        states.addState(new int[]{}, shape(LiquidTheme.surface(activity), LiquidTheme.border(activity), 1, 13));
        button.setBackground(states);
        button.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        return button;
    }

    static LinearLayout card(Activity activity, GameRecord game, View.OnClickListener click, File art, long artworkVersion) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        card.setFocusable(true); card.setClickable(true); card.setOnClickListener(click);
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, shape(LiquidTheme.raisedSurface(activity), LiquidTheme.accent(activity), 3, 14));
        states.addState(new int[]{}, shape(LiquidTheme.surface(activity), LiquidTheme.border(activity), 1, 14));
        card.setBackground(states);
        card.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        Motion.interaction(card, true);
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(activity, 142)));
        TextView title = text(activity, game.title, 14, true);
        title.setGravity(Gravity.CENTER); title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(activity, 48));
        titleParams.topMargin = dp(activity, 6); card.addView(title, titleParams);
        bindCard(activity, card, game, click, art, artworkVersion);
        return card;
    }

    static void bindCard(Activity activity, LinearLayout card, GameRecord game,
                         View.OnClickListener click, File art, long artworkVersion) {
        card.setTag(game.id); card.setOnClickListener(click);
        ((TextView)card.getChildAt(1)).setText(game.title);
        ImageView image = (ImageView)card.getChildAt(0);
        image.setTag(null);
        if (artworkVersion > 0) {
            image.setPadding(0, 0, 0, 0); image.setBackgroundColor(Color.TRANSPARENT);
            loadArtwork(image, art, dp(activity, 220), artworkVersion);
        } else {
            image.setImageResource(R.drawable.sva_image);
            image.setBackgroundColor(LiquidTheme.background(activity));
            image.setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 24));
        }
    }

    static void loadArtwork(ImageView image, File file, int targetPx) {
        loadArtwork(image, file, targetPx, file.lastModified());
    }
    static void loadArtwork(ImageView image, File file, int targetPx, long version) {
        String key = file.getAbsolutePath() + ':' + version + ':' + targetPx;
        image.setTag(key);
        Bitmap cached = ARTWORK_CACHE.get(key);
        if (cached != null) { image.setPadding(0, 0, 0, 0); image.setImageBitmap(cached); return; }
        image.setImageResource(R.drawable.sva_image);
        ARTWORK_WORKER.execute(() -> {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;
            int sample = 1;
            while (bounds.outWidth / sample > targetPx || bounds.outHeight / sample > targetPx) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return;
            ARTWORK_CACHE.put(key, bitmap);
            MAIN.post(() -> {
                if (image.getContext() instanceof Activity) {
                    Activity owner = (Activity) image.getContext();
                    if (owner.isFinishing() || owner.isDestroyed()) return;
                }
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
