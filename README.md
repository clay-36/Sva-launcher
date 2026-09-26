# Duo Launcher

A lightweight Android TV home launcher with customizable app shortcuts, a clock, and URL-based IPTV playlists with XMLTV guide data, channel logos, category rows, and channel visibility controls. The launcher uses the Duo TV logo as its app icon.

## Build

Open the project in Android Studio, or run:

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The project targets Android 15 and supports Android 8.0 (API 26) and newer. Android Studio may ask you to select your Android SDK.

## Use

After installing, choose Duo Launcher as the device's Home app. Open the full-screen **Settings** page and select **Live TV** to enter an M3U/M3U8 playlist URL and an optional XMLTV URL. Plain `.xml` and gzip-compressed `.xml.gz` guides are supported. Guide entries match channels by `tvg-id`. A configured playlist and guide refresh automatically whenever the launcher starts; **Refresh now** requests another immediate update. Cached channels remain visible while the refresh runs.

Choose apps and set their order in the **Apps** section; selections and arrow-button moves apply immediately. If no custom list is saved, the home screen shows up to eight installed apps. The **Clock** section controls 12/24-hour display and date visibility.

Channel logos are loaded from M3U `tvg-logo` values and cached in memory and on disk. Browse channels in horizontal rows by M3U `group-title`; use **Manage hidden channels** in Settings to search by name and hide or restore channels. Select a photo folder or paste a Google Photos public album link in Settings to cycle through its images every five seconds. Google Photos albums must be shared with anyone who has the link. The backdrop covers 70% of the screen, with app tiles along its bottom edge. Below it, the home screen stays black. IPTV tiles stay black with a subtle border; both kinds of tile show a bright focus outline for remote navigation.

During playback, Up/Down changes channels. Player controls are disabled; use Back to return to the launcher.

Choose a custom hero wallpaper from **Settings**; the selection is retained between launches and appears when no photo folder is selected.

For Widevine channels, enter a global license URL and optional request headers in Settings, or include channel-specific Kodi `KODIPROP` license tags in the M3U. Per-channel values override global values. Playback uses Media3 ExoPlayer; supported DRM schemes, stream packaging, and codecs still depend on the TV device.

Per-channel tags follow Kodi's common form; URL-encode the license request headers:

```m3u
#EXTINF:-1 tvg-id="channel.one" tvg-logo="https://img.example/logo.png" group-title="News",Channel One
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://license.example/widevine|Authorization=Bearer%20token&Origin=tv.example|R{SSM}|
https://stream.example/channel/manifest.mpd
```

Widevine playback supports DASH and FMP4/CMAF HLS when the Android TV device supports the stream's DRM and codecs.
