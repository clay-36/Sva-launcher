<p align="center">
  <img src="app/src/main/res/drawable/sva_image.png" alt="Sva Launcher logo" width="128" />
</p>

<h1 align="center">Sva Launcher</h1>

<p align="center">
  <strong>A calm, personal home screen for Android TV.</strong><br />
  Bring apps, live TV, games, calendars, saved videos, weather, and your own photos together in one remote-first interface.
</p>

<p align="center">
  <img alt="Android TV" src="https://img.shields.io/badge/Android_TV-ready-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img alt="Android 8.0 or newer" src="https://img.shields.io/badge/Android-8.0%2B-29333D?style=flat-square&logo=android&logoColor=white" />
  <img alt="License GPL v3" src="https://img.shields.io/badge/license-GPLv3-blue?style=flat-square" />
</p>

<p align="center">
  <a href="#screenshots">Screenshots</a> ·
  <a href="#features">Features</a> ·
  <a href="#getting-started">Getting started</a> ·
  <a href="#build-from-source">Build from source</a> ·
  <a href="#contributing">Contributing</a>
</p>

> Sva is a launcher and media front end. It does not provide TV channels, programme data, ROMs, BIOS files, game artwork, or paid-media credentials. Bring content that you are legally allowed to use.


## Screenshots

<table>
  <tr>
    <td width="50%" align="center"><img src="screenshots/home.png" alt="Sva home screen with apps, weather, clock, and a city wallpaper" /><br /><sub><strong>Home</strong> — apps and useful information at a glance</sub></td>
    <td width="50%" align="center"><img src="screenshots/live.png" alt="Live TV channels grouped by category" /><br /><sub><strong>Live TV</strong> — categories, channel logos, and now/next data</sub></td>
  </tr>
  <tr>
    <td width="50%" align="center"><img src="screenshots/guide.png" alt="TV programme guide showing current and upcoming programmes" /><br /><sub><strong>TV guide</strong> — current and upcoming programmes</sub></td>
    <td width="50%" align="center"><img src="screenshots/iptv.png" alt="Integrated IPTV playback with DRM support" /><br /><sub><strong>Live TV</strong> — DRM supported with previous/next navigation</sub></td>
  </tr>
  <tr><td width="50%" align="center"><img src="screenshots/calendar.png" alt="Monthly events calendar with selected-day details and lunar illumination" /><br /><sub><strong>Events</strong> — a remote-friendly monthly calendar</sub></td>
   <td width="50%" align="center"><img src="screenshots/games.png" alt="Imported NES games in the Games tab" /><br /><sub><strong>Games</strong> — a local NES library powered by Nestopia UE</sub></td>
</tr>
<tr>
  <td width="50%" align="center"><img src="screenshots/games_play_2.png" alt="Bring your own NES games" /><br /><sub><strong>Games</strong> — import your own NES ROMs</sub></td>
<td width="50%" align="center"><img src="screenshots/saved.png" alt="Saved YouTube videos and playlists" /><br /><sub><strong>Saved</strong> — shortcuts to videos and playlists</sub></td>
  </tr>
</table>

## Features

- **Remote-first navigation** — focusing a top-level tab selects it immediately, with clear focus states throughout the UI.
- **Personal home screen** — select and reorder app shortcuts; keep the clock, date, weather, Wi-Fi, tools, and settings close by.
- **Photo backdrops** — use a single image, rotate a local folder, or connect a public Google Photos album.
- **Live TV and guide** — load M3U/M3U8 playlists and optional XMLTV or XMLTV.GZ data, browse `group-title` categories, display `tvg-logo` artwork, and hide unwanted channels.
- **Integrated playback** — watch with Media3 ExoPlayer, move between channels with Up/Down, view programme progress, prefer Hindi audio when available, and configure global or per-channel Widevine settings.
- **NES library** — import your own `.nes` files and launch them directly through the embedded Nestopia UE core, without a separate emulator menu.
- **Events calendar** — connect a public Google Calendar iCal feed, see today's events, browse 12 months in either direction, and view selected-day details and lunar illumination.
- **Saved videos** — create artwork-rich shortcuts for YouTube videos and playlists.
- **Weather** — current conditions and a three-day forecast from Open-Meteo, using a chosen city or optional approximate device location.
- **Configurable refresh schedules** — independently control updates for photos, playlists, programme data, calendars, and Google Photos.
- **Backup and restore** — export launcher preferences, app and channel choices, and IPTV/DRM configuration to JSON.
- **TV file browser** — browse internal storage and USB drives with the remote when adding games, choosing images or photo folders, and saving or restoring backups. **Other locations** opens an Android document provider for cloud files.
- **Home-button helper** — an optional Accessibility service can reopen Sva on TVs that insist on returning to the manufacturer's launcher.

Cached channels, guide data, calendar entries, weather, and artwork remain available where supported when the network is unavailable.

## Getting started

