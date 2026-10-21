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

## Download CI builds

The **Android build and checks** workflow is manual-only. Start it from
**Actions → Android build and checks → Run workflow**, then choose **Build output**:
`apk`, `aab`, or `both` (the default). Tests and lint run for every choice.
After a successful run, download the selected artifacts from the run's summary page:

| Artifact | Contents |
| --- | --- |
| `sva-debug-apk` | Debug-signed `app-debug.apk`, installable for testing |
| `sva-release-apk-unsigned` | Optimized `app-release-unsigned.apk` |
| `sva-release-aab-unsigned` | Optimized `app-release.aab` for signing and store upload |

Artifacts are retained for 14 days. By default, release outputs are unsigned.
Enable **Sign release outputs** on a manual run from `main` to produce
`sva-release-apk-signed` (containing `app-release.apk`) and/or
`sva-release-aab-signed`. The debug APK remains debug-signed.
The workflow does not publish a GitHub release or submit anything to a store.

## Configure workflow signing

In **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret | Value |
| --- | --- |
| `SVA_KEYSTORE_BASE64` | Base64-encoded contents of your existing release/upload keystore |
| `SVA_STORE_PASSWORD` | Keystore password |
| `SVA_KEY_ALIAS` | Key alias |
| `SVA_KEY_PASSWORD` | Key password |

On macOS, copy the encoded keystore directly to the clipboard without displaying it:

```sh
base64 -i /absolute/path/to/release.jks | pbcopy
```

Paste that value into `SVA_KEYSTORE_BASE64`. Base64 is an encoding, not encryption;
keep the value in GitHub secrets and retain a secure backup of the original key.
GitHub secrets have a 48 KB size limit; the encoded keystore must fit that limit.

Start the workflow on `main`, choose `apk`, `aab`, or `both`, and enable
**Sign release outputs**. Missing secrets fail the run rather than silently
producing an unsigned release. The workflow restores the keystore into the
runner's temporary directory, exposes passwords only to the signing steps,
verifies signatures, and deletes the temporary keystore even if the build fails.
Push and pull-request builds never receive the signing secrets, and signing
requests from other branches are rejected.

For direct APK distribution, use your app signing key. For Google Play with
Play App Signing, use your registered upload key for AABs. If these are different
keys, configure the appropriate one before running the workflow for that format.
See [Android app signing](https://developer.android.com/studio/publish/app-signing)
and [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

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
