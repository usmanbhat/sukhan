# Architecture

## App shape

Sukhan is a native Android application written in Kotlin. The current UI is built with Android `Activity`, `View`, and `RemoteViews` APIs; it is not a web app and does not use Jetpack Compose. The package and namespace are `com.sukhan.poetry`.

`MainActivity` owns the primary navigation and screen state. It constructs the home, library, poem, search, favorites, daily verse, and settings experiences, reads the selected language/theme, and delegates data, speech, and sharing work to focused helpers. Localized labels are held in code for English, Urdu, and Persian, and the UI switches layout direction for Urdu/Persian.

## Main components

| Component | Responsibility |
| --- | --- |
| `MainActivity.kt` | App entry point, screen rendering/navigation, search, poem and translation views, preferences, pack-download actions, and daily verse actions. |
| `CorpusStore.kt` | Parses the bundled poet catalogue; copies the two bundled packs; downloads, verifies, installs, removes, and queries read-only SQLite packs. |
| `LocalStore.kt` | Stores device-local preferences, favorites, notes, and recent-search state in an app-private SQLite database. |
| `AppTheme.kt` | Resolves system/light/dark/sepia color palettes and shared theme helpers. |
| `SettingsSheet.kt` | Theme and language choices, speech controls, cache information/clear action, and About entry. |
| `PersianTts.kt` | Runs Persian phonemization and offline Piper/Sherpa-ONNX synthesis on a worker thread, then streams audio through `AudioTrack`. |
| `DailyQuoteWidgetProvider.kt` | Updates the Android home-screen widget from the local daily verse and wires open/share actions. |
| `DailyQuoteShare.kt` | Renders a quote image locally and shares it through a content URI. |
| `ShareActions.kt` and `ShareFileProvider.kt` | Select poem lines and share poem text, a generated image, or PDF using app-private temporary files. |
| `AiIntegration.kt` | Placeholder configuration hook only; no AI-provider request is implemented in this offline demo. |

## Reading and storage flow

1. At startup, the app reads `assets/data/poet_catalog.json` and validates/prepares the bundled Rumi (`moulavi`) and Saadi packs.
2. Bundled and downloaded SQLite packs are copied into the app’s private `filesDir/poet-packs` directory. Queries open installed packs read-only.
3. When a user requests another poet, the app fetches that poet’s pack over HTTPS, writes a temporary file, checks advertised/expected size, computes SHA-256, runs SQLite `quick_check`, and installs only a valid file. Existing optional packs can be removed individually; bundled packs cannot be removed from the library.
4. User settings, favorites, notes, and recent searches are stored locally. Poem/quote image and PDF files are temporary share artifacts in app cache.

The downloaded poetry stays available offline after successful installation. The app does not need an account to browse its bundled packs. Optional pack downloads are the intentional network-backed feature; the AI integration is not active.

## Speech flow

The app bundles one Persian Piper voice (`fa_IR-mana-medium`) and uses Piper phonemization plus Sherpa-ONNX for on-device generation. Synthesis runs off the main thread and audio is streamed through Android `AudioTrack`. The Settings sheet persists speech speed and pitch/tone preferences; it currently offers no second voice to select.

## Widget and share flow

The `DailyQuoteWidgetProvider` reads a date-based verse from installed local poetry, applies the saved theme and language, and updates `RemoteViews`. Tapping the card opens the app’s Daily Verse screen. The share action renders an image locally and launches Android’s share chooser. Poem sharing separately offers text/image/PDF and optional line selection.

## Build boundaries

Gradle packages only `app/src/main/iqbal-assets` as application assets. It does not package the large monolithic `demo_poetry.db` source corpus or the review database. The runtime catalogue, two bundled poet packs, brand/fonts/images, speech model, and notices are in the selected asset directory. Build outputs are redirected to `.fresh-build/` and should remain untracked.
