"""Triangulate cursor position from three independent sources at the same moments.

The user reported "I don't see any cursor moving" - so a green verdict is not
enough. This probe makes the cursor move on the local cursor_target.html oracle
page and, for each position, records:

  1. LOG     - the app's own ``Cursor: click at target (x, y)`` (logcat), the
               ground truth the activity acts on.
  2. ORACLE  - the page mirrors the click point into document.title, which
               Fulguris shows in the toolbar label (field_text): "<x>,<y>".
  3. VISION  - the cursor arrow is a pure-white, tall-narrow blob on the
               screenshot; its top-left corner is the arrow tip (the logical
               point). Calibrated: sanity shot detected tip (961,602) vs the
               true screen center (960,600).

All three are printed side by side, and screenshots are saved so the cursor's
travel can be eyeballed. A real burst that drops key events would show up as
the three sources disagreeing on the *magnitude* of travel while still
agreeing on direction - and the screenshots would show the arrow actually
moved.

Run:  python scripts/tests/probe_cursor_triangulate.py [--serial SERIAL]
"""
from __future__ import annotations

import argparse
import os
import re
import sys
import time

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from framework import AndroidDevice, keys  # noqa: E402  (also puts scripts/tools on sys.path)
import adb  # noqa: E402
import cursor_tests  # noqa: E402
import toolbar_field_test as tf  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "out")


# --- Vision: the cursor arrow is a white, tall-narrow blob --------------------
# Calibrated on sanity_on.png: the arrow fill is a 39x58 blob (h/w ~1.5). Every
# page-content blob on the YouTube page is square or wide (h/w <= 1.25), so this
# aspect + area window isolates the arrow. The logical point is the arrow TIP,
# at the top-LEFT of the blob.
def _white_blobs(img: Image.Image, thresh: int = 240, min_area: int = 150):
    a = np.asarray(img.convert("RGB"), dtype=np.int16)
    white = (a[:, :, 0] > thresh) & (a[:, :, 1] > thresh) & (a[:, :, 2] > thresh)
    ys, xs = np.nonzero(white)
    pts = set(zip(ys.tolist(), xs.tolist()))
    seen: set = set()
    out = []
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
            x0, x1 = int(ca[:, 1].min()), int(ca[:, 1].max())
            y0, y1 = int(ca[:, 0].min()), int(ca[:, 0].max())
            out.append(dict(area=len(comp), x0=x0, x1=x1, y0=y0, y1=y1))
    return out


def _cursor_tip(path: str):
    """Return the arrow-tip (x, y) from a screenshot, or None. Prints every
    candidate blob so a false positive is visible, not silent.

    Tuned to the TV arrow (39x58, area ~971, calibrated on sanity_on.png):
    the page's own white content is small/square, so we floor on width,
    height and area and keep a loose tall-narrow aspect band."""
    img = Image.open(path)
    w, h = img.size
    cands = []
    for b in _white_blobs(img):
        bw = b["x1"] - b["x0"] + 1
        bh = b["y1"] - b["y0"] + 1
        ar = bh / bw if bw else 0
        if bw >= 25 and bh >= 40 and 1.20 <= ar <= 2.10 and 500 <= b["area"] <= 4000:
            cands.append((b["area"], b))
    cands.sort(key=lambda t: -t[0])
    print(f"    blobs: " + ", ".join(
        f"(x{b['x0']}-{b['x1']},y{b['y0']}-{b['y1']},a{b['area']})" for _, b in cands))
    if not cands:
        return None
    b = cands[0][1]
    tip = (b["x0"], b["y0"])
    print(f"    vision tip = {tip}  (arrow {b['x1'] - b['x0'] + 1}x{b['y1'] - b['y0'] + 1}, area {b['area']})")
    return tip


# --- Position reads -----------------------------------------------------------
def _read_oracle(device) -> str | None:
    """Select-press: the page mirrors the click into its title -> toolbar label."""
    if not tf._reshow_toolbar(device):
        return None
    time.sleep(0.3)
    device.key(keys.DPAD_CENTER, wait=0.7)
    t = device.field_text().strip()
    m = re.fullmatch(r"(\d+),(\d+)", t)
    return f"({m.group(1)},{m.group(2)})" if m else (t or None)


