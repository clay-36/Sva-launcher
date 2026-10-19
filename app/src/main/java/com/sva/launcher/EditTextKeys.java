package com.sva.launcher;

import android.app.Activity;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

/**
 * Lets D-pad up/down escape a single-line text field.
 *
 * EditText consumes DPAD_UP/DPAD_DOWN itself (it treats them as cursor movement), so once a
 * remote sends focus into a text field it can never leave — every control below becomes
 * unreachable. The settings dialog handles this in its own key handler, but full-screen
 * activities never see that code, so the guard lives here and MotionActivity installs it.
 *
 * Multi-line fields are left alone: there, up/down genuinely moves the caret between lines.
 */
final class EditTextKeys {
    private EditTextKeys() { }

    /** Returns true when the key was consumed, i.e. focus has moved on. */
    static boolean handOff(Activity activity, int keyCode) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN && keyCode != KeyEvent.KEYCODE_DPAD_UP) return false;
        View focused = activity.getCurrentFocus();
        if (!(focused instanceof EditText)) return false;
        boolean multiline = (((EditText) focused).getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (multiline) return false;
        int direction = keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN : View.FOCUS_UP;
        View next = focused.focusSearch(direction);
        return next != null && next != focused && next.requestFocus();
    }
}
