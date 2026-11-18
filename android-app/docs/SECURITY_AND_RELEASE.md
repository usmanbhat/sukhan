# Security, privacy, and release checklist

## Repository contents and exclusions

This source folder is public. It intentionally excludes credentials, tokens, private keys, Firebase service-account files, personal data, the raw 225 MB demo corpus, local Gradle/build caches, and APK/AAB outputs. The root `.gitignore` protects `android-app/.fresh-build/`, `android-app/dist/`, `.gradle/`, local `ai.properties`/`local.properties`, signing keys, and package outputs. Keep these rules in place.

`ai.properties.example` contains empty placeholders only. The AI integration is not active. Build properties can be compiled into `BuildConfig`, which is recoverable from an APK; never put a production secret in a client build, a public source file, or an APK. Do not add Firebase credentials or signing keys to the repository.

## Runtime privacy boundaries

Poetry packs, favorites, notes, preferences, recent searches, and generated share files are stored locally in app-private storage/cache. The bundled speech model performs synthesis on-device; poem text is not sent to a speech service. The app does make network requests when the user explicitly downloads an optional poet pack, using the HTTPS URL in the embedded catalogue. The current AI hook does not send requests.

## Third-party material

Read `THIRD-PARTY-NOTICES.md` and the license files in `licenses/` and the app asset notices before distributing a built app. In particular, Piper phonemization is GPL-3.0 and may create source-offer/corresponding-source obligations; the bundled voice model has its own upstream terms. This document is not legal advice. The poetry dataset’s reuse notes are preserved in the pack metadata; consult Ganjoor’s project for reuse guidance.

## Before publishing a source change

1. Check the exact changed paths with `git status --short` and `git diff --stat`.
2. Confirm the repository-root `manifest.json` and root poet-pack files have not been unintentionally changed or moved.
3. Confirm the Android folder does not contain `.fresh-build/`, `dist/`, `local.properties`, `ai.properties`, `.gradle/`, `.kotlin/`, APK/AAB files, raw source databases, service-account JSON, signing keys, or token/private-key strings.
4. Check every added file is below GitHub’s 100 MB individual-file limit. GitHub Release assets are a separate mechanism and should be coordinated with manifest URLs.
5. Run the build and `python3 tests/native_contract.py dist/Sukhan-offline-demo-debug.apk` when the Android SDK is available; describe any unrun check honestly.
6. Run `git diff --check`; review third-party notices and provenance when data/assets change.
7. Stage only the intended Android source folder, documentation, root README pointer, and ignore rules. Do not stage generated outputs.
