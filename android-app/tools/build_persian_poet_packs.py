#!/usr/bin/env python3
"""Build one app-compatible SQLite pack per Ganjoor Persian-language poet.

The source database is read-only. Opaque source_json_zlib columns are omitted as in
existing Sukhan packs; source labels, canonical poem/verse fields, and translation
columns are retained. Existing Rumi/Saadi bundled packs are never rewritten.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sqlite3
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

SOURCE_URL = "https://github.com/ganjoor/ganjoor-data"
PACK_BASE_URL = "https://raw.githubusercontent.com/usmanbhat/sukhan/main"
RELEASE_URLS = {9: "https://github.com/usmanbhat/sukhan/releases/download/ganjoor-fa-2026-11/attar.sqlite"}
EXCLUDED_IDS = {2, 5, 7, 34, 42}  # Hafez, Rumi, Saadi, Amir Khusro, Iqbal already exist.
BUNDLED_IDS = {5, 7}
KNOWN_ENGLISH = {
    2: "Hafez", 5: "Rumi", 7: "Saadi", 34: "Amir Khusro", 42: "Iqbal",
}

SCHEMA = """
CREATE TABLE database_notes (note_key TEXT PRIMARY KEY, note_text TEXT NOT NULL);
CREATE TABLE source_catalog (
  source_key TEXT PRIMARY KEY, source_name TEXT NOT NULL, source_url TEXT,
  source_revision TEXT, source_snapshot TEXT, included_scope TEXT,
  reuse_note TEXT, import_note TEXT
);
CREATE TABLE ganjoor_category (
  id INTEGER PRIMARY KEY, poet_id INTEGER NOT NULL, parent_id INTEGER,
  title TEXT, full_url TEXT, category_type TEXT, child_categories_json TEXT,
  persian_poem_refs_json TEXT
);
CREATE TABLE ganjoor_poet (
  id INTEGER PRIMARY KEY, slug TEXT NOT NULL, name TEXT, nickname TEXT,
  full_url TEXT, description_fa TEXT, birth_year_hijri INTEGER,
  death_year_hijri INTEGER, birth_place TEXT, death_place TEXT
);
CREATE TABLE ganjoor_poem (
  id INTEGER PRIMARY KEY, poet_id INTEGER NOT NULL, category_id INTEGER,
  title TEXT, full_title TEXT, full_url TEXT, rhyme_letters TEXT,
  poem_summary_fa TEXT, source_name TEXT, source_url_slug TEXT, metre_json TEXT,
  sections_json TEXT, literary_tags_json TEXT, persian_verse_count INTEGER NOT NULL
);
CREATE TABLE ganjoor_verse (
  poem_id INTEGER NOT NULL, vorder INTEGER NOT NULL, position TEXT,
  text_persian TEXT NOT NULL, couplet_index INTEGER, section_index INTEGER,
  couplet_summary_fa TEXT, translation_en TEXT, translation_ur TEXT,
  hard_word_meanings TEXT, PRIMARY KEY (poem_id, vorder)
);
CREATE TABLE translation_provenance (
  poem_id INTEGER PRIMARY KEY, english_status TEXT NOT NULL,
  urdu_status TEXT NOT NULL, glossary_status TEXT NOT NULL,
  method TEXT NOT NULL, model_label TEXT NOT NULL,
  source_translation_used INTEGER NOT NULL,
  online_availability_checked TEXT NOT NULL,
  human_review_status TEXT NOT NULL, source_url TEXT,
  generated_at TEXT NOT NULL, notes TEXT NOT NULL
);
CREATE TABLE pack_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""
INDEXES = """
CREATE INDEX ganjoor_poem_poet_id ON ganjoor_poem(poet_id);
CREATE INDEX ganjoor_poem_category_id ON ganjoor_poem(category_id);
CREATE INDEX ganjoor_verse_couplet ON ganjoor_verse(poem_id, couplet_index);
CREATE INDEX ganjoor_category_poet_id ON ganjoor_category(poet_id);
"""
SOURCE_NOTE_TRANSLATIONS = (
    "translation_en, translation_ur, and hard_word_meanings are intentionally NULL "
    "for poets without reviewed, rights-cleared translations or glosses. The Ganjoor "
    "export provides Persian text and Persian paraphrases, not vetted English/Urdu "
    "translations or a lexicon. These columns are included for future reviewed content."
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def slug_english(slug: str) -> str:
    return " ".join(part.capitalize() for part in slug.replace("_", "-").split("-") if part)


def source_rows(source: sqlite3.Connection, sql: str, args: tuple, target: sqlite3.Connection,
                insert_sql: str, batch_size: int = 4000) -> int:
    cur = source.execute(sql, args)
    total = 0
    while True:
        batch = cur.fetchmany(batch_size)
        if not batch:
            break
        target.executemany(insert_sql, batch)
        total += len(batch)
    return total


def make_pack(source: sqlite3.Connection, source_meta: dict[str, str], source_notes: list[tuple],
              poet: tuple, english_name: str, output: Path) -> dict:
    pid, slug = poet[0], poet[1]
    temp = output.with_suffix(output.suffix + ".building")
    if temp.exists():
        temp.unlink()
    db = sqlite3.connect(temp)
    db.execute("PRAGMA journal_mode=OFF")
    db.execute("PRAGMA synchronous=OFF")
    db.execute("PRAGMA cache_size=-32768")
    db.executescript(SCHEMA)
    db.execute("BEGIN")
    try:
        source_rows(
            source,
            "SELECT id,poet_id,parent_id,title,full_url,category_type,child_categories_json,persian_poem_refs_json "
            "FROM ganjoor_category WHERE poet_id=? ORDER BY id",
            (pid,), db,
            "INSERT INTO ganjoor_category VALUES (?,?,?,?,?,?,?,?)",
        )
        source_rows(
            source,
            "SELECT id,slug,name,nickname,full_url,description_fa,birth_year_hijri,death_year_hijri,birth_place,death_place "
            "FROM ganjoor_poet WHERE id=?",
            (pid,), db,
            "INSERT INTO ganjoor_poet VALUES (?,?,?,?,?,?,?,?,?,?)",
        )
        source_rows(
            source,
            "SELECT id,poet_id,category_id,title,full_title,full_url,rhyme_letters,poem_summary_fa,source_name,source_url_slug,"
            "metre_json,sections_json,literary_tags_json,persian_verse_count FROM ganjoor_poem WHERE poet_id=? ORDER BY id",
            (pid,), db,
            "INSERT INTO ganjoor_poem VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        )
        source_rows(
            source,
            "SELECT poem_id,vorder,position,text_persian,couplet_index,section_index,couplet_summary_fa,translation_en,translation_ur,hard_word_meanings "
            "FROM ganjoor_verse WHERE poem_id IN (SELECT id FROM ganjoor_poem WHERE poet_id=?) ORDER BY poem_id,vorder",
            (pid,), db,
            "INSERT INTO ganjoor_verse VALUES (?,?,?,?,?,?,?,?,?,?)",
        )
        db.executemany("INSERT INTO source_catalog VALUES (?,?,?,?,?,?,?,?)", source.execute("SELECT * FROM source_catalog"))
        db.executemany("INSERT INTO database_notes VALUES (?,?)", source_notes)
        db.execute("INSERT INTO database_notes VALUES (?,?)", ("pack_translation_columns", SOURCE_NOTE_TRANSLATIONS))
        db.executescript(INDEXES)
        poem_count = db.execute("SELECT COUNT(*) FROM ganjoor_poem").fetchone()[0]
        verse_count = db.execute("SELECT COUNT(*) FROM ganjoor_verse").fetchone()[0]
        nonempty = db.execute("SELECT COUNT(*) FROM ganjoor_verse WHERE TRIM(COALESCE(text_persian,''))<>''").fetchone()[0]
        en_count = db.execute("SELECT COUNT(*) FROM ganjoor_verse WHERE TRIM(COALESCE(translation_en,''))<>''").fetchone()[0]
        ur_count = db.execute("SELECT COUNT(*) FROM ganjoor_verse WHERE TRIM(COALESCE(translation_ur,''))<>''").fetchone()[0]
        metadata = {
            "format_version": "1", "poet_id": str(pid), "slug": slug,
            "english_name": english_name, "poem_count": str(poem_count),
            "verse_count": str(verse_count), "nonempty_verse_count": str(nonempty),
            "english_translation_count": str(en_count), "urdu_translation_count": str(ur_count),
            "source_name": source_meta["name"], "source_url": source_meta["url"],
            "source_revision": source_meta["revision"], "source_snapshot": source_meta["snapshot"],
            "source_json_zlib": "omitted from pack; source DB is preserved locally",
        }
        db.executemany("INSERT INTO pack_metadata VALUES (?,?)", metadata.items())
        db.commit()
        check = db.execute("PRAGMA quick_check(1)").fetchone()[0]
        if check != "ok":
            raise RuntimeError(f"SQLite check failed for {slug}: {check}")
    except Exception:
        db.rollback()
        db.close()
        if temp.exists():
            temp.unlink()
        raise
    db.close()
    os.replace(temp, output)
    return {
        "poem_count": poem_count, "verse_count": verse_count,
        "nonempty_verse_count": nonempty,
        "english_translation_count": en_count, "urdu_translation_count": ur_count,
    }


def check_pack(path: Path, poet_id: int) -> dict:
    db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        if db.execute("PRAGMA quick_check(1)").fetchone()[0] != "ok":
            raise RuntimeError(f"Existing pack is corrupt: {path}")
        row = db.execute("SELECT id,slug,name,nickname,full_url,birth_year_hijri,death_year_hijri FROM ganjoor_poet").fetchone()
        if not row or row[0] != poet_id:
            raise RuntimeError(f"Poet ID mismatch in {path}: {row}")
        stats = {
            "poem_count": db.execute("SELECT COUNT(*) FROM ganjoor_poem").fetchone()[0],
            "verse_count": db.execute("SELECT COUNT(*) FROM ganjoor_verse").fetchone()[0],
            "nonempty_verse_count": db.execute("SELECT COUNT(*) FROM ganjoor_verse WHERE TRIM(COALESCE(text_persian,''))<>''").fetchone()[0],
            "english_translation_count": db.execute("SELECT COUNT(*) FROM ganjoor_verse WHERE TRIM(COALESCE(translation_en,''))<>''").fetchone()[0],
            "urdu_translation_count": db.execute("SELECT COUNT(*) FROM ganjoor_verse WHERE TRIM(COALESCE(translation_ur,''))<>''").fetchone()[0],
        }
        return {"row": row, **stats}
    finally:
        db.close()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--app", type=Path, required=True)
    parser.add_argument("--repo", type=Path, required=True)
    args = parser.parse_args()
    app = args.app.resolve()
    repo = args.repo.resolve()
    source_path = args.source.resolve()
    current_catalog_path = app / "app/src/main/iqbal-assets/assets/data/poet_catalog.json"
    current_catalog = json.loads(current_catalog_path.read_text(encoding="utf-8"))
    previous_by_id = {p["id"]: p for p in current_catalog["poets"]}

    source = sqlite3.connect(f"file:{source_path}?mode=ro", uri=True)
    source.row_factory = None
    source_meta_row = source.execute(
        "SELECT source_name,source_url,source_revision,source_snapshot,included_scope,reuse_note FROM source_catalog WHERE source_key='ganjoor-public-export'"
    ).fetchone()
    if not source_meta_row:
        raise RuntimeError("Pinned Ganjoor source metadata not found")
    source_meta = dict(zip(("name", "url", "revision", "snapshot", "included_scope", "reuse_note"), source_meta_row))
    source_notes = source.execute("SELECT note_key,note_text FROM database_notes ORDER BY note_key").fetchall()
    poets = source.execute(
        "SELECT id,slug,name,nickname,full_url,birth_year_hijri,death_year_hijri,description_fa,birth_place,death_place FROM ganjoor_poet ORDER BY id"
    ).fetchall()
    if len(poets) != 245:
        raise RuntimeError(f"Expected 245 Persian poets in the pinned source, found {len(poets)}")

    # Copy the existing, selected optional packs into the existing public layout unchanged.
    packs_dir = app / "data-packs"
    bundled_dir = app / "app/src/main/iqbal-assets/assets/poet_packs"
    stats_by_id: dict[int, dict] = {}
    source_rows_by_id: dict[int, tuple] = {p[0]: p for p in poets}
    english_by_id: dict[int, str] = {}
    for p in poets:
        pid, slug = p[0], p[1]
        legacy = source.execute("SELECT poet_name FROM poet WHERE id=?", (pid,)).fetchone()
        old_name = previous_by_id.get(pid, {}).get("english_name")
        english_by_id[pid] = old_name or KNOWN_ENGLISH.get(pid) or ((legacy[0].strip()) if legacy and legacy[0] and legacy[0].strip() else slug_english(slug))
        if pid in EXCLUDED_IDS:
            src_path = (bundled_dir / f"{slug}.sqlite") if pid in BUNDLED_IDS else (packs_dir / f"{slug}.sqlite")
            if not src_path.is_file():
                raise FileNotFoundError(f"Existing pack not found: {src_path}")
            stats_by_id[pid] = check_pack(src_path, pid)
            stats_by_id[pid].pop("row", None)
            if pid not in BUNDLED_IDS:
                shutil.copy2(src_path, repo / f"{slug}.sqlite")
            continue
        output = repo / f"{slug}.sqlite"
        stats_by_id[pid] = make_pack(source, source_meta, source_notes, p, english_by_id[pid], output)
        print(f"PACK {pid:3d} {slug:<22} poems={stats_by_id[pid]['poem_count']:6d} verses={stats_by_id[pid]['verse_count']:7d} bytes={output.stat().st_size}", flush=True)

    # Verify all 245 source poets were represented and derive hashes from the final bytes.
    entries = []
    for p in poets:
        pid, slug, name, nickname, full_url, birth, death = p[:7]
        st = stats_by_id[pid]
        filename = f"{slug}.sqlite"
        bundled = pid in BUNDLED_IDS
        pack_path = (bundled_dir / filename) if bundled else (repo / filename)
        size = pack_path.stat().st_size
        entry = dict(previous_by_id.get(pid, {}))
        entry.update({
            "id": pid, "slug": slug, "name": name or "", "nickname": nickname or "",
            "english_name": english_by_id[pid], "full_url": full_url or "",
            "birth_year_hijri": birth, "death_year_hijri": death,
            "poem_count": st["poem_count"], "verse_count": st["verse_count"],
            "nonempty_verse_count": st["nonempty_verse_count"],
            "english_translation_count": st["english_translation_count"],
            "urdu_translation_count": st["urdu_translation_count"],
            "bundled_in_app": bundled, "pack_file": filename,
            "pack_size_bytes": size, "sha256": sha256(pack_path),
            "download_url": "" if bundled else RELEASE_URLS.get(pid, f"{PACK_BASE_URL}/{filename}"),
        })
        entries.append(entry)
    entries.sort(key=lambda x: (not x["bundled_in_app"], x["english_name"].casefold(), x["id"]))
    poem_total = sum(p["poem_count"] for p in entries)
    verse_total = sum(p["verse_count"] for p in entries)
    download_count = sum(not p["bundled_in_app"] for p in entries)
    translation_note = (
        "For all poets other than Rumi, English/Urdu translation and hard-word glossary fields are empty; "
        "the columns are ready for reviewed, rights-cleared content. Rumi includes 996 complete bilingual "
        "AI-generated draft poems across 21,024 aligned lines in each language. These drafts are not "
        "human-reviewed; the exact model ID is unverified. Five complete Rumi poems without provenance "
        "are excluded from the translations."
    )
    manifest = {
        "format_version": 1,
        "created_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "source": {
            "name": source_meta["name"], "url": source_meta["url"],
            "revision": source_meta["revision"], "snapshot": source_meta["snapshot"],
            "included_scope": source_meta["included_scope"], "reuse_note": source_meta["reuse_note"],
        },
        "catalog_summary": {
            "poet_count": len(entries), "poem_count": poem_total,
            "verse_count": verse_total, "bundled_pack_count": len(BUNDLED_IDS),
            "optional_download_count": download_count,
        },
        "translation_note": translation_note,
        "bundled_slugs": ["moulavi", "saadi"],
        "poets": entries,
    }
    if "translation_audit" in current_catalog:
        manifest["translation_audit"] = current_catalog["translation_audit"]
    app_catalog_path = app / "app/src/main/iqbal-assets/assets/data/poet_catalog.json"
    data_manifest_path = app / "data-packs/manifest.json"
    for path in (app_catalog_path, data_manifest_path, repo / "manifest.json"):
        path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    source.close()
    print(f"TOTAL poets={len(entries)} poems={poem_total} verses={verse_total} bundled=2 optional={download_count}")
    print(f"PUBLIC PACKS: {sum(not e['bundled_in_app'] for e in entries)}; new packs: {len(entries)-len(EXCLUDED_IDS)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
