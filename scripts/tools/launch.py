#!/usr/bin/env python3
"""Launch (or restart) the app on a device or all connected devices.

    python scripts/tools/launch.py                 # launch
    python scripts/tools/launch.py --restart       # force-stop then launch
    python scripts/tools/launch.py --all
"""
from __future__ import annotations

import argparse

import adb


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", help="Target a specific adb device serial")
    parser.add_argument("--all", action="store_true", help="Act on all connected devices")
    parser.add_argument("--restart", action="store_true", help="Force-stop before launching")
    args = parser.parse_args()

    for serial in adb.resolve_devices(args.device, args.all):
        package = adb.detect_package(serial)
        if args.restart:
            adb.restart(serial, package)
        else:
            adb.launch(serial, package)
        print(f"Launched {package} on {adb.device_label(serial)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
