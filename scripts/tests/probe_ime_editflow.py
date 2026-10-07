#!/usr/bin/env python3
"""Replicate the exact test flow of test_center_enters_edit_mode and watch
the IME signals over time, with a screenshot at the point of failure.

Difference from probe_ime_state.py (which worked): that probe did a fresh
app RESTART first. The tests run navigate() without a restart, on the
already-running app. This probe mirrors the test flow so we can tell whether
the keyboard visually appears when mInputShown stays false.

Usage:
    python scripts/tests/probe_ime_editflow.py --device SERIAL
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time

_scripts = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for _d in (_scripts, os.path.join(_scripts, "tools"), os.path.join(_scripts, "tests")):
    if _d not in sys.path:
        sys.path.insert(0, _d)

from framework import keys  # noqa: E402
from appdevice import resolve_devices  # noqa: E402


def _flags(serial: str) -> dict:
    out = subprocess.run(
        ["adb", "-s", serial, "shell", "dumpsys", "input_method"],
        capture_output=True).stdout.decode("utf-8", "replace")
    flags = {}
    for key in ("mInputShown", "mIsInputViewShown"):
        m = re.search(re.escape(key) + r"=(\w+)", out)
        flags[key] = m.group(1) if m else "n/a"
    # window list: an IME window with a visible surface
    wout = subprocess.run(
        ["adb", "-s", serial, "shell", "dumpsys", "window", "windows"],
        capture_output=True).stdout.decode("utf-8", "replace")
    flags["ime_window"] = any("InputMethod" in l for l in wout.splitlines())
    flags["mInputMethodWindow"] = "mInputMethodWindow=" in wout
    return flags


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True, help="adb serial")
    args = ap.parse_args()

    device = resolve_devices(args.device, False)[0]
    serial = device.id
    print(f"=== IME edit-flow probe on {serial} (no restart, like the tests) ===")

    # Exactly like the tests: navigate without restart.
    device.navigate("example.com", reset=False)
    print("after navigate:", _flags(serial))
    print("field_focused:", device.field_focused(), "webview_focused:", device.webview_focused())

    # Navigation focus.
    device.key(keys.SEARCH, wait=0.7)
    print("after SEARCH: field_focused:", device.field_focused(), _flags(serial))

    # Enter edit mode.
    device.key(keys.DPAD_CENTER, wait=0.8)
    t0 = time.time()
    for i in range(15):  # 7.5 s
        print(f"  t={time.time() - t0:4.1f}s  {_flags(serial)}  field={device.field_focused()}")
        if i == 4:  # ~2.5 s: screenshot at the moment tests would give up early
            shot = os.path.join(_scripts, "tools", "out", f"ime_editflow_{device.safe_id}.png")
            device.screenshot(shot)
            print(f"    screenshot: {shot}")
        if i < 14:
            time.sleep(0.5)

    # Hide + cancel, clean state.
    device.key(keys.BACK, wait=0.8)
    device.key(keys.BACK, wait=0.8)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
