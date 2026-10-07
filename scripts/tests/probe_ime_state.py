#!/usr/bin/env python3
"""Probe what 'keyboard shown' actually looks like on this device.

The url_field edit-mode tests assert ``device.ime_shown()`` (which reads
``dumpsys input_method mInputShown``). On the Huawei P30 Pro (EMUI 10) every such
assertion fails even though edit mode clearly works (the IME-independent edit
tests pass). This probe enters edit mode the same way the tests do, then dumps
every signal we could use to detect the keyboard, so we can pick a robust one:

* ``dumpsys input_method`` (mInputShown / mIsInputViewShown / the shown window)
* ``dumpsys window windows`` (an IME / InputMethod window in the window list)
* ``dumpsys input`` (the IME window as an input target)
* a screenshot (for a manual visual check)

Usage:
    python scripts/tests/probe_ime_state.py --device SERIAL
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


def _raw(serial: str, *args: str) -> str:
    import subprocess
    out = subprocess.run(["adb", "-s", serial, "shell", *args],
                         capture_output=True)
    return out.stdout.decode("utf-8", "replace")


def _im_shown_flags(serial: str) -> dict:
    out = _raw(serial, "dumpsys", "input_method")
    import re
    flags = {}
    for key in ("mInputShown", "mIsInputViewShown", "mIsInTouchMode",
                "mShowImeRequested"):
        m = re.search(re.escape(key) + r"=(\w+)", out)
        flags[key] = m.group(1) if m else "n/a"
    return flags


def _windows_with_ime(serial: str) -> list:
    """Lines in `dumpsys window windows` mentioning an IME / InputMethod window."""
    out = _raw(serial, "dumpsys", "window", "windows")
    return [l for l in out.splitlines()
            if "InputMethod" in l or "ime" in l.lower() and "Window" in l]


def _input_ime_targets(serial: str) -> list:
    """Lines in `dumpsys input` that mention an IME window (an active input target)."""
    out = _raw(serial, "dumpsys", "input")
    return [l for l in out.splitlines() if "InputMethod" in l or "IME" in l]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True, help="adb serial")
    args = ap.parse_args()

    device = resolve_devices(args.device, False)[0]
    print(f"=== IME probe on {device.label()} ({device.id}) ===")

    # 1. Clean loaded state (web view focused, no keyboard).
    device.restart()
    device.navigate("example.com")
    time.sleep(2.0)
    print("\n--- STATE: loaded page, web view focused (keyboard should be HIDDEN) ---")
    print("ime_shown():", device.ime_shown())
    print("im_shown_flags:", _im_shown_flags(device.id))
    print("windows_with_ime:", _windows_with_ime(device.id) or "none")
    print("input_ime_targets:", _input_ime_targets(device.id) or "none")

    # 2. Enter edit mode exactly like the tests do (navigation focus + center).
    device.key(keys.SEARCH, wait=0.7)
    device.key(keys.DPAD_CENTER, wait=1.0)
    time.sleep(1.0)
    print("\n--- STATE: edit mode entered via center (keyboard should be SHOWN) ---")
    print("ime_shown():", device.ime_shown())
    print("field_focused():", device.field_focused())
    print("field_text():", repr(device.field_text()))
    print("im_shown_flags:", _im_shown_flags(device.id))
    print("windows_with_ime:", _windows_with_ime(device.id) or "none")
    print("input_ime_targets:", _input_ime_targets(device.id) or "none")

    # 3. Screenshot for a manual look at whether the keyboard is really visible.
    shot = os.path.join(_scripts, "tools", "out", f"ime_probe_{device.safe_id}.png")
    device.screenshot(shot)
    print(f"\nscreenshot: {shot}")

    # 4. Hide keyboard with back, re-check.
    device.key(keys.BACK, wait=1.0)
    print("\n--- STATE: after BACK (keyboard should be HIDDEN again) ---")
    print("ime_shown():", device.ime_shown())
    print("im_shown_flags:", _im_shown_flags(device.id))

    # Hygiene: cancel edit, back to the browser.
    device.key(keys.BACK, wait=0.8)
    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
