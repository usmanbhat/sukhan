# Poetry data and downloads

## Where the data lives

The repository root is the public pack host. Its `manifest.json` and optional per-poet SQLite files remain in place; do not move them into `android-app/`. The Android application subtree contains the app’s embedded runtime catalogue at `app/src/main/iqbal-assets/assets/data/poet_catalog.json`, plus bundled copies of the Rumi and Saadi packs at `app/src/main/iqbal-assets/assets/poet_packs/`.

The catalogue covers 245 poets, 139,038 poems, and 3,145,024 verse rows from the pinned Ganjoor Persian-language export. Rumi and Saadi are bundled for offline use; the other 243 poets are available as separate downloads. Most optional packs are served from repository-root files. Attar’s pack exceeds GitHub’s 100 MB per-file commit limit and is served as a GitHub Release asset; its exact URL, size, and hash are listed in the manifest/catalogue.

The root manifest and the app’s embedded catalogue serve different roles but must agree on pack filename, byte size, SHA-256, and URL when pack data changes. The `manifest.json` is the canonical public-host metadata. Keep the embedded catalogue in sync so the app can validate installed/downloaded packs.

## Download and installation behavior

Optional poet packs are requested only when the user selects Download. `CorpusStore`:

- accepts HTTPS sources and follows HTTPS redirects;
- checks a known response content length against catalogue metadata and enforces the expected maximum size while streaming;
- writes into a temporary file, computes SHA-256, then checks SQLite `PRAGMA quick_check(1)` before installation;
- retries transient transport/server failures once; and
- uses a jsDelivr fallback only for small raw-GitHub files at or below 45 MiB.

A pack that fails the size, hash, or SQLite integrity check is discarded rather than installed. Successful packs are kept in the app-private `filesDir/poet-packs` directory and queried read-only. Optional downloaded packs can be removed from the device; Rumi and Saadi are bundled and remain available offline. Searches and browsing can use the packs currently installed on that device.

The app’s runtime installer does not update packs automatically. To change public pack data, regenerate and review the data, update hashes and sizes in the root manifest and embedded app catalogue, verify compatibility, and then publish the coordinated change.

## Translation and glossary status

Rumi has 996 complete English-and-Urdu AI-draft poems with 21,024 aligned lines per language. They are explicitly marked as not human-reviewed; the exact model ID is unverified. The UI labels these as drafts. Do not present them as reviewed translations.

Saadi and the other optional poets do not currently have populated English/Urdu translation or hard-word glossary content in these packs. The schema has fields for English translation, Urdu translation, provenance/review status, and glosses so reviewed content can be added later. Presence of a field does not mean content exists. Validate translation completeness, source, rights, and review status before changing catalogue counts or badges.

## Provenance and source material

The pack source is the [Ganjoor public poetry export](https://github.com/ganjoor/ganjoor-data), pinned to commit `05d7bc88cca1fe5b95eb7038d4af2d271b320ab3` (snapshot recorded as `2026-11-08T04:17:56.7846096Z`). Packs preserve source labels and selected attribution metadata while omitting redundant compressed source JSON. The repository retains Ganjoor’s reuse note; this project does not make a broader license claim. Consult the source project and its notices before redistribution or derivative work.

The 225 MB monolithic `demo_poetry.db` corpus and the Rumi translation checkpoint are deliberately omitted from the published app source folder. Data preparation scripts are present, but recreating the packs requires those upstream/input files. They are not necessary to build the app from the checked-in catalogue and runtime packs.
