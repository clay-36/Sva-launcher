package com.deo.launcher;

import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;

import org.json.JSONObject;

final class ControllerProfile {
    static final String[] NAMES = {"Up", "Down", "Left", "Right", "NES B", "NES A", "Select", "Start", "Pause"};
    static final int[] BITS = {4, 5, 6, 7, 0, 8, 2, 3, -2};
    static final int[] DEFAULT_KEYS = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_MODE};
    final int[] keys = DEFAULT_KEYS.clone();

    static ControllerProfile load(Context context, InputDevice device) {
        ControllerProfile profile = new ControllerProfile();
        if (device == null) return profile;
        String raw = context.getSharedPreferences("gameControllers", Context.MODE_PRIVATE)
                .getString(device.getDescriptor(), "");
        try {
            JSONObject value = new JSONObject(raw);
            for (int i = 0; i < profile.keys.length; i++) profile.keys[i] = value.optInt("k" + i, profile.keys[i]);
        } catch (Exception ignored) { }
        return profile;
    }

    void save(Context context, InputDevice device) throws Exception {
        JSONObject value = new JSONObject(); for (int i = 0; i < keys.length; i++) value.put("k" + i, keys[i]);
        context.getSharedPreferences("gameControllers", Context.MODE_PRIVATE).edit()
                .putString(device.getDescriptor(), value.toString()).apply();
    }

    int bitFor(int keyCode) {
        for (int i = 0; i < keys.length; i++) if (keys[i] == keyCode) return BITS[i];
        return -1;
    }
}
