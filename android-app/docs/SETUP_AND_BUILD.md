# Setup and build

## Requirements

Install a JDK supported by the Android Gradle Plugin (JDK 17 or later), Android SDK Platform 37, and Android Build Tools 37.0.0. The project’s Gradle wrapper downloads Gradle 9.9.1; the first build also needs network access to resolve Android and Maven dependencies. Once dependencies and SDK packages are present, builds may be run offline.

The app uses `applicationId com.sukhan.poetry`, `minSdk 23`, `compileSdk 37`, and `targetSdk 37`. Native libraries are packaged for `arm64-v8a` and `armeabi-v7a`.

## Build a debug APK

From the `android-app/` directory:

```bash
./build-debug-apk.sh
```

The script uses `$ANDROID_HOME`; if unset, it assumes `$HOME/Android/Sdk`. It runs `:app:clean :app:assembleDebug`, verifies the debug APK with Android `apksigner`, and copies it to:

```text
dist/Sukhan-offline-demo-debug.apk
```

Gradle’s configured intermediate build directory is `.fresh-build/`. Both `.fresh-build/` and `dist/` are ignored by Git. The APK is a local debug artifact, not a repository deliverable.

To invoke Gradle directly:

```bash
./gradlew :app:assembleDebug
```

After the wrapper, Android SDK packages, and dependencies are already cached, this can be run with `./gradlew --offline :app:assembleDebug`.

## Verify the packaged app

Run the project’s APK/source contract checks after building:

```bash
python3 tests/native_contract.py dist/Sukhan-offline-demo-debug.apk
```

The script checks the APK archive, required app assets, architecture ABIs, bundled Rumi/Saadi database integrity and counts, catalogue/download metadata, the logo, and key source contracts for downloads, speech, settings, and the widget. It uses Python’s standard library plus Pillow. This is a packaging/contract check; it is not a substitute for device-based UI, accessibility, or instrumentation testing.

For a device smoke test, install the debug APK on an API 23+ Android device or emulator. Check first launch, language and theme settings, search/browsing, offline Rumi and Saadi reading, speech, widget add/open/share actions, and an optional poet-pack download while connected to the network.

## Optional local configuration

`ai.properties.example` is an empty example for a future provider adapter. The current offline demo does not use an AI service and does not need an API key. The build can read endpoint/key properties into `BuildConfig`, but the integration is only a placeholder; do not place a real key in a committed file or a public APK. If experimenting locally, copy the example to `ai.properties`; this file is ignored by Git. See [Security, privacy, and release checklist](SECURITY_AND_RELEASE.md).

## Regenerating artwork and data

`tools/render_sukhan_logo.py` regenerates the supplied-logo variants from local source assets. Its documented dependencies include Pillow, NumPy, OpenCV, `rsvg-convert`, and Fontconfig; it does not need network access.

The pack-building and translation-checkpoint scripts are maintainer utilities, not part of a normal app build. They require the large raw Ganjoor source database and/or translation checkpoint inputs, which are intentionally not included in this public source subtree. The public root `manifest.json` and optional poet packs are the canonical hosted outputs. Check each tool’s `--help` and input-path options before using it; do not overwrite root pack files without checking source provenance and hashes.
