# Release review: 4.1.0 (version code 6)

Reviewed the combined staged, unstaged, and new source files on 18 October 2026.
No remaining release-blocking findings were identified in the reviewed scope.

## Findings resolved

- Clipped buffered TV guide cells to the schedule viewport so panning cannot cover the fixed channel column. Added a pixel-based regression test.
- Restored clipping on horizontal browsers and settings lists to keep scrolled items within their viewport.
- Removed the extra appearance-mode cache so restored preferences take effect immediately. Added theme assertions to the guide tests.
- Limited guide cell text to two lines with ellipsis, preventing vertical clipping; the detail header retains the full title. Added a text-layout height assertion.
- Corrected storage permission documentation after removal of the system-picker button.

## Verification

- 86 JVM tests passed.
- All 39 Android instrumentation tests passed on the API 36 arm64 TV emulator. This covers game import, guide navigation/reminders/state, IPTV fixture playback and ClearKey, lifecycle races, interrupted/disabled animations, native emulation, screensaver rendering/cleanup, and settings/navigation.
- All eight guide tests passed again after the final text-layout adjustment, including light/dark screenshots, a 1920×1080 offscreen hierarchy render, and viewport/text clipping assertions.
- The optimized, signed R8 release build passed the native runtime test and a cold launcher startup check using an isolated application ID. No crash entries were recorded for that release process.
- Production debug APK, signed release APK, and signed release AAB built successfully.
- Android lint: zero errors, 110 warnings remain.
- Native timing/pixel-format helper checks passed.
- Source audit, including reachable history: zero findings.
- APK content/license/ABI checks and ZIP/ELF 16 KB alignment checks: zero findings.
- APK signature verified against the existing release certificate (SHA-256: 6f5d126c32ec38c0e859897453b21b6465214a2e076c0042c192f41f1e00ee36).
- AAB signature verification and bundletool structural validation passed.
- Staged and unstaged whitespace checks passed.

One initial instrumentation startup ended in an ANR before any tests ran. The explicit-class full-suite rerun passed; the optimized release then started cold successfully. No production Java exception was recorded during this review.

## Artifacts and remaining release steps

Versioned APK, AAB, SHA-256 checksums, and the private R8 mapping are in `app/build/outputs/release/4.1.0/`. Do not distribute the mapping as a public app download.

The artifacts use package `com.sva.launcher`, version name `4.1.0`, and version code `6`.

Physical TV/OEM Home override, physical gamepad/audio behavior, system screensaver activation, and provider-specific live Widevine licenses were not verified in this review. IPTV playback tests use local fixtures; Widevine configuration is tested without a production license service. Other ABIs were built and inspected, but device runtime tests used arm64.

Changes remain uncommitted. Include all new production/resource files and tests when committing, then tag and retain that exact source revision before publishing these binaries. Nothing was pushed or published.

## Final Home appearance follow-up

Dark is now the default when no appearance preference is saved; explicitly saved System or Light choices remain supported. Home app-row focus padding now lives inside the scroll content, and focus scrolling reserves room for the scaled border at both ends.

Twelve targeted emulator checks passed after this adjustment: two new Home tests (including a light-system default check and first/last app focus bounds), six settings tests, and four motion tests. The 4.1.0 signed APK/AAB and checksums were rebuilt after these changes.
