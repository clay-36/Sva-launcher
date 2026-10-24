# UI performance

The performance pass keeps the current View-based UI, artwork, focus effects,
animations, refresh schedules, and persisted data formats.

## Changes

- Home creates other tabs' content on first use. Live TV results and feedback can
  arrive before its views exist; the latest state is shown when the tab opens.
- Parsed calendar events are reused while their serialized source is unchanged.
  Month rendering also checks the actual source, including same-timestamp edits.
- The Home clock/programme/calendar tick stops on pause and restarts once on
  resume. Hidden month and channel views catch up when opened.
- Games and Saved use RecyclerView with bounded attached views, reusable cards,
  and diff-based updates. Game catalogue reads and artwork file checks run on a
  worker. Saved and Games tab list positions survive activity recreation.
- Game library wallpaper decoding runs on a worker behind the existing fallback.
- File browsing scans, validates, filters, and sorts on workers; rows are recycled.
  A navigation generation discards old results. Confirmation stays disabled until
  the selected directory has finished loading.
- Channel search waits 150 ms after input, filters a snapshot off the UI thread,
  and discards results after another query or section change.
- Guide panning uses timeline-only adapter payloads. Visible cells update and
  cached rows rebind correctly when reused, without rebinding channel logos.
- Screensaver drawing creates an offscreen alpha layer only during crossfades.
  Existing image caches and bounded photo windows are retained.

## Task feedback

- Game imports, Live TV/guide refreshes, backup/restore, photo album loading, and
  weather/calendar refreshes show local progress while retaining cached content.
- Noncritical indicators wait 300 ms, reserve their layout space, and stop when
  their view is hidden or detached. They do not receive remote focus.
- Playback uses Media3's centered buffering indicator. Restore prevents settings
  edits; import and backup actions reject duplicate work.
- File browsing retains its existing loading behavior.

## Reproduce startup measurements

Install a signed, optimized release APK, then run:

```sh
python3 scripts/measure-startup.py --serial emulator-5554 --samples 10 --output /tmp/sva-startup.json
```

The script retains application data, excludes one warm-up, and records process-cold
`am start -W` TotalTime values, their median and nearest-rank p95, plus memory and
rendering diagnostics. It waits for background startup work between samples and
rejects launches which reused an existing activity. Compare the same device,
cache data, locale, display settings, build type, and sample count. The p95 of a
small sample is not a production latency estimate. Startup gfxinfo covers startup
frames, not scrolling or playback performance.

## Emulator comparison (2026-10-23)

Android TV API 36, arm64 emulator, 1280×720, Hindi, existing cached launcher data.
Both APKs were optimized release builds signed with the same local test key.
Six measured process-cold launches per build, excluding a warm-up:

| Metric | Before | After |
| --- | ---: | ---: |
| Median startup TotalTime | 196.5 ms | 183.0 ms |
| Startup p95 (six samples) | 202 ms | 194 ms |
| Process PSS after startup | 83,361 KiB | 71,114 KiB |
| Attached Home views | 226 | 102 |

Median startup was about 6.9% lower and the final PSS sample about 14.7% lower.
These are small emulator samples, not guarantees for physical TVs or network startup.
The startup arrays were `[202, 194, 196, 175, 197, 202]` ms before and
`[171, 180, 194, 175, 194, 186]` ms after. Frame timing during scrolling has not been
quantified on a physical TV; bounded-view and remote-navigation tests cover
large collections independently of these startup numbers.

## Regression checks

- 126 JVM tests; debug and optimized release builds; Android lint; localization
  and source hygiene checks.
- 50 distinct Android instrumentation cases across Home, settings, languages,
  guide, performance, screensaver, game import, and lifecycle suites.
- Performance fixtures: 250 Saved entries with fewer than 12 attached item views;
  100 games with fewer than 30; 1,000 directory entries with fewer than 25.
- Remote navigation through offscreen items, unchanged card retention, Saved
  position restoration, rapid folder navigation, latest-query channel search,
  lazy tab initialization, calendar snapshot invalidation, and paused timers.
- English, Hindi, Arabic, and Urdu navigation coverage. Screenshot inspection of
  Saved, game cards, and file browsing. Fixtures are synthetic and removed after
  tests; the emulator's original preferences and source caches are restored.

Lifecycle instrumentation starts a fresh task because MainActivity is singleTask;
reusing an existing instance can leave startActivitySync waiting for a new launch.
Physical low-end Android TV measurements are still needed before extrapolating
emulator results to real devices.
