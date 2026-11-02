# Sva Launcher 4.1.9

- Removed All files access and legacy storage permissions. Games, cover art, wallpapers, photo folders, and JSON backups now use Android's document picker. Only saved wallpapers and photo folders retain access beyond the selection flow.
- Improved news artwork: select the largest image supplied by each feed, request larger BBC renditions, avoid unnecessary downsampling, and allow news images up to 12 MB while retaining the smaller channel-logo download limit.
- Improved Activities with larger artwork cards, app labels, clearer focus, helpful empty states, and themed customization dialogs. Focus restoration uses TV-provider program IDs so recommendations sharing a destination remain distinct.
- Updated all 23 supported interface languages for the new messages.

## Compatibility

File selection requires a working document picker on the TV. Available locations and USB support depend on the device. Previously selected local wallpapers and photo folders may need to be selected again after upgrading. Imported games and other private app data remain in app storage.

An existing Google Play installation must receive an update signed with its matching app-signing key. A locally signed APK cannot replace it when the keys differ.

## Verification

- Source and localization checks, bundled notices, and native helper checks.
- JVM tests, Android lint, debug/test APK builds, and release APK/AAB packaging.
- Targeted API 36 emulator tests for Activities artwork, customization, accessible labels, duplicate-destination focus restoration, news parsing and image sampling, game imports, picker intent grants, and picker launch/cancellation.
- Inspected Activities screenshots and the Android 9 Mi TV's existing News screen through ADB. The updated build was tested on the emulator; the Play-signed Mi TV installation was not replaced.

Successful document selection, persisted wallpaper access after restart, and backup round-trip still need end-to-end device testing. Picker cancellation tests alone do not establish that a functional document provider is installed.
