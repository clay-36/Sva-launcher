# Sva Launcher 4.1.4

Version code: 9

- Combine channels from multiple M3U/M3U8 playlist sources entered one URL per line in Settings → Live TV. Duplicate source URLs and duplicate channel identities are ignored.
- Merge schedules from multiple XMLTV, XMLTV.GZ, or JSON programme guide sources. A failed source does not block successful sources.
- Prevent stale channel category updates from being applied during rapid navigation, reducing crash risk when switching quickly.
- Reduce guide refresh work by updating programme labels only on visible channel cards.
- Improve tab navigation by updating styling only for the previous and newly selected tabs. Reduce media status polling when nothing is playing.
- Add regression coverage for multi-source cache matching and merged guide schedules.
- Make Android build checks manual-only; pushes and pull requests no longer trigger the workflow.
