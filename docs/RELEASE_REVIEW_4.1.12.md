# Release review: 4.1.12 (17)

Reviewed on 2026-11-08. Prepared on branch `release/4.1.12`.

## Changes reviewed and corrected

- Four-column Live TV browsing uses one `RecyclerView` with a full-width header
  and channel adapter. The title and categories scroll away with the channels;
  off-screen cards recycle instead of creating an entire provider catalogue.
- A 5,000-channel test verifies bounded child-view counts, row navigation,
  returning to categories, and switching to a smaller category. Pending channel
  focus requests are cancelled when leaving the tab.
- Xtream Codes imports live channels only, maps categories and XMLTV channel IDs,
  encodes credentials correctly, validates server URLs/ports, masks the password
  field, and reports failures without including account URLs. Missing categories
  do not discard usable live streams. Existing M3U sources are preserved.
- Aerial catalogue data is pinned to an upstream revision and checked byte for
  byte against that revision. Playback uses a texture surface, disables audio
  tracks/audio focus, releases resources on pause/exit, and bounds buffering and
  failed-clip retries. Source attribution, license text and ownership notes are
  included in settings, the APK and repository notices.
- Wallpaper and screensaver options save independently; photo mode remains
  available. Clock/weather/event overlays use the existing renderer. New
  preference keys participate in backup/restore.
- New labels and validation errors are translated in all 23 supported languages.

## Verification results

| Check | Result |
| --- | --- |
| JVM unit tests | 128 passed; no failures/errors |
| Full connected Android TV suite | 102 passed; no failures/errors |
| Final targeted ADB instrumentation | 7 passed, including real streamed wallpaper and the 5,000-channel grid |
| Optimized release JNI smoke | `NativeRuntimeTest` passed against R8 release code |
| Debug lint | 0 errors; 292 warnings (including programmatically created view constructor warnings) |
| Localization coverage | 23 languages, 688 translatable strings and 13 plurals; 0 errors |
| Native helper checks | Passed |
| Source and reachable-history audit | 0 findings |
| Bundled notices verification | Passed |
| Signed APK contents and alignment | Passed; all three ABIs present; 64-bit ELF/RELRO and APK ZIP alignment verified for 16 KB pages |
| APK signature | Verified |
| AAB signature | Verified (self-signed Android upload certificate) |
| APK version | 4.1.12, version code 17, min SDK 26, target SDK 36 |

ADB visual and interaction checks on the optimized release APK covered:

- Real streamed aerial Home wallpaper and screensaver preview with a clock.
- Xtream account entry through settings, saving, live channel/category refresh,
  XMLTV guide refresh, and playback of a local diagnostic stream.
- Four channel columns, clipping above the navigation dock, vertical remote
  movement, and the heading scrolling out of view.
- No AndroidRuntime crash during the checked flows.

A temporary copy of the optimized release APK was signed with the development
key for installation over the emulator's development build. The distributable
APK/AAB are signed with the configured release key. Test data was temporary;
the original emulator app preferences and files were backed up and restored.

## Release artifacts and remaining sign-off

Signed APK, signed AAB, source archive, checksums and R8 mapping are prepared under
`build/releases/4.1.12/`. The source archive is created from the release commit.
No push, tag, store submission or public publication is performed by this review.

The available device was an Android TV 720p emulator on Android 16. A physical TV,
a real Xtream provider account, hardware HDR/decoder behavior and system-wide
DreamService activation still require maintainer sign-off before public rollout,
as described in `RELEASING.md`. The emulator rejects the shell dream-start command
unless rooted; the existing system screensaver selection was restored after that
check. Screensaver preview and the shared lifecycle/overlay paths were exercised.
