# Accessibility disclosure pre-production verification — 2026-11-06

Release candidate: **4.1.10 / version code 15**, on `fix/accessibility-prominent-disclosure`, following disclosure commit `01d82ac`.

## Fixes following the initial review

- Added all four disclosure strings to each of the 22 non-English resource sets. The API, accessed events/package names, local-only data handling, optional behavior and consent/decline instructions are translated.
- Fixed disclosure contrast in light mode. Both the custom title and message explicitly use the active palette; the dialog uses the selected theme. Screenshot inspection caught the original pale title/body on a pale surface.
- Corrected the navigation test baseline to include Activities after Live TV. The two-step move-to-first test retains the expected `news,activities,live_tv,...` order. Remote picker tests now wait for the modal to close before opening another picker and assert each move actually selected the first position.
- Unrelated launcher UI tests now explicitly start after the disclosure has been shown, using a shared test-launch helper. They do not grant accessibility consent. Dedicated disclosure tests still exercise genuine first launch.
- Provisioned TV-listing and reminder permissions in the corresponding test fixtures. The recommendation test no longer depends on an accidentally pre-granted permission, and the reminder behavior test no longer opens an unhandled Android alarm-permission screen.
- Made the recommendation artwork fixture use the actual application ID and restricted legacy instrumentation discovery to the app test package.
- Updated Gradle to version 4.1.10 / code 15 and added release notes.

## Verification

**All local automated checks pass.** Physical-TV validation, localization review and Play submission operations remain separate release steps.

- 128 JVM tests passed, including backup consent export/import protection.
- All 95 connected Android TV tests passed: zero failures, errors or skips (216.325 seconds of test execution on the API 36 TV emulator).
- Android lint passed with zero errors (288 existing warnings).
- Localization coverage/format/newline checks passed for all 23 languages: 663 translatable strings and 13 plurals, zero errors. Device resource checks also ensure the disclosure does not fall back to English in another selected language.
- Disclosure layout, title/message colors, complete button labels and initial remote decline were verified for all 23 languages in both themes (46 combinations). Arabic and Malayalam light-mode screenshots and French dark-mode screenshots were visually inspected; text and buttons are readable. Verified screenshots are archived at `/tmp/sva-4110-disclosure-verified.tar`.
- Both formerly failing navigation tests, recommendation rows, Guide reminders, Home UI, playback, language recreation/direction, native runtime, screensaver and lifecycle checks pass in the complete connected run.
- Native timing/pixel-format checks passed.
- Source hygiene, including reachable Git history: zero findings.
- Final normal production-ID debug, optimized release APK and release app bundle builds passed. APK content/licenses/packaged-ABI/zip alignment/16 KB ELF checks found zero issues. The app bundle is unsigned. Release APK metadata confirms 4.1.10 / code 15.

Reproducible build command: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease --offline`.

Connected tests were run with `./gradlew -I /tmp/sva-accessibility-runtime.gradle connectedDebugAndroidTest --offline`. The temporary init script changes only the application ID for runtime verification; the checked-in test runner limits discovery to `com.sva.launcher`.

Device test evidence: `app/build/outputs/androidTest-results/connected/debug/TEST-Television_720p(AVD) - 16-_app-.xml` and `app/build/reports/androidTests/connected/debug/index.html`.

## Final unsigned artifacts

| Artifact | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | `2dfd2ca2e97a5c198d0581e9782c2e46d3c3cf57c34757d841659269ecafaa55` |
| `app/build/outputs/bundle/release/app-release.aab` | `84b0c6f82894582d272af66e80358b4f0bfd95116c976a0a91d4126d9ea2adf5` |

## Runtime scope

The TV emulator has a differently signed existing installation. Device checks use a temporary Gradle init script changing only the application ID to `com.sva.launcher.accessibilitycheck`, preserving that installation. All application sources are included. Production-ID builds and artifact checks run without the init script.

The focused tests exercise first-launch disclosure, actual Back/decline behavior, the real settings entry point, affirmative consent, consent withdrawal, settings fallback behavior, backup protection, and the actual service callback’s supported-package filtering/throttling/consent checks. The translated layout test checks button visibility, full labels, message/title palette, and default remote decline for every supported language in both dark and light modes; it saves screenshots to the test app’s `cache/disclosure-screenshots` directory.

A separate OS integration check passed on the emulator: after app consent and enabling the registered service in the emulator settings database, pressing Home returned from the stock TV launcher to Sva. Disabling the service left Home on the stock launcher. No system accessibility service remains enabled by the test. This verifies real OS event delivery; it does not replace the manual Android settings UI flow on physical TVs.

## Remaining release operations

- Verify manual service enablement, Home override and disablement on physical supported TVs, including upgrading an installation whose service was already enabled. Those physical-device checks are not claimed here.
- Review translated policy text with the normal localization review process.
- Follow [Play review instructions](ACCESSIBILITY_PLAY_REVIEW.md) for store listing/declaration updates and a reviewer video showing both consent paths and actual feature behavior.
- Build/sign the exact approved revision using the registered key and verify the signed artifacts before submitting to Play. These local artifacts are unsigned; nothing has been uploaded, published or submitted.

The original music-work stash remains untouched. Follow-up code, tests, translations and documentation are recorded in the 4.1.10 follow-up commit; the original disclosure commit has not been amended.
