# Sva Launcher — Developer Guide

This document provides architecture notes, build instructions, testing procedures, and engineering guidelines for developers contributing to or maintaining **Sva Launcher**.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites & Toolchain](#prerequisites--toolchain)
3. [Building the Project](#building-the-project)
4. [Testing & Verification](#testing--verification)
5. [Emulator & Hardware Testing](#emulator--hardware-testing)
6. [Codebase Map](#codebase-map)
7. [Android TV UI Engineering Guidelines](#android-tv-ui-engineering-guidelines)
8. [Release Builds & Signing](#release-builds--signing)
9. [Continuous Integration (CI)](#continuous-integration-ci)
10. [Hygiene & Compliance Checklist](#hygiene--compliance-checklist)
11. [IPTV & DRM Stream Configuration](#iptv--drm-stream-configuration)

---

## Architecture Overview

Sva Launcher is designed specifically for **Android TV (API 26+)** with a remote-first interface. It deliberately minimizes heavy dependencies (avoiding complex Leanback fragments or Jetpack Compose overhead) to guarantee high-performance, fluid 60fps rendering even on low-cost TV chipsets (e.g., Amlogic, MediaTek, Realtek).

### Interface localization

`AppLanguage` resolves the 23 supported languages, formats display text, and caches
the localized application context. Standard activities inherit `LocalizedActivity`
(through `MotionActivity` where applicable); the DreamService wraps its own context.
Android 13+ uses `LocaleManager` and the declared locale configuration to synchronize
the in-app selector with Android settings. Older TVs store the override in launcher
preferences. Configuration changes invalidate the cached context and recreate
screens while preserving drafts and navigation. The language preference travels
with JSON backups; the internal migration marker does not.

Add user-facing text to `values/strings.xml` and each translated `strings.xml`.
Use numbered placeholders and Android plurals for counts. Keep protocol values,
URLs, date parsing patterns, preference keys, and supplied content out of translation
resources. Domain helpers retain English diagnostic fallbacks for JVM tests through
`AppLanguage.text`; visible Android text comes from resources. Use `AppLanguage.locale()`
for display formatting and fixed locales for machine-readable formats.

All translations are bundled, including in app bundles, so changing languages never
requires a download. Initial translations were machine generated; native-speaker
wording improvements are welcome. Portuguese uses Brazilian wording and Punjabi
uses Gurmukhi. Chinese has separate Hans and Hant resources. Arabic and Urdu use
mirrored layout and remote focus navigation; programme timelines retain chronological
left-to-right coordinates and game controls retain their physical directions.

Run `python3 scripts/check-localization.py` to verify coverage and placeholder
contracts. `LanguageUiTest` verifies all language resources, plural formatting,
representative screens, and draft preservation after a language change. It can save
screenshots to the app cache with instrumentation argument `-e screenshots true`.

```
                      ┌──────────────────────────────────────┐
                      │             MainActivity             │
                      │  (Tab Shell, Navigation, Wallpaper)   │
                      └──────────────────┬───────────────────┘
                                         │
         ┌───────────────────┬───────────┴───────────┬───────────────────┐
         ▼                   ▼                       ▼                   ▼
┌─────────────────┐ ┌─────────────────┐     ┌─────────────────┐ ┌─────────────────┐
│     Home Tab    │ │   Live TV / EPG │     │    Games Tab    │ │   Events Tab    │
│ App shortcuts,  │ │ IPTV repository,│     │ Nestopia UE C++ │ │ iCal calendar,  │
│ weather, clock  │ │ Media3 ExoPlayer│     │ embedded core   │ │ lunar data      │
└─────────────────┘ └─────────────────┘     └─────────────────┘ └─────────────────┘
         │                   │                       │                   │
         └───────────────────┼───────────────────────┴───────────────────┘
                             ▼
     ┌──────────────────────────────────────────────────────────────┐
     │              Shared Foundation & Styling                     │
     │  - LiquidTheme (dynamic theme colors, system typography)     │
     │  - Motion (fluid state animators, focus scales, elevation)   │
     │  - SettingsUi (remote-first settings controls, forms)        │
     │  - ScreensaverView / SvaDreamService (Sva Memories)          │
     └──────────────────────────────────────────────────────────────┘
```

### Key Technical Pillars

1. **Lightweight Android Layer:**
   - Standard Android View hierarchy with custom-drawn drawables (`GlassDrawable`, code-drawn shapes, vector paths) instead of heavy asset-based themes.
   - Low memory footprint: Bitmaps are sampled according to display target size, cached with explicit eviction, and recycled cleanly.
2. **Native Emulation Layer (Nestopia UE):**
   - Embedded C++ core located under `third_party/nestopia` and compiled via the Android NDK (`app/src/main/jni/`).
   - Renders NES frames directly into an Android Surface via a clean JNI bridge.
   - Pinned source code ensures fully deterministic, reproducible offline builds without fetching external binaries at build time.
3. **IPTV & Streaming Playback Engine:**
   - Built on AndroidX **Media3 ExoPlayer** (`1.8.1`).
   - Supports HLS (M3U8) and DASH (MPD) manifests with HTTP header injection.
   - DRM modular support for Widevine (`com.widevine.alpha`) and ClearKey (`org.w3.clearkey`) with per-channel KODIPROP / VLC override support.

---

## Prerequisites & Toolchain

Ensure your development environment meets the pinned toolchain requirements:

| Component | Pinned Version | Notes |
| --- | --- | --- |
| **JDK** | 17 | Required for Gradle 8.13 and AGP 8.12.2 compatibility |
| **Android SDK Platform** | API 36 (Android 16) | Target & compile SDK |
| **Android SDK Build-Tools**| `36.0.0` | Pinned build tools |
| **Android NDK** | `28.2.13676358` | Specified in `app/build.gradle` (`ndkVersion`) |
| **Gradle** | `8.13` | Managed by Gradle Wrapper (`./gradlew`) |
| **Android Gradle Plugin** | `8.12.2` | Defined in root `build.gradle` |
| **C++ Compiler** | C++17 capable | Required for `./scripts/check-native.sh` |
| **Python** | 3.8+ | Required for source audits and APK inspection scripts |

---

## Building the Project

### Clone Repository

```sh
git clone https://github.com/clay-36/Duo-launcher-iptv.git
cd Duo-launcher-iptv
```

### Build Debug APK

```sh
./gradlew assembleDebug
```

The debug APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Offline Builds

Once dependencies and wrapper files are downloaded, you can build without network access:

```sh
./gradlew assembleDebug --offline
```

---

## Testing & Verification

Always run the full local verification pipeline before submitting pull requests:

```sh
# 1. Run JVM unit tests and Android Lint
./gradlew testDebugUnitTest lintDebug

# 2. Verify debug and unsigned release packaging
./gradlew assembleDebug assembleRelease

# 3. Check native Nestopia C++ compilation and headers
./scripts/check-native.sh

# 4. Run source hygiene and license audit
python3 scripts/audit-source.py --history

# 5. Inspect generated release APK for sizing and forbidden assets
python3 scripts/check-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

### Performance checks

See [UI performance](docs/PERFORMANCE.md) for the optimization details, large-data
regression scenarios, and repeatable release startup measurements.

### Connected Device & Instrumentation Tests

When an Android TV device or emulator is running:

```sh
./gradlew connectedDebugAndroidTest
```

- Verifies screensaver rendering, native runtime execution, and NTSC diagnostic fixtures.
- Diagnostic fixtures are bundled only into the test APK (`androidTest.assets.srcDirs`); they are excluded from production builds.
- Covers settings navigation (`SettingsNavigationTest`), UI performance and bounded collections (`PerformanceUiTest`), background task loader workflows (`TaskLoaderUiTest`), progress indicator lifecycle (`TaskProgressTest`), TV guide interactions (`GuideUiTest`), language/RTL layouts (`LanguageUiTest`), and IPTV playback (`IptvPlaybackTest`).

---

## Emulator & Hardware Testing

### Recommended Emulator Configuration

- **Device Definition:** Android TV (1080p or 720p)
- **API Level:** API 34 (Android 14) or API 36 (Android 16)
- **ABI:** `x86_64` or `arm64-v8a` (NDK builds native targets for both)

### Installing on Connected Emulator or TV

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.sva.launcher/.MainActivity
```

### Remote Navigation Simulation via ADB

TV apps depend completely on the D-pad and hardware keys. Use these key events to navigate without a physical remote:

| Action | ADB Command |
| --- | --- |
| **D-pad Up** | `adb shell input keyevent KEYCODE_DPAD_UP` (19) |
| **D-pad Down** | `adb shell input keyevent KEYCODE_DPAD_DOWN` (20) |
| **D-pad Left** | `adb shell input keyevent KEYCODE_DPAD_LEFT` (21) |
| **D-pad Right** | `adb shell input keyevent KEYCODE_DPAD_RIGHT` (22) |
| **Select / OK** | `adb shell input keyevent KEYCODE_DPAD_CENTER` (23) |
| **Back** | `adb shell input keyevent KEYCODE_BACK` (4) |
| **Home** | `adb shell input keyevent KEYCODE_HOME` (3) |

### Taking Screen Captures

```sh
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png ./screen.png
```

---

## Codebase Map

| Package / File | Purpose |
| --- | --- |
| `app/src/main/java/com/sva/launcher/` | **Main application code** |
| ├── `MainActivity.java` | Core launcher activity, navigation bar, wallpaper loading, tab management, settings dialog |
| ├── `Motion.java` | System-aligned animations, focus scale (`StateListAnimator`), alpha transitions, dialog styles |
| ├── `LiquidTheme.java` | Glass UI palette, light/dark color resolution, typography helper, dimension utilities |
| ├── `SettingsUi.java` | Remote-first settings form elements (toggles, choice dialogs, text fields, action buttons) |
| ├── `IptvRepository.java` | M3U playlist parsing, XMLTV/JSON guide loading, channel caching and filter management |
| ├── `PlaybackActivity.java` | Full-screen ExoPlayer video player, channel switcher, DRM configuration handler |
| ├── `PlaybackLauncher.java` | Intent dispatcher for video playback |
| ├── `GuideActivity.java` | Full-screen TV guide schedule viewer |
| ├── `GuideTimeline.java` | Schedule calculations, time slot management, now indicator, program bounds |
| ├── `GuideReminders.java` | In-app reminders for upcoming TV broadcasts |
| ├── `ChannelAdapter.java` | Live TV category and channel grid adapter |
| ├── `GameCardAdapter.java` | RecyclerView adapter for game library cards with bounded view recycling |
| ├── `GameLibraryActivity.java`| NES ROM import, metadata display, game launcher |
| ├── `GameDetailActivity.java` | Individual game details and play launcher |
| ├── `GameUi.java` | Game card styling, cartridge art, badges |
| ├── `NesEmulatorView.java` | SurfaceView hosting native Nestopia emulation output |
| ├── `ControllerSettingsActivity.java` | Gamepad detection, button mapping, controller testing |
| ├── `PublicCalendarRepository.java` | Public Google Calendar (iCal) synchronization, lunar illumination math |
| ├── `ScreensaverView.java` | 9-atmosphere "Sva Memories" photo screensaver renderer |
| ├── `ScreensaverSettingsActivity.java` | Atmosphere selector, photo preview, idle timeout setup |
| ├── `SvaDreamService.java` | Android TV DreamService integration for system-wide idle screensaver |
| ├── `TaskProgress.java` | Noncritical indeterminate progress indicator with delayed reveal (300 ms) |
| ├── `TvFilePickerActivity.java` | Remote-friendly file picker for local storage and USB drives |
| └── `BackNavigation.java` | Predictable Android TV back-stack management |
| `app/src/main/jni/` | **NDK C++ native bridge** (`Android.mk`, `Application.mk`, JNI wrapper) |
| `third_party/nestopia/` | **Pinned Nestopia UE C++ core** (`source/`, `headers/`, `NTSC filters`) |
| `scripts/` | **Development, verification, and audit tools** |
| ├── `audit-source.py` | Validates licenses, checks forbidden strings, audits file hygiene |
| ├── `check-apk.py` | Asserts release APK size, validates stripped native symbols, checks manifest |
| ├── `check-native.sh` | C++ test build of native source independent of Gradle |
| ├── `generate-iptv-fixtures.py`| Creates test M3U8 and XMLTV fixtures for testing |
| ├── `measure-startup.py` | Measures process-cold startup latency, PSS memory, and frame timing |
| └── `update-notices.py` | Aggregates and updates third-party license notices |
| `docs/` | **Deep-dive documentation** |
| ├── `PERFORMANCE.md` | Architecture notes, bounded view benchmarks, and startup measurements |
| ├── `RELEASING.md` | Release workflow, tagging, keystore management |
| ├── `RELEASE_AUDIT.md` | Detailed dependency & security checklist |
| ├── `THIRD_PARTY.md` | Complete inventory of upstream libraries & licenses |
| └── `ASSET_PROVENANCE.md` | Origin and licensing details for all embedded fonts and icons |

---

## Android TV UI Engineering Guidelines

### 1. Remote-First Focus Architecture

- Every interactive control must be focusable (`setFocusable(true)`, `setFocusableInTouchMode(false)`).
- Never hide the focus highlight. Use [`Motion.interaction(view, contained)`](app/src/main/java/com/sva/launcher/Motion.java) or [`SettingsUi.navigationStates(...)`](app/src/main/java/com/sva/launcher/SettingsUi.java) to provide high-contrast focus rings and elevation.
- Focus movement should be continuous and intuitive. Use explicit `setNextFocusLeftId()`, `setNextFocusRightId()`, `setNextFocusUpId()`, and `setNextFocusDownId()` where linear layout traversal is ambiguous.

### 2. Container Clipping & Focus Scaling Rules

- Focus scale effects (`1.02f` or `1.08f`) and elevation drop-shadows require clearance to avoid clipping:
  - Form rows in settings must have at least `8dp` horizontal margins (`SettingsUi.add(...)`).
  - Containers hosting scaling elements inside a scroll view must disable clipping on the content form (`form.setClipChildren(false)` and `form.setClipToPadding(false)`).
- **CRITICAL: Never set `setClipChildren(false)` on a `ScrollView`!**
  - A `ScrollView` **must** clip its children to its visible viewport bounds (`clipChildren = true`, the Android default).
  - Setting `scroll.setClipChildren(false)` causes views that scroll above or below the scroll view bounds to bleed over headers, dividers, and action buttons.
  - Set `scroll.setClipToPadding(false)` so items can animate into the scroll view's padding area without exceeding the view bounds.

### 3. Screen Densities & Overscan

- Test UI at both standard 1080p (`1920x1080`, `320dpi`) and 720p (`1280x720`, `213dpi`).
- Respect TV display safe zones (at least `24dp` to `48dp` margin around the edges of the screen).

### 4. Animations & Motion

- Navigation, focus highlights, dialogs, and information panels use short, interruptible animations.
- Motion timing and interpolators respect Android system animation settings (`Settings.Global.ANIMATOR_DURATION_SCALE` and `TRANSITION_ANIMATION_SCALE`).

---

## Release Builds & Signing

### Unsigned Release Builds

You can always build an optimized release APK and AAB without signing keys:

```sh
./gradlew assembleRelease bundleRelease
```

Outputs:
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `app/build/outputs/bundle/release/app-release.aab`

### Signed Release Builds

To sign release artifacts locally or in CI, supply the following environment variables:

```sh
export SVA_KEYSTORE_PATH="/path/to/release.keystore"
export SVA_STORE_PASSWORD="keystore-password"
export SVA_KEY_ALIAS="key-alias"
export SVA_KEY_PASSWORD="key-password"

./gradlew assembleRelease bundleRelease
```

When all four variables are non-empty, Gradle automatically signs the APK as `app-release.apk` and signs the AAB.

For full release procedures, see [docs/RELEASING.md](docs/RELEASING.md).

---

## Continuous Integration (CI)

The GitHub Actions workflow is located at [`.github/workflows/android.yml`](.github/workflows/android.yml).

- Runs on every `push` and `pull_request` targeting `main`.
- Automates:
  - Toolchain verification (JDK 17, NDK 28, Android SDK 36).
  - Unit tests and Android Lint.
  - Native verification via `./scripts/check-native.sh`.
  - Source hygiene audit via `audit-source.py`.
  - Debug APK, unsigned release APK, and AAB assembly.
  - Artifacts are retained for 14 days in the run summary.
- Manual triggers (**Android build and checks** in GitHub Actions):
  - Choose `apk`, `aab`, or `both` packaging formats.
  - Enable **Sign release outputs** on `main` using repository secrets.
  - See [workflow signing setup](docs/RELEASING.md#configure-workflow-signing) and [CI build downloads](docs/RELEASING.md#download-ci-builds).

---

## Hygiene & Compliance Checklist

Before committing code:

1. **No Proprietary ROMs or Copyrighted Feeds:** Never commit proprietary `.nes` ROMs, BIOS files, copyrighted video links, or private tokens.
2. **Preserve Documentation Integrity:** Maintain all existing class comments, notices, and method docstrings.
3. **GPLv3 Compliance:** Any modifications to Sva Launcher code are licensed under GPLv3. The Nestopia UE core source remains GPL-2.0-or-later. Third-party notices must be kept up to date in [NOTICE](NOTICE).
4. **Audit Pass:** Ensure `python3 scripts/audit-source.py` passes with zero warnings.

---

## IPTV & DRM Stream Configuration

Sva Launcher's IPTV playback engine supports multiple M3U/M3U8 playlists and multiple XMLTV, XMLTV.GZ, or JSON programme guides. Enter one source URL per line in **Settings → Live TV**. Channels from playlists are combined, duplicate channel identities are skipped, and guide schedules are merged across sources. A failed source does not prevent successful sources from loading.

Playlists support embedded stream metadata, custom HTTP request headers, and DRM license configuration.

### Widevine (DASH & FMP4/CMAF HLS)

Global Widevine settings can be configured in **Settings → Playback & DRM**. Channels can also specify per-channel Widevine license endpoints and request headers via Kodi properties in the M3U playlist (per-channel settings override global defaults):

```m3u
#EXTM3U
#EXTINF:-1 tvg-id="channel.one" tvg-logo="https://img.example/logo.png" group-title="News",Channel One
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://license.example/widevine|Authorization=Bearer%20token&Origin=tv.example|R{SSM}|
https://stream.example/channel/manifest.mpd
```

### Custom Request Headers & Stream Type Hints

Playlists can supply stream request headers and an explicit manifest hint (HLS or DASH) for stream URLs that lack standard file extensions:

```m3u
#EXTINF:-1,Authenticated HLS
#EXTVLCOPT:http-user-agent=Sva TV
#EXTVLCOPT:http-referrer=https://tv.example/
#KODIPROP:inputstream.adaptive.manifest_type=hls
#KODIPROP:inputstream.adaptive.stream_headers=Authorization=Bearer%20token&Origin=https%3A%2F%2Ftv.example
https://stream.example/live
```

- **Header precedence:** URL suffixes (e.g., `|Authorization=Bearer%20token`) > Kodi `stream_headers` > `#EXTVLCOPT` options.
- **Percent-encoding:** Individual header values must be percent-encoded (including literal `&`, `=`, and `+`).
- **Scope:** Stream headers apply to manifests, media segments, and HLS encryption keys. DRM license headers are configured independently within `license_key`.
- **Manifest hints:** `manifest_type` accepts `hls` or `mpd`/`dash`. `mime_type` also accepts standard MIME types.

### ClearKey DASH

ClearKey DASH channels can specify inline 128-bit hexadecimal `KID:key` pairs (comma-separated for multiple keys), a JWK key set, or an HTTP(S) license endpoint:

```m3u
#EXTINF:-1,ClearKey DASH example
#KODIPROP:inputstream.adaptive.manifest_type=mpd
#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
#KODIPROP:inputstream.adaptive.license_key=000102030405060708090a0b0c0d0e0f:00112233445566778899aabbccddeeff
https://stream.example/live
```

- **JWK format:** Pass JSON key set as `license_key`, e.g., `{"keys":[{"kty":"oct","kid":"AAECAwQFBgcICQoLDA0ODw","k":"ABEiM0RVZneImaq7zN3u_w"}]}`.
- **License endpoint:** Pass `https://license.example/clearkey|Authorization=Bearer%20token`.
- **Independence:** ClearKey channels operate independently of global Widevine settings. Invalid inline keys fail only that channel without preventing overall playlist import.
- **Backups:** ClearKey keys and headers are retained across cache updates and backup/restore (exported JSON backups include these values unencrypted).

