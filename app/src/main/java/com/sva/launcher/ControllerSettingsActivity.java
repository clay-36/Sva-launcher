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

public final class ControllerSettingsActivity extends MotionActivity {
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
        LinearLayout shell = SettingsUi.page(this, AppLanguage.get(R.string.ui_game_controllers), AppLanguage.get(R.string.ui_configure_buttons_for_your_connected_gamepads), this::handleBack);
        LinearLayout page = SettingsUi.scroll(shell);
        SettingsUi.heading(page, AppLanguage.get(R.string.ui_connected_controllers));
        SettingsUi.hint(page, AppLanguage.get(R.string.ui_left_stick_and_directional_pad_movement_work_automatically_selec));
        Button preferred = null;
        if (controllers.isEmpty()) {
            SettingsUi.heading(page, AppLanguage.get(R.string.ui_no_controller_connected));
            SettingsUi.hint(page, AppLanguage.get(R.string.ui_connect_a_gamepad_by_bluetooth_or_usb_then_choose_refresh));
        }
        for (InputDevice device : controllers) {
            Button button = SettingsUi.action(this, device.getName(), () -> {
                focusedController = device.getDescriptor(); begin(device);
            });
            SettingsUi.add(page, button);
            if (preferred == null || device.getDescriptor().equals(focusedController)) preferred = button;
        }
        Button refresh = SettingsUi.action(this, AppLanguage.get(R.string.ui_refresh_controllers), this::renderList);
        SettingsUi.add(page, refresh);
        setContentView(shell); (preferred == null ? refresh : preferred).requestFocus();
    }

    private void begin(InputDevice device) {
        mappingDevice = device; mapping = new ControllerProfile(); step = 0;
        LinearLayout shell = SettingsUi.page(this, AppLanguage.get(R.string.ui_map_controller), device.getName(), this::handleBack);
        LinearLayout page = SettingsUi.scroll(shell);
        SettingsUi.heading(page, AppLanguage.get(R.string.ui_button_mapping));
        prompt = SettingsUi.text(this, "", 28, false);
        prompt.setPadding(0, SettingsUi.dp(this, 32), 0, SettingsUi.dp(this, 32));
        page.addView(prompt);
        SettingsUi.hint(page, AppLanguage.get(R.string.ui_press_a_button_on_the_selected_controller_press_back_to_cancel_w));
        setContentView(shell); updatePrompt();
    }

    private void updatePrompt() {
        prompt.setText(AppLanguage.get(R.string.ui_button_1_s_of_2_s_3_s, (step + 1), mapping.keys.length, ControllerProfile.names()[step]));
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
            try { mapping.save(this, mappingDevice); Toast.makeText(this, AppLanguage.get(R.string.ui_controller_mapping_saved), Toast.LENGTH_SHORT).show(); }
            catch (Exception e) { Toast.makeText(this, AppLanguage.get(R.string.ui_could_not_save_mapping), Toast.LENGTH_LONG).show(); }
            mappingDevice = null; renderList();
        }
        return true;
    }
}
