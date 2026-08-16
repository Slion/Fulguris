#!/usr/bin/env python3
"""Capture a screenshot from a device.

    python scripts/tools/capture.py                        # -> scripts/tools/out/<serial>.png
    python scripts/tools/capture.py --device SERIAL --out shot.png
    python scripts/tools/capture.py --all                  # one screenshot per device
"""
from __future__ import annotations

import argparse
import os

import adb

OUT_DIR = os.path.join(os.path.dirname(__file__), "out")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", help="Target a specific adb device serial")
    parser.add_argument("--all", action="store_true", help="Capture from all connected devices")
    parser.add_argument("--out", help="Output PNG path (single device only)")
    args = parser.parse_args()

    devices = adb.resolve_devices(args.device, args.all)
    for serial in devices:
        if args.out and len(devices) == 1:
            path = args.out
        else:
            path = os.path.join(OUT_DIR, f"{serial.replace(':', '_')}.png")
        adb.screenshot(serial, path)
        print(f"Saved {path}  ({adb.device_label(serial)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
