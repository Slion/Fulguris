#!/usr/bin/env python3
"""Does the toolbar overflow menu offer 'Add bookmark' on this device?

The bookmark tests used the Ctrl+B hotkey, but `input keycombination` does
not exist on API 29 (Huawei P30 Pro, Android 10) — the chord is silently
lost. The same action is reachable from the toolbar overflow menu
(menuItemAddBookmark, id in uiautomator), which works on every API level.

Usage:
    python scripts/tests/probe_menu_addbookmark.py --device SERIAL
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


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True, help="adb serial")
    args = ap.parse_args()

    device = resolve_devices(args.device, False)[0]
    print(f"=== menu add-bookmark probe on {device.id} ===")

    device.restart()
    device.navigate("https://www.example.org/")
    time.sleep(1.5)

    # Open the toolbar overflow menu.
    menu_btn = None
    deadline = time.time() + 20.0
    while time.time() < deadline:
        menu_btn = tools_adb.find_node(device.serial, ":id/button_more")
        if menu_btn and menu_btn.bounds:
            break
        time.sleep(1.0)
    assert menu_btn and menu_btn.bounds, "toolbar menu button not found"
    device.tap(*menu_btn.center, wait=1.5)

    nodes = device.nodes()
    bm = [n for n in nodes if (n.resource_id or "").endswith("menuItemAddBookmark")]
    print("menuItemAddBookmark node:",
          f"found, text={bm[0].text!r}, bounds={bm[0].bounds}" if bm else "NOT FOUND")
    for n in bm:
        if n.center:
            device.tap(*n.center, wait=2.0)
            texts = [x.text for x in device.nodes() if x.text]
            print("after tap, 'Add bookmark' dialog present:",
                  any("bookmark_title" in (x.resource_id or "") for x in device.nodes()))
            print("sample texts:", sorted(texts)[:20])
            device.key(keys.BACK, wait=1.0)
            break
    device.key(keys.BACK, wait=1.0)
    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
