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
    private String focusedController;

    @Override public void onCreate(Bundle state) { super.onCreate(state); getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); BackNavigation.install(this, this::handleBack); renderList(); }

    private void renderList() {
        controllers.clear(); for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id); if (device == null) continue;
            if ((device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) controllers.add(device);
        }
        LinearLayout shell = SettingsUi.page(this, "Game controllers", "Configure buttons for your connected gamepads", this::handleBack);
        LinearLayout page = SettingsUi.scroll(shell);
        SettingsUi.heading(page, "Connected controllers");
        SettingsUi.hint(page, "Left-stick and directional-pad movement work automatically. Select a controller to remap its buttons.");
        Button preferred = null;
        if (controllers.isEmpty()) {
            SettingsUi.heading(page, "No controller connected");
            SettingsUi.hint(page, "Connect a gamepad by Bluetooth or USB, then choose Refresh.");
        }
        for (InputDevice device : controllers) {
            Button button = SettingsUi.action(this, device.getName(), () -> {
                focusedController = device.getDescriptor(); begin(device);
            });
            SettingsUi.add(page, button);
            if (preferred == null || device.getDescriptor().equals(focusedController)) preferred = button;
        }
        Button refresh = SettingsUi.action(this, "Refresh controllers", this::renderList);
        SettingsUi.add(page, refresh);
        setContentView(shell); (preferred == null ? refresh : preferred).requestFocus();
    }

    private void begin(InputDevice device) {
        mappingDevice = device; mapping = new ControllerProfile(); step = 0;
        LinearLayout shell = SettingsUi.page(this, "Map controller", device.getName(), this::handleBack);
        LinearLayout page = SettingsUi.scroll(shell);
        SettingsUi.heading(page, "Button mapping");
        prompt = SettingsUi.text(this, "", 28, false);
        prompt.setPadding(0, SettingsUi.dp(this, 32), 0, SettingsUi.dp(this, 32));
        page.addView(prompt);
        SettingsUi.hint(page, "Press a button on the selected controller. Press Back to cancel without saving.");
        setContentView(shell); updatePrompt();
    }

    private void updatePrompt() {
        prompt.setText("Button " + (step + 1) + " of " + mapping.keys.length + "\n\n" + ControllerProfile.NAMES[step]);
    }

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
