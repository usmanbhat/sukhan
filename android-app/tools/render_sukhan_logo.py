#!/usr/bin/env python3
"""Generate Sukhan theme wordmarks from the supplied final PNG and launcher mark
from the statically extracted SVG in SokhanLogo.html. No browser or JavaScript is
used; the local Aref Ruqaa font is used for the mark SVG.
"""
from __future__ import annotations

import os
import subprocess
import tempfile
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BRAND_DIR = ROOT / "app/src/main/iqbal-assets/assets/images/brand"
SOURCE_PNG = BRAND_DIR / "sukhan-brand-source.png"
MARK_SVG = BRAND_DIR / "sukhan-mark-source.svg"
FONT_DIR = ROOT / "app/src/main/iqbal-assets/assets/fonts"
FONT_FILE = FONT_DIR / "ArefRuqaa-Bold.ttf"
ICON_OUT = ROOT / "app/src/main/res/drawable-nodpi/ic_sukhan.png"

# Source swatches in the supplied PNG / extracted mark.
INK_SOURCE = np.array([20.0, 33.0, 61.0], dtype=np.float32)
MUTED_SOURCE = np.array([85.0, 85.0, 92.0], dtype=np.float32)

THEMES = {
    "light": {
        "ink": (20, 33, 61), "surface": (252, 252, 251), "muted": (92, 92, 87),
    },
    "dark": {
        "ink": (246, 246, 244), "surface": (12, 12, 11), "muted": (143, 143, 136),
    },
    "sepia": {
        "ink": (51, 43, 33), "surface": (250, 246, 236), "muted": (110, 98, 82),
    },
}


def project_coverage(rgb: np.ndarray, foreground: np.ndarray, background: np.ndarray) -> np.ndarray:
    vector = foreground - background
    denominator = float(np.dot(vector, vector))
    coverage = np.sum((rgb - background) * vector, axis=2) / denominator
    return np.clip(coverage, 0.0, 1.0)


def external_background(rgb: np.ndarray, background: np.ndarray) -> np.ndarray:
    """Find paper pixels connected to the image edge; enclosed circle highlights stay opaque."""
    distance = np.linalg.norm(rgb - background, axis=2)
    candidate = (distance <= 105.0).astype(np.uint8)
    _, labels, _, _ = cv2.connectedComponentsWithStats(candidate, connectivity=8)
    edge_labels = np.unique(np.concatenate((labels[0, :], labels[-1, :], labels[:, 0], labels[:, -1])))
    edge_labels = edge_labels[edge_labels != 0]
    return np.isin(labels, edge_labels)


def content_crop(image: Image.Image, alpha: np.ndarray, row_min: int = 0) -> Image.Image:
    mask = alpha[row_min:, :] > 3
    ys, xs = np.where(mask)
    if not len(xs):
        raise RuntimeError("Could not locate visible Sukhan logo pixels")
    ys = ys + row_min
    left, right = int(xs.min()), int(xs.max()) + 1
    top, bottom = int(ys.min()), int(ys.max()) + 1
    pad = max(12, int(max(right - left, bottom - top) * 0.055))
    left, top = max(0, left - pad), max(0, top - pad)
    right, bottom = min(image.width, right + pad), min(image.height, bottom + pad)
    return image.crop((left, top, right, bottom))


