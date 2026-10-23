package com.sva.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class GameLibraryActivity extends MotionActivity {
    static final String EXTRA_BACKGROUND_URI = "background_uri";
    private static final int PICK_FILES = 7101, PICK_FOLDER = 7102;
    private GameRepository repository;
    private androidx.recyclerview.widget.RecyclerView grid;
    private GameCardAdapter adapter;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final LifecycleWork work = new LifecycleWork(this, main);
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newFixedThreadPool(2);
    private int loadGeneration;
    private TextView empty;
    private String focusedGameId;
    private LinearLayout heading;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        repository = new GameRepository(this);
        build();
    }

    @Override protected void onResume() { super.onResume(); if (grid != null) render(); }

    private void build() {
        FrameLayout screen = new FrameLayout(this);
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setImageDrawable(LiquidTheme.scenicBackground(this));
        work.execute(worker, () -> {
            Drawable background = libraryBackground();
            work.post(() -> backdrop.setImageDrawable(background));
        });
        screen.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false); page.setClipToPadding(false);
        page.setPadding(GameUi.dp(this, 48), GameUi.dp(this, 30), GameUi.dp(this, 48), GameUi.dp(this, 30));
        page.setBackgroundColor(Color.TRANSPARENT);
        heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setClipChildren(false); heading.setClipToPadding(false);
        TextView title = GameUi.text(this, AppLanguage.get(R.string.ui_games), 32, true); heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button addFiles = GameUi.button(this, AppLanguage.get(R.string.ui_add_files)); addFiles.setOnClickListener(v -> pickFiles());
        LinearLayout.LayoutParams filesParams = new LinearLayout.LayoutParams(GameUi.dp(this, 142), GameUi.dp(this, 52));
        filesParams.setMargins(GameUi.dp(this, 4), GameUi.dp(this, 4), GameUi.dp(this, 4), GameUi.dp(this, 4));
        heading.addView(addFiles, filesParams);
        Button addFolder = GameUi.button(this, AppLanguage.get(R.string.ui_add_folder)); addFolder.setOnClickListener(v -> pickFolder());
        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(GameUi.dp(this, 142), GameUi.dp(this, 52));
        folderParams.setMargins(GameUi.dp(this, 8), GameUi.dp(this, 4), GameUi.dp(this, 4), GameUi.dp(this, 4)); heading.addView(addFolder, folderParams);
        page.addView(heading, new LinearLayout.LayoutParams(-1, GameUi.dp(this, 70)));
        TextView hint = GameUi.text(this, AppLanguage.get(R.string.ui_your_local_nes_library_user_provided_nes_files_only), 14, false);
        hint.setTextColor(LiquidTheme.secondaryText(this)); page.addView(hint, new LinearLayout.LayoutParams(-1, GameUi.dp(this, 42)));
        grid = new androidx.recyclerview.widget.RecyclerView(this);
        grid.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 5));
        grid.setItemAnimator(null); grid.setClipChildren(true); grid.setClipToPadding(false);
        adapter = new GameCardAdapter(this,
                GameUi.dp(this, 220), GameUi.dp(this, 6), GameUi.dp(this, 6),
                GameUi.dp(this, 6), GameUi.dp(this, 6), this::open);
        grid.setAdapter(adapter);
        page.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        empty = GameUi.text(this, AppLanguage.get(R.string.ui_no_games_yet_add_your_own_nes_files_to_get_started), 18, false);
        empty.setGravity(Gravity.CENTER); page.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        screen.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
    }

    private Drawable libraryBackground() {
        Drawable photo = null;
        String wallpaper = getIntent().getStringExtra(EXTRA_BACKGROUND_URI);
        if (wallpaper == null) wallpaper = getSharedPreferences("launcher", MODE_PRIVATE).getString("wallpaper", null);
        if (wallpaper != null) {
            try (java.io.InputStream input = getContentResolver().openInputStream(Uri.parse(wallpaper))) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(input, null, bounds);
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    int target = Math.max(getResources().getDisplayMetrics().widthPixels,
                            getResources().getDisplayMetrics().heightPixels);
                    BitmapFactory.Options sample = new BitmapFactory.Options();
                    sample.inSampleSize = Math.max(1, Math.min(bounds.outWidth, bounds.outHeight) / Math.max(1, target));
                    try (java.io.InputStream image = getContentResolver().openInputStream(Uri.parse(wallpaper))) {
                        Bitmap bitmap = BitmapFactory.decodeStream(image, null, sample);
                        if (bitmap != null) photo = new BitmapDrawable(getResources(), bitmap);
                    }
                }
            } catch (Exception ignored) { }
        }
        if (photo == null) photo = getDrawable(R.drawable.mountain_wallpaper);
        GradientDrawable veil = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                LiquidTheme.isLight(this)
                        ? new int[]{0xB8E9F1EE, 0xE8E9F1EE, 0xF4E9F1EE}
                        : new int[]{0x91041114, 0xC907171A, 0xF4071417});
        return new LayerDrawable(new Drawable[]{photo, veil});
    }

    private void render() {
        View focused = grid.findFocus();
        if (focused != null && focused.getTag() instanceof String) focusedGameId = (String)focused.getTag();
        int generation = ++loadGeneration;
        work.execute(worker, () -> {
            List<GameCardAdapter.Item> games = GameCardAdapter.load(this);
            work.post(() -> {
                if (generation != loadGeneration) return;
                empty.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
                grid.setVisibility(games.isEmpty() ? View.GONE : View.VISIBLE);
                adapter.submit(games);
                if (!games.isEmpty() && !heading.hasFocus()) {
                    int position = adapter.position(focusedGameId);
                    grid.scrollToPosition(position);
                    grid.post(() -> {
                        if (generation != loadGeneration || !work.active() || heading.hasFocus()) return;
                        androidx.recyclerview.widget.RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
                        if (holder != null) holder.itemView.requestFocus();
                    });
                }
            });
        });
    }

    @Override protected void onDestroy() {
        work.close(); worker.shutdownNow(); super.onDestroy();
    }

    private void open(GameRecord game) {
        focusedGameId = game.id;
        startActivity(new Intent(this, GameDetailActivity.class).putExtra("gameId", game.id));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && isDpadDirection(event.getKeyCode())) {
            View focused = getCurrentFocus();
            if (focused != null && isWithin(focused, grid)) {
                int direction = focusDirection(event.getKeyCode());
                View next = focused.focusSearch(direction);
                if (next != null && isWithin(next, grid)
                        && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                if (direction == View.FOCUS_UP && next != null && isWithin(next, heading)
                        && next.isEnabled() && next.requestFocus()) return true;
                return super.dispatchKeyEvent(event);
            }
            if (focused != null && isWithin(focused, heading)) {
                int direction = focusDirection(event.getKeyCode());
                View next = focused.focusSearch(direction);
                if (next != null && next.getParent() == heading
                        && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                if (direction == View.FOCUS_DOWN && next != null && isWithin(next, grid)
                        && next.isEnabled() && next.requestFocus()) return true;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isWithin(View child, View ancestor) {
        for (View current = child; current != null; ) {
            if (current == ancestor) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean isDpadDirection(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private int focusDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            default: return View.FOCUS_RIGHT;
        }
    }

    private void pickFiles() {
        startActivityForResult(TvFilePickerActivity.intent(this, TvFilePickerActivity.MODE_NES_FILES), PICK_FILES);
    }

    private void pickFolder() {
        startActivityForResult(TvFilePickerActivity.intent(this, TvFilePickerActivity.MODE_NES_FOLDER), PICK_FOLDER);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if ((request != PICK_FILES && request != PICK_FOLDER) || result != RESULT_OK || data == null) return;
        final List<Uri> files = request == PICK_FILES ? GameRepository.urisFromResult(data) : null;
        final Uri tree = request == PICK_FOLDER ? data.getData() : null;
        if (files != null && files.isEmpty() || request == PICK_FOLDER && tree == null) return;
        Toast.makeText(this, AppLanguage.get(R.string.ui_importing_games), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            GameRepository.ImportResult imported = files != null ? repository.importUris(files) : repository.importTree(tree);
            runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; Toast.makeText(this, imported.message(), Toast.LENGTH_LONG).show(); render(); });
        }, "nes-import").start();
    }
}
