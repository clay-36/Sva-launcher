# Release checklist

## Verify the source revision

1. Review the complete diff, including any work already present in the checkout.
2. Run `python3 scripts/audit-source.py --history`, `./scripts/check-native.sh`, and
   `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`.
3. Run `./gradlew connectedDebugAndroidTest` on an Android TV emulator. Also check
   a physical TV: launcher/Home override, remote focus and Back, IPTV/DRM with your
   own provider, gamepad controls/audio, and system screensaver activation.
4. Review [third-party notices](THIRD_PARTY.md) and [asset provenance](ASSET_PROVENANCE.md).
   Inspect screenshots and APK contents for private data or unintended content.
5. Update `versionCode` monotonically and choose a release `versionName` in
   `app/build.gradle`. Tag the exact reviewed source revision and retain its source.

The checked-in workflow performs build, JVM, lint, native-helper and source-hygiene
checks. Device tests and hardware checks still need to be run separately.

## Sign and inspect the APK

A clean checkout builds `app-release-unsigned.apk`. To produce `app-release.apk`,
provide `SVA_KEYSTORE_PATH`, `SVA_STORE_PASSWORD`, `SVA_KEY_ALIAS`, and
`SVA_KEY_PASSWORD` in the process environment. The key belongs outside Git.
Gradle does not automatically load `.env`. Use a durable release key and keep a
secure backup; signing with a different key prevents in-place app updates.

Use the SDK Build Tools on the resulting signed APK:

```sh
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release.apk
```

Automate the content and ELF checks with:

```sh
python3 scripts/check-apk.py app/build/outputs/apk/release/app-release-unsigned.apk --zipalign /path/to/sdk/build-tools/36.0.0/zipalign
```

Inspect all packaged ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) and verify the JNI
bridge can load its core after R8 shrinking. The 64-bit library load/RELRO segments and APK packaging are checked for
16 KB alignment. Keep `mapping.txt` and native symbols privately for debugging.
An unsigned APK is a build artifact, not an installable public release.

## Publish

Publish the signed APK, its SHA-256 checksum, release notes and a link to the exact
matching complete source revision. Include the vendored Nestopia source and native
build files in source distributions; do not ship a binary-only emulator dependency.
Licenses are included under `assets/licenses` in the APK.

Confirm repository visibility, license, default branch, issue settings and private
vulnerability reporting. CI and releases must not contain private signing keys,
backup files or production credentials. Publishing/pushing/tagging is a separate
maintainer action; the audit does not perform it automatically.
