"""Probe what KEYCODE_APP_SWITCH / KEYCODE_HOME actually do on a device.

Sends each key from the app's foreground and reports the top resumed activity
after a delay, so we can see whether the key backgrounds the app or does
nothing on a given platform (e.g. the RPi Android TV box).

    python scripts/tests/probe_appswitch.py --device 192.168.178.67:5555
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import framework  # noqa: E402
from framework import keys  # noqa: E402
import adb  # noqa: E402  (after framework, so the tools dir is on sys.path)

SERIAL = "192.168.178.67:5555"
DELAY = 3.0


def top(serial: str) -> str:
    out = adb._adb(serial, ["shell", "dumpsys", "activity", "activities"])
    for line in out.splitlines():
        if "topResumedActivity=" in line or "mResumedActivity=" in line:
            return line.strip()
    return "<none>"


def main() -> None:
    device = framework.AndroidDevice(SERIAL)
    device.settle()
    print(f"start        fg={device.foreground_package()!r}  {top(SERIAL)[:160]}")

    device.key(keys.APP_SWITCH, wait=DELAY)
    print(f"after switch fg={device.foreground_package()!r}  {top(SERIAL)[:160]}")
    device.launch()

    device.settle()
    print(f"start        fg={device.foreground_package()!r}  {top(SERIAL)[:160]}")
    device.key(keys.HOME, wait=DELAY)
    print(f"after home   fg={device.foreground_package()!r}  {top(SERIAL)[:160]}")
    device.launch()
    print(f"after intent fg={device.foreground_package()!r}  {top(SERIAL)[:160]}")


if __name__ == "__main__":
    main()
