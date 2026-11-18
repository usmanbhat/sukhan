# Sukhan Android app (سخن)

This folder contains the complete native Kotlin Android app source for Sukhan, an offline-first Persian poetry reader. The project is intentionally kept separate from the repository’s poet-pack hosting area. The canonical optional poet-pack files and `manifest.json` stay at the repository root; this folder includes the source, build inputs, and the two poetry packs and Persian voice model required by the app at runtime.

## At a glance

- Native Android app, package `com.sukhan.poetry`; minimum Android API 23, compile/target API 37.
- A 245-poet catalogue from Ganjoor, with Rumi and Saadi bundled for offline use and 243 other poets offered as individual downloads.
- Search and browse poets, collections, poems, and verses; read locally and keep local settings, favorites, and notes.
- English, Urdu, and Persian interface choices with right-to-left layout for Urdu and Persian; system, light, dark, and sepia themes.
- Persian offline text-to-speech with adjustable speed and pitch/tone. One Persian voice is bundled today.
- Daily Verse home-screen widget and locally generated share images; poem sharing also supports text and PDF.

The roadmap distinguishes these existing features from what is still planned. Translation coverage is incomplete: Rumi’s bilingual translations are AI drafts awaiting human review, and the other poets’ translation/glossary fields are generally empty. More selectable Persian voices are not included yet.

## Build quickly

Install JDK 17 or later and Android SDK Platform 37 plus Build Tools 37.0.0. From this directory, run:

```bash
./build-debug-apk.sh
python3 tests/native_contract.py dist/Sukhan-offline-demo-debug.apk
```

The build script uses `$ANDROID_HOME` (defaulting to `$HOME/Android/Sdk`), builds a debug APK, verifies its signature, and writes the local result under `dist/`. APKs and build products are ignored by Git and are not part of the published source. See [Setup and build](docs/SETUP_AND_BUILD.md) for details.

## Documentation

- [Setup and build](docs/SETUP_AND_BUILD.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Data and downloads](docs/DATA_AND_DOWNLOADS.md)
- [Current features and limitations](docs/FEATURES.md)
- [Roadmap and future plans](docs/ROADMAP.md)
- [Security, privacy, and release checklist](docs/SECURITY_AND_RELEASE.md)

## Source layout

```text
android-app/
├── app/src/main/java/com/sukhan/poetry/     Kotlin app and feature logic
├── app/src/main/iqbal-assets/assets/         Runtime catalog, bundled packs, fonts, art, TTS model
├── app/src/main/res/                         Android resources, including widget layouts
├── app/src/main/review-assets/               Small review fixture; not packaged in the APK
├── app/libs/                                 Piper phonemizer Android library
├── gradle/                                   Gradle wrapper
├── tools/                                    Asset/data preparation utilities
├── tests/                                    APK/source contract checks
├── licenses/ and THIRD-PARTY-NOTICES.md      Third-party notices
└── docs/                                     Project guides and roadmap
```

The large raw `demo_poetry.db` source corpus, local build caches, developer credentials/configuration, and APK outputs are intentionally not included. The app’s two bundled SQLite runtime packs and its offline voice model are included because the build and offline features need them. Read the third-party notices and [security/release guide](docs/SECURITY_AND_RELEASE.md) before distributing builds.
