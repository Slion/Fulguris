#!/usr/bin/env python3
"""Run the URL address bar UI tests over adb.

Examples:
    # Run all tests on every connected device
    python scripts/tests/run.py --all

    # Run on a specific device
    python scripts/tests/run.py --device 192.168.178.67:5555

    # Run a single test (by name or unique prefix)
    python scripts/tests/run.py --all --test suggestions

    # Restart the app between tests (default keeps it running, which is faster)
    python scripts/tests/run.py --all --restart

    # Keep the tabs tests create (default closes them after each test, as
    # hygiene; the tab count itself has no performance impact)
    python scripts/tests/run.py --all --keep-tabs

    # Force an orientation and record the configuration it ran in
    python scripts/tests/run.py --device R58R91GBTZK --orientation landscape

    # List available tests
    python scripts/tests/run.py --list

Each run is saved under scripts/tests/results/<MODEL>/ as a YAML record plus a
Markdown table (with per-test descriptions) so runs, results and regressions
can be tracked for a specific device model and screen/orientation — see
results.py.
"""
from __future__ import annotations

import argparse
import os
import sys
import time
import traceback

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb
import results as results_store
import url_field_tests as suite
import cursor_tests

# All tests across every suite, plus a merged description map for the reports.
ALL_TESTS = suite.ALL_TESTS + cursor_tests.ALL_TESTS
TEST_DESCRIPTIONS = {**suite.TEST_DESCRIPTIONS, **cursor_tests.TEST_DESCRIPTIONS}

# Named feature groups that can be run as a subset via --group. url_field_tests has no groups of
# its own; cursor_tests defines the cursor feature groups. "cursor" is a convenience alias for all
# of them.
FEATURE_GROUPS = dict(cursor_tests.FEATURE_GROUPS)
FEATURE_GROUPS["cursor"] = cursor_tests.ALL_TESTS


def select_tests(name: str | None, group: str | None):
    if group:
        if group not in FEATURE_GROUPS:
            print(f"Unknown group '{group}'. Available: {', '.join(sorted(FEATURE_GROUPS))}")
            sys.exit(2)
        tests = FEATURE_GROUPS[group]
    else:
        tests = ALL_TESTS
    if name:
        tests = [t for t in tests if name in t.__name__]
        if not tests:
            print(f"No test matches '{name}'.")
            sys.exit(2)
    return tests


def run_one(t, serial: str, package: str, ctx: dict) -> tuple[float, str | None]:
    """Run a single test with timing. Returns (elapsed seconds, error line or None).

    The tabs the test created are closed again afterwards (hygiene) unless
    --keep-tabs was passed; see adb.TABS_OPENED / adb.KEEP_TABS.
    """
    adb.reset_tab_counter()
    t0 = time.monotonic()
    try:
        t(serial, package, ctx)
        result: str | None = None
    except AssertionError as e:
        result = f"FAIL  {t.__name__}: {e}"
    except Exception as e:  # noqa: BLE001
        traceback.print_exc()
        result = f"ERROR {t.__name__}: {e}"
    elapsed = time.monotonic() - t0
    if not adb.KEEP_TABS and adb.TABS_OPENED > 0:
        adb.close_tabs(serial, adb.TABS_OPENED)
    return elapsed, result


def _status(error: str | None) -> str:
    if error is None:
        return "pass"
    return "error" if error.startswith("ERROR") else "fail"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", help="Target a specific adb device serial")
    parser.add_argument("--all", action="store_true", help="Run on all connected devices")
    parser.add_argument("--test", help="Run only tests whose name contains this substring")
    parser.add_argument("--group", help="Run only a named feature group (e.g. cursor, cursor-movement)")
    parser.add_argument("--package", help="Override the app package to test")
    parser.add_argument("--restart", action="store_true",
                        help="Restart the app between tests (default: keep it running, faster)")
    parser.add_argument("--keep-tabs", action="store_true",
                        help="Do not close the tabs tests create (default: close them after each test; hygiene, no perf impact)")
    parser.add_argument("--orientation", choices=adb.ORIENTATIONS,
                        help="Force device orientation before running (portrait/landscape/sensor); recorded in the results")
    parser.add_argument("--no-save", action="store_true",
                        help="Do not append the run to scripts/tests/results/")
    parser.add_argument("--list", action="store_true", help="List available tests and exit")
    args = parser.parse_args()

    adb.reset_between_tests(args.restart)
    adb.set_keep_tabs(args.keep_tabs)

    if args.list:
        for t in ALL_TESTS:
            print(t.__name__)
        return 0

    devices = adb.resolve_devices(args.device, args.all)
    tests = select_tests(args.test, args.group)

    overall_ok = True
    total_start = time.monotonic()
    for serial in devices:
        package = args.package or adb.detect_package(serial)
        saved_state = None
        if args.orientation:
            saved_state = adb.orientation_state(serial)
            adb.set_orientation(serial, args.orientation)
        config = adb.device_config(serial)
        print(f"\n=== {adb.device_label(serial)}  [{package}] ===")
        print(f"  config: {config['config_id']}  "
              f"({config['orientation']}, rot {config['rotation']}°, sw{config['smallest_width_dp']}dp, "
              f"Android {config['android']})")
        ctx: dict = {"notes": []}
        passed = 0
        timings: list[tuple[str, float]] = []
        test_records: list[dict] = []
        device_start = time.monotonic()
        for t in tests:
            elapsed, error = run_one(t, serial, package, ctx)
            timings.append((t.__name__, elapsed))
            record = {"name": t.__name__, "status": _status(error), "duration_s": round(elapsed, 1)}
            if error:
                overall_ok = False
                record["message"] = error.split(": ", 1)[-1]
                print(f"  {error}  ({elapsed:.1f}s)")
            else:
                passed += 1
                print(f"  PASS  {t.__name__}  ({elapsed:.1f}s)")
            test_records.append(record)
        device_elapsed = time.monotonic() - device_start
        print(f"  -> {passed}/{len(tests)} passed in {device_elapsed:.1f}s")
        if timings:
            slowest = max(timings, key=lambda item: item[1])
            print(f"  slowest: {slowest[0]} ({slowest[1]:.1f}s)")
        for note in ctx["notes"]:
            print(f"  note: {note}")

        if not args.no_save:
            previous = results_store.load_last_run(config["model"], config["config_id"], serial)
            record = results_store.build_record(
                config, package,
                {"restart": args.restart, "keep_tabs": args.keep_tabs,
                 "orientation": args.orientation, "test_filter": args.test},
                test_records, device_elapsed,
            )
            diff = results_store.compare(previous, record)
            yaml_path, md_path = results_store.save_run(record, TEST_DESCRIPTIONS)
            if diff["regressions"]:
                print(f"  REGRESSIONS vs last run: {', '.join(diff['regressions'])}")
            if diff["fixes"]:
                print(f"  fixed since last run: {', '.join(diff['fixes'])}")
            if previous is None:
                first = f"  saved (no previous run to compare) -> {os.path.relpath(yaml_path)}"
            else:
                first = f"  saved (compared to {previous['timestamp']}) -> {os.path.relpath(yaml_path)}"
            print(first + f"  [+ {os.path.basename(md_path)}]")

        if saved_state is not None:
            adb.restore_orientation(serial, *saved_state)
    print(f"\nTotal: {time.monotonic() - total_start:.1f}s across {len(devices)} device(s)")

    return 0 if overall_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
