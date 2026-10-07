#!/usr/bin/env python3
"""Drive the bookmarks IMPORT picker flow on this device and dump the picker
state after each step, to find where the file becomes unpickable.

Usage:
    python scripts/tests/probe_import_picker.py --device SERIAL
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

ASSETS = os.path.join(os.path.dirname(__file__), "assets")
REMOTE = "/sdcard/Download/autotest_import_probe.html"


def _nodes(device):
    return device.nodes()


def _texts(device):
    return [n.text for n in _nodes(device) if n.text]


def _tap_text(device, text, exact=True, timeout=8.0, index=0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        matches = [n for n in _nodes(device) if n.bounds and n.text
                   and (n.text == text if exact else text in n.text)]
        i = len(matches) + index if index < 0 else index
        if i < len(matches):
            device.tap(*matches[i].center, wait=1.5)
            return True
        time.sleep(0.5)
    return False


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True)
    a = ap.parse_args()
    device = resolve_devices(a.device, False)[0]
    print(f"=== import picker probe on {device.id} ===")

    device.restart()
    device.settle()

    # Push the fixture (with the EMUI-10 media-scan nudge).
    device.transport.shell(["shell", "rm", "-f", REMOTE], timeout=20)
    tools_adb.push(device.serial, os.path.join(ASSETS, "import_probe.html"), REMOTE)
    indexed = False
    deadline = time.time() + 20.0
    while time.time() < deadline:
        out = device.transport.shell(
            ["shell", "content", "query", "--uri", "content://media/external/file",
             "--projection", "_display_name"], timeout=20)
        if "autotest_import_probe.html" in out:
            indexed = True
            break
        if time.time() > deadline - 8.0:
            device.transport.shell(
                ["shell", "am", "broadcast",
                 "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                 "-d", f"file://{REMOTE}"], timeout=20)
        time.sleep(1.0)
    print("indexed:", indexed)

    # Settings -> Backup -> Import.
    ok_menu = False
    deadline = time.time() + 20.0
    while time.time() < deadline and not ok_menu:
        n = tools_adb.find_node(device.serial, ":id/button_more")
        if n and n.center:
            device.tap(*n.center, wait=1.5)
            ok_menu = True
        else:
            time.sleep(0.6)
    if not ok_menu:
        print("no overflow menu — abort"); return 1
    if not _tap_text(device, "Settings", exact=True):
        print("no Settings row — abort"); return 1
    time.sleep(1.5)
    if not _tap_text(device, "Backup", exact=True, timeout=6.0):
        w, h = device.screen_size()
        for _ in range(4):
            device.transport.shell(["shell", "input", "swipe", str(w // 2), str(int(h * 0.7)),
                                    str(w // 2), str(int(h * 0.3)), "300"], timeout=15)
            time.sleep(0.8)
            if _tap_text(device, "Backup", exact=True, timeout=3.0):
                break
    print("on Backup page; tapping Import ...")
    if not _tap_text(device, "Import", exact=True, index=0):
        print("no Import row — abort"); return 1

    # The EMUI 'Open with' chooser can sit in front of the picker — resolve it
    # by tapping 'Files' (the AOSP DocumentsUI, which has the search icon).
    def chooser():
        t = {x.strip() for x in _texts(device)}
        return "JUST ONCE" in t and ("Open with" in t or "ALWAYS" in t)

    if chooser():
        print("chooser present — tapping 'Files' ...")
        _tap_text(device, "Files", exact=True)
        time.sleep(2.0)
        if chooser():
            _tap_text(device, "JUST ONCE", exact=True)
            time.sleep(2.0)
        print("after resolve, chooser still present:", chooser())

    # Watch what the picker shows.
    for i in range(10):
        time.sleep(2.0)
        texts = _texts(device)
        has_search = any((n.resource_id or "").endswith("option_menu_search") or
                         (n.resource_id or "").endswith("search_src_text")
                         for n in _nodes(device))
        print(f"  t={2 * (i + 1):2d}s fg={device.foreground_package()!r} "
              f"search_ui={has_search} texts={sorted(texts)[:18]!r}")
        if has_search:
            # try the search + type like the test does
            s = None
            for n in _nodes(device):
                if (n.resource_id or "").endswith("option_menu_search"):
                    s = n
                    break
            if s:
                device.tap(*s.center, wait=1.5)
                device.type_text("autotest_import_probe.html", 0.15)
                time.sleep(3.0)
                found = [n for n in _nodes(device)
                         if n.bounds and n.text == "autotest_import_probe.html" and n.bounds[1] > 200]
                print("search results row found:", bool(found))
            break

    device.key(4, wait=1.0)
    device.key(4, wait=1.0)
    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
