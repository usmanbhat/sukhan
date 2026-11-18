#!/usr/bin/env python3
from pathlib import Path
from io import BytesIO
import json
import sqlite3
import sys
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
APK = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "dist/Sukhan-offline-demo-debug.apk"
assert APK.is_file(), f"APK not found: {APK}"
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()
module = (ROOT / "app/build.gradle").read_text()
activity = (ROOT / "app/src/main/java/com/sukhan/poetry/MainActivity.kt").read_text()
corpus = (ROOT / "app/src/main/java/com/sukhan/poetry/CorpusStore.kt").read_text()
ai = (ROOT / "app/src/main/java/com/sukhan/poetry/AiIntegration.kt").read_text()
tts = (ROOT / "app/src/main/java/com/sukhan/poetry/PersianTts.kt").read_text()
widget = (ROOT / "app/src/main/java/com/sukhan/poetry/DailyQuoteWidgetProvider.kt").read_text()
settings = (ROOT / "app/src/main/java/com/sukhan/poetry/SettingsSheet.kt").read_text()
logo_script = (ROOT / "tools/render_sukhan_logo.py").read_text()

def composite_contrast_pixels(png_bytes, surface, y_fraction=0.0):
    image = Image.open(BytesIO(png_bytes)).convert("RGBA")
    canvas = Image.new("RGBA", image.size, surface + (255,))
    canvas.alpha_composite(image)
    y_start = int(image.height * y_fraction)
    pixels = canvas.crop((0, y_start, image.width, image.height)).convert("RGB").getdata()
    return sum(1 for pixel in pixels if sum(abs(pixel[i] - surface[i]) for i in range(3)) > 100)

assert 'android.permission.INTERNET' in manifest
assert 'android:label="Sukhan"' in manifest and 'android:icon="@drawable/ic_sukhan"' in manifest
assert "assets.srcDirs = ['src/main/iqbal-assets']" in module
assert "androidResources" in module and "noCompress += ['onnx']" in module
assert "com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v2.0.1" in module
assert "implementation files('libs/piper-phonemize-1.5.9.aar')" in module
assert "ColorMatrixColorFilter" in activity and "invertFilter" in activity
assert "setOnApplyWindowInsetsListener" in activity and "systemWindowInsetTop" in activity and "systemWindowInsetBottom" in activity
assert 'text("سخن"' in activity and 'text("IQBAL"' not in activity
assert 'SOURCE_PNG = BRAND_DIR / "sukhan-brand-source.png"' in logo_script
assert 'MARK_SVG = BRAND_DIR / "sukhan-mark-source.svg"' in logo_script
assert 'ICON_OUT = ROOT / "app/src/main/res/drawable-nodpi/ic_sukhan.png"' in logo_script
assert "render_wordmarks()" in logo_script and "render_launcher_mark()" in logo_script and "FONTCONFIG_FILE" in logo_script
assert "loadBitmap(splashLogoPath())" in activity and "loadBitmap(wordmarkLogoPath())" in activity
assert "sukhan-wordmark-dark.png" in activity and "sukhan-wordmark-sepia.png" in activity
mark_source = (ROOT / "app/src/main/iqbal-assets/assets/images/brand/sukhan-mark-source.svg").read_text()
assert 'aria-label="Sokhan mark"' in mark_source and "Aref Ruqaa" in mark_source and "{{ink}}" not in mark_source
assert "local.addRecentSearch(query)" in activity and "scheduleSearchSave" in activity
assert "ic_share_24" in activity and "Share as image" in activity
assert '"listen"' in activity and "togglePersianSpeech" in activity
assert "speechSpeed" in activity and "speechPitch" in activity
assert '"speech_speed"' in activity and '"speech_pitch"' in activity
assert "translationBadge" in activity and '"aiDraft"' in activity
assert "R.drawable.ic_download_24" in activity and "downloadPackFromUrl" in activity
assert "downloadFailed" in activity and "downloadUrl" in corpus and "HttpURLConnection" in corpus
assert "cdn.jsdelivr.net/gh/usmanbhat/sukhan@main/" in corpus, "Small-pack CDN fallback is missing"
assert "repeat(2)" in corpus and 'setRequestProperty("User-Agent"' in corpus, "Transient download retry/user-agent is missing"
assert 'setRequestProperty("Accept-Encoding", "identity")' in corpus, "Downloads must preserve manifest byte lengths"
assert "quickCheck(temporary)" in corpus and "actualHash.equals(poet.sha256" in corpus, "Pack integrity checks must remain enabled"
assert "rootCategories(poet.id)" in activity and "renderCategoryContents()" in activity and "poemsInCategory" in activity
assert "fun childCategories(poetId: Int, parentId: Int)" in corpus and "categoryPath" in activity
assert "TIMELINE" not in activity and "renderTimeline" not in activity and "Historical Timeline" not in activity
assert "DailyQuoteWidgetProvider" in manifest and "DailyQuoteShare.share" in activity
assert "onThemeChange" in settings and "onLanguageChange" in settings
assert '"speed"' in settings and '"pitch"' in settings and "SeekBar" in settings
assert "offline" in ai.lower() and "SUKHAN_AI_API_KEY" in module
assert "SQLiteDatabase.OPEN_READONLY" in corpus
assert 'piperPhonemizeText(chunk, "fa")' in tts
assert "phonemeCodepoints" in tts and "AudioTrack" in tts and "setPlaybackRate" in tts

