# Sva Launcher 4.1.7

Version code: 12

- **OEM TV Soft Input Handling & Dialog Focus**: Configured window soft input resize modes (`SOFT_INPUT_ADJUST_RESIZE`) across custom dialogs, full-screen settings, and the game title editor to prevent OEM TV firmwares (e.g., TCL P615) from stealing focus away from `EditText` fields to dialog action buttons when the on-screen keyboard appears.
- **TV Remote Click Activation for Text Inputs**: Added explicit click listeners across settings input fields and backup filename inputs in the TV File Picker to reliably trigger `InputMethodManager.showSoftInput` when selected using TV remote D-pad Center / OK or air-mouse clicks.
- **D-Pad Text Field Navigation Trapping Fix**: Excluded `EditText` elements from custom vertical focus navigation neighbor overrides and rail-tab horizontal linkage in settings. This ensures remote D-pad Left/Right and Up/Down navigation correctly maneuvers the text cursor and keyboard suggestions inside the input field rather than accidentally jumping focus to parent settings tabs.
- **Multi-tiered Storage Permission Fallback (Android 11+)**: Upgraded the file picker storage permission request ladder to seamlessly fall through from per-app all-files access (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`), to general all-files access (`ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`), and finally to general application details settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) for OEM TV builds that omit dedicated all-files access screens.
- **Non-GMS Geocoder Availability Guard**: Guarded weather location reverse-geocoding calls with `Geocoder.isPresent()` checks to prevent `IOException: Service not available` and silent weather look-up failures on de-Googled, Fire TV, or AOSP TV devices lacking Google Play Services.

## Verification

- Passed all 126 JVM unit tests (including backup integrity, catalogue sanitization, and calendar feeds).
- Passed `scripts/audit-source.py --history` (0 hygiene findings across full history).
- Passed `./scripts/check-native.sh` (native timing and pixel-format checks passed).
- Passed `scripts/check-localization.py` (0 errors across 23 languages, 620 strings, 13 plurals).
- Clean compilation on debug and release build variants with zero lint errors.
