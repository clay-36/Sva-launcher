package com.sva.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        repository = new GameRepository(this);
        build();
    }

    @Override protected void onResume() { super.onResume(); if (grid != null) render(); }

    private void build() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(GameUi.dp(this, 48), GameUi.dp(this, 30), GameUi.dp(this, 48), GameUi.dp(this, 30));
        page.setBackgroundColor(0xff091421);
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = GameUi.text(this, "Games", 32, true); heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button addFiles = GameUi.button(this, "Add files"); addFiles.setOnClickListener(v -> pickFiles());
        heading.addView(addFiles, new LinearLayout.LayoutParams(GameUi.dp(this, 142), GameUi.dp(this, 52)));
        Button addFolder = GameUi.button(this, "Add folder"); addFolder.setOnClickListener(v -> pickFolder());
        LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(GameUi.dp(this, 142), GameUi.dp(this, 52));
        folderParams.leftMargin = GameUi.dp(this, 12); heading.addView(addFolder, folderParams);
        page.addView(heading, new LinearLayout.LayoutParams(-1, GameUi.dp(this, 70)));
        TextView hint = GameUi.text(this, "Your local NES library · user-provided .nes files only", 14, false);
        hint.setTextColor(0xffb7c5d1); page.addView(hint, new LinearLayout.LayoutParams(-1, GameUi.dp(this, 42)));
        ScrollView scroll = new ScrollView(this); grid = new GridLayout(this); grid.setColumnCount(5);
        grid.setUseDefaultMargins(false); scroll.addView(grid); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        empty = GameUi.text(this, "No games yet. Add your own .nes files to get started.", 18, false);
        empty.setGravity(Gravity.CENTER); page.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page); render();
    }

    private void render() {
        grid.removeAllViews(); List<GameRecord> games = repository.games();
        empty.setVisibility(games.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        grid.setVisibility(games.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        int width = Math.max(150, (getResources().getDisplayMetrics().widthPixels - GameUi.dp(this, 128)) / 5);
        for (GameRecord game : games) {
            LinearLayout card = GameUi.card(this, game, v -> open(game));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(); params.width = width; params.height = GameUi.dp(this, 220);
            params.setMargins(GameUi.dp(this, 6), GameUi.dp(this, 6), GameUi.dp(this, 6), GameUi.dp(this, 6));
            grid.addView(card, params);
        }
        if (!games.isEmpty()) grid.getChildAt(0).requestFocus();
    }

    private void open(GameRecord game) {
        startActivity(new Intent(this, GameDetailActivity.class).putExtra("gameId", game.id));
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
