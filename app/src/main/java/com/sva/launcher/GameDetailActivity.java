package com.sva.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class GameDetailActivity extends Activity {
    private static final int PICK_ART = 7201;
    private GameRepository repository; private String id; private GameRecord game;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repository = new GameRepository(this); id = getIntent().getStringExtra("gameId");
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override protected void onResume() { super.onResume(); game = repository.find(id); if (game == null) { finish(); return; } render(); }

    private void render() {
        LinearLayout root = new LinearLayout(this); root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(GameUi.dp(this, 80), GameUi.dp(this, 50), GameUi.dp(this, 80), GameUi.dp(this, 50)); root.setBackground(LiquidTheme.scenicBackground(this));
        ImageView cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        java.io.File art = repository.artworkFile(game);
        if (art.isFile()) GameUi.loadArtwork(cover, art, GameUi.dp(this, 480));
        else { cover.setImageResource(R.drawable.sva_image); cover.setPadding(60,60,60,60); }
        root.addView(cover, new LinearLayout.LayoutParams(GameUi.dp(this, 330), GameUi.dp(this, 430)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(GameUi.dp(this, 54), 0, 0, 0);
        TextView eyebrow = GameUi.text(this, "NINTENDO ENTERTAINMENT SYSTEM", 12, true);
        eyebrow.setTextColor(LiquidTheme.accent(this));
        eyebrow.setLetterSpacing(.18f);
        copy.addView(eyebrow);
        TextView title = GameUi.text(this, game.title, 38, true); LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2); titleParams.topMargin = GameUi.dp(this, 16); copy.addView(title, titleParams);
        Button play = GameUi.button(this, "Play"); play.setOnClickListener(v -> play());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(GameUi.dp(this, 240), GameUi.dp(this, 64)); playParams.topMargin = GameUi.dp(this, 38); copy.addView(play, playParams);
        LinearLayout actions = new LinearLayout(this); LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, GameUi.dp(this, 56)); actionsParams.topMargin = GameUi.dp(this, 18); copy.addView(actions, actionsParams);
        Button rename = GameUi.button(this, "Rename"); rename.setOnClickListener(v -> rename()); actions.addView(rename, new LinearLayout.LayoutParams(GameUi.dp(this, 140), -1));
        Button artwork = GameUi.button(this, "Artwork"); artwork.setOnClickListener(v -> pickArtwork()); LinearLayout.LayoutParams small = new LinearLayout.LayoutParams(GameUi.dp(this, 140), -1); small.leftMargin = GameUi.dp(this, 10); actions.addView(artwork, small);
        Button remove = GameUi.button(this, "Remove"); remove.setOnClickListener(v -> remove()); LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(GameUi.dp(this, 140), -1); removeParams.leftMargin = GameUi.dp(this, 10); actions.addView(remove, removeParams);
        root.addView(copy, new LinearLayout.LayoutParams(0, -2, 1)); setContentView(root); play.requestFocus();
    }

    private void play() {
        try { repository.markPlayed(id); } catch (Exception ignored) { }
        startActivity(new Intent(this, GamePlayerActivity.class).putExtra("gameId", id));
    }

    private void rename() {
        EditText input = new EditText(this); input.setText(game.title); input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Game title").setView(input).setPositiveButton("Save", (d, w) -> {
            String value = input.getText().toString().trim(); if (value.isEmpty()) return;
            try { repository.updateTitle(id, value); render(); } catch (Exception e) { Toast.makeText(this, "Could not save title.", Toast.LENGTH_LONG).show(); }
        }).setNegativeButton("Cancel", null).show();
    }

    private void pickArtwork() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(picker, PICK_ART); } catch (Exception e) { Toast.makeText(this, "No image picker is available.", Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_ART || result != RESULT_OK || data == null || data.getData() == null) return;
        try { repository.updateArtwork(id, data.getData()); render(); }
        catch (Exception e) { Toast.makeText(this, "Could not save artwork.", Toast.LENGTH_LONG).show(); }
    }

    private void remove() {
        new AlertDialog.Builder(this).setTitle("Remove " + game.title + "?")
                .setMessage("Sva's imported copy, artwork, and battery save will be deleted. Your original file will not be changed.")
                .setPositiveButton("Remove", (d, w) -> { try { repository.remove(id); finish(); } catch (Exception e) { Toast.makeText(this, "Could not remove game.", Toast.LENGTH_LONG).show(); } })
                .setNegativeButton("Cancel", null).show();
    }
}
