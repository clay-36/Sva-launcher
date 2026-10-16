# Third-party components

The app combines GPL-3.0 application code with the GPL-compatible components below.
Original upstream files and notices remain in the vendored source or dependencies.

| Component | Version / revision | License | Source |
| --- | --- | --- | --- |
| Nestopia libretro | `8f00f500912a847062de432e38765c7285483e62` | GPL-2.0-or-later | [libretro/nestopia](https://github.com/libretro/nestopia) |
| nes_ntsc | 0.2.2, as vendored by Nestopia | LGPL-2.1-or-later | `third_party/nestopia/source/nes_ntsc` |
| Manrope | Bundled font | SIL OFL 1.1 | `app/src/main/assets/licenses/manrope_ofl.txt` |
| AndroidX Media3 | 1.8.1 | Apache-2.0 | [androidx/media](https://github.com/androidx/media) |
| AndroidX RecyclerView | 1.4.0 | Apache-2.0 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/) |
| JUnit (tests only) | 4.13.2 | EPL-1.0 | [junit4](https://github.com/junit-team/junit4) |
| JSON-java (JVM tests only) | 20240303 | Public domain | [JSON-java](https://github.com/stleary/JSON-java) |

Android's framework `org.json` implementation is used on-device. The standalone
JSON-java dependency is for JVM tests, not the application APK. The Android test
runner is supplied by the platform and compiled against SDK stubs.

The three `.nes` files in the vendored `nes_ntsc/tests` directory are upstream
diagnostic fixtures. Only the instrumentation test APK uses them; they are not
application assets. No games, firmware or BIOS files are required for normal builds.

The pinned NDK supplies the C++ runtime. Refer to its `NOTICE` for toolchain runtime
notices. Transitive JVM dependencies can be inspected with:

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

APK releases should link to their exact complete source revision, including the
vendored core, native bridge, Gradle build files and license files. Keep this
inventory and bundled notices in sync when dependencies change.
