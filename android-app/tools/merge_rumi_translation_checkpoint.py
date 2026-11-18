#!/usr/bin/env python3
"""Merge only complete, provenance-backed Rumi AI drafts into a local poet pack.

The source corpus and translated checkpoint are opened read-only. The generated
moulavi.sqlite pack is the only SQLite file modified. Unprovenanced or partial
translations are excluded and counted in the catalog/manifest.
"""
from __future__ import annotations

import argparse
import json
import shutil
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "app/src/main/assets/demo_poetry.db"
DEFAULT_CHECKPOINT = Path("/workspace/extracted_app_db/MainDB_translated_checkpoint.db")
DEFAULT_PACK_DIR = ROOT / "data-packs"
DEFAULT_ASSET_PACK_DIR = ROOT / "app/src/main/iqbal-assets/assets/poet_packs"
DEFAULT_CATALOG = ROOT / "app/src/main/iqbal-assets/assets/data/poet_catalog.json"
PROVENANCE_COLUMNS = (
    "poem_id,english_status,urdu_status,glossary_status,method,model_label,"
    "source_translation_used,online_availability_checked,human_review_status,"
    "source_url,generated_at,notes"
)

AUDIT_SQL = """
WITH per_poem AS (
  SELECT p.id,COUNT(v.vorder) AS n,
         SUM(CASE WHEN TRIM(COALESCE(v.translation_en,''))<>'' THEN 1 ELSE 0 END) AS en,
         SUM(CASE WHEN TRIM(COALESCE(v.translation_ur,''))<>'' THEN 1 ELSE 0 END) AS ur,
         MAX(CASE WHEN tp.poem_id IS NOT NULL THEN 1 ELSE 0 END) AS has_provenance
  FROM ganjoor_poem p
  LEFT JOIN ganjoor_verse v ON v.poem_id=p.id
  LEFT JOIN translation_provenance tp ON tp.poem_id=p.id
  WHERE p.poet_id=?
  GROUP BY p.id
)
SELECT COUNT(*),
       SUM(CASE WHEN n>0 AND en=n AND ur=n THEN 1 ELSE 0 END),
       SUM(CASE WHEN en>0 OR ur>0 THEN 1 ELSE 0 END),
       SUM(CASE WHEN (en>0 OR ur>0) AND (en<n OR ur<n) THEN 1 ELSE 0 END),
       SUM(CASE WHEN en=0 AND ur=0 THEN 1 ELSE 0 END),
       SUM(CASE WHEN n>0 AND en=n AND ur=n AND has_provenance=1 THEN 1 ELSE 0 END),
       SUM(CASE WHEN (en>0 OR ur>0) AND has_provenance=0 THEN 1 ELSE 0 END),
       SUM(n),SUM(en),SUM(ur)
FROM per_poem
"""


def open_ro(path: Path) -> sqlite3.Connection:
    return sqlite3.connect(f"file:{path.resolve().as_posix()}?mode=ro", uri=True, timeout=20)


