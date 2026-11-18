#!/usr/bin/env python3
"""Prepare a Piper ONNX voice for Sherpa-ONNX (see Sherpa's Piper guide).

Usage:
  python tools/prepare_piper_sherpa.py \
    app/src/main/iqbal-assets/assets/tts/piper-fa/fa_IR-mana-medium.onnx \
    app/src/main/iqbal-assets/assets/tts/piper-fa/fa_IR-mana-medium.onnx.json

Requires onnx==1.17.0. This updates the ONNX metadata and writes tokens.txt next to it.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import onnx


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("model", type=Path)
    parser.add_argument("config", type=Path)
    args = parser.parse_args()

    model_path = args.model.resolve()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    audio = config["audio"]
    language = config["language"]
    espeak = config["espeak"]
    id_map = config["phoneme_id_map"]

    tokens_path = model_path.with_name("tokens.txt")
    with tokens_path.open("w", encoding="utf-8", newline="\n") as token_file:
        for symbol, ids in id_map.items():
            token_file.write(f"{symbol} {int(ids[0])}\n")

    graph = onnx.load(str(model_path), load_external_data=True)
    graph.ClearField("metadata_props")
    metadata = {
        "model_type": "vits",
        "comment": "piper",
        "language": language.get("name_english", "Persian"),
        "voice": espeak["voice"],
        "has_espeak": 1,
        "n_speakers": int(config["num_speakers"]),
        "sample_rate": int(audio["sample_rate"]),
    }
    for key, value in metadata.items():
        item = graph.metadata_props.add()
        item.key = key
        item.value = str(value)

    temp_path = model_path.with_suffix(".sherpa.tmp.onnx")
    onnx.save_model(graph, str(temp_path))
    onnx.checker.check_model(str(temp_path))
    temp_path.replace(model_path)
    print(f"Prepared {model_path}")
    print(f"Wrote {tokens_path} ({len(id_map)} symbols)")
    print(f"Metadata: {metadata}")


if __name__ == "__main__":
    main()
