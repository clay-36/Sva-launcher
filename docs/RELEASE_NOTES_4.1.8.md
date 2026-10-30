# Sva Launcher 4.1.8

- **Activities recommendations**: Added an Activities tab for recommendation channels shared by installed Android TV apps, with artwork cards, refresh, visibility controls, and channel ordering. Improved focus and artwork transitions, and placed Activities after Live TV in the default tab order.
- **News and read mode**: Added a time-sorted RSS/Atom feed with duplicate-link removal, a headline carousel, fullscreen browsing, missing-image gradients, and article extraction using Mozilla Readability. Cached headlines appear immediately while feeds refresh.
- **Smoother Home browsing**: Added a focused-app spotlight with animated app artwork and subtle wallpaper movement.
- **Guide reminders and IPTV cache**: Added background programme reminders and bounded IPTV cache storage.
- **Documentation and demo capture**: Refreshed README screenshots and added an ADB feature-tour recording script that captures the main tabs, guide, article reader, and screensaver.

## Verification

- Built the debug APK and ran `lintDebug`; no lint errors or `NewApi` compatibility findings were reported.
- Verified News feed refresh and chronological mixing across configured sources on an Android TV emulator.
- Captured and inspected the ADB feature-tour clips and assembled video on the emulator.
- `bash -n scripts/record-feature-tour.sh` and `git diff --check` pass.

The compatibility check was static for the minimum supported API 26; runtime verification used an API 36 emulator.
