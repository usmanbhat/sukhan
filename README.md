# Sukhan Persian poet packs

This public repository hosts **243 individual SQLite downloads** for Sukhan. The app’s 245-poet catalog bundles Rumi and Saadi; the other 243 poets are optional, individually downloadable packs. This release adds 240 poets beyond the three optional packs already hosted here.

Use the [public manifest](https://raw.githubusercontent.com/usmanbhat/sukhan/main/manifest.json) to find any poet’s Persian and English display names, poem and verse counts, direct download URL, exact file size, and SHA-256. The app verifies each requested HTTPS download by size, hash, and SQLite integrity before installation. Most pack files are in the repository root; Attar’s 120,754,176-byte pack is a public GitHub Release asset because it exceeds GitHub’s 100 MB per-file limit for repository commits. Its direct asset URL is recorded in the same manifest.

## What is inside

The catalog covers **245 poets, 139,038 poems, and 3,145,024 verse rows** from the pinned Persian-language Ganjoor export. Each pack uses Sukhan’s app-facing SQLite schema, keeps poem-level source labels and the translation/gloss columns, and records the source revision. Opaque compressed `source_json_zlib` blobs are omitted to keep packs compact. Rumi and Saadi’s SQLite files are not in this repository because they ship in the app.

For poets other than Rumi, English/Urdu translation and hard-word glossary fields are empty and ready for reviewed, rights-cleared content. Rumi has 996 complete bilingual AI-draft poems (21,024 aligned lines in each language); they are marked not human-reviewed, and the exact model ID is unverified. Five complete Rumi poems without translation provenance are excluded from those draft translations.

## Source notes

The source is the [Ganjoor public poetry export](https://github.com/ganjoor/ganjoor-data), pinned to commit `05d7bc88cca1fe5b95eb7038d4af2d271b320ab3` (snapshot `2026-11-08T04:17:56.7846096Z`). The manifest retains the source project’s reuse note and poem-level source labels; this README does not make a separate or stronger license claim. Consult Ganjoor’s project and notices for reuse guidance.
