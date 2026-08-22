"""Measure pure-white blob components on calibration screenshots.

The cursor arrow is a white fill + black outline. Page content (text,
badges) is also white - this script lists every significant white blob
(centroid + bbox + area) so we can see what distinguishes the arrow from
page content on the shots where we know where the cursor really is.

Run:  python scripts/tests/probe_cursor_blobs.py [shot.png ...]
      (defaults to the calibration shots in scripts/tests/out/)
"""
import os
import sys

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
DEFAULTS = [
    ("sanity_on  (cursor known @ ~965,640)", os.path.join(OUT, "192.168.178.67_5555_sanity_on.png")),
    ("youtube_loaded (detector FP @ ~92,932)", os.path.join(OUT, "192.168.178.67_5555_s02_youtube_video_cursor_loaded.png")),
    ("steer_gave_up", os.path.join(OUT, "steer_gave_up.png")),
]


def white_blobs(img: Image.Image, thresh: int = 240, min_area: int = 60, max_list: int = 12):
    """Flood-fill the pure-white pixels and return the biggest blobs as
    (area, cy, cx, bbox) tuples, sorted by area desc."""
    a = np.asarray(img.convert("RGB"), dtype=np.int16)
    white = (a[:, :, 0] > thresh) & (a[:, :, 1] > thresh) & (a[:, :, 2] > thresh)
    ys, xs = np.nonzero(white)
    pts = set(zip(ys.tolist(), xs.tolist()))
    seen = set()
    blobs = []
    for p in pts:
        if p in seen:
            continue
        stack = [p]
        comp = []
        seen.add(p)
        while stack:
            cy, cx = stack.pop()
            comp.append((cy, cx))
            for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                if (ny, nx) in pts and (ny, nx) not in seen:
                    seen.add((ny, nx))
                    stack.append((ny, nx))
        if len(comp) >= min_area:
            ca = np.array(comp)
            blobs.append((len(comp),
                          int(ca[:, 0].mean()), int(ca[:, 1].mean()),
                          (int(ca[:, 1].min()), int(ca[:, 0].min()),
                           int(ca[:, 1].max()), int(ca[:, 0].max()))))
    blobs.sort(reverse=True)
    return blobs[:max_list]


def main() -> None:
    paths = sys.argv[1:] or [p for _, p in DEFAULTS]
    if not sys.argv[1:]:
        paths = [(n, p) for n, p in DEFAULTS]
    for item in paths:
        if isinstance(item, tuple):
            label, path = item
        else:
            label, path = os.path.basename(item), item
        if not os.path.exists(path):
            print(f"== {label}: MISSING {path}")
            continue
        img = Image.open(path)
        print(f"== {label}  ({img.size[0]}x{img.size[1]}) ==")
        for area, cy, cx, (x0, y0, x1, y1) in white_blobs(img):
            print(f"  area={area:6d} centroid=({cx},{cy})  bbox x[{x0}-{x1}] y[{y0}-{y1}]  ({x1 - x0 + 1}x{y1 - y0 + 1})")


if __name__ == "__main__":
    main()
