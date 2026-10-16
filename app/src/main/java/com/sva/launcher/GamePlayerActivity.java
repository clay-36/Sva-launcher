package com.sva.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.input.InputManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GamePlayerActivity extends Activity implements InputManager.InputDeviceListener {
    private final NestopiaSession session = new NestopiaSession();
    private final Map<Integer, Integer> ports = new LinkedHashMap<>();
    private final Map<Integer, ControllerProfile> profiles = new LinkedHashMap<>();
    private final int[] masks = new int[2];
    private int remoteMask;
    private GameRepository repository; private GameRecord game; private FrameLayout pauseLayer;
    private TextView pauseTitle; private boolean started, menuVisible;
    private AudioManager audioManager; private AudioFocusRequest focusRequest;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BackNavigation.install(this, this::handleBack);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        repository = new GameRepository(this); game = repository.find(getIntent().getStringExtra("gameId"));
        if (game == null) { finish(); return; }
        assignControllers(); build(); requestAudio();
        ((InputManager) getSystemService(INPUT_SERVICE)).registerInputDeviceListener(this, null);
    }

    private void build() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        SurfaceView screen = new SurfaceView(this);
        int width = getResources().getDisplayMetrics().widthPixels, height = getResources().getDisplayMetrics().heightPixels;
        int displayWidth = Math.min(width, Math.round(height * 4f / 3f));
        FrameLayout.LayoutParams screenParams = new FrameLayout.LayoutParams(displayWidth, height, Gravity.CENTER);
        root.addView(screen, screenParams);
        screen.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                if (!started) {
                    File rom = repository.romFile(game), save = repository.saveFile(game);
                    started = session.start(rom.getAbsolutePath(), save.getAbsolutePath(), getFilesDir().getAbsolutePath());
                    if (!started) { Toast.makeText(GamePlayerActivity.this, "This game could not be started.", Toast.LENGTH_LONG).show(); finish(); return; }
                }
                session.surface(holder.getSurface());
                session.pause(false);
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { session.surface(holder.getSurface()); }
            @Override public void surfaceDestroyed(SurfaceHolder holder) { session.surface(null); session.pause(true); }
        });
        pauseLayer = new FrameLayout(this); pauseLayer.setBackgroundColor(0xd9041013); pauseLayer.setVisibility(View.GONE);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(GameUi.dp(this, 36), GameUi.dp(this, 28), GameUi.dp(this, 36), GameUi.dp(this, 28));
        panel.setBackground(GameUi.shape(LiquidTheme.surface(this), LiquidTheme.accent(this), 2, 22));
        pauseTitle = GameUi.text(this, game.title, 26, true); pauseTitle.setGravity(Gravity.CENTER); panel.addView(pauseTitle, new LinearLayout.LayoutParams(GameUi.dp(this, 430), GameUi.dp(this, 58)));
        Button resume = GameUi.button(this, "Resume"); resume.setOnClickListener(v -> hidePause()); panel.addView(resume, new LinearLayout.LayoutParams(GameUi.dp(this, 300), GameUi.dp(this, 56)));
        Button restart = GameUi.button(this, "Restart"); restart.setOnClickListener(v -> { session.reset(); hidePause(); }); LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(GameUi.dp(this, 300), GameUi.dp(this, 56)); action.topMargin = GameUi.dp(this, 10); panel.addView(restart, action);
        Button exit = GameUi.button(this, "Exit to Sva"); exit.setOnClickListener(v -> finish()); LinearLayout.LayoutParams exitParams = new LinearLayout.LayoutParams(GameUi.dp(this, 300), GameUi.dp(this, 56)); exitParams.topMargin = GameUi.dp(this, 10); panel.addView(exit, exitParams);
        pauseLayer.addView(panel, new FrameLayout.LayoutParams(GameUi.dp(this, 510), GameUi.dp(this, 310), Gravity.CENTER)); root.addView(pauseLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void showPause(String title) { menuVisible = true; pauseTitle.setText(title); pauseLayer.setVisibility(View.VISIBLE); session.pause(true); pauseLayer.post(() -> ((View)((android.view.ViewGroup)((android.view.ViewGroup)pauseLayer.getChildAt(0))).getChildAt(1)).requestFocus()); }
    private void hidePause() { menuVisible = false; pauseLayer.setVisibility(View.GONE); session.pause(false); }

    // Kept for Android <33; modern system Back uses BackNavigation.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }
    private void handleBack() { if (game == null) { finish(); return; } if (menuVisible) hidePause(); else showPause(game.title); }

    // Controller/TV Back events also carry press and repeat information used by the pause control.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (menuVisible) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && isDpadDirection(event.getKeyCode())) {
                View focused = getCurrentFocus();
                View next = focused == null ? null : focused.focusSearch(focusDirection(event.getKeyCode()));
                if (next != null && isWithinPauseLayer(next)
                        && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() > 0) return true;
        if (ports.get(event.getDeviceId()) == null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // Back is the dedicated pause control. Never treat a held Back
            // press as an NES Start press.
            // Long presses can be synthesized by some remotes while holding OK;
            // only a short Back press should open the pause menu.
            if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) showPause(game.title);
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
        Integer port = ports.get(event.getDeviceId());
        if (port == null) {
            int bit = remoteBitFor(event.getKeyCode());
            if (bit < 0) return super.dispatchKeyEvent(event);
            boolean ok = bit == 8;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                remoteMask |= 1 << bit;
                // A five-button TV remote still needs a way past NES title
                // screens: a normal OK press is A and holding OK adds Start.
                if (ok && event.getRepeatCount() > 0) remoteMask |= 1 << 3;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                remoteMask &= ~(1 << bit);
                if (ok) remoteMask &= ~(1 << 3);
            }
            pushInput(0); return true;
        }
        if (port == null) return super.dispatchKeyEvent(event);
        ControllerProfile profile = profiles.get(event.getDeviceId());
        int bit = profile == null ? -1 : profile.bitFor(event.getKeyCode());
        if (bit == -2) {
            if (isConfirmKey(event.getKeyCode())) {
                pushConfirmAsGameInput(port, event);
                return true;
            }
            showPause(game.title);
            return true;
        }
        if (bit < 0) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) masks[port] |= 1 << bit; else if (event.getAction() == KeyEvent.ACTION_UP) masks[port] &= ~(1 << bit);
        pushInput(port); return true;
    }

    private boolean isDpadDirection(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void pushConfirmAsGameInput(int port, KeyEvent event) {
        int confirm = 1 << 8;
        int start = 1 << 3;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            masks[port] |= confirm;
            if (event.getRepeatCount() > 0) masks[port] |= start;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            masks[port] &= ~(confirm | start);
        }
        pushInput(port);
    }

    private int focusDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            default: return View.FOCUS_RIGHT;
        }
    }

    private boolean isWithinPauseLayer(View child) {
        for (View current = child; current != null; ) {
            if (current == pauseLayer) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        Integer port = ports.get(event.getDeviceId()); if (port == null || event.getAction() != MotionEvent.ACTION_MOVE || menuVisible) return super.onGenericMotionEvent(event);
        float x = axis(event, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_X), y = axis(event, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_Y);
        masks[port] &= ~((1 << 4) | (1 << 5) | (1 << 6) | (1 << 7));
        if (y < -.5f) masks[port] |= 1 << 4; if (y > .5f) masks[port] |= 1 << 5;
        if (x < -.5f) masks[port] |= 1 << 6; if (x > .5f) masks[port] |= 1 << 7;
        pushInput(port); return true;
    }

    private void pushInput(int port) { session.input(port, masks[port] | (port == 0 ? remoteMask : 0)); }

    private float axis(MotionEvent event, int preferred, int fallback) { float value = event.getAxisValue(preferred); return Math.abs(value) > .1f ? value : event.getAxisValue(fallback); }
    private int remoteBitFor(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return 4;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 5;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 6;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 7;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return 8; // NES A
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_PROG_RED: return 0; // NES B
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
            case KeyEvent.KEYCODE_INFO: return 2; // Select
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return 3; // Start
            default: return -1;
        }
    }
    private void assignControllers() {
        ports.clear(); profiles.clear(); for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id); if (device == null) continue;
            int sources = device.getSources(); if ((sources & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD && (sources & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) continue;
            ports.put(id, ports.size()); profiles.put(id, ControllerProfile.load(this, device)); if (ports.size() == 2) break;
        }
    }

    @Override public void onInputDeviceAdded(int id) { assignControllers(); }
    @Override public void onInputDeviceRemoved(int id) { assignControllers(); masks[0] = masks[1] = 0; pushInput(0); pushInput(1); }
    @Override public void onInputDeviceChanged(int id) { assignControllers(); }

    private void requestAudio() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (!started) return;
                    runOnUiThread(() -> {
                        if (change <= 0) session.pause(true);
                        else if (!menuVisible) session.pause(false);
                    });
                }).build();
        audioManager.requestAudioFocus(focusRequest);
    }

    @Override protected void onPause() { if (started) session.pause(true); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (started && !menuVisible) session.pause(false); }
    @Override protected void onDestroy() {
        ((InputManager) getSystemService(INPUT_SERVICE)).unregisterInputDeviceListener(this);
        if (focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest); session.stop(); super.onDestroy();
    }
}
