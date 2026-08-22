"""Controlled experiment: does the cursor move on the TV, and under what conditions?

Drives the phase-3-style burst against the deterministic cursor_target.html oracle
(which mirrors the cursor's click coordinates into the page title -> toolbar label,
readable over adb), with screenshots at each step. Varies two things the field test
and the passing cursor suite differ on:

  * cursor fade  - 3000ms (suite default) vs 0 (field test). A faded/GONE overlay
    makes CursorController.moveBy a no-op, so fade could zero out all movement.
  * key cadence  - 0.15s (proven suite) vs 0.06s (field-test burst).

    python scripts/tests/probe_cursor_movement.py
"""
from __future__ import annotations

import os
import re
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from framework import AndroidDevice, keys  # noqa: E402

import cursor_tests  # noqa: E402  (local asset server + reverse tunnel)
import adb  # noqa: E402  (framework puts scripts/tools on sys.path)

SERIAL = "192.168.178.67:5555"
OUT = os.path.join(os.path.dirname(__file__), "out")
FADE_KEY = "pref_key_cursor_fade_timeout"


def set_fade(device, value: str) -> None:
    """Host-rewrite the (unsuffixed) cursor fade pref; app must be stopped."""
    device.force_stop()
    path = f"shared_prefs/{device.package}_preferences.xml"
    xml = device.read_prefs(path)
    entry = f'<int name="{FADE_KEY}" value="{value}" />'
    pat = re.compile(rf'<int name="{re.escape(FADE_KEY)}" value="-?\d+" />')
    xml = pat.sub(entry, xml) if pat.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
    device.write_prefs(path, xml)
    device.launch()


def click_coords(device) -> tuple[int, int] | None:
    device.key(keys.DPAD_CENTER, wait=0.8)
    m = re.fullmatch(r"(\d+),(\d+)", device.field_text().strip())
    return (int(m.group(1)), int(m.group(2))) if m else None


def fresh_cursor(device, shot: str) -> tuple[int, int] | None:
    """Ensure cursor mode is freshly ON (centered) and return the start position."""
    if device.find_node(":id/cursorOverlay"):
        device.key_longpress(keys.MEDIA_PLAY_PAUSE, wait=1.2)  # off
        time.sleep(0.5)
    device.key_longpress(keys.MEDIA_PLAY_PAUSE, wait=1.2)  # on -> centers
    time.sleep(0.6)
    assert device.find_node(":id/cursorOverlay"), "cursor did not turn on"
    p = click_coords(device)
    device.screenshot(os.path.join(OUT, f"{shot}.png"))
    return p


def burst(device, n: int, code, wait: float) -> None:
    for _ in range(n):
        device.key(code, wait=wait)


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    device = AndroidDevice(SERIAL)
    print(f"device: {device.label()}  screen: {adb.screen_size(SERIAL)}  center={(960,600)}")

    cursor_tests._ensure_server()
    cursor_tests._ensure_reverse(device)
    url = f"http://localhost:{cursor_tests.PORT}/cursor_target.html?cb={int(time.time() * 1000)}"

    # ---- Case A: fade 0 (field-test value) ----
    set_fade(device, "0")
    device.navigate(url, reset=False)
    time.sleep(3.0)
    a0 = fresh_cursor(device, "probeA_fade0_start")
    print(f"\n[A] fade=0     start={a0}")
    burst(device, 45, keys.DPAD_RIGHT, 0.06)
    burst(device, 15, keys.DPAD_UP, 0.06)
    time.sleep(0.5)
    a1 = click_coords(device)
    device.screenshot(os.path.join(OUT, "probeA_fade0_end.png"))
    print(f"[A] fade=0     end  ={a1}  delta={('dx%+d dy%+d' % (a1[0]-a0[0], a1[1]-a0[1])) if a1 else 'n/a'}")

    # ---- Case B: fade 3000 (suite default) ----
    set_fade(device, "3000")
    device.navigate(url, reset=False)
    time.sleep(3.0)
    b0 = fresh_cursor(device, "probeB_fade3000_start")
    print(f"\n[B] fade=3000  start={b0}")
    burst(device, 45, keys.DPAD_RIGHT, 0.06)
    burst(device, 15, keys.DPAD_UP, 0.06)
    time.sleep(0.5)
    b1 = click_coords(device)
    device.screenshot(os.path.join(OUT, "probeB_fade3000_end.png"))
    print(f"[B] fade=3000  end  ={b1}  delta={('dx%+d dy%+d' % (b1[0]-b0[0], b1[1]-b0[1])) if b1 else 'n/a'}")

    # ---- Case C: fade 3000 but SLOW 0.15s cadence (the proven suite rhythm) ----
    device.navigate(url, reset=False)
    time.sleep(3.0)
    c0 = fresh_cursor(device, "probeC_slow_start")
    print(f"\n[C] fade=3000  start={c0}")
    burst(device, 45, keys.DPAD_RIGHT, 0.15)
    time.sleep(0.5)
    c1 = click_coords(device)
    device.screenshot(os.path.join(OUT, "probeC_slow_end.png"))
    print(f"[C] fade=3000  end  ={c1}  delta={('dx%+d dy%+d' % (c1[0]-c0[0], c1[1]-c0[1])) if c1 else 'n/a'}")

    print("\nsummary: fade=0 vs fade=3000 isolates the GONE-overlay no-op;")
    print("          0.06s vs 0.15s isolates whether rapid network key events are dropped.")
    set_fade(device, "3000")  # leave the device at the suite default


if __name__ == "__main__":
    main()
