"""Probe: does enabling the cursor fire a page mouse hover on the phone?

Loads cursor_target.html, enables the cursor (which must dispatch an initial
ACTION_HOVER_MOVE to the page -> the page sets document.title to 'hover'),
then reads back the title. Also tries a D-pad nudge (which re-dispatches the
hover) before reading again, so a dropped initial dispatch is distinguished
from hovers never reaching the page at all.

Usage: python scripts/tests/probe_phone_hover.py [serial]
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
from cursor_tests import _load_target, _toggle, _title  # noqa: E402


def main() -> None:
    if len(sys.argv) > 1:
        device = AndroidDevice(sys.argv[1])
    else:
        devices = resolve_devices(device=None, use_all=True)
        if not devices:
            raise SystemExit("no devices")
        device = devices[0]
    print(f"device: {device.id}  package: {device.package}")

    _load_target(device)
    print(f"initial title: {_title(device)!r}")

    _toggle(device)
    time.sleep(1.5)
    print(f"after enable:  title={_title(device)!r}")

    device.key(keys.DPAD_RIGHT, wait=1.0)  # movement re-dispatches the hover
    print(f"after nudge:   title={_title(device)!r}")

    device.key(keys.DPAD_DOWN, wait=1.0)
    print(f"after nudge2:  title={_title(device)!r}")

    _toggle(device)


if __name__ == "__main__":
    main()
