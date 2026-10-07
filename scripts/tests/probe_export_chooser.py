#!/usr/bin/env python3
"""Drive the bookmarks EXPORT flow on this device and inspect the EMUI-10
'Open with' chooser that the SAF save dialog triggers (two competing file
providers). Determines which app to pick so the real SAF save-name dialog
appears.

Usage:
    python scripts/tests/probe_export_chooser.py --device SERIAL
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


def _nodes(device):
    return device.nodes()


def _texts(device):
    return {n.text for n in _nodes(device) if n.text}


def _tap_text(device, text, exact=True, timeout=8.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in _nodes(device):
            t = (n.text or "").strip()
            if (t == text if exact else text in t) and n.center:
                device.tap(*n.center, wait=1.5)
                return True
        time.sleep(0.5)
    return False


def _chooser_present(device) -> bool:
    t = _texts(device)
    return "JUST ONCE" in t and ("Open with" in t or "ALWAYS" in t)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True)
    ap.add_argument("--pick", default="Files",
                    help="the app name in the chooser to tap (default 'Files')")
    a = ap.parse_args()
    device = resolve_devices(a.device, False)[0]
    print(f"=== export chooser probe on {device.id}, pick={a.pick!r} ===")

    device.restart()
    # The previous (failed) run may have left the EMUI 'Open with' chooser on
    # screen, which poisons settle(). Dismiss any stale chooser first, then
    # wait for the toolbar's overflow button.
    for _ in range(3):
        if _chooser_present(device):
            device.key(keys.BACK, wait=1.0)
    n = None
    deadline = time.time() + 25.0
    while time.time() < deadline:
        n = tools_adb.find_node(device.serial, ":id/button_more")
        if n and n.center:
            break
        if _chooser_present(device):
            device.key(keys.BACK, wait=1.0)
        time.sleep(0.6)
    assert n and n.center, (
        f"no overflow button (stuck? nodes: {sorted(_texts(device))[:30]!r})"
    )
    device.tap(*n.center, wait=1.5)
    if not _tap_text(device, "Settings"):
        print("no Settings row — abort")
        return 1
    time.sleep(1.5)
    # find the Backup row (scroll if needed)
    if not _tap_text(device, "Backup", timeout=6.0):
        # scroll the settings list a few times
        w, h = device.screen_size()
        for _ in range(4):
            device.transport.shell(["shell", "input", "swipe", str(w // 2), str(int(h * 0.7)),
                                    str(w // 2), str(int(h * 0.3)), "300"], timeout=15)
            time.sleep(0.8)
            if _tap_text(device, "Backup", timeout=3.0):
                break
    if not _tap_text(device, "Export", timeout=8.0):
        print("no Export row — abort")
        return 1
    time.sleep(2.0)

    print("after Export tap, chooser present:", _chooser_present(device))
    print("chooser texts:", sorted(_texts(device)))

    if _chooser_present(device):
        print(f"tapping app {a.pick!r} ...")
        ok_app = _tap_text(device, a.pick, exact=True)
        print("app tapped:", ok_app)
        time.sleep(0.8)
        print("tapping JUST ONCE ...")
        ok_once = _tap_text(device, "JUST ONCE", exact=True)
        print("JUST ONCE tapped:", ok_once)
        time.sleep(2.5)

    # Now look for the real SAF save-name field.
    deadline = time.time() + 15.0
    found = False
    while time.time() < deadline:
        for n in _nodes(device):
            if "EditText" in (n.cls or "") and (n.text or "").startswith("FulgurisBookmarks-"):
                found = True
                break
        if found:
            break
        time.sleep(0.6)
    print("SAF save-name field present:", found)
    print("texts now:", sorted(_texts(device))[:30])

    device.key(keys.BACK, wait=1.0)
    device.key(keys.BACK, wait=1.0)
    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
