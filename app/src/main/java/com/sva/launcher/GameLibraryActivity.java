package com.sva.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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

public final class GameLibraryActivity extends Activity {
    private static final int PICK_FILES = 7101, PICK_FOLDER = 7102;
    private GameRepository repository;
    private GridLayout grid;
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
        backdrop.setImageDrawable(libraryBackground());
        screen.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(GameUi.dp(this, 48), GameUi.dp(this, 30), GameUi.dp(this, 48), GameUi.dp(this, 30));
        page.setBackgroundColor(Color.TRANSPARENT);
        heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = GameUi.text(this, "Games", 32, true); heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button addFiles = GameUi.button(this, "Add files"); addFiles.setOnClickListener(v -> pickFiles());
        heading.addView(addFiles, new LinearLayout.LayoutParams(GameUi.dp(this, 142), GameUi.dp(this, 52)));
        Button addFolder = GameUi.button(this, "Add folder"); addFolder.setOnClickListener(v -> pickFolder());
        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(GameUi.dp(this, 142), GameUi.dp(this, 52));
        folderParams.leftMargin = GameUi.dp(this, 12); heading.addView(addFolder, folderParams);
        page.addView(heading, new LinearLayout.LayoutParams(-1, GameUi.dp(this, 70)));
        TextView hint = GameUi.text(this, "Your local NES library · user-provided .nes files only", 14, false);
        hint.setTextColor(LiquidTheme.secondaryText(this)); page.addView(hint, new LinearLayout.LayoutParams(-1, GameUi.dp(this, 42)));
        ScrollView scroll = new ScrollView(this); grid = new GridLayout(this); grid.setColumnCount(5);
        grid.setUseDefaultMargins(false); scroll.addView(grid); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        empty = GameUi.text(this, "No games yet. Add your own .nes files to get started.", 18, false);
        empty.setGravity(Gravity.CENTER); page.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        screen.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen); render();
    }

    private Drawable libraryBackground() {
        String wallpaper = getSharedPreferences("launcher", MODE_PRIVATE).getString("wallpaper", null);
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
                        if (bitmap != null) return new BitmapDrawable(getResources(), bitmap);
                    }
                }
            } catch (Exception ignored) { }
        }
        return getDrawable(R.drawable.mountain_wallpaper);
    }

    private void render() {
        View focused = grid.findFocus();
        if (focused != null && focused.getTag() instanceof String)
            focusedGameId = (String) focused.getTag();
        grid.removeAllViews(); List<GameRecord> games = repository.games();
        empty.setVisibility(games.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        grid.setVisibility(games.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        int width = Math.max(150, (getResources().getDisplayMetrics().widthPixels - GameUi.dp(this, 128)) / 5);
        for (GameRecord game : games) {
            LinearLayout card = GameUi.card(this, game, v -> open(game));
            card.setTag(game.id);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = width; params.height = GameUi.dp(this, 220);
            params.setMargins(GameUi.dp(this, 6), GameUi.dp(this, 6), GameUi.dp(this, 6), GameUi.dp(this, 6));
            grid.addView(card, params);
        }
        if (!games.isEmpty()) {
            View target = null;
            for (int i = 0; i < grid.getChildCount(); i++) {
                View card = grid.getChildAt(i);
                if (focusedGameId != null && focusedGameId.equals(card.getTag())) {
                    target = card;
                    break;
                }
            }
            if (target == null) target = grid.getChildAt(0);
            target.requestFocus();
        }
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
                return true;
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
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(picker, PICK_FILES); }
        catch (Exception e) { Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show(); }
    }

    private void pickFolder() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(picker, PICK_FOLDER); }
        catch (Exception e) { Toast.makeText(this, "No folder picker is available.", Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        final List<Uri> files = request == PICK_FILES ? GameRepository.urisFromResult(data) : null;
        final Uri tree = request == PICK_FOLDER ? data.getData() : null;
        Toast.makeText(this, "Importing games…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            GameRepository.ImportResult imported = files != null ? repository.importUris(files) : repository.importTree(tree);
            runOnUiThread(() -> { Toast.makeText(this, imported.message(), Toast.LENGTH_LONG).show(); render(); });
        }, "nes-import").start();
    }
}
