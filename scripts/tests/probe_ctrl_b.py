#!/usr/bin/env python3
"""Does Ctrl+B (the 'Add bookmark' hotkey) work on this device?

The bookmarks tests use ``input keycombination 112 66`` (CTRL_LEFT + B) —
the only reliable way to deliver a modified key over adb. On the P30 Pro
(EMUI 10) the 'Add bookmark' dialog never appeared, so this probe tries the
chord and then the toolbar menu as an alternative trigger, dumping nodes
after each.

Usage:
    python scripts/tests/probe_ctrl_b.py --device SERIAL
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
import adb as tools_adb  # noqa: E402  (the scripts/tools adb shim)


def _texts(device) -> list:
    return sorted({n.text for n in device.nodes() if n.text})


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True, help="adb serial")
    args = ap.parse_args()

    device = resolve_devices(args.device, False)[0]
    print(f"=== Ctrl+B probe on {device.id} ===")

    device.restart()
    device.navigate("example.com")
    time.sleep(1.0)

    # Attempt 1: the key combination the tests use.
    print("\n--- attempt 1: key_combination(CTRL_LEFT, B) ---")
    device.key_combination(tools_adb.KEY_CTRL_LEFT, keys.KEY_B, wait=2.0)
    texts = _texts(device)
    print("has 'Add bookmark' dialog:", any("Add bookmark" in t for t in texts))
    print("sample nodes:", texts[:25])
    # Back out of the dialog if it opened.
    device.key(keys.BACK, wait=1.0)

    # Attempt 2: hold CTRL a bit longer by using key_hold? (not possible for a
    # chord) — instead try the menu route: open the toolbar menu and look for
    # the bookmark action.
    print("\n--- attempt 2: toolbar menu (KEYCODE_MENU=82) ---")
    device.key(82, wait=1.5)  # KEYCODE_MENU
    texts = _texts(device)
    print("sample nodes:", texts[:30])
    bookmarkish = [t for t in texts if "ookmark" in t or "Add to" in t]
    print("bookmark-ish items:", bookmarkish)
    device.key(keys.BACK, wait=1.0)

    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
