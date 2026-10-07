#!/usr/bin/env python3
"""Find an API-29-portable way to clear a pre-filled dialog EditText.

The bookmark add-dialog pre-fills the title and URL fields. The tests clear
them with Ctrl+A (select-all) then type — but `input keycombination` does not
exist on API 29 (Huawei P30 Pro), so the chord is dropped and typing appends.

This probe opens the dialog (via the working menu route), taps the title
field, and tries candidate clear strategies, reporting the field text after
each so we can pick the portable one:

  A) `input keyevent --longpress 67`   (long-press backspace, repeated)
  B) burst of `input keyevent 67`      (N discrete backspaces)

Usage:
    python scripts/tests/probe_clear_field.py --device SERIAL
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


def _field_text(device, suffix: str) -> str:
    n = tools_adb.find_node(device.serial, ":id/" + suffix)
    return n.text if n else "<missing>"


def _tap_id(device, suffix: str, timeout: float = 15.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        n = tools_adb.find_node(device.serial, ":id/" + suffix)
        if n and n.center:
            device.tap(*n.center, wait=1.2)
            return True
        time.sleep(0.5)
    return False


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True)
    a = ap.parse_args()
    device = resolve_devices(a.device, False)[0]
    print(f"=== clear-field probe on {device.id} ===")

    device.restart()
    device.navigate("https://www.example.org/")
    time.sleep(1.5)
    _tap_id(device, "button_more")
    _tap_id(device, "menuItemTabMenu")
    _tap_id(device, "menuItemAddBookmark")
    time.sleep(1.5)

    print("title pre-filled:", repr(_field_text(device, "bookmark_title")))
    print("url   pre-filled:", repr(_field_text(device, "bookmark_url")))

    # Focus the title field.
    _tap_id(device, "bookmark_title")
    time.sleep(1.0)
    print("after tap, title:", repr(_field_text(device, "bookmark_title")),
          "focused id:", [n.resource_id for n in device.nodes() if n.focused][:1])

    # Strategy D: ONE shell command — MOVE_END, then a backspace loop in a
    # single adb round-trip (fast, no 40 separate calls).
    t0 = time.time()
    device.transport.shell(
        ["shell", "input keyevent 123; for i in $(seq 1 40); do input keyevent 67; done"],
        timeout=30)
    dt = time.time() - t0
    print(f"D) single-cmd MOVE_END + 40 backspaces ({dt:.1f}s):",
          repr(_field_text(device, "bookmark_title")),
          "focused id:", [n.resource_id for n in device.nodes() if n.focused][:1])
    device.type_text("CleanValue", 0.25)
    time.sleep(0.5)
    print("D) after typing CleanValue:", repr(_field_text(device, "bookmark_title")),
          "focused id:", [n.resource_id for n in device.nodes() if n.focused][:1])

    device.key(keys.BACK, wait=1.0)  # dismiss keyboard
    device.key(keys.BACK, wait=1.0)  # dismiss dialog
    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