def _shot(device, tag: str) -> str:
    path = os.path.join(OUT, f"tri_{tag}.png")
    device.screenshot(path)
    return path


def _click_and_read(device, tag: str) -> tuple[str | None, tuple | None, str | None]:
    """One select-press + screenshot. Returns (log, vision_tip, oracle)."""
    path = _shot(device, tag)
    device.key(keys.DPAD_CENTER, wait=0.8)
    oracle = _read_oracle(device)
    tip = _cursor_tip(path)
    return oracle, tip, path


def _burst(device, n: int, code, wait: float = 0.15) -> None:
    for _ in range(n):
        device.key(code, wait=wait)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--serial", default="192.168.178.67:5555")
    args = ap.parse_args()
    device = AndroidDevice(args.serial)

    cursor_tests._ensure_server()
    cursor_tests._ensure_reverse(device)
    cursor_tests._reset_cursor_prefs(device)  # speed 40, accel 20, fade 3000
    tf._set_pref(device, tf.TIMEOUT_KEY, "0", "float", suffixed=True)  # no auto-hide during the probe
    tf._set_pref(device, tf.FADE_KEY, "0", "int", suffixed=False)     # never fade, so vision is stable
    device.launch()
    time.sleep(3.0)
    cursor_tests._load_target(device)  # cursor_target.html, cursor OFF
    time.sleep(1.0)
    if not tf._ensure_cursor(device, args.serial, "tri", want=True):
        print("!! could not turn cursor on - aborting")
        return 1
    time.sleep(1.0)

    adb.logcat(args.serial, "Cursor:", clear=True)  # reset the log so we capture this run only
    print(f"=== cursor triangulation on {device.label()} ===")

    # --- Position 0: where the app centers the cursor on enable -------------
    o0, tip0, p0 = _click_and_read(device, "start")
    print(f"  P0  oracle={o0}  vision_tip={tip0}")

    # --- A real burst: 10 right + 6 up --------------------------------------
    _burst(device, 10, keys.DPAD_RIGHT)
    _burst(device, 6, keys.DPAD_UP)
    time.sleep(0.5)
    o1, tip1, p1 = _click_and_read(device, "after_burst")
    print(f"  P1  oracle={o1}  vision_tip={tip1}")

    # --- The app's own words (log) for the two clicks ------------------------
    time.sleep(0.5)
    log = adb.logcat(args.serial, "Cursor: click at target")
    print("  LOG (app logcat 'Cursor: click at target'):")
    if log.strip():
        for line in log.splitlines():
            print(f"    {line}")
    else:
        print("    (none - Timber.d is stripped in this build, or the tag differs)")

    # --- Verdicts ------------------------------------------------------------
    print("\n=== triangulation ===")
    print(f"  screenshots: {os.path.basename(p0)}  ->  {os.path.basename(p1)}")
    if tip0 and tip1:
        print(f"  vision travel:  d=({tip1[0] - tip0[0]:+d}, {tip1[1] - tip0[1]:+d})")
    if o0 and o1:
        try:
            (ax, ay), (bx, by) = (map(int, s.strip("()").split(",")) for s in (o0, o1))
            print(f"  oracle travel:  d=({bx - ax:+d}, {by - ay:+d})")
        except ValueError:
            print(f"  oracle travel:  could not parse {o0} -> {o1}")
    print("  PASS criterion: travel is non-zero in BOTH vision and oracle, and they agree in direction")
    # leave the cursor off and prefs as the suite default
    tf._ensure_cursor(device, args.serial, "tri_off", want=False)
    tf._set_pref(device, tf.TIMEOUT_KEY, tf.DEFAULT_TIMEOUT, "float", suffixed=True)
    tf._set_pref(device, tf.FADE_KEY, tf.DEFAULT_FADE, "int", suffixed=False)
    print(f"prefs reset (timeout={tf.DEFAULT_TIMEOUT}, fade={tf.DEFAULT_FADE})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
