#!/usr/bin/env python3
"""Verify the full menu route to 'Add bookmark' on this device (API-level
independent): overflow menu -> 'Tab menu' -> 'Add bookmark'.

The bookmark tests used the Ctrl+B hotkey, which cannot be delivered on API
29 (Huawei P30 Pro) because `input keycombination` does not exist there. The
menu route works on every API level and is the path real phone users take.

Usage:
    python scripts/tests/probe_menu_route.py --device SERIAL
"""
from __future__ import annotations

import argparse
import os
import sys
import time

_scripts = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for _d in (_scripts, os.path.join(_scripts, "tools"), os.path.join(_scripts, "tests")):
    if _d not in sys.path:
        sys.path.insert(0, _d)

from framework import keys  # noqa: E402
from appdevice import resolve_devices  # noqa: E402
import adb as tools_adb  # noqa: E402


def _tap_id(device, suffix: str, timeout: float = 15.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        n = tools_adb.find_node(device.serial, ":id/" + suffix)
        if n and n.center:
            device.tap(*n.center, wait=1.5)
            return True
        time.sleep(0.5)
    return False


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True, help="adb serial")
    a = ap.parse_args()
    device = resolve_devices(a.device, False)[0]
    print(f"=== menu route probe on {device.id} ===")

    device.restart()
    device.navigate("https://www.example.org/")
    time.sleep(1.5)

    assert _tap_id(device, "button_more"), "no overflow menu button"
    assert _tap_id(device, "menuItemTabMenu"), "no 'Tab menu' switcher item"
    assert _tap_id(device, "menuItemAddBookmark"), "no 'Add bookmark' item in the tab menu"

    # Wait for the add-bookmark dialog (title 'Add bookmark' / the title field).
    deadline = time.time() + 15.0
    ok = False
    while time.time() < deadline:
        texts = {n.text for n in device.nodes() if n.text}
        if any(t.strip() == "Add bookmark" for t in texts):
            ok = True
            break
        time.sleep(0.5)
    print("add-bookmark dialog present:", ok)
    print("title field present:",
          bool(tools_adb.find_node(device.serial, ":id/bookmark_title")))

    device.key(keys.BACK, wait=1.0)
    device.key(keys.BACK, wait=1.0)
    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