with zipfile.ZipFile(APK) as zf:
    assert zf.testzip() is None, "APK zip integrity failed"
    names = zf.namelist()
    assert not any(name.endswith("demo_poetry.db") or name.endswith("review_poetry.db") for name in names), "Monolithic/demo DB must not ship"
    cover_assets = [name for name in names if "/images/covers/book_" in name and name.endswith(".png")]
    assert len(cover_assets) == 11, f"Expected 11 local source covers, got {len(cover_assets)}"
    required = (
        "assets/assets/data/poet_catalog.json",
        "assets/assets/data/splash_quotes.json",
        "assets/assets/images/splash_logo.png",
        "assets/assets/images/brand/sukhan-brand-source.png",
        "assets/assets/images/brand/sukhan-mark-source.svg",
        "assets/assets/images/brand/sukhan-logo-light.png",
        "assets/assets/images/brand/sukhan-logo-dark.png",
        "assets/assets/images/brand/sukhan-logo-sepia.png",
        "assets/assets/images/brand/sukhan-wordmark-light.png",
        "assets/assets/images/brand/sukhan-wordmark-dark.png",
        "assets/assets/images/brand/sukhan-wordmark-sepia.png",
        "assets/assets/images/welcome/hero_book.webp",
        "assets/assets/images/welcome/hero_library.webp",
        "assets/assets/images/welcome/hero_quill.webp",
        "assets/assets/licenses/IqbalLiteratureApp-MIT.txt",
        "assets/assets/licenses/piper-phonemize-GPL-3.0.txt",
        "assets/assets/licenses/sherpa-onnx-Apache-2.0.txt",
        "assets/assets/licenses/THIRD-PARTY-TTS-NOTICES.txt",
        "assets/assets/tts/piper-fa/fa_IR-mana-medium.onnx",
        "assets/assets/tts/piper-fa/fa_IR-mana-medium.onnx.json",
        "assets/assets/tts/piper-fa/tokens.txt",
        "assets/assets/poet_packs/moulavi.sqlite",
        "assets/assets/poet_packs/saadi.sqlite",
    )
    for item in required:
        assert item in names, f"Missing packaged asset: {item}"
    assert zf.read("assets/assets/images/splash_logo.png") == zf.read("assets/assets/images/brand/sukhan-logo-light.png"), "Splash logo is not the full PNG-derived wordmark"
    assert zf.read("assets/assets/images/brand/sukhan-brand-source.png") == (ROOT / "app/src/main/iqbal-assets/assets/images/brand/sukhan-brand-source.png").read_bytes(), "Supplied PNG source was altered"
    surfaces = {"light": (252, 252, 251), "dark": (12, 12, 11), "sepia": (250, 246, 236)}
    for theme, surface in surfaces.items():
        full = zf.read(f"assets/assets/images/brand/sukhan-logo-{theme}.png")
        compact = zf.read(f"assets/assets/images/brand/sukhan-wordmark-{theme}.png")
        assert composite_contrast_pixels(full, surface, 0.48) > 3000, f"{theme} full logo is missing its visible wordmark"
        assert composite_contrast_pixels(compact, surface) > 3000, f"{theme} compact wordmark is blank on its app surface"
    assert zf.getinfo("assets/assets/tts/piper-fa/fa_IR-mana-medium.onnx").file_size > 55_000_000, "Persian voice model is missing or truncated"
    app_abis = {name.split("/")[1] for name in names if name.startswith("lib/") and name.endswith(".so")}
    assert app_abis == {"arm64-v8a", "armeabi-v7a"}, f"Unexpected APK native ABIs: {app_abis}"
    assert any(name.endswith("/ic_sukhan.png") for name in names), "Sukhan launcher icon is missing"
    assert not any(name.endswith("/ic_iqbal.png") for name in names), "Old launcher icon is still packaged"
    catalog = json.loads(zf.read("assets/assets/data/poet_catalog.json"))
    assert catalog["bundled_slugs"] == ["moulavi", "saadi"]
    assert len(catalog["poets"]) == 245, f"Expected 245 Ganjoor poets, got {len(catalog['poets'])}"
    assert len({p["id"] for p in catalog["poets"]}) == 245
    assert catalog["catalog_summary"] == {
        "poet_count": 245,
        "poem_count": 139038,
        "verse_count": 3145024,
        "bundled_pack_count": 2,
        "optional_download_count": 243,
    }
    optional = [p for p in catalog["poets"] if not p["bundled_in_app"]]
    assert len(optional) == 243, f"Expected 243 optional packs, got {len(optional)}"
    release_asset_urls = {
        "attar": "https://github.com/usmanbhat/sukhan/releases/download/ganjoor-fa-2026-11/attar.sqlite",
    }
    for poet in optional:
        expected_url = release_asset_urls.get(poet["slug"], f"https://raw.githubusercontent.com/usmanbhat/sukhan/main/{poet['pack_file']}")
        assert poet["download_url"] == expected_url
        assert poet["pack_size_bytes"] > 0 and len(poet["sha256"]) == 64
    assert {p["slug"] for p in catalog["poets"] if p["bundled_in_app"]} == {"moulavi", "saadi"}

    expected = {
        "moulavi": (6329, 133100, 21024, 21024),
        "saadi": (2010, 34248, 0, 0),
    }
    for slug, counts in expected.items():
        db = sqlite3.connect(":memory:")
        try:
            db.deserialize(zf.read(f"assets/assets/poet_packs/{slug}.sqlite"))
            assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok", f"{slug} pack integrity failed"
            actual = (
                db.execute("SELECT COUNT(*) FROM ganjoor_poem").fetchone()[0],
                db.execute("SELECT COUNT(*) FROM ganjoor_verse").fetchone()[0],
                db.execute("SELECT COUNT(*) FROM ganjoor_verse WHERE TRIM(COALESCE(translation_en,''))<>''").fetchone()[0],
                db.execute("SELECT COUNT(*) FROM ganjoor_verse WHERE TRIM(COALESCE(translation_ur,''))<>''").fetchone()[0],
            )
            assert actual == counts, f"Unexpected {slug} pack counts: {actual}"
            if slug == "moulavi":
                review = db.execute("SELECT COUNT(*) FROM translation_provenance WHERE human_review_status='not_human_reviewed'").fetchone()[0]
                assert review == 996, f"Expected 996 clearly marked Rumi AI draft poems, got {review}"
            if slug == "saadi":
                root = db.execute("SELECT id FROM ganjoor_category WHERE parent_id IS NULL AND poet_id=7").fetchone()[0]
                books = {row[0] for row in db.execute("SELECT title FROM ganjoor_category WHERE parent_id=?", (root,))}
                assert {"بوستان", "گلستان"}.issubset(books), f"Saadi's Bostan/Golestan collections are missing: {books}"
                bostan = db.execute("SELECT id FROM ganjoor_category WHERE parent_id=? AND title='بوستان'", (root,)).fetchone()[0]
                chapters = db.execute("SELECT id FROM ganjoor_category WHERE parent_id=?", (bostan,)).fetchall()
                assert len(chapters) >= 8, f"Expected Bostan's chapter hierarchy, got {len(chapters)} chapters"
                assert db.execute("SELECT COUNT(*) FROM ganjoor_poem WHERE category_id=?", (chapters[1][0],)).fetchone()[0] > 0, "Bostan chapter poems are not linked to categories"
        finally:
            db.close()

print(f"PASS: {APK.name}; offline-first Kotlin app with verified optional HTTPS poet-pack downloads; widget, adjustable Persian TTS, and Rumi/Saadi packs verified.")
