# Sva Launcher 4.1.3

Version code: 8

- Combine channels from multiple M3U/M3U8 playlist sources entered one URL per line in Settings → Live TV. Duplicate source URLs are ignored, and duplicate channel identities are deduplicated.
- Merge programme schedules from multiple XMLTV, XMLTV.GZ, or JSON guide sources entered one URL per line. A failed source does not prevent successful sources from loading.
- Prevent stale channel category updates from being applied during rapid navigation, reducing the risk of crashes when switching quickly.
- Reduce guide refresh work by updating programme labels only on visible channel cards.
- Reduce background polling when no media is playing.
- Improve tab navigation performance by updating styling only for the previous and newly selected tabs.
- Add regression coverage for multi-source cache matching and merging overlapping guide schedules.
- Make the Android build workflow manual-only; commits and pull requests no longer trigger it automatically.
