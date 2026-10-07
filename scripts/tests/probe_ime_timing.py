#!/usr/bin/env python3
"""Sample mInputShown over time after entering edit mode.

Confirms the hypothesis from probe_ime_state.py: on EMUI-10 (P30 Pro) the
``mInputShown`` flag lags behind the actual keyboard appearance, so the
single-shot ``device.ime_shown()`` check in the tests reads it too early.

This probe enters edit mode, then polls the flag every 0.5 s for ~8 s and
prints a timeline. Usage:
    python scripts/tests/probe_ime_timing.py --device SERIAL
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


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True, help="adb serial")
    args = ap.parse_args()

    device = resolve_devices(args.device, False)[0]
    serial = device.id
    print(f"=== IME timing probe on {serial} ===")

    device.restart()
    device.navigate("example.com")
    time.sleep(2.0)
    print("baseline (hidden):", _flag(serial))

    # Enter edit mode exactly like the tests do.
    device.key(keys.SEARCH, wait=0.7)
    device.key(keys.DPAD_CENTER, wait=1.0)
    t0 = time.time()
    for i in range(17):  # 8.5 s at 0.5 s steps
        print(f"  t={time.time() - t0:4.1f}s  mInputShown={_flag(serial)}")
        if i < 16:
            time.sleep(0.5)

    # Back to a clean state.
    device.key(keys.BACK, wait=0.8)
    time.sleep(0.5)
    print("after BACK (hidden):", _flag(serial))
    device.key(keys.BACK, wait=0.8)
    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
