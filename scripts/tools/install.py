#!/usr/bin/env python3
"""Install a deployable APK on a device or on all connected devices.

The agentDebug variant (slionsFullAgentDebug) is a debug build of the "agent"
PUBLISHER flavor — a robot launcher icon, dedicated to automated testing; see
docs/features/agent-variant.md.

    python scripts/tools/install.py                 # single device, or all if only one
    python scripts/tools/install.py --all
    python scripts/tools/install.py --device SERIAL
    python scripts/tools/install.py --build         # build first, then install
    python scripts/tools/install.py --build-type agentRelease
    python scripts/tools/install.py --build --build-type downloadDebug
"""
from __future__ import annotations

import argparse

import adb


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", help="Target a specific adb device serial")
    parser.add_argument("--all", action="store_true", help="Install on all connected devices")
    parser.add_argument("--build", action="store_true", help="Build before installing")
    parser.add_argument("--build-type", choices=sorted(adb.AGENT_VARIANTS),
                        default=adb.DEFAULT_BUILD_TYPE,
                        help="Which variant to install (default: agentDebug)")
    args = parser.parse_args()

    if args.build:
        code = adb.gradle_build(args.build_type)
        if code != 0:
            print("Build FAILED, not installing.")
            return code

    apk = adb.apk_path(args.build_type)
    if not apk:
        print("No APK found; run build first.")
        return 2

    ok = True
    for serial in adb.resolve_devices(args.device, args.all):
        success = adb.install_apk(serial, apk)
        print(f"{'OK  ' if success else 'FAIL'} {adb.device_label(serial)}")
        ok = ok and success
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
