package com.sva.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class ControllerSettingsActivity extends Activity {
    private final List<InputDevice> controllers = new ArrayList<>();
    private InputDevice mappingDevice; private ControllerProfile mapping; private int step; private TextView prompt;

    @Override public void onCreate(Bundle state) { super.onCreate(state); BackNavigation.install(this, this::handleBack); renderList(); }

    private void renderList() {
        controllers.clear(); for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id); if (device == null) continue;
            if ((device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) controllers.add(device);
        }
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(GameUi.dp(this, 64), GameUi.dp(this, 42), GameUi.dp(this, 64), GameUi.dp(this, 42)); page.setBackground(LiquidTheme.scenicBackground(this));
        page.addView(GameUi.text(this, "Game controllers", 30, true));
        TextView hint = GameUi.text(this, "Choose a connected controller to remap. Left-stick and hat directions always work automatically.", 15, false);
        hint.setTextColor(LiquidTheme.secondaryText(this)); LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, GameUi.dp(this, 62)); hintParams.topMargin = GameUi.dp(this, 12); page.addView(hint, hintParams);
        if (controllers.isEmpty()) { TextView empty = GameUi.text(this, "Connect an Android gamepad, then reopen this screen.", 19, false); empty.setGravity(Gravity.CENTER); page.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1)); }
        for (InputDevice device : controllers) {
            Button button = GameUi.button(this, "Remap  ·  " + device.getName()); button.setOnClickListener(v -> begin(device));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(GameUi.dp(this, 520), GameUi.dp(this, 58)); params.bottomMargin = GameUi.dp(this, 12); page.addView(button, params);
        }
        setContentView(page); if (!controllers.isEmpty()) page.getChildAt(2).requestFocus();
    }

    private void begin(InputDevice device) {
        mappingDevice = device; mapping = new ControllerProfile(); step = 0;
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER); page.setBackground(LiquidTheme.scenicBackground(this));
        prompt = GameUi.text(this, "", 28, true); prompt.setGravity(Gravity.CENTER); page.addView(prompt, new LinearLayout.LayoutParams(-1, GameUi.dp(this, 120)));
        page.addView(GameUi.text(this, "Press Back to cancel. Each input must be a controller button.", 15, false)); setContentView(page); updatePrompt();
    }

    private void updatePrompt() { prompt.setText("Press the button for\n" + ControllerProfile.NAMES[step]); }

    private void handleBack() {
        if (mappingDevice != null) { mappingDevice = null; renderList(); } else finish();
    }

    // Physical Back must cancel mapping before it can be recorded as a controller input.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (mappingDevice != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) handleBack();
            return true;
        }
        if (mappingDevice == null || event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0)
            return super.dispatchKeyEvent(event);
        if (event.getDeviceId() != mappingDevice.getId()) return true;
        mapping.keys[step++] = event.getKeyCode();
        if (step < mapping.keys.length) updatePrompt();
        else {
            try { mapping.save(this, mappingDevice); Toast.makeText(this, "Controller mapping saved.", Toast.LENGTH_SHORT).show(); }
            catch (Exception e) { Toast.makeText(this, "Could not save mapping.", Toast.LENGTH_LONG).show(); }
            mappingDevice = null; renderList();
        }
        return true;
    }
}