def sha256(path: Path) -> str:
    import hashlib

    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def update_json(path: Path, pack_path: Path, data: dict) -> None:
    if not path.is_file():
        return
    document = json.loads(path.read_text(encoding="utf-8"))
    for poet in document.get("poets", []):
        if poet.get("slug") == "moulavi":
            poet.update(data)
            poet["pack_size_bytes"] = pack_path.stat().st_size
            poet["sha256"] = sha256(pack_path)
            poet["pack_file"] = pack_path.name
    document["translation_audit"] = data["translation_audit"]
    document["translation_note"] = data["translation_note"]
    path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--checkpoint", type=Path, default=DEFAULT_CHECKPOINT)
    parser.add_argument("--pack-dir", type=Path, default=DEFAULT_PACK_DIR)
    parser.add_argument("--asset-pack-dir", type=Path, default=DEFAULT_ASSET_PACK_DIR)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    args = parser.parse_args()
    for path in (args.source, args.checkpoint):
        if not path.is_file():
            raise SystemExit(f"Required read-only input is missing: {path}")
    source = open_ro(args.source)
    checkpoint = open_ro(args.checkpoint)
    source.execute("PRAGMA query_only=ON")
    checkpoint.execute("PRAGMA query_only=ON")
    try:
        source_poet = source.execute("SELECT id FROM ganjoor_poet WHERE slug='moulavi'").fetchone()
        checkpoint_poet = checkpoint.execute("SELECT id FROM ganjoor_poet WHERE slug='moulavi'").fetchone()
        if not source_poet or not checkpoint_poet:
            raise RuntimeError("Rumi (moulavi) is missing from a source database")
        audit_row = checkpoint.execute(AUDIT_SQL, (checkpoint_poet[0],)).fetchone()
        (total_poems, complete_bilingual, any_translations, partial_poems, untranslated,
         complete_with_provenance, translated_without_provenance, total_verses,
         english_lines, urdu_lines) = (int(v or 0) for v in audit_row)
        excluded_ids = [r[0] for r in checkpoint.execute(
            """WITH per_poem AS (
                 SELECT p.id,COUNT(v.vorder) n,
                        SUM(CASE WHEN TRIM(COALESCE(v.translation_en,''))<>'' THEN 1 ELSE 0 END) en,
                        SUM(CASE WHEN TRIM(COALESCE(v.translation_ur,''))<>'' THEN 1 ELSE 0 END) ur,
                        MAX(CASE WHEN tp.poem_id IS NOT NULL THEN 1 ELSE 0 END) has_provenance
                 FROM ganjoor_poem p LEFT JOIN ganjoor_verse v ON v.poem_id=p.id
                 LEFT JOIN translation_provenance tp ON tp.poem_id=p.id
                 WHERE p.poet_id=? GROUP BY p.id
               ) SELECT id FROM per_poem WHERE n>0 AND en=n AND ur=n AND has_provenance=0 ORDER BY id""",
            (checkpoint_poet[0],)
        )]

        provenance_rows = checkpoint.execute(
            f"SELECT {PROVENANCE_COLUMNS} FROM translation_provenance tp "
            "JOIN ganjoor_poem p ON p.id=tp.poem_id WHERE p.poet_id=? ORDER BY tp.poem_id",
            (checkpoint_poet[0],),
        ).fetchall()
        verified = []
        line_updates = []
        rejected_status = 0
        rejected_mismatch = 0
        rejected_incomplete = 0
        for record in provenance_rows:
            (poem_id, english_status, urdu_status, _glossary_status, _method,
             _model_label, source_translation_used, _availability, human_review,
             _source_url, _generated_at, _notes) = record
            if (english_status != "ai_generated_draft" or urdu_status != "ai_generated_draft"
                    or source_translation_used != 0 or human_review != "not_human_reviewed"):
                rejected_status += 1
                continue
            checkpoint_lines = checkpoint.execute(
                "SELECT vorder,text_persian,translation_en,translation_ur FROM ganjoor_verse WHERE poem_id=? ORDER BY vorder",
                (poem_id,),
            ).fetchall()
            source_lines = source.execute(
                "SELECT vorder,text_persian,translation_en,translation_ur FROM ganjoor_verse WHERE poem_id=? ORDER BY vorder",
                (poem_id,),
            ).fetchall()
            if not checkpoint_lines or len(checkpoint_lines) != len(source_lines):
                rejected_mismatch += 1
                continue
            if any(a[0] != b[0] or a[1] != b[1] for a, b in zip(checkpoint_lines, source_lines)):
                rejected_mismatch += 1
                continue
            if any(not (str(line[2] or "").strip() and str(line[3] or "").strip()) for line in checkpoint_lines):
                rejected_incomplete += 1
                continue
            if any(str(line[2] or "").strip() or str(line[3] or "").strip() for line in source_lines):
                raise RuntimeError(f"Refusing to overwrite pre-existing source translation in poem {poem_id}")
            verified.append(record)
            line_updates.extend((line[2], line[3], poem_id, line[0]) for line in checkpoint_lines)

        pack_path = args.pack_dir / "moulavi.sqlite"
        if not pack_path.is_file():
            raise SystemExit(f"Generated Rumi pack not found: {pack_path}; run build_poet_packs.py first")
        staged_pack = pack_path.with_name("moulavi.translation-staged.sqlite")
        staged_pack.unlink(missing_ok=True)
        shutil.copy2(pack_path, staged_pack)
        target = sqlite3.connect(staged_pack)
        try:
            target.execute("PRAGMA foreign_keys=ON")
            target.execute("BEGIN IMMEDIATE")
            target.execute(
                "CREATE TABLE IF NOT EXISTS translation_provenance ("
                "poem_id INTEGER PRIMARY KEY,english_status TEXT NOT NULL,urdu_status TEXT NOT NULL,"
                "glossary_status TEXT NOT NULL,method TEXT NOT NULL,model_label TEXT NOT NULL,"
                "source_translation_used INTEGER NOT NULL,online_availability_checked TEXT NOT NULL,"
                "human_review_status TEXT NOT NULL,source_url TEXT,generated_at TEXT NOT NULL,notes TEXT NOT NULL)"
            )
            target.executemany(
                "UPDATE ganjoor_verse SET translation_en=?,translation_ur=? WHERE poem_id=? AND vorder=?",
                line_updates,
            )
            target.executemany(
                f"INSERT OR REPLACE INTO translation_provenance ({PROVENANCE_COLUMNS}) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                verified,
            )
            audit = {
                "source_checkpoint": args.checkpoint.name,
                "rumi_total_poems": total_poems,
                "complete_bilingual_poems_in_checkpoint": complete_bilingual,
                "complete_bilingual_with_provenance": complete_with_provenance,
                "included_complete_bilingual_ai_drafts": len(verified),
                "included_english_lines": len(line_updates),
                "included_urdu_lines": len(line_updates),
                "partial_translation_poems": partial_poems,
                "complete_unprovenanced_poems_excluded": translated_without_provenance,
                "unprovenanced_complete_poem_ids_excluded": excluded_ids,
                "untranslated_poems": untranslated,
                "rumi_verse_rows": total_verses,
                "checkpoint_english_lines_all": english_lines,
                "checkpoint_urdu_lines_all": urdu_lines,
                "excluded_provenance_status_records": rejected_status,
                "excluded_source_mismatch_poems": rejected_mismatch,
                "excluded_incomplete_bilingual_poems": rejected_incomplete,
                "review_status": "not_human_reviewed",
                "model_label": "AI runtime; exact model ID unverified",
            }
            excluded_id_text = ", ".join(str(value) for value in excluded_ids) or "none"
            note = (
                f"Rumi translations: {len(verified)} complete bilingual AI-generated drafts, "
                f"covering {len(line_updates):,} aligned verse rows in each language; all are "
                f"marked not_human_reviewed. {translated_without_provenance} complete bilingual "
                f"poems without provenance (IDs {excluded_id_text}) are excluded; no partial "
                f"translations were found; {untranslated} Rumi poems remain untranslated. "
                "The AI model ID is unverified."
            )
            target.execute(
                "INSERT OR REPLACE INTO database_notes(note_key,note_text) VALUES (?,?)",
                ("ai_draft_translation_status", note),
            )
            target.execute(
                "INSERT OR REPLACE INTO source_catalog VALUES (?,?,?,?,?,?,?,?)",
                (
                    "ai-generated-rumi-drafts", "AI-generated Rumi draft translations", "",
                    "", args.checkpoint.name,
                    f"{len(verified)} complete bilingual poems; {len(line_updates)} aligned lines per language",
                    "AI-generated drafts only; not human-reviewed or verified; exact model ID unverified",
                    "Five complete unprovenanced poems and all partial/untranslated poems excluded.",
                ),
            )
            metadata = {
                "translation_source": args.checkpoint.name,
                "translation_status": "ai_generated_draft_pending_human_review",
                "translation_review_status": "not_human_reviewed",
                "translation_model_label": "AI runtime; exact model ID unverified",
                "complete_bilingual_ai_draft_poems": str(len(verified)),
                "complete_bilingual_ai_draft_lines_en": str(len(line_updates)),
                "complete_bilingual_ai_draft_lines_ur": str(len(line_updates)),
                "unprovenanced_complete_poems_excluded": str(translated_without_provenance),
                "partial_translation_poems": str(partial_poems),
            }
            target.executemany(
                "INSERT OR REPLACE INTO pack_metadata(key,value) VALUES (?,?)", metadata.items()
            )
            target.commit()
            result = target.execute("PRAGMA integrity_check").fetchone()[0]
            if result != "ok":
                raise RuntimeError(f"Rumi pack integrity failure: {result}")
        except Exception:
            target.rollback()
            raise
        finally:
            target.close()

        staged_pack.replace(pack_path)

        args.asset_pack_dir.mkdir(parents=True, exist_ok=True)
        staged = args.asset_pack_dir / "moulavi.sqlite.tmp"
        shutil.copy2(pack_path, staged)
        staged.replace(args.asset_pack_dir / "moulavi.sqlite")
        shared_data = {
            "english_translation_count": len(line_updates),
            "urdu_translation_count": len(line_updates),
            "bilingual_complete_poem_count": len(verified),
            "ai_draft_pending_human_review": True,
            "translation_review_status": "not_human_reviewed",
            "translation_model_label": "AI runtime; exact model ID unverified",
            "translation_source": args.checkpoint.name,
            "unprovenanced_complete_poems_excluded": translated_without_provenance,
            "unprovenanced_complete_poem_ids_excluded": excluded_ids,
            "partial_translation_poems_excluded": partial_poems,
            "untranslated_poem_count": untranslated,
            "translation_audit": audit,
            "translation_note": note,
        }
        update_json(args.catalog, pack_path, shared_data)
        update_json(args.pack_dir / "manifest.json", pack_path, shared_data)
        print(note)
        print(
            "Audit summary:",
            f"complete bilingual={complete_bilingual}, provenance-backed={complete_with_provenance},",
            f"included={len(verified)}, partial={partial_poems},",
            f"unprovenanced excluded={translated_without_provenance} IDs={excluded_ids},",
            f"source mismatches={rejected_mismatch}, incomplete rejected={rejected_incomplete},",
            f"Rumi total poems={total_poems}, untranslated={untranslated}.",
        )
        print(f"Updated local pack: {pack_path} ({pack_path.stat().st_size:,} bytes)")
    finally:
        source.close()
        checkpoint.close()


if __name__ == "__main__":
    main()
