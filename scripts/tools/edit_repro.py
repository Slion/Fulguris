#!/usr/bin/env python3
"""Reproduce/diagnose the address field entering edit mode over D-pad.

Navigates to a known page, focuses the field for navigation, presses center to
enter edit mode, then dumps every text-bearing text field node (to disambiguate
our field from the leanback IME's field on TV) and captures screenshots.

    python scripts/tools/edit_repro.py                 # first connected device
    python scripts/tools/edit_repro.py --device SERIAL
"""
from __future__ import annotations

import argparse
import os

import adb

OUT_DIR = os.path.join(os.path.dirname(__file__), "out")


def _node_lines(serial: str) -> None:
    for n in adb.nodes(serial):
        if n.text and ("EditText" in n.cls or "search" in n.resource_id.lower()):
            rid = n.resource_id.rsplit(":", 1)[-1]
            print(f"  NODE id={rid} cls={n.cls} text={n.text!r} focused={n.focused} bounds={n.bounds}")
    print(f"  field_text() -> {adb.field_text(serial)!r}  (first :id/search node)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", help="Target a specific adb device serial")
    parser.add_argument("--all", action="store_true", help="Act on all connected devices")
    args = parser.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)
    for serial in adb.resolve_devices(args.device, args.all):
        package = adb.detect_package(serial)
        print(f"\n=== {adb.device_label(serial)} ===")
        if not adb.settle(serial, package):
            print(f"  WARNING: {package} not settled; continuing anyway")

        adb.navigate(serial, package, "example.com")
        adb.key(serial, adb.KEY_SEARCH, wait=1.5)
        print(f"-- after SEARCH: ime={adb.ime_shown(serial)} field_focused={adb.field_focused(serial)}")
        _node_lines(serial)

        adb.key(serial, adb.KEY_DPAD_CENTER, wait=2.0)
        print(f"-- after CENTER: ime={adb.ime_shown(serial)} field_focused={adb.field_focused(serial)}")
        _node_lines(serial)
        p1 = os.path.join(OUT_DIR, f"edit_{serial.replace(':', '_')}.png")
        adb.screenshot(serial, p1)
        print(f"  screenshot: {p1}")

        adb.key(serial, adb.KEY_BACK, wait=1.5)
        print(f"-- after BACK (hide keyboard): ime={adb.ime_shown(serial)} field_focused={adb.field_focused(serial)}")
        _node_lines(serial)
        p2 = os.path.join(OUT_DIR, f"edit_nokb_{serial.replace(':', '_')}.png")
        adb.screenshot(serial, p2)
        print(f"  screenshot: {p2}")

        # restore: second back cancels the edition
        adb.key(serial, adb.KEY_BACK, wait=1.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
