# Third-party notices for the offline Persian voice

This is a test build. The following components are bundled with the Persian Listen feature:

- **Sherpa-ONNX v2.0.1** — Apache-2.0. Source: <https://github.com/k2-fsa/sherpa-onnx>. The full license is in `licenses/sherpa-onnx-Apache-2.0.txt`.
- **piper-phonemize v1.5.9** — GPL-3.0. Source: <https://github.com/csukuangfj/piper-phonemize>. The full license is in `licenses/piper-phonemize-GPL-3.0.txt`.
- **Mana Persian Piper medium (fa-IR)** — the Hugging Face model card declares MIT; source and card: <https://huggingface.co/MahtaFetrat/Mana-Persian-Piper>. Its base checkpoint is `SadeghK/persian-text-to-speech`, whose model listing declares Apache-2.0. The model card recommends context-aware phonemization for Persian. This demo uses the standard Piper phonemizer rather than the context-aware/LCA pipeline.

The voice model is roughly 64 MB and is included in the APK. It runs on-device; app source and user poem text are not sent to a service. Before publicly distributing a build containing the GPL phonemizer, review applicable copyleft/source-offer requirements and the model’s current upstream terms. This notice is informational, not legal advice.
