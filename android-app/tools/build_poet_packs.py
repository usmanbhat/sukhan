#!/usr/bin/env python3
"""Split the preserved Ganjoor SQLite export into small, provenance-carrying poet packs.

The input is opened read-only. Every pack keeps app-facing Persian text, summaries,
source labels, empty EN/UR translation columns, database notes, and source catalog.
The redundant source_json_zlib columns are omitted from packs; the full source DB is
left untouched. Only Rumi (slug=moulavi) and Saadi are copied into Android assets.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "app/src/main/assets/demo_poetry.db"
DEFAULT_PACK_DIR = ROOT / "data-packs"
DEFAULT_ASSET_PACK_DIR = ROOT / "app/src/main/iqbal-assets/assets/poet_packs"
DEFAULT_CATALOG = ROOT / "app/src/main/iqbal-assets/assets/data/poet_catalog.json"
BUNDLED = {"moulavi", "saadi"}
LATIN_NAMES = {
    "moulavi": "Rumi",
    "saadi": "Saadi",
    "hafez": "Hafez",
    "iqbal": "Iqbal",
    "khosro": "Amir Khusro",
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
CREATE INDEX ganjoor_poem_poet_id ON ganjoor_poem(poet_id);
CREATE INDEX ganjoor_poem_category_id ON ganjoor_poem(category_id);
CREATE INDEX ganjoor_verse_couplet ON ganjoor_verse(poem_id, couplet_index);
CREATE INDEX ganjoor_category_poet_id ON ganjoor_category(poet_id);
"""

