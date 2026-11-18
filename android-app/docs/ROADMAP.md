# Roadmap and future plans

The roadmap separates what is already implemented from remaining work. No dates or delivery commitments are assigned here.

## Already implemented

- **English, Urdu, and Persian UI choices:** interface labels and right-to-left behavior for Urdu/Persian are present. Broader translation coverage of the poetry itself is a separate, incomplete content task.
- **Per-poet offline packs:** two packs (Rumi and Saadi) are preloaded; each of the other 243 poets has an individual on-demand pack. Downloads are integrity-checked, usable offline after installation, and optional packs can be removed.
- **Persian TTS controls:** offline Persian speech, adjustable speed, and pitch/tone are available in Settings. The app currently bundles only one voice.
- **Daily quote widget and share image:** the widget reads local date-based poetry, follows saved theme/language, opens the Daily Verse screen, and can share a generated quote image.
- **Poem exports:** text, image, and PDF sharing are implemented, including line selection.

## Remaining work, in priority order

### 1. Build a trustworthy translation and glossary layer

Review and correct the existing Rumi English/Urdu drafts before treating them as reader-ready. Preserve clear provenance and human-review status. Then add rights-cleared translations for additional poets, with separate English and Urdu fields, per-line availability indicators, and contextual meanings for difficult words. Keep untranslated fields visibly absent rather than implying coverage.

### 2. Add selectable Persian voices

Extend the current one-voice Settings card into an actual voice selector when additional properly licensed models are ready. Define whether voices are bundled or downloaded as optional model packs, show storage size and language/locale, retain speed/pitch controls, and verify quality and offline playback on supported Android devices. Do not imply voice choice is available until more than one voice has been integrated.

### 3. Complete multilingual experience quality

Keep English/Urdu/Persian as the baseline, then audit all screens, widget labels, share chooser text, dates, accessibility labels, RTL alignment, and error states for consistent localization. Add further interface languages only with translated strings and locale-specific QA rather than exposing partially translated choices.

### 4. Strengthen release validation and data maintenance

Add device/instrumentation and accessibility checks alongside the current APK contract script. For each catalogue/pack change, regenerate or validate the pack, compare root manifest and embedded catalogue metadata, verify file size/SHA-256/SQLite integrity, and review source attribution and license requirements. Keep raw source corpora, translation checkpoints, credentials, and generated APKs out of the public source commit.

## Release-state rule

Update this roadmap when work ships. A feature should move to “implemented” only after its code/data is in the repository and its behavior has been verified; generated translations should not be described as reviewed unless review status is recorded and confirmed.