1. Build and install the APK, then open **Sva Launcher**.
2. When Android asks for a Home app, select Sva. You can change this later in Android settings.
3. Open the gear icon and configure only the sections you need:

   | Section | What to add |
   | --- | --- |
   | **Apps** | Choose and reorder installed app shortcuts. Without a saved selection, Sva shows up to eight apps. |
   | **Appearance** | Select a wallpaper, a local photo folder, or a public Google Photos album. |
   | **Live TV** | Add an M3U/M3U8 playlist URL and, optionally, an XMLTV or XMLTV.GZ guide URL. |
   | **Channels** | Search, hide, and restore channels from the loaded playlist. |
   | **YouTube** | Save video and playlist links as cards in the Saved tab. |
   | **Calendar** | Add a public Google Calendar iCal link for the Events tab. |
   | **Games** | Import `.nes` files and optionally remap connected gamepads. |
   | **Weather** | Search for a city or explicitly request approximate device location. |
   | **Schedules** | Set an independent refresh cadence for each network-backed source. |

The first time you browse local files, allow Sva storage access in Android settings. This lets its TV file browser find NES files, photos, and backups across local storage and USB drives. You can use **Other locations** for files provided by another app.

If the TV continues to open its built-in launcher, go to **Settings → Home button**, open Android's Accessibility settings, and enable Sva's service. This helper is optional and can be disabled at any time.

## Sva Memories screensaver

Open **Settings → Appearance → Screensaver · Sva Memories**. The screensaver follows
Home's public Google Photos album automatically; save a separate shared album link
here to override it, or choose **Use Home album** to follow Home again. Changing the
screensaver album never changes Home's wallpaper source.

Choose one of nine atmospheres:

| Theme | Presentation |
| --- | --- |
| Cinema | Full-screen photographs, slow camera movement, surrounding edge shadows and cinematic crossfades |
| Darkroom | Amber contact sheet with a large photograph, supporting album photos and a moving light leak |
| Editorial | Oversized photography, an ivory caption column and elegant serif typography |
| Floating Prints | Different album photographs on layered, gently drifting paper prints |
| Monolith | A sculptural photo panel against architectural planes colored from the image |
| Prism | A luminous photograph, translucent geometric planes and flowing light ribbons |
| Silhouette | A continuous monochrome photograph revealed through broad vertical panels |
| Travel Journal | Warm textured paper, photographic keepsakes, abstract routes and your caption |
| Photo Weave | Different album photos arranged in a slowly changing asymmetric grid |

Existing saved themes automatically resolve to their replacements. Collage themes use
up to three distinct album photos and adapt to one or two available photos. Captions
are optional; Sva does not add stock quotes, dates or locations to your photographs.

Set an optional caption, choose 12, 20, 30 or 60 seconds per photo, and enable
**Clock**, **Weather** and **Events** separately under **Show on screensaver**.
Weather and events use the saved Home weather and calendar data. **Preview screensaver** opens the same renderer used by the system;
press any remote key to return. Previously downloaded photographs work offline.
Without a usable album or cached photographs, a landscape wallpaper appears.
Google Photos album refresh follows the Google Photos schedule in Sva's settings.

For automatic playback while the launcher is open, set **Start while Sva is idle**
to 1, 3, 5 or 10 minutes. It defaults to Off and skips active playback and settings.
This also works on TVs that restrict third-party system screensavers.

For a system-wide screensaver, use **Choose in Android TV settings**, select
**Sva Memories**, and choose the TV's
idle timeout. Availability and activation controls depend on the TV manufacturer;
Sva does not replace a manufacturer's Ambient mode automatically. Its registered
Android DreamService runs independently of the launcher and exits on user input.

## Live TV notes

Guide entries are matched to channels by `tvg-id`. Selecting a current programme starts playback; selecting an upcoming programme creates an in-app reminder five minutes before it begins. Reminders are shown only while Sva is open and do not create background notifications.

For Widevine streams, configure a default license URL and request headers under **Playback & DRM**, or use per-channel Kodi properties in the playlist. Per-channel values take precedence:

```m3u
#EXTM3U
#EXTINF:-1 tvg-id="channel.one" tvg-logo="https://img.example/logo.png" group-title="News",Channel One
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://license.example/widevine|Authorization=Bearer%20token&Origin=tv.example|R{SSM}|
https://stream.example/channel/manifest.mpd
```

Widevine playback supports DASH and FMP4/CMAF HLS when the device supports the stream's DRM scheme, packaging, and codecs.

## Game controls

| Input | Player one action |
| --- | --- |
| Remote arrows | D-pad |
| Remote OK | NES A |
| Hold remote OK | Start |
| Back | Resume, Restart, and Exit menu |

Standard Android gamepads are assigned automatically for up to two players and can be remapped under **Settings → Games**. Save data is kept in Sva's private app storage.

## Build from source

### Requirements

- Android Studio or a command-line Android SDK installation
- JDK 17
- Android SDK Platform 36 and Build Tools `36.0.0`
- Android NDK `28.2.13676358`

Clone and build the project:

