# Synthetic IPTV fixtures

These four-second H.264 test patterns were generated from FFmpeg's `testsrc2`;
they contain no third-party recordings. They are test-only assets under the
repository's GPLv3 license and are not packaged in the application APK.

Regenerate using FFmpeg (generated with 8.1.2) and Shaka Packager (3.9.3):

```sh
python3 scripts/generate-iptv-fixtures.py --packager /path/to/packager
```

Shaka Packager: https://github.com/shaka-project/shaka-packager (Apache-2.0).
The verified macOS arm64 v3.9.3 generator SHA-256 was
`b3049e743451aab5c2cd7b1316a4ce055682c41effe06a49e2e6c95a9243d351`.
No packager or FFmpeg binary is included or required by an application build.

The public diagnostic CENC KID is `000102030405060708090a0b0c0d0e0f`;
its key is `00112233445566778899aabbccddeeff`. `key.bin` is the same
public test key for AES-128 HLS. These are deliberately non-secret fixture values.

`plain.m3u8` and `dash.mpd` exercise ordinary playback; `encrypted.m3u8`
exercises header propagation to AES key requests; `ck.mpd` uses CENC and
Common-System PSSH for ClearKey tests. `sample.ts` and `sample.mp4` are
progressive playback regression fixtures.