def render_wordmarks() -> None:
    source = Image.open(SOURCE_PNG).convert("RGB")
    rgb = np.asarray(source, dtype=np.float32)
    height, width, _ = rgb.shape

    corners = np.concatenate((rgb[:32, :32].reshape(-1, 3), rgb[:32, -32:].reshape(-1, 3),
                              rgb[-32:, :32].reshape(-1, 3), rgb[-32:, -32:].reshape(-1, 3)))
    background = np.median(corners, axis=0)
    if np.max(np.abs(corners - background)) > 4:
        raise RuntimeError("Logo source corners are not a uniform background; refusing a destructive matte")

    outside = external_background(rgb, background)
    icon_band_end = int(height * 0.52)
    y_grid = np.arange(height)[:, None]

    # The upper mark is a dark disc with light details. Keep enclosed paper details
    # opaque and recolor them to the theme surface; only edge-connected paper clears.
    icon_coverage = project_coverage(rgb, INK_SOURCE, background)
    # The lower Arabic/Latin wordmark sits on paper; estimate antialias coverage
    # against its original ink colors so counters remain transparent and edges stay smooth.
    lower_is_muted = y_grid >= int(height * 0.83)
    lower_coverage = np.empty((height, width), dtype=np.float32)
    lower_coverage[:] = project_coverage(rgb, INK_SOURCE, background)
    muted_coverage = project_coverage(rgb, MUTED_SOURCE, background)
    lower_coverage[lower_is_muted[:, 0], :] = muted_coverage[lower_is_muted[:, 0], :]

    for theme, colors in THEMES.items():
        target_ink = np.asarray(colors["ink"], dtype=np.float32)
        target_surface = np.asarray(colors["surface"], dtype=np.float32)
        target_muted = np.asarray(colors["muted"], dtype=np.float32)
        out = np.zeros((height, width, 4), dtype=np.uint8)

        icon_inside = (~outside[:icon_band_end, :])
        icon_outside = outside[:icon_band_end, :]
        cov = icon_coverage[:icon_band_end, :, None]
        icon_mix = cov * target_ink + (1.0 - cov) * target_surface
        out[:icon_band_end, :, :3][icon_inside] = np.clip(icon_mix[icon_inside], 0, 255).astype(np.uint8)
        out[:icon_band_end, :, 3][icon_inside] = 255
        out[:icon_band_end, :, :3][icon_outside] = np.tile(target_ink.astype(np.uint8), (int(icon_outside.sum()), 1))
        out[:icon_band_end, :, 3][icon_outside] = np.round(icon_coverage[:icon_band_end, :][icon_outside] * 255).astype(np.uint8)

        lower_ink_rows = ~lower_is_muted[:, 0]
        lower_muted_rows = lower_is_muted[:, 0]
        cov = lower_coverage[:, :, None]
        lower_colors = np.broadcast_to(target_ink, (height, width, 3)).copy()
        lower_colors[lower_muted_rows, :, :] = target_muted
        out[icon_band_end:, :, :3] = np.clip(lower_colors[icon_band_end:, :, :], 0, 255).astype(np.uint8)
        out[icon_band_end:, :, 3] = np.round(lower_coverage[icon_band_end:, :] * 255).astype(np.uint8)

        full = Image.fromarray(out, "RGBA")
        full_crop = content_crop(full, out[:, :, 3])
        full_crop.save(BRAND_DIR / f"sukhan-logo-{theme}.png", optimize=True)
        wordmark_crop = content_crop(full, out[:, :, 3], row_min=icon_band_end)
        wordmark_crop.save(BRAND_DIR / f"sukhan-wordmark-{theme}.png", optimize=True)
        if theme == "light":
            full_crop.save(BRAND_DIR.parent / "splash_logo.png", optimize=True)


def render_launcher_mark() -> None:
    if not MARK_SVG.is_file() or not FONT_FILE.is_file():
        raise SystemExit("The extracted mark SVG and local Aref Ruqaa Bold font are required")
    import xml.etree.ElementTree as ET

    root = ET.parse(MARK_SVG).getroot()
    root.set("width", "1024")
    root.set("height", "1024")
    root.set("viewBox", "0 0 128 128")
    with tempfile.TemporaryDirectory(prefix="sukhan-fontconfig-") as tmp:
        tmp_path = Path(tmp)
        config = tmp_path / "fonts.conf"
        cache = tmp_path / "cache"
        config.write_text(
            "<?xml version='1.0'?><fontconfig>"
            f"<dir>{FONT_DIR}</dir><cachedir>{cache}</cachedir>"
            "</fontconfig>"
        )
        temporary_svg = tmp_path / "sukhan-mark.svg"
        temporary_svg.write_bytes(ET.tostring(root, encoding="utf-8", xml_declaration=True))
        env = os.environ.copy()
        env["FONTCONFIG_FILE"] = str(config)
        subprocess.run(
            ["rsvg-convert", "--format", "png", "--width", "1024", "--height", "1024",
             "--output", str(ICON_OUT), str(temporary_svg)],
            check=True, env=env,
        )
    icon = Image.open(ICON_OUT).convert("RGBA")
    alpha = np.asarray(icon)[:, :, 3]
    ys, xs = np.where(alpha > 4)
    if not len(xs) or (xs.max() - xs.min() < 800) or (ys.max() - ys.min() < 800):
        raise RuntimeError("Rendered launcher mark is unexpectedly small or empty")


def main() -> None:
    if not SOURCE_PNG.is_file():
        raise SystemExit(f"Place the unchanged supplied PNG at {SOURCE_PNG}")
    if not MARK_SVG.is_file():
        raise SystemExit(f"Place the sanitized static mark SVG at {MARK_SVG}")
    BRAND_DIR.mkdir(parents=True, exist_ok=True)
    render_wordmarks()
    render_launcher_mark()
    print(f"Rendered complete theme-aware wordmarks from {SOURCE_PNG}")
    print(f"Rendered launcher mark from static SVG {MARK_SVG}")


if __name__ == "__main__":
    main()
