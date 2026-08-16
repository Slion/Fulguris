#!/usr/bin/env python3
"""Trace the address field state step by step (with polling for stable reads) while
entering edit mode over D-pad, saving a full uiautomator dump and logcat per step.

The TV over network adb is slow, so a single uiautomator read can race the real state.
Each step therefore polls until two consecutive reads agree.

    python scripts/tools/edit_trace.py                 # first device
    python scripts/tools/edit_trace.py --device SERIAL
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
    r = subprocess.run(cmd, capture_output=True, encoding="utf-8", errors="replace", timeout=40)
    return r.stdout or ""


def _state(serial: str) -> dict:
    return {
        "focused": adb.field_focused(serial),
        "text": adb.field_text(serial),
        "ime": adb.ime_shown(serial),
        "webview": adb.webview_focused(serial),
    }


def _stable_state(serial: str, tag: str, dump: bool, tries: int = 5) -> dict:
    """Poll the state until two consecutive reads agree (or we run out of tries)."""
    last: dict | None = None
    for i in range(tries):
        st = _state(serial)
        print(f"  [{tag}] read{i}: {st}")
        if dump and (last is None or st != last):
            _adb_raw(serial, ["uiautomator", "dump", "/sdcard/w.xml"])
            xml = _adb_raw(serial, ["cat", "/sdcard/w.xml"])
            path = os.path.join(OUT_DIR, f"edit_trace_{serial.replace(':', '_')}_{tag}_{i}.xml")
            with open(path, "w", encoding="utf-8") as f:
                f.write(xml)
        if st == last:
            return st
        last = st
        time.sleep(0.5)
    return last or _state(serial)


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

        adb.navigate(serial, package, "example.com")
        _adb_raw(serial, ["logcat", "-c"])

        st = _stable_state(serial, "0_after_navigate", dump=True)
        print(f"-- after navigate (stable): {st}")

        adb.key(serial, adb.KEY_SEARCH, wait=1.0)
        st = _stable_state(serial, "1_after_search", dump=True)
        print(f"-- after SEARCH (stable): {st}")

        adb.key(serial, adb.KEY_DPAD_CENTER, wait=2.0)
        st = _stable_state(serial, "2_after_center", dump=True)
        print(f"-- after CENTER (stable): {st}")

        # Let a late clearing run, if any, happen and observe it.
        time.sleep(2.0)
        st2 = _stable_state(serial, "3_after_center_wait", dump=True)
        print(f"-- after CENTER +2s (stable): {st2}")

        time.sleep(1.0)
        log = _adb_raw(serial, ["logcat", "-d"])
        path = os.path.join(OUT_DIR, f"edit_trace_{serial.replace(':', '_')}.log")
        with open(path, "w", encoding="utf-8") as f:
            f.write(log)
        print(f"log saved: {path} ({len(log)} chars)")

        adb.key(serial, adb.KEY_BACK, wait=1.0)
        adb.key(serial, adb.KEY_BACK, wait=1.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
