#!/usr/bin/env python3
"""Probe whether `input keyevent --duration <ms>` actually holds the key on a device.

On the *current* (pre-fix) build the action-key long press fires as soon as the OS sends
FLAG_LONG_PRESS, which happens ~400 ms into a hold. So:

  - if the key is genuinely held for `--ms` (>= 400), the link context menu opens;
  - if the device's `--duration` is a no-op / instant, no menu opens.

That makes "did the menu open?" a clean oracle for "does this device hold the key?".

    python scripts/tests/probe_hold.py --device SERIAL [--ms 1500]
"""
from __future__ import annotations

import argparse
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb  # noqa: E402
import cursor_tests  # noqa: E402
import framework  # noqa: E402
from framework import keys  # noqa: E402

LINK_URL = "https://example.com/"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", help="Target a specific adb device serial")
    parser.add_argument("--ms", type=int, default=1500, help="How long to hold DPAD_CENTER (default 1500)")
    args = parser.parse_args()

    rc = 1
    for device in framework.resolve_devices(args.device, False, package="net.slions.fulguris.full.download.debug"):
        print(f"=== {device.label()} ===")
        cursor_tests._load_page(device, "context_target.html")
        cursor_tests._toggle(device)

        # Inject DPAD_CENTER held for `args.ms` via --duration (NOT --longpress, which only
        # holds for the system timeout).
        adb._adb(device.id, ["shell", "input", "keyevent", "--duration", str(args.ms), str(keys.DPAD_CENTER)])
        time.sleep(1.5)

        nodes = device.nodes()
        menu_open = any(n.text == LINK_URL for n in nodes)
        print(f"  --duration {args.ms} -> context menu {'OPENED' if menu_open else 'not opened'}")
        print(f"  => {'--duration HOLDS the key on this device' if menu_open else '--duration does NOT hold the key (no-op/instant)'}")

        # tidy up: dismiss any menu, cursor off
        if menu_open:
            device.key(keys.BACK, wait=0.8)
        cursor_tests._toggle(device)
        if menu_open:
            rc = 0
    return rc


if __name__ == "__main__":
    raise SystemExit(main())
