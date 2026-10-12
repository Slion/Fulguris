"""Probe: long-press the folder box on the BOOKMARKS PAGE (the HTML home page).

Repro of StringIndexOutOfBoundsException in
LightningDialogBuilder.showLongPressedDialogForBookmarkUrl: the folder link's
href is file://.../files/<folder>/bookmarks.html, so the legacy
filename.substring(0, len - 14 - 1) computes substring(0, -1) and throws.

Deterministic setup: reset all bookmarks, add ONE bookmark in ONE new folder,
so the bookmarks page contains exactly one box (the folder, centered on the
page — .parent flex-centers a single child). Long-press the screen center.

    python scripts/tests/probe_bm_page_longpress.py
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "framework"))

from framework import resolve_devices  # noqa: E402
import adb  # noqa: E402
from bookmarks_tests import (  # noqa: E402
    _close_to_browser,
    _open_backup_page,
    _tap_text,
    _wait_text,
    _open_bookmarks_drawer,
    _close_drawer,
    _add_bookmark_menu,
    _set_field,
    _tap_ok,
    _drawer_has,
    _texts,
)

PKG = adb.DEFAULT_PACKAGE
FOLDER = "AutoTestCrashFolder"
ENTRY_TITLE = "AutoTest crash folder entry"
ENTRY_URL = "https://www.example.org/autotest-crash-folder"


def _reset_all(device) -> None:
    _close_to_browser(device)
    _open_backup_page(device)
    assert _tap_text(device, "Reset", timeout=10.0, exact=True), "no 'Reset' row"
    assert _wait_text(device, "Delete all bookmarks?", timeout=15.0, exact=True)
    assert _tap_text(device, "Delete", timeout=10.0, exact=True)
    time.sleep(1.5)
    _close_to_browser(device)


def main():
    device = resolve_devices("R58R91GBTZK", use_all=False)[0]
    serial = device.serial

    # 1. deterministic state: exactly one folder with one entry
    _reset_all(device)
    device.navigate("https://www.example.org/")
    _add_bookmark_menu(device)
    assert _wait_text(device, "Add bookmark", timeout=15.0, exact=True)
    _set_field(device, ":id/bookmark_title", ENTRY_TITLE)
    _set_field(device, ":id/bookmark_url", ENTRY_URL)
    _set_field(device, ":id/bookmark_folder", FOLDER)
    assert _tap_ok(device)
    _open_bookmarks_drawer(device)
    assert _drawer_has(device, FOLDER, timeout=10.0), "folder missing"
    _close_drawer(device)

    # 2. load the bookmarks page (the HTML page, not the drawer)
    device.navigate("about:bookmarks")
    time.sleep(2.0)
    print("toolbar label:", repr(adb.field_text(serial)))

    out = os.path.join(os.path.dirname(__file__), "out", "bm_page_before.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    device.transport.shell(["shell", "screencap", "-p", "/sdcard/pb.png"], timeout=20)
    adb.pull(serial, "/sdcard/pb.png", out)
    print("screenshot (before):", out)

    # 3. long-press the (only) box, centered on the page
    w, h = device.screen_size()
    x, y = w // 2, h // 2
    before = adb.pidof(serial, PKG)
    print(f"long-pressing at ({x}, {y})  pid before: {before!r}")
    device.tap_longpress(x, y, hold_ms=1000, wait=2.0)
    time.sleep(3.0)
    after = adb.pidof(serial, PKG)
    print("pid after:", after, "=> CRASHED" if after != before else "=> alive")
    print("texts:", sorted(_texts(device))[:30])

    out2 = os.path.join(os.path.dirname(__file__), "out", "bm_page_after.png")
    device.transport.shell(["shell", "screencap", "-p", "/sdcard/pa.png"], timeout=20)
    adb.pull(serial, "/sdcard/pa.png", out2)
    print("screenshot (after):", out2)
    if after != before:
        adb.restart(serial, PKG)


if __name__ == "__main__":
    main()
