"""Probe: what pointer/mouse events does the page actually receive from the cursor?

Loads assets/hover_log.html (logs every pointerover/enter/move + mouseover/move into
document.title), enables the cursor (initial hover at center), then nudges it with the
D-pad (continuous hover dispatch), and prints the recorded event log. Run on a device
where hovers DO work (TV) and one where they DON'T (phone) to compare the shapes.

Usage: python scripts/tests/probe_hover_log.py [serial]
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))

from framework import keys  # noqa: E402
from framework.android import AndroidDevice  # noqa: E402
from framework import resolve_devices  # noqa: E402
from cursor_tests import _load_page, _toggle, _title  # noqa: E402


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
    print(f"initial:   {_title(device)!r}")

    _toggle(device)
    time.sleep(1.0)
    print(f"after on:  {_title(device)!r}")

    device.key(keys.DPAD_RIGHT, wait=1.0)
    device.key(keys.DPAD_RIGHT, wait=1.0)
    device.key(keys.DPAD_DOWN, wait=1.0)
    print(f"after move:{_title(device)!r}")

    _toggle(device)


if __name__ == "__main__":
    main()
