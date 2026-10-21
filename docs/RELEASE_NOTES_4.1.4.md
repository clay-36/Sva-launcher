# Sva Launcher 4.1.4

Version code: 9

- Support multiple iCalendar feeds entered one URL per line in Settings → Calendar, including non-Google providers. Keep each source cached independently, retain cached events when a source fails, and merge duplicate event identities.
- Validate calendar URLs and redirects, support HTTPS and webcal links, and allow HTTP feeds on the local network.
- Fix remote navigation to Save changes in Live TV and DRM settings, including moving out of text fields with the up and down buttons.
- Fix navigation between app reorder controls: pressing left from the down arrow moves to the up arrow, then the app name, before returning to the category list.
- Add a TV Input shortcut beside Wi-Fi to select inputs exposed by the TV. Show remote Input / Source instructions when inputs are unavailable or cannot be opened.
- Fall back to general Android settings when the Accessibility settings screen is unavailable, with instructions for finding Accessibility manually.
- Keep screensaver time at the bottom left, weather at the bottom center, and events at the bottom right, even when individual overlays are disabled. Prevent long labels from overlapping.
- Remove the screensaver prompt to add a Google Photos album when no album is configured.

## Verification

- Passed 120 unit tests and 29 Android TV emulator instrumentation tests covering settings navigation, calendar feeds, accessibility shortcuts, the TV Input shortcut, and screensaver behavior.
- Passed debug and release builds, Android lint, native checks, the source hygiene audit, and release APK checks.
- Physical TV input switching and Android 8 OEM settings behavior still require device verification. Game controls are unchanged.
