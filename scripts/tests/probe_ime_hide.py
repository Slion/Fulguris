#!/usr/bin/env python3
"""Measure how long the two 'keyboard shown' signals persist after BACK
hides the keyboard (needed for the negative ime_shown assertions):

1. ``mInputShown`` (dumpsys input_method)
2. ``mInputMethodWindow=Window{…}`` (dumpsys window windows)

Usage:
    python scripts/tests/probe_ime_hide.py --device SERIAL
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


def _flag(serial: str) -> str:
    out = subprocess.run(
        ["adb", "-s", serial, "shell", "dumpsys", "input_method"],
        capture_output=True).stdout.decode("utf-8", "replace")
    m = re.search(r"mInputShown=(\w+)", out)
    return m.group(1) if m else "n/a"


def _window(serial: str) -> bool:
    out = subprocess.run(
        ["adb", "-s", serial, "shell", "dumpsys", "window", "windows"],
        capture_output=True).stdout.decode("utf-8", "replace")
    return "mInputMethodWindow=Window{" in out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True, help="adb serial")
    args = ap.parse_args()

    device = resolve_devices(args.device, False)[0]
    serial = device.id
    print(f"=== IME hide-timing probe on {serial} ===")

    device.restart()
    device.navigate("example.com")
    time.sleep(2.0)
    print("baseline (hidden): flag =", _flag(serial), " window =", _window(serial))

    device.key(keys.SEARCH, wait=0.7)
    device.key(keys.DPAD_CENTER, wait=0.8)
    time.sleep(1.0)
    print("shown:             flag =", _flag(serial), " window =", _window(serial))

    device.key(keys.BACK, wait=1.0)  # hide the keyboard
    t0 = time.time()
    for i in range(11):  # 5 s at 0.5 s steps
        print(f"  t={time.time() - t0:4.1f}s  flag={_flag(serial):5s}  window={_window(serial)}")
        if i < 10:
            time.sleep(0.5)

    device.key(keys.BACK, wait=0.8)  # cancel edit
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
