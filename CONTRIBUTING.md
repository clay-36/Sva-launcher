# Contributing to Sva Launcher

Use a focused pull request that explains the problem, resulting behavior and how
you verified it. For substantial changes, discuss the design in an issue first.

## Development checks

Use JDK 17, Android SDK 36, Build Tools 36.0.0 and NDK 28.2.13676358. Build with the
checked-in Gradle wrapper; release builds do not require a signing key.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./scripts/check-native.sh
python3 scripts/audit-source.py
```

With a TV emulator or connected test device:

```sh
./gradlew connectedDebugAndroidTest
```

Native helper checks use a C++17 compiler. Device tests include the upstream NTSC
diagnostic fixtures in the test APK only. Screensaver render checks save PNGs in
the test app's `cache/screensaver-art-qa` directory for visual inspection.

## Reviewing changes

- Keep TV remote/D-pad navigation, focus visibility and Back behavior usable.
- Check UI changes at 1280×720, including a compact logical layout, and attach
  screenshots containing demonstration data rather than private content.
- Add regression coverage for behavior changes. Run relevant tests and lint;
  explain any remaining limitation in the PR.
- Keep bitmap ownership, download limits and background-work cancellation explicit.
- Update notices and provenance when adding fonts, artwork or dependencies.
- Do not commit signing keys, local SDK paths, backups, private URLs, commercial
  games or firmware. The small source-hygiene checker is not a full secret scanner.

## Bug reports

Include the device/TV model, Android version, app version, reproduction steps and
expected/actual behavior. Use sanitized sample feeds when useful. Remove query
credentials, tokens, device locations and personal photographs from logs and images.
For vulnerabilities, follow [SECURITY.md](SECURITY.md) instead of posting exploit
instructions or private data publicly.
