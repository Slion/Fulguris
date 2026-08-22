"""Calibrate screenshot-based cursor detection.

The cursor arrow is a pure-white fill with a near-black outline (see
CursorView). On any page, a small region of pure-white pixels ringed by
near-black pixels is a strong cursor signature. This script checks that
heuristic against existing screenshots whose cursor position is known:

  sanity_on.png          cursor centered at screen center (1920x1200 -> 960,600)

Run:  python scripts/tests/probe_cursor_detect.py [shot.png ...]
"""
import sys
import numpy as np
from PIL import Image

OUT = r"scripts\tests\out"

def detect_cursor(img: Image.Image):
    """Return (cx, cy) of the best cursor candidate or None.

    Signature: pixel is near-white (>240 all channels) AND within 6px of a
    near-black pixel (<50 all channels). The white core of the arrow is
    small; the outline is dark. Page content rarely has pure white with
    pure black that close (text is anti-aliased, borders are not both).
    """
    a = np.asarray(img.convert("RGB"), dtype=np.int16)
    white = (a[:, :, 0] > 240) & (a[:, :, 1] > 240) & (a[:, :, 2] > 240)
    black = (a[:, :, 0] < 50) & (a[:, :, 1] < 50) & (a[:, :, 2] < 50)
    # dilate the black mask by 6px with a simple max-filter chain
    black_d = black.copy()
    for _ in range(6):
        black_d = (black_d
                   | np.roll(black_d, 1, 0) | np.roll(black_d, -1, 0)
                   | np.roll(black_d, 1, 1) | np.roll(black_d, -1, 1))
    cand = white & black_d
    if cand.sum() < 10:
        return None
    # find the largest connected white blob among candidates
    ys, xs = np.nonzero(cand)
    # simple clustering: take all candidate pixels within a window of the
    # densest seed (grid histogram)
    from collections import Counter
    grid = Counter((y // 8, x // 8) for x, y in zip(xs, ys))
    if not grid:
        return None
    (gy, gx), _count = grid.most_common(1)[0]
    sel = (np.abs(ys // 8 - gy) <= 1) & (np.abs(xs // 8 - gx) <= 1)
    cy = float(ys[sel].mean())
    cx = float(xs[sel].mean())
    return cx, cy

def main():
    import os
    if len(sys.argv) > 1:
        paths = sys.argv[1:]
    else:
        paths = [os.path.join(OUT, "192.168.178.67_5555_sanity_on.png")]
    for p in paths:
        if not os.path.exists(p):
            print(f"  MISSING {p}")
            continue
        img = Image.open(p)
        w, h = img.size
        pos = detect_cursor(img)
        print(f"  {os.path.basename(p)}: {w}x{h}  cursor at {pos}")

if __name__ == "__main__":
    main()
