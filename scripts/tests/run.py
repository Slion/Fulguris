#!/usr/bin/env python3
"""Run the device UI tests over adb.

Without ``--test`` or ``--group`` the fast **smoke** group is the default
(launch, open a site, open settings, background/foreground) — pass ``--group all``
or ``--test <substr>`` to select something else.

Examples:
    # Run the smoke group on a specific device (the default selection)
    python scripts/tests/run.py --device 192.168.178.67:5555

    # Run every test on every connected device
    python scripts/tests/run.py --all --group all

    # Run a single test (by name or unique prefix)
    python scripts/tests/run.py --device SERIAL --test suggestions

    # Restart the app between tests (default keeps it running, which is faster)
    python scripts/tests/run.py --all --restart

    # Keep the tabs tests create (default closes them after each test, as
    # hygiene; the tab count itself has no performance impact)
    python scripts/tests/run.py --all --keep-tabs

    # Force an orientation and record the configuration it ran in
    python scripts/tests/run.py --device R58R91GBTZK --orientation landscape

    # Show a device notification with the test currently running
    python scripts/tests/run.py --device 192.168.178.67:5555 --notify

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

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb
import framework
import results as results_store
import url_field_tests as suite
import smoke_tests
import cursor_tests
import rotation_tests
import settings_tests
import toolbar_hide_tests

# All tests across every suite, plus a merged description map for the reports.
ALL_TESTS = (suite.ALL_TESTS + smoke_tests.ALL_TESTS + cursor_tests.ALL_TESTS
             + rotation_tests.ALL_TESTS + settings_tests.ALL_TESTS
             + toolbar_hide_tests.ALL_TESTS)
TEST_DESCRIPTIONS = {**suite.TEST_DESCRIPTIONS, **smoke_tests.TEST_DESCRIPTIONS,
                     **cursor_tests.TEST_DESCRIPTIONS, **rotation_tests.TEST_DESCRIPTIONS,
                     **settings_tests.TEST_DESCRIPTIONS,
                     **toolbar_hide_tests.TEST_DESCRIPTIONS}

# Named feature groups that can be run as a subset via --group. url_field_tests has no groups of
# its own; cursor_tests defines the cursor feature groups. "cursor" is a convenience alias for all
# of them, and "all" runs every test.
FEATURE_GROUPS = dict(smoke_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(cursor_tests.FEATURE_GROUPS)
FEATURE_GROUPS["cursor"] = cursor_tests.ALL_TESTS
FEATURE_GROUPS.update(rotation_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(settings_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(toolbar_hide_tests.FEATURE_GROUPS)
FEATURE_GROUPS["all"] = ALL_TESTS


def select_tests(name: str | None, group: str | None) -> tuple[list, str | None]:
    """Resolve the tests to run.

    Returns ``(tests, group_or_None)``. When neither ``--test`` nor ``--group``
    is given, the fast **smoke** group is the default — a full-suite run is an
    explicit choice (``--group all``) because it is slow on real devices.
    """
    if name:
        base = ALL_TESTS  # --test searches every suite, not just the default group
        tests = [t for t in base if name in t.__name__]
        if not tests:
            print(f"No test matches '{name}'.")
            sys.exit(2)
        return tests, None
    if group is None:
        group = "smoke"
    if group not in FEATURE_GROUPS:
        print(f"Unknown group '{group}'. Available: {', '.join(sorted(FEATURE_GROUPS))}")
        sys.exit(2)
    return FEATURE_GROUPS[group], group


def run_one(t, device, ctx: dict) -> tuple[float, str | None]:
    """Run a single test with timing. Returns (elapsed seconds, error line or None).

    The tabs the test created are closed again afterwards (hygiene) unless
    --keep-tabs was passed; see framework.tabs_opened() / framework.keep_tabs().
    """
    framework.reset_tab_counter()
    t0 = time.monotonic()
    try:
        t(device, ctx)
        result: str | None = None
    except AssertionError as e:
        result = f"FAIL  {t.__name__}: {e}"
    except Exception as e:  # noqa: BLE001
        traceback.print_exc()
        result = f"ERROR {t.__name__}: {e}"
    elapsed = time.monotonic() - t0
    if not framework.keep_tabs() and framework.tabs_opened() > 0:
        device.close_tabs(framework.tabs_opened())
    return elapsed, result


def _status(error: str | None) -> str:
    if error is None:
        return "pass"
    return "error" if error.startswith("ERROR") else "fail"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--device", help="Target a specific adb device serial")
    parser.add_argument("--all", action="store_true", help="Run on all connected devices")
    parser.add_argument("--test", help="Run only tests whose name contains this substring (searches all suites)")
    parser.add_argument("--group", help="Run only a named feature group (e.g. smoke, cursor, cursor-movement, all); default: smoke")
    parser.add_argument("--package", help="Override the app package to test")
    parser.add_argument("--restart", action="store_true",
                        help="Restart the app between tests (default: keep it running, faster)")
    parser.add_argument("--keep-tabs", action="store_true",
                        help="Do not close the tabs tests create (default: close them after each test; hygiene, no perf impact)")
    parser.add_argument("--orientation", choices=framework.ORIENTATIONS,
                        help="Force device orientation before running (portrait/landscape/sensor); recorded in the results")
    parser.add_argument("--no-save", action="store_true",
                        help="Do not append the run to scripts/tests/results/")
    parser.add_argument("--notify", action="store_true",
                        help="Show a device notification with the test currently running (dismissed at the end)")
    parser.add_argument("--list", action="store_true", help="List available tests and exit")
    args = parser.parse_args()

    framework.reset_between_tests(args.restart)
    framework.set_keep_tabs(args.keep_tabs)

    if args.list:
        for t in ALL_TESTS:
            print(t.__name__)
        return 0

    devices = framework.resolve_devices(args.device, args.all, args.package)
    tests, selected_group = select_tests(args.test, args.group)
    if not args.test and not args.group:
        print("No --test/--group given; running the default 'smoke' group "
              f"({len(tests)} tests). Use --group <name> or --group all to select otherwise.")

    overall_ok = True
    total_start = time.monotonic()
    for device in devices:
        package = device.package
        saved_state = None
        if args.orientation:
            saved_state = device.orientation_state()
            device.set_orientation(args.orientation)
        config = device.config()
        print(f"\n=== {device.label()}  [{package}] ===")
        print(f"  config: {config['config_id']}  "
              f"({config['orientation']}, rot {config['rotation']}°, sw{config['smallest_width_dp']}dp, "
              f"Android {config['android']})")
        ctx: dict = {"notes": []}
        passed = 0
        timings: list[tuple[str, float]] = []
        test_records: list[dict] = []
        device_start = time.monotonic()
        if args.notify:
            # Clear any leftover from a previously crashed run, then show the run banner.
            try:
                adb.dismiss_test_notification(device.id)
                adb.post_test_notification(device.id, f"Running {len(tests)} test(s) on {device.label()}")
            except Exception as e:  # noqa: BLE001 - never let a notification fail a run
                print(f"  note: --notify unavailable on this device ({e}); continuing without it")
        for i, t in enumerate(tests, 1):
            if args.notify:
                try:
                    adb.post_test_notification(device.id, f"({i}/{len(tests)}) {t.__name__}")
                except Exception:  # noqa: BLE001 - notification is cosmetic; never fail a run
                    pass
            elapsed, error = run_one(t, device, ctx)
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
        if args.notify:
            try:
                summary = f"Done: {passed}/{len(tests)} passed" + (
                    f" on {device.label()}" if len(devices) > 1 else "")
                adb.post_test_notification(device.id, summary)
                adb.dismiss_test_notification(device.id)
            except Exception:  # noqa: BLE001 - best effort
                pass

        if not args.no_save:
            previous = results_store.load_last_run(config["model"], config["config_id"], device.id)
            record = results_store.build_record(
                config, package,
                {"restart": args.restart, "keep_tabs": args.keep_tabs,
                 "orientation": args.orientation, "test_filter": args.test,
                 "group": selected_group},
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
            device.restore_orientation(*saved_state)
    print(f"\nTotal: {time.monotonic() - total_start:.1f}s across {len(devices)} device(s)")

    return 0 if overall_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
