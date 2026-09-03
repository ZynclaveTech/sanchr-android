#!/usr/bin/env python3
"""Regenerate launcher mipmaps and the transparent in-app mark from the source logo.

Usage: scripts/generate-brand-assets.py ~/Downloads/logo.png
The launcher is the navy composition the iOS App Store icon uses; the in-app
mark is the transparent bird-and-S with background-removal specks dropped.
"""
import pathlib, sys
from collections import deque
import numpy as np
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
NAVY = (11, 14, 31)
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

def clean(src: Image.Image) -> Image.Image:
    a = np.array(src.split()[3]); m = a > 32
    h, w = m.shape; lab = np.zeros_like(a, dtype=np.int32); n = 0; sizes = []
    for y in range(h):
        for x in range(w):
            if m[y, x] and lab[y, x] == 0:
                n += 1; q = deque([(y, x)]); lab[y, x] = n; size = 0
                while q:
                    cy, cx = q.popleft(); size += 1
                    for ny, nx in ((cy-1,cx),(cy+1,cx),(cy,cx-1),(cy,cx+1)):
                        if 0 <= ny < h and 0 <= nx < w and m[ny, nx] and lab[ny, nx] == 0:
                            lab[ny, nx] = n; q.append((ny, nx))
                sizes.append(size)
    keep = np.zeros(n + 1, bool); keep[1:] = np.array(sizes) >= 400
    arr = np.array(src); arr[..., 3] = np.where(keep[lab], arr[..., 3], 0)
    out = Image.fromarray(arr, "RGBA"); return out.crop(out.getbbox())

def fit(mark: Image.Image, box: int) -> Image.Image:
    canvas = Image.new("RGBA", (box, box), (0, 0, 0, 0)); m = mark.copy(); m.thumbnail((box, box), Image.LANCZOS)
    canvas.paste(m, ((box - m.width) // 2, (box - m.height) // 2), m); return canvas

def main(source: str) -> None:
    mark = clean(Image.open(source).convert("RGBA"))
    for d, s in DENSITIES.items():
        # In-app mark: 128dp canvas.
        out = ROOT / "core/designsystem/src/main/res" / f"drawable-{d}"; out.mkdir(exist_ok=True)
        fit(mark, int(128 * s)).save(out / "sanchr_logo.png", optimize=True)
        # Adaptive launcher: 108dp foreground, mark inside the 72dp safe zone at 80%.
        mip = ROOT / "app/src/main/res" / f"mipmap-{d}"; mip.mkdir(exist_ok=True)
        fg_px = int(108 * s); fg = Image.new("RGBA", (fg_px, fg_px), (0, 0, 0, 0))
        inner = fit(mark, int(72 * 0.8 * s)); fg.paste(inner, ((fg_px - inner.width) // 2, (fg_px - inner.height) // 2), inner)
        fg.save(mip / "ic_launcher_foreground.png", optimize=True)
        # Legacy launcher (pre-26 and some launchers): 48dp navy rounded square / circle with the mark at 66%.
        px = int(48 * s); legacy = Image.new("RGBA", (px, px), (0, 0, 0, 0)); draw = ImageDraw.Draw(legacy)
        draw.rounded_rectangle((0, 0, px - 1, px - 1), radius=px // 6, fill=NAVY + (255,))
        m = fit(mark, int(px * 0.66)); legacy.paste(m, ((px - m.width) // 2, (px - m.height) // 2), m)
        legacy.save(mip / "ic_launcher.png", optimize=True)
        round_icon = Image.new("RGBA", (px, px), (0, 0, 0, 0)); ImageDraw.Draw(round_icon).ellipse((0, 0, px - 1, px - 1), fill=NAVY + (255,))
        round_icon.paste(m, ((px - m.width) // 2, (px - m.height) // 2), m); round_icon.save(mip / "ic_launcher_round.png", optimize=True)
    (ROOT / "app/src/main/res/values/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#0B0E1F</color>\n</resources>\n')
    print("brand assets written")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else str(pathlib.Path.home() / "Downloads/logo.png"))
