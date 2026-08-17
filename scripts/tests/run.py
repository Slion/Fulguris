#!/usr/bin/env python3
"""Run the URL address bar UI tests over adb.

Examples:
    # Run all tests on every connected device
    python scripts/tests/run.py --all

    # Run on a specific device
    python scripts/tests/run.py --device 192.168.178.67:5555

    # Run a single test (by name or unique prefix)
    python scripts/tests/run.py --all --test suggestions

    # List available tests
    python scripts/tests/run.py --list
"""
from __future__ import annotations

import argparse
import os
import sys
import time
import traceback

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb
import url_field_tests as suite


def select_tests(name: str | None):
    if not name:
        return suite.ALL_TESTS
    matches = [t for t in suite.ALL_TESTS if name in t.__name__]
    if not matches:
        print(f"No test matches '{name}'.")
        sys.exit(2)
    return matches


def run_one(t, serial: str, package: str, ctx: dict) -> tuple[float, str | None]:
    """Run a single test with timing. Returns (elapsed seconds, error line or None)."""
    t0 = time.monotonic()
    try:
        t(serial, package, ctx)
    except AssertionError as e:
        return time.monotonic() - t0, f"FAIL  {t.__name__}: {e}"
    except Exception as e:  # noqa: BLE001
        traceback.print_exc()
        return time.monotonic() - t0, f"ERROR {t.__name__}: {e}"
    return time.monotonic() - t0, None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", help="Target a specific adb device serial")
    parser.add_argument("--all", action="store_true", help="Run on all connected devices")
    parser.add_argument("--test", help="Run only tests whose name contains this substring")
    parser.add_argument("--package", help="Override the app package to test")
    parser.add_argument("--list", action="store_true", help="List available tests and exit")
    args = parser.parse_args()

    if args.list:
        for t in suite.ALL_TESTS:
            print(t.__name__)
        return 0

    devices = adb.resolve_devices(args.device, args.all)
    tests = select_tests(args.test)

    overall_ok = True
    total_start = time.monotonic()
    for serial in devices:
        package = args.package or adb.detect_package(serial)
        print(f"\n=== {adb.device_label(serial)}  [{package}] ===")
        ctx: dict = {"notes": []}
        passed = 0
        timings: list[tuple[str, float]] = []
        device_start = time.monotonic()
        for t in tests:
            elapsed, error = run_one(t, serial, package, ctx)
            timings.append((t.__name__, elapsed))
            if error:
                overall_ok = False
                print(f"  {error}  ({elapsed:.1f}s)")
            else:
                passed += 1
                print(f"  PASS  {t.__name__}  ({elapsed:.1f}s)")
        device_elapsed = time.monotonic() - device_start
        print(f"  -> {passed}/{len(tests)} passed in {device_elapsed:.1f}s")
        if timings:
            slowest = max(timings, key=lambda item: item[1])
            print(f"  slowest: {slowest[0]} ({slowest[1]:.1f}s)")
        for note in ctx["notes"]:
            print(f"  note: {note}")
    print(f"\nTotal: {time.monotonic() - total_start:.1f}s across {len(devices)} device(s)")

    return 0 if overall_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