POET_COLS = (
    "id,slug,name,nickname,full_url,description_fa,birth_year_hijri,"
    "death_year_hijri,birth_place,death_place"
)
CATEGORY_COLS = (
    "id,poet_id,parent_id,title,full_url,category_type,"
    "child_categories_json,persian_poem_refs_json"
)
POEM_COLS = (
    "id,poet_id,category_id,title,full_title,full_url,rhyme_letters,"
    "poem_summary_fa,source_name,source_url_slug,metre_json,sections_json,"
    "literary_tags_json,persian_verse_count"
)
VERSE_COLS = (
    "v.poem_id,v.vorder,v.position,v.text_persian,v.couplet_index,v.section_index,"
    "v.couplet_summary_fa,v.translation_en,v.translation_ur,v.hard_word_meanings"
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def poet_rows(source: sqlite3.Connection) -> list[dict]:
    query = """
      SELECT p.id,p.slug,p.name,COALESCE(p.nickname,''),p.full_url,
             p.birth_year_hijri,p.death_year_hijri,
             COUNT(DISTINCT po.id),COUNT(v.vorder),
             SUM(CASE WHEN TRIM(COALESCE(v.text_persian,''))<>'' THEN 1 ELSE 0 END),
             SUM(CASE WHEN TRIM(COALESCE(v.translation_en,''))<>'' THEN 1 ELSE 0 END),
             SUM(CASE WHEN TRIM(COALESCE(v.translation_ur,''))<>'' THEN 1 ELSE 0 END)
      FROM ganjoor_poet p
      LEFT JOIN ganjoor_poem po ON po.poet_id=p.id
      LEFT JOIN ganjoor_verse v ON v.poem_id=po.id
      GROUP BY p.id,p.slug,p.name,p.nickname,p.full_url,p.birth_year_hijri,p.death_year_hijri
    """
    rows = []
    for row in source.execute(query):
        poet_id, slug, name, nickname, url, birth, death, poems, verses, nonempty, en, ur = row
        rows.append({
            "id": poet_id, "slug": slug, "name": name or "", "nickname": nickname or "",
            "english_name": LATIN_NAMES.get(slug, name or slug), "full_url": url or "",
            "birth_year_hijri": birth, "death_year_hijri": death,
            "poem_count": poems, "verse_count": verses,
            "nonempty_verse_count": nonempty or 0,
            "english_translation_count": en or 0, "urdu_translation_count": ur or 0,
            "bundled_in_app": slug in BUNDLED,
        })
    return sorted(rows, key=lambda p: (not p["bundled_in_app"], p["english_name"].casefold()))


def source_details(source: sqlite3.Connection) -> dict:
    row = source.execute("""
      SELECT source_name,source_url,source_revision,source_snapshot,included_scope,reuse_note
      FROM source_catalog WHERE source_url='https://github.com/ganjoor/ganjoor-data' LIMIT 1
    """).fetchone()
    if not row:
        raise RuntimeError("Source DB is missing its Ganjoor provenance row")
    return dict(zip(("name", "url", "revision", "snapshot", "included_scope", "reuse_note"), row))


def build_pack(source: sqlite3.Connection, pack_path: Path, poet: dict, source_info: dict) -> None:
    pack_path.parent.mkdir(parents=True, exist_ok=True)
    pack_path.unlink(missing_ok=True)
    target = sqlite3.connect(pack_path)
    try:
        target.execute("PRAGMA page_size=4096")
        target.execute("PRAGMA journal_mode=DELETE")
        target.execute("PRAGMA synchronous=OFF")
        target.executescript(SCHEMA)
        target.executemany("INSERT INTO database_notes VALUES (?,?)", source.execute("SELECT note_key,note_text FROM database_notes"))
        target.executemany(
            "INSERT INTO source_catalog VALUES (?,?,?,?,?,?,?,?)",
            source.execute("SELECT source_key,source_name,source_url,source_revision,source_snapshot,included_scope,reuse_note,import_note FROM source_catalog")
        )
        target.executemany(
            "INSERT INTO ganjoor_poet VALUES (?,?,?,?,?,?,?,?,?,?)",
            source.execute(f"SELECT {POET_COLS} FROM ganjoor_poet WHERE id=?", (poet["id"],))
        )
        target.executemany(
            "INSERT INTO ganjoor_category VALUES (?,?,?,?,?,?,?,?)",
            source.execute(f"SELECT {CATEGORY_COLS} FROM ganjoor_category WHERE poet_id=?", (poet["id"],))
        )
        target.executemany(
            "INSERT INTO ganjoor_poem VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            source.execute(f"SELECT {POEM_COLS} FROM ganjoor_poem WHERE poet_id=?", (poet["id"],))
        )
        target.executemany(
            "INSERT INTO ganjoor_verse VALUES (?,?,?,?,?,?,?,?,?,?)",
            source.execute(f"SELECT {VERSE_COLS} FROM ganjoor_verse v JOIN ganjoor_poem p ON p.id=v.poem_id WHERE p.poet_id=?", (poet["id"],))
        )
        metadata = {
            "format_version": "1",
            "poet_id": str(poet["id"]),
            "slug": poet["slug"],
            "english_name": poet["english_name"],
            "poem_count": str(poet["poem_count"]),
            "verse_count": str(poet["verse_count"]),
            "nonempty_verse_count": str(poet["nonempty_verse_count"]),
            "english_translation_count": str(poet["english_translation_count"]),
            "urdu_translation_count": str(poet["urdu_translation_count"]),
            "source_name": source_info["name"],
            "source_url": source_info["url"],
            "source_revision": source_info["revision"] or "",
            "source_snapshot": source_info["snapshot"] or "",
            "source_json_zlib": "omitted from pack; source DB is preserved locally",
        }
        target.executemany("INSERT INTO pack_metadata(key,value) VALUES (?,?)", metadata.items())
        target.execute("PRAGMA user_version=1")
        target.commit()
        check = target.execute("PRAGMA integrity_check").fetchone()[0]
        if check != "ok":
            raise RuntimeError(f"Integrity failure for {pack_path.name}: {check}")
        target.execute("VACUUM")
    finally:
        target.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--pack-dir", type=Path, default=DEFAULT_PACK_DIR)
    parser.add_argument("--asset-pack-dir", type=Path, default=DEFAULT_ASSET_PACK_DIR)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    args = parser.parse_args()
    source_path = args.source.resolve()
    if not source_path.is_file():
        raise SystemExit(f"Input DB does not exist: {source_path}")
    args.pack_dir.mkdir(parents=True, exist_ok=True)
    source = sqlite3.connect(f"file:{source_path.as_posix()}?mode=ro", uri=True)
    try:
        details = source_details(source)
        poets = poet_rows(source)
        if not poets:
            raise RuntimeError("No poets found in source DB")
        manifest_poets = []
        for poet in poets:
            out = args.pack_dir / f"{poet['slug']}.sqlite"
            build_pack(source, out, poet, details)
            poet["pack_file"] = out.name
            poet["pack_size_bytes"] = out.stat().st_size
            poet["sha256"] = sha256(out)
            poet["download_url"] = ""
            manifest_poets.append(poet)
            print(f"Built {out.name}: {out.stat().st_size:,} bytes; {poet['poem_count']:,} poems; {poet['verse_count']:,} verses")

        args.asset_pack_dir.mkdir(parents=True, exist_ok=True)
        for poet in manifest_poets:
            if poet["slug"] in BUNDLED:
                shutil.copy2(args.pack_dir / poet["pack_file"], args.asset_pack_dir / poet["pack_file"])

        catalog = {
            "format_version": 1,
            "source": details,
            "translation_note": "English/Urdu translations and hard-word meanings are empty in the supplied source. Only reviewed, rights-cleared translations should be shown.",
            "bundled_slugs": sorted(BUNDLED),
            "poets": manifest_poets,
        }
        args.catalog.parent.mkdir(parents=True, exist_ok=True)
        args.catalog.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        manifest = {
            "format_version": 1,
            "source": details,
            "created_at_utc": datetime.now(timezone.utc).isoformat(),
            "poets": manifest_poets,
        }
        (args.pack_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        total = sum(p["pack_size_bytes"] for p in manifest_poets)
        print(f"Catalog: {args.catalog}")
        print(f"Pack total: {total:,} bytes")
    finally:
        source.close()


if __name__ == "__main__":
    main()
