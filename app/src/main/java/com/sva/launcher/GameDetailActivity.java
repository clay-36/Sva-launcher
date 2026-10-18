package com.sva.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class GameDetailActivity extends MotionActivity {
    private static final int PICK_ART = 7201;
    private GameRepository repository; private String id; private GameRecord game;
    private LinearLayout actions;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repository = new GameRepository(this); id = getIntent().getStringExtra("gameId");
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override protected void onResume() {
        super.onResume();
        if (id != null) game = repository.find(id);
        if (game == null) {
            java.util.List<GameRecord> games = repository.games();
            if (!games.isEmpty()) { game = games.get(0); id = game.id; }
        }
        if (game == null) { finish(); return; }
        render();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this); root.setGravity(Gravity.CENTER_VERTICAL);
        root.setClipChildren(false); root.setClipToPadding(false);
        root.setPadding(GameUi.dp(this, 48), GameUi.dp(this, 32), GameUi.dp(this, 48), GameUi.dp(this, 32)); root.setBackground(LiquidTheme.scenicBackground(this));

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int coverHeight = Math.min(GameUi.dp(this, 390), screenHeight - GameUi.dp(this, 96));
        int coverWidth = Math.round(coverHeight * 0.72f);

        FrameLayout coverCard = new FrameLayout(this);
        coverCard.setBackground(GameUi.shape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 24));
        coverCard.setElevation(GameUi.dp(this, 12));
        coverCard.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), GameUi.dp(GameDetailActivity.this, 24));
            }
        });
        coverCard.setClipToOutline(true);

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        java.io.File art = repository.artworkFile(game);
        if (art.isFile()) {
            GameUi.loadArtwork(cover, art, GameUi.dp(this, 480));
        } else {
            cover.setImageResource(R.drawable.sva_image);
        }
        coverCard.addView(cover, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(coverWidth, coverHeight);
        cardParams.rightMargin = GameUi.dp(this, 36);
        root.addView(coverCard, cardParams);

        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        copy.setClipChildren(false); copy.setClipToPadding(false);
        TextView eyebrow = GameUi.text(this, "NINTENDO ENTERTAINMENT SYSTEM", 12, true);
        eyebrow.setTextColor(LiquidTheme.accent(this));
        eyebrow.setLetterSpacing(.18f);
        copy.addView(eyebrow);
        TextView title = GameUi.text(this, game.title, 38, true); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2); titleParams.topMargin = GameUi.dp(this, 16); copy.addView(title, titleParams);
        Button play = GameUi.button(this, "Play"); play.setOnClickListener(v -> play());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(GameUi.dp(this, 240), GameUi.dp(this, 64));
        playParams.setMargins(GameUi.dp(this, 4), GameUi.dp(this, 38), GameUi.dp(this, 4), GameUi.dp(this, 4));
        copy.addView(play, playParams);
        actions = new LinearLayout(this);
        actions.setClipChildren(false); actions.setClipToPadding(false);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, GameUi.dp(this, 56)); actionsParams.topMargin = GameUi.dp(this, 18); copy.addView(actions, actionsParams);
        addAction("Rename", this::rename);
        addAction("Artwork", this::pickArtwork);
        addAction("Remove", this::remove);
        root.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f)); setContentView(root); play.requestFocus();
    }

    private void addAction(String label, Runnable click) {
        Button button = GameUi.button(this, label);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setSingleLine(true);
        button.setPadding(GameUi.dp(this, 12), 0, GameUi.dp(this, 12), 0);
        button.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, GameUi.dp(this, 50), 1f);
        params.setMargins(GameUi.dp(this, actions.getChildCount() > 0 ? 10 : 2),
                GameUi.dp(this, 2), GameUi.dp(this, 2), GameUi.dp(this, 2));
        actions.addView(button, params);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && isDpadDirection(event.getKeyCode())) {
            View focused = getCurrentFocus();
            if (focused != null && isWithin(focused, actions)) {
                int direction = focusDirection(event.getKeyCode());
                View next = focused.focusSearch(direction);
                if ((direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)
                        && next != null && next.getParent() == actions
                        && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
                    if (next != null && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                }
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

    private void play() {
        try { repository.markPlayed(id); } catch (Exception ignored) { }
        startActivity(new Intent(this, GamePlayerActivity.class).putExtra("gameId", id));
    }

    private void rename() {
        EditText input = new EditText(this); input.setText(game.title); input.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(this, LiquidTheme.alertDialogTheme(this)).setTitle("Game title").setView(input).setPositiveButton("Save", (d, w) -> {
            String value = input.getText().toString().trim(); if (value.isEmpty()) return;
            try { repository.updateTitle(id, value); render(); } catch (Exception e) { Toast.makeText(this, "Could not save title.", Toast.LENGTH_LONG).show(); }
        }).setNegativeButton("Cancel", null).create();
        Motion.dialog(dialog); dialog.show();
    }

    private void pickArtwork() {
        startActivityForResult(TvFilePickerActivity.intent(this, TvFilePickerActivity.MODE_IMAGE), PICK_ART);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_ART || result != RESULT_OK || data == null || data.getData() == null) return;
        try { repository.updateArtwork(id, data.getData()); render(); }
        catch (Exception e) { Toast.makeText(this, "Could not save artwork.", Toast.LENGTH_LONG).show(); }
    }

    private void remove() {
        AlertDialog dialog = new AlertDialog.Builder(this, LiquidTheme.alertDialogTheme(this)).setTitle("Remove " + game.title + "?")
                .setMessage("Sva's imported copy, artwork, and battery save will be deleted. Your original file will not be changed.")
                .setPositiveButton("Remove", (d, w) -> { try { repository.remove(id); finish(); } catch (Exception e) { Toast.makeText(this, "Could not remove game.", Toast.LENGTH_LONG).show(); } })
                .setNegativeButton("Cancel", null).create();
        Motion.dialog(dialog); dialog.show();
    }
}
