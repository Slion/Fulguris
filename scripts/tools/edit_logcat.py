#!/usr/bin/env python3
"""Capture app logcat around entering edit mode over D-pad, to find what clears the field.

    python scripts/tools/edit_logcat.py                 # first device
    python scripts/tools/edit_logcat.py --device SERIAL
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import adb

OUT_DIR = os.path.join(os.path.dirname(__file__), "out")


def _adb_raw(serial: str, args: list[str]) -> str:
    cmd = ["adb", "-s", serial, "shell"] + args
    r = subprocess.run(cmd, capture_output=True, encoding="utf-8", errors="replace", timeout=30)
    return r.stdout or ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", help="Target a specific adb device serial")
    args = parser.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)
    for serial in adb.resolve_devices(args.device, False):
        package = adb.detect_package(serial)
        print(f"=== {adb.device_label(serial)} ===")
        if not adb.settle(serial, package):
            print(f"  WARNING: {package} not settled")

        _adb_raw(serial, ["logcat", "-c"])
        adb.navigate(serial, package, "example.com")
        _adb_raw(serial, ["logcat", "-c"])

        adb.key(serial, adb.KEY_SEARCH, wait=1.0)
        print(f"-- after SEARCH: focused={adb.field_focused(serial)} text={adb.field_text(serial)!r}")

        adb.key(serial, adb.KEY_DPAD_CENTER, wait=2.5)
        print(f"-- after CENTER: ime={adb.ime_shown(serial)} focused={adb.field_focused(serial)} text={adb.field_text(serial)!r}")

        time.sleep(1.0)
        log = _adb_raw(serial, ["logcat", "-d"])
        path = os.path.join(OUT_DIR, f"edit_logcat_{serial.replace(':', '_')}.txt")
        with open(path, "w", encoding="utf-8") as f:
            f.write(log)
        print(f"logcat saved: {path} ({len(log)} chars)")
        adb.key(serial, adb.KEY_BACK, wait=1.0)
        adb.key(serial, adb.KEY_BACK, wait=1.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
