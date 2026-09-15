"""Probe: can the phone's WebView receive a hover event injected from the SHELL?

Loads assets/hover_log.html and sends `input motionevent` (SOURCE_MOUSE,
TOOL_TYPE_MOUSE, ACTION_HOVER_ENTER + HOVER_MOVE) via adb shell — bypassing the
app's synthetic-event path entirely. If the page records events, the WebView is
capable and the app's dispatch route is the problem. If not, the platform/WebView
build drops hover events no matter who sends them.

Usage: python scripts/tests/probe_shell_hover.py [serial]
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))

from framework import AndroidDevice  # noqa: E402
from framework import resolve_devices  # noqa: E402
from cursor_tests import _load_page, _title  # noqa: E402

import adb as adbtools  # noqa: E402  (on sys.path via the framework import)

# ACTION_HOVER_ENTER=7, ACTION_HOVER_MOVE=8.
# input motionevent: <action> <id:type:x:y>...   type 2 = TOOL_TYPE_MOUSE
HOVER_ENTER = "7 0:2:400:800"
HOVER_MOVE = "8 0:2:410:810"


def shell_hover(device: AndroidDevice, spec: str) -> None:
    adbtools._adb(device.serial, ["shell", "input", "motionevent", spec])


def main() -> None:
    if len(sys.argv) > 1:
        device = AndroidDevice(sys.argv[1])
    else:
        devices = resolve_devices(device=None, use_all=True)
        if not devices:
            raise SystemExit("no devices")
        device = devices[0]
    print(f"device: {device.id}  package: {device.package}")

    _load_page(device, "hover_log.html")
    time.sleep(0.5)
    print(f"initial:   {_title(device)!r}")

    shell_hover(device, HOVER_ENTER)
    time.sleep(0.5)
    print(f"after enter:{_title(device)!r}")

    shell_hover(device, HOVER_MOVE)
    shell_hover(device, HOVER_MOVE)
    time.sleep(0.5)
    print(f"after move: {_title(device)!r}")


if __name__ == "__main__":
    main()
