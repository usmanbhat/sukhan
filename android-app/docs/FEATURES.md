# Current features and limitations

This page describes what the checked-in app implements today, not a promise about future releases.

## Poetry library

- Browse the Ganjoor catalogue of 245 poets and navigate their collection/category hierarchy.
- Read Rumi and Saadi offline from bundled packs. Other poets are listed individually and can be downloaded on demand; installed optional packs remain available offline and can be removed individually.
- Search the library and query the poems/verses available in installed packs.
- See translation-availability badges on lines/poems when translation text is present. Rumi’s bilingual AI drafts are marked as unreviewed drafts.
- Save favorites, notes, and recent searches locally on the device.
- Open a date-based Daily Verse using local poetry data.

## Languages and appearance

- The in-app language selector offers English, Urdu, and Persian interface labels. Urdu and Persian use right-to-left layout.
- The selector does not mean every poem is translated into all three languages: original Persian is the source text, translations are only present for some content, and hard-word glosses are not yet broadly populated.
- Appearance options include system default, light, dark, and sepia themes.

## Listening, widget, and sharing

- Persian text-to-speech is on-device and works offline using the bundled Mana Persian Piper model with Sherpa-ONNX and Piper phonemization.
- Settings persist adjustable speed (0.75×–1.25×) and pitch/tone (0.80×–1.20×).
- Only one Persian voice is bundled. The Settings sheet labels it and notes that it is the sole available voice; choosing among multiple voices is not implemented.
- The Android Daily Quote widget shows a locally selected daily verse, applies the chosen theme/language, opens the Daily Verse screen, and can share a locally rendered quote image.
- Poem sharing supports text, image, and PDF, with an option to choose lines before sharing.

## Explicitly incomplete

- **Translation quality and coverage:** Rumi’s 996 English/Urdu drafts are not human-reviewed; the exact generating model is unverified. Saadi and the other poets have no broadly populated translation/glossary coverage in the current data.
- **Contextual hard-word meanings:** database fields exist for future glossary work, but explanatory meanings are not populated as a general reader feature.
- **Multiple voice choices:** there is only one bundled Persian voice. Speed and pitch/tone are implemented; voice switching is not.
- **AI provider features:** `AiIntegration.kt` is a placeholder configuration hook. The offline demo makes no AI-provider requests and does not provide AI-generated explanations or new translations in the app.
- **Automated test coverage:** the provided Python script is a packaging/source contract check. Device-based UI, instrumentation, accessibility, and broad locale testing remain separate validation work.
