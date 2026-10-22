# Sva Launcher 4.1.5

Version code: 10

- **23 Interface Languages**: Added full interface localization with country flag identifiers for 🇺🇸/🇬🇧 English, 🇮🇳 Hindi, 🇪🇸 Spanish, 🇫🇷 French, 🇸🇦 Arabic, 🇧🇷 Portuguese, 🇨🇳 Simplified Chinese, 🇹🇼 Traditional Chinese, 🇩🇪 German, 🇯🇵 Japanese, 🇰🇷 Korean, 🇷🇺 Russian, 🇮🇩 Indonesian, 🇹🇷 Turkish, 🇧🇩 Bengali, 🇵🇰 Urdu, 🇮🇳 Tamil, 🇮🇳 Telugu, 🇮🇳 Marathi, 🇮🇳 Gujarati, 🇮🇳 Kannada, 🇮🇳 Malayalam, and 🇮🇳 Punjabi.
- **System & In-App Language Selection**: Follow the TV's system language or select a dedicated language under **Settings → System → Language** (synchronized via Android 13+ `LocaleManager` with local fallback for earlier Android TV releases).
- **RTL Layout Support**: Full mirrored right-to-left layout and remote D-pad navigation for Arabic and Urdu.
- **Remote D-Pad Navigation in Settings**: Fixed child category boundary navigation so pressing `DPAD_UP` from any child control or top of multiline inputs returns focus to the parent rail tab across all 15 settings sections.
- **YouTube Saved Shortcuts Navigation**: Enabled horizontal remote D-pad cycling between `Title`, `Art`, and `Remove` action buttons, and direct transition from `Add to Saved` to `Title`.
- **Screensaver Localization**: Localized atmosphere theme selection and status feedback in Sva Memories settings.
- **Calendar Error Handling**: Converted calendar feed HTTP error detection to typed exceptions so feed error handling is completely locale-independent.
- **UI Spacing & Plural Grammar**: Fixed delimiter spacing in YouTube cards, badges, and weather units, and wired Android quantity plurals for channel and event counts.

## Verification

- Passed all 120+ JVM unit tests and 40 Android test suite runs.
- Passed 16 on-device instrumentation tests in `SettingsNavigationTest` (including all 15 settings UP navigation checks and YouTube saved shortcuts action cycling), 3 in `LanguageUiTest`, and 4 in `HomeUiTest`.
- Passed `scripts/check-localization.py` (0 errors across 23 languages) and `scripts/audit-source.py` (0 hygiene findings).
