# Aerial video wallpaper and screensaver

Open **Settings → Appearance → Aerial video settings**. Wallpaper and
screensaver have independent switches. Choose All collections, Apple,
Jetson Creative, Robin Fourcade, or Amazon. Settings save immediately.
The same page is reachable from **Screensaver settings**. Preview starts
video playback without changing the selected screensaver mode.

Videos stream muted at 1080p, preferring H.264 and falling back to SDR.
HDR renditions are excluded. No video files are bundled or downloaded in
advance. Internet access and ongoing bandwidth are required. Providers
can remove or change their URLs; stalled/failed clips are skipped, and
repeated failures leave the landscape fallback visible. The catalogue is
bundled, so selecting collections does not require a GitHub API request.

Home releases its player while paused or settings are open. Disabling
video wallpaper restores the existing photo/single-wallpaper setup.
Screensaver preview, launcher idle, and Android's DreamService share the
same content. Clock, weather, and event overlay preferences continue to
apply. Photo atmospheres apply when the aerial screensaver switch is off.
System-wide activation still requires choosing Sva in Android TV's
screensaver settings; this feature does not change that system setting.

The Java catalogue adapter and media metadata are derived from Aerial
Views at the pinned revision in `third_party/aerialviews/NOTICE.md`.
Attribution and GPLv3 are also available in the in-app Aerial settings.

Validation:

- `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest --offline`
- Instrumentation classes: `AerialIntegrationTest`, `ScreensaverLifecycleTest`,
  `ScreensaverOverlayLayoutTest`.
- The optional online smoke check is opt-in, since it contacts a real video
  host: `adb shell am instrument -w -e class com.sva.launcher.AerialIntegrationTest#testOnlineWallpaperFirstFrame -e onlineAerial true com.sva.launcher.test/android.test.InstrumentationTestRunner`.
  It restores preferences and writes `files/aerial-home-test.png` in app storage.
