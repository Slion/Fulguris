#!/usr/bin/env python3
"""Dump the textBookmark row texts in the bookmarks drawer (diagnose the
'folder row not tappable' failure — the row's node text may differ from the
folder name, e.g. a path prefix or truncation).

Usage:
    python scripts/tests/probe_drawer_rows.py --device SERIAL
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

from appdevice import resolve_devices  # noqa: E402
import adb as tools_adb  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True)
    a = ap.parse_args()
    device = resolve_devices(a.device, False)[0]
    print(f"=== drawer rows probe on {device.id} ===")

    device.restart()

    # Create the folder the same way the test does: navigate, overflow menu,
    # Tab menu -> Add bookmark, set the folder field.
    device.navigate("https://www.example.org/")

    def tap_id(suffix, timeout=20.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            n = tools_adb.find_node(device.serial, ":id/" + suffix)
            if n and n.center:
                device.tap(*n.center, wait=1.2)
                return True
            time.sleep(0.5)
        return False

    def clear_and_type(suffix, value):
        n = None
        deadline = time.time() + 10
        while time.time() < deadline:
            n = tools_adb.find_node(device.serial, ":id/" + suffix)
            if n and n.bounds:
                break
            time.sleep(0.5)
        if not n:
            return False
        device.tap(*n.center, wait=1.2)
        presses = min(len(n.text or "") + 6, 60)
        device.transport.shell(
            ["shell", "input keyevent 123; for i in $(seq 1 "
                     + str(presses) + "); do input keyevent 67; done"], timeout=30)
        time.sleep(0.4)
        device.type_text(value, 0.25)
        time.sleep(0.3)
        return True

    if tap_id("button_more") and tap_id("menuItemTabMenu", 10) and tap_id("menuItemAddBookmark", 15):
        time.sleep(1.0)
        clear_and_type("bookmark_title", "AutoTest folder bookmark")
        clear_and_type("bookmark_url", "https://www.example.org/autotest-in-folder")
        clear_and_type("bookmark_folder", "AutoTestFolder")
        # OK button = :id/button1
        deadline = time.time() + 10
        while time.time() < deadline:
            for nd in device.nodes():
                if (nd.resource_id or "").endswith("button1") and nd.bounds and nd.enabled:
                    device.tap(*nd.center, wait=1.5)
                    break
            else:
                time.sleep(0.5)
                continue
            break
        time.sleep(1.0)
        device.key(4, wait=1.0)  # drop the keyboard if up
    else:
        print("could not open the add-bookmark dialog — abort")
        return 1

    # Now open the drawer and dump its rows.
    if not tap_id("button_more") or not tap_id("menuItemBookmarks", 10):
        print("could not open the bookmarks drawer — abort")
        return 1
    time.sleep(1.5)

    print("--- textBookmark rows ---")
    for n in device.nodes():
        if (n.resource_id or "").endswith("textBookmark") and n.bounds:
            print(repr(n.text), "center=", n.center)
    # Also dump any row that mentions AutoTest in any resource id.
    print("--- all nodes mentioning AutoTest ---")
    for n in device.nodes():
        if "AutoTest" in (n.text or ""):
            print(repr(n.resource_id), repr(n.text), "bounds=", n.bounds)

    device.key(4, wait=1.0)
    device.key(4, wait=1.0)
    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
