# Aerial Views integration

Source: https://github.com/theothernt/AerialViews
Pinned revision: b8ec8d3e81b7354ef8b0aae8ca9a1ba592089f65
Retrieved: 2026-11-07
License: GNU GPL version 3 (see LICENSE in this directory).
Copyright: Aerial Views contributors, including Neil Turner.

Sva's AerialCatalog.java adapts the catalogue parsing and 1080p SDR quality
selection from AppleMediaProvider, Comm1MediaProvider, Comm2MediaProvider,
AmazonMediaProvider and the associated video models. It is a Java adaptation,
not an unmodified upstream source file. Sva owns the player and lifecycle.

The eight aerial_*.json resources are unmodified copies of upstream's
app/src/main/res/raw/{tvos26,comm1,comm2,fireos8}{,_strings}.json resources.
They contain media URLs and descriptive metadata. Videos are streamed from
their original hosts, not bundled or redistributed in the APK. The GPL
license for the application does not grant rights to third-party videos.
Media remains owned by Apple, Amazon, Jetson Creative, Robin Fourcade and
other respective rights holders. This integration does not claim their media
is GPL-licensed. Aerial Views attribution and the GPL text are also available
in Sva's Aerial videos settings.

The upstream provider logic chooses H.264 for 1080p where available. Sva uses
that selection with an SDR fallback, excludes HDR variants, and retains HTTPS
URLs rather than applying upstream's blanket HTTP substitution for Apple.
