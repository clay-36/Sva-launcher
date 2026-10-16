# Open-source release audit

Completed against the working tree on 2026-10-17. Review and tag the final source
before publishing; these results do not refer to an already-published release.

## Outcome

Debug and optimized unsigned release builds pass. The confirmed release-build,
native-runtime and input-validation issues below were fixed. Documentation,
bundled license notices and automated checks are in place. No production signing
key was used, and this audit did not push, tag or publish anything.

## Confirmed issues fixed

| Area | Finding and resulting behavior |
| --- | --- |
| Release signing | A hard-coded private keystore path prevented independent release builds. A clean checkout now builds an unsigned optimized APK; signing uses four explicit environment variables. |
| R8 / JNI | The referenced ProGuard file was missing. JNI entrypoints now have explicit keep rules and were exercised in an optimized release APK. |
| Gradle wrapper | Wrapper JAR/scripts did not match the declared Gradle version. Regenerated with Gradle 8.13; verified the official wrapper checksum and pinned the distribution checksum. |
| Native frame timing | The core loop had no frame pacing after audio writes became nonblocking. Added timed, interruptible waits based on the core's frame rate. |
| Native ownership / saves | UI-thread pause could write save RAM concurrently with emulation. Saves now run on the emulation worker; pause/stop notifications share its wait mutex. Concurrent libretro sessions are rejected. |
| Native colors | Pixel conversion did not match RGBX byte ordering. Added channel-order conversion and full 5/6-bit expansion, with host regression tests. |
| Game import | Added a 32 MB ROM limit, 12 MB artwork limit, bounded document-tree depth, image validation and atomic artwork replacement. Catalogue paths are constrained to generated basenames. |
| Image decoding | Logo and artwork sampling could leave extreme aspect-ratio images oversized. Sampling now bounds the longest dimension. |
| Guide inflation | XML/GZIP guide processing lacked a decompressed-byte limit. XML is capped at 128 MB after inflation; existing playlist/JSON limits remain. |
| Backup restore | Unrelated JSON and incompatible preference types could replace settings and break the next launch. Validate the format, known preference types and finite float values before restoring. |
| Calendar | All-day entries without DTEND now last a local day; old unbounded anniversaries reach the current display window; recurrence output is capped at 10,000 events. Valid empty calendars are accepted. |
| Back navigation | Five lint errors identified legacy Back interception. Added API 33+ callbacks while retaining documented physical-TV/pre-33 fallbacks; controller mapping consumes Back consistently. |
| System backup | Added explicit cloud/device-transfer exclusions alongside disabled automatic backup. Manual JSON exports remain available and unencrypted. |
| Publication hygiene | Removed the private signing-path assumption, expanded ignore rules, added source/history checks, dependency-update configuration and read-only CI. |
| Notices | Added GPL, LGPL, Apache, font, per-file native and pinned NDK notices to the APK; documented the resolved runtime dependencies and maintainer-confirmed asset rights. |

## Verification evidence

| Check | Result |
| --- | --- |
| JVM regression suite | **65 tests passed** |
| Android lint | **0 errors**, 113 warnings; no baseline or global suppression added |
| Debug APK | Built successfully |
| Optimized release APK | Built successfully without release-signing secrets |
| Device suite | **7 tests passed** on an Android API 36, arm64 emulator |
| R8 release JNI smoke test | Passed using a temporary copy signed with the standard development key |
| Native helper checks | Frame durations and RGBX/RGB565/XRGB1555 conversion checks passed |
| Release APK contents | Three ABIs, both native libraries per ABI and bundled notices present; no ROMs, keystores or `.env` artifacts |
| 16 KB checks | All arm64-v8a/x86_64 ELF load and RELRO checks passed; SDK `zipalign -c -P 16 4` passed |
| Source/history hygiene | 0 findings from the included high-confidence checker across the checkout and reachable local history |
| Maven advisory query | 40 resolved runtime/test/build Maven coordinates queried through OSV; no advisories returned at audit time ([snapshot](DEPENDENCY_AUDIT.json)) |
| Bundled notice consistency | Verified against root licenses, vendored headers and the pinned NDK |

Device tests cover screensaver rendering, portrait/landscape photo handling,
long captions, all overlay combinations, bounded photo windows, failed loads,
shutdown cleanup, native frame pacing, pause/resume, restart and single-session
ownership. The native tests use upstream NTSC diagnostic fixtures in the test APK;
the application APK contains no game ROMs.

The runtime emulator uses 4 KB pages. Static ELF/ZIP alignment checks are not a
substitute for running the application on a 16 KB device or emulator. The upstream
32-bit core retains 4 KB ELF alignment; the 16 KB checks apply to the 64-bit ABIs.

## Remaining release work and limits

- Run the physical-TV smoke checks in [RELEASING.md](RELEASING.md): real provider
  streams/Widevine, Home override, gamepad/audio behavior, file-picker fallbacks and
  system DreamService activation. API 26 and 32-bit runtime behavior were not
  exercised on hardware in this audit.
- Test on a 16 KB Android environment before claiming runtime compatibility there.
- Review the 113 non-blocking lint warnings. Most concern hard-coded UI text and
  style/resource suggestions; they are not all fixed by this audit.
- The advisory result is a dated database query, not proof that every dependency
  or native parser is vulnerability-free. Dependency updates remain enabled for
  review; no unrelated library upgrades were made during this audit.
- The included source checker is intentionally small. It does not OCR images,
  inspect unreachable/deleted remote Git objects, or replace an independent
  secret scanner or penetration test.
- User-provided HTTP sources and plaintext exported backups are intentional trust
  boundaries, documented in README and SECURITY.md. Full iCalendar semantics and
  every TV vendor's behavior are outside this regression suite.
- Asset redistribution rights were confirmed by the maintainer; see
  [ASSET_PROVENANCE.md](ASSET_PROVENANCE.md). No independent copyright/title audit
  was performed.
- Choose the public version/tag, sign with the durable release key, attach matching
  complete source and enable private vulnerability reporting. The GitHub workflow
  has been added and its commands run locally; no hosted CI run was triggered.

## Reproduce

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./scripts/check-native.sh
python3 scripts/audit-source.py --history
python3 scripts/update-notices.py --check
python3 scripts/check-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --zipalign /path/to/sdk/build-tools/36.0.0/zipalign
./gradlew connectedDebugAndroidTest
```

Use JDK 17 and the pinned SDK/NDK described in README. Set `ANDROID_HOME` when
checking the NDK notice copy. APK/Gradle lint reports are generated under
`app/build/outputs` and `app/build/reports` and are intentionally ignored by Git.

## Primary references

- [Gradle 8.13 wrapper checksum](https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256)
- [Gradle 8.13 distribution checksum](https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256)
- [Android JNI keep-rule guidance](https://developer.android.com/topic/performance/app-optimization/keep-rule-examples)
- [Android 16 KB page-size checks](https://developer.android.com/guide/practices/page-sizes)
- [OSV query API](https://google.github.io/osv.dev/api/)
- [Media3 security advisories](https://github.com/androidx/media/security)
