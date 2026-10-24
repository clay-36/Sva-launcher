# Sva Launcher 4.1.6

Version code: 11

- **Dynamic Header Theming & Contrast Adaptation**: Analyzes wallpaper and photo backdrop luminance across separate header widget regions (Clock, Weather, and Calendar) to automatically adapt text colors, secondary text, accents, dividers, and drop shadows for optimal legibility over light, dark, or complex wallpapers, with smooth 350 ms animated color crossfading during background transitions.
- **Lazy Tab Initialization**: Content for inactive tabs (Live TV, Games, Events, Saved) is created on demand on first selection rather than all at launcher startup. Live TV results and calendar events arriving before tab views exist are safely buffered and rendered immediately when opened.
- **Bounded View Recycling**: Converted Saved YouTube shortcuts and the Game Library to `RecyclerView` with reusable cards, bounded attached views, and diff-based updates. Refactored the TV File Picker to recycle list rows with generational tracking to safely discard stale navigation results.
- **Background Work Offloading & Debouncing**:
  - ROM catalogue parsing, artwork checks, and file browsing validation/sorting now execute on worker threads.
  - Channel search in Settings debounces input by 150 ms and performs snapshot filtering off the main thread.
  - TV Guide schedule panning uses timeline-only adapter payloads so visible cells update smoothly without rebinding channel logos.
  - Clock and periodic update ticks stop when the launcher is backgrounded/paused and resume cleanly upon returning.
- **Non-Intrusive Task Progress Feedback**: Added `TaskProgress`, a remote-safe progress indicator for background operations (game imports, Live TV/guide refreshes, calendar/weather sync, backup/restore, photo albums). Indicators delay appearance by 300 ms to eliminate visual flicker on quick tasks, reserve their layout footprint to prevent UI shifts, and never intercept remote D-pad focus.
- **Comprehensive Interface Translation Polish**: Overhauled wording across all 22 non-English locales (Arabic, Simplified Chinese, Traditional Chinese, Bengali, German, Spanish, French, Gujarati, Hindi, Indonesian, Japanese, Kannada, Korean, Malayalam, Marathi, Punjabi, Portuguese, Russian, Tamil, Telugu, Turkish, Urdu), replacing awkward machine-translated terms with natural Android TV vocabulary (e.g., French "Retour" instead of "Dos", "Activé" instead of "Sur", "Masqué" instead of "Caché", "Affiché" instead of "Montré", "chaîne" instead of "canal").
- **Right-to-Left (RTL) Remote Navigation**: Preserved physical D-pad directionality for TV Guide toolbar arrows across both LTR and RTL locales (Arabic and Urdu).
- **Dark Mode & Styling Refinements**: Flattened remote focus elevation (`z = 0`) to prevent unnatural 3D drop-shadows on TV displays, removed dark/light tint overlays on the Home tab so user wallpapers render cleanly, and optimized dark theme widget styling with direct palette fallbacks.
- **Player Buffering Feedback**: Integrated Media3 ExoPlayer buffering indicators that display during stream preparation and cleanly dismiss on ready, error, or player release.

## Verification

- Passed all 126 JVM unit tests (including multiline calendar feeds, deduplication, and backup format safety).
- Passed 50+ on-device Android TV instrumentation test cases across `PerformanceUiTest`, `TaskProgressTest`, `TaskLoaderUiTest`, `SettingsNavigationTest`, `LanguageUiTest`, `GuideUiTest`, and `IptvPlaybackTest`.
- Passed `scripts/check-localization.py` (0 errors across 23 languages, 620 strings, 13 plurals), `scripts/audit-source.py --history` (0 hygiene findings), and `./scripts/check-native.sh` (native timing and pixel-format checks passed).
- Verified startup latency and memory reduction using `scripts/measure-startup.py` (attached Home views reduced from 226 to 102; startup memory reduced by ~14.7%).