```sh
git clone https://github.com/clay-36/Duo-launcher-iptv.git
cd Duo-launcher-iptv
./gradlew assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected TV or emulator with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run local checks with:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./scripts/check-native.sh
python3 scripts/audit-source.py --history
python3 scripts/check-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

The native helper checks require a C++17 compiler (`c++`, or set `CXX`). On a
connected Android TV emulator, run `./gradlew connectedDebugAndroidTest` for the
screensaver and native runtime regression checks. The instrumentation test APK
includes upstream diagnostic fixtures; the application APK includes no games.

The project pins Gradle 8.13, Android Gradle Plugin 8.12.2 and the NDK version
above. The wrapper verifies the downloaded distribution's SHA-256 checksum.
First builds download the toolchain and Maven dependencies; `--offline` works
once they are cached.

The app targets Android 16 (API 36), supports Android 8.0 (API 26) and newer, and builds native libraries for `arm64-v8a`, `armeabi-v7a`, and `x86_64`. The pinned Nestopia source is included under `third_party/nestopia` so native builds do not download emulator source at build time.

### Release builds and signing

A clean checkout builds an **unsigned**, optimized release APK without private keys:

```sh
./gradlew assembleRelease bundleRelease
```

Outputs: `app/build/outputs/apk/release/app-release-unsigned.apk` and
`app/build/outputs/bundle/release/app-release.aab`. For a signed build,
provide all four environment variables through your shell or CI secret store:

| Variable | Value |
| --- | --- |
| `SVA_KEYSTORE_PATH` | Absolute path to your release keystore, kept outside the repository |
| `SVA_STORE_PASSWORD` | Keystore password |
| `SVA_KEY_ALIAS` | Signing key alias |
| `SVA_KEY_PASSWORD` | Signing key password |

Run `./gradlew assembleRelease bundleRelease` again; a complete signing configuration produces
`app-release.apk` and a signed `app-release.aab`. Gradle reads environment variables directly; it does not load a
`.env` file automatically. Incomplete signing configuration produces an unsigned
APK. Keep the same release key for updates. A debug APK cannot update a release
installation signed with a different key.

GitHub Actions builds debug and release APKs and a release AAB on each push and
pull request. You can also start **Android build and checks** manually from the
Actions tab and choose `apk`, `aab`, or `both`. Automatic runs build both formats.
Download the selected artifacts from a successful run's summary;
they are retained for 14 days. The debug APK is installable; release outputs
are unsigned by default. Manual runs on `main` can enable **Sign release outputs**
using GitHub secrets. See [workflow signing setup](docs/RELEASING.md#configure-workflow-signing)
and [CI build downloads](docs/RELEASING.md#download-ci-builds).

See [the release checklist](docs/RELEASING.md), [audit notes](docs/RELEASE_AUDIT.md),
[third-party inventory](docs/THIRD_PARTY.md) and [asset provenance](docs/ASSET_PROVENANCE.md).

## Privacy and security

- Location permission is optional and is requested only when you choose device-based weather setup or refresh. City search works without device location.
- Wallpapers, imported games, and cached data are stored locally. Public sources are contacted to fetch their content; the app contains no analytics or account-login service.
- Public album, calendar, playlist, guide, weather, and YouTube metadata requests are made directly from the TV.
- Exported JSON backups are **unencrypted** and can contain playlist URLs, DRM endpoints, credentials, public album links and weather coordinates. Store them privately; never attach them to public issues.
- HTTP is supported for user-provided IPTV and local-network sources; these connections are unencrypted. Prefer HTTPS when your provider supports it.
- The TV file-picker fallback may request all-files access on TVs without a usable system document picker. Deny it if you use only system-picked documents.
- Installed-app visibility supports launcher shortcuts. Optional notification access supports Now Playing; the Home override accessibility service only watches window changes and cannot retrieve window content.
- Only connect sources and open links that you trust. Stream, DRM, and codec compatibility depends on the TV.

## Contributing

Bug reports, feature proposals, documentation fixes, and pull requests are welcome.

1. Search the existing issues before opening a new one.
2. Keep each pull request focused on one change.
3. Run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` and `./scripts/check-native.sh` before submitting.
4. For UI changes, include sanitized before/after screenshots and verify remote/D-pad focus at normal and compact layouts.
5. Do not commit commercial ROMs, credentials, private playlist URLs, or artwork without redistribution rights. Run `python3 scripts/audit-source.py` before submitting.

See [CONTRIBUTING.md](CONTRIBUTING.md) and report security issues using [SECURITY.md](SECURITY.md).

When reporting a bug, include the TV/device model, Android version, reproduction steps, and a sanitized sample playlist or feed when relevant.

## License

Sva Launcher and the distributed combination with Nestopia UE are licensed under [GPLv3](LICENSE). Nestopia remains GPL-2.0-or-later and its original notices are preserved. See [NOTICE](NOTICE) for third-party attribution.

Every distributed APK must provide access to its matching complete source revision, including the native build files and pinned emulator core source.
