#!/usr/bin/env python3
"""Run the Fulguris device UI tests.

This is a thin host-side entry point over the generic runner in the **AutoTest**
submodule (:mod:`autotest.runner`). It assembles the Fulguris test suites into an
:class:`autotest.Suite`, resolves :class:`appdevice.FulgurisDevice` objects, and
hands off to :meth:`autotest.Runner.run`.

Without ``--test`` or ``--group`` the fast **smoke** group is the default
(launch, open a site, open settings, background/foreground) — pass ``--group all``
or ``--test <substr>`` to select something else.

Examples:
    # Run the smoke group on a specific device (the default selection)
    python scripts/tests/run.py --device R58R91GBTZK

    # Run every test on every connected device
    python scripts/tests/run.py --all --group all

    # Run a single test (by name or unique prefix)
    python scripts/tests/run.py --device SERIAL --test suggestions

    # Restart the app between tests (default keeps it running, which is faster)
    python scripts/tests/run.py --all --restart

    # Keep the tabs tests create (default closes them after each test, as hygiene)
    python scripts/tests/run.py --all --keep-tabs

    # Force an orientation and record the configuration it ran in
    python scripts/tests/run.py --device R58R91GBTZK --orientation landscape

    # Show a device notification with the test currently running
    python scripts/tests/run.py --device 192.168.178.67:5555 --notify

    # List available tests
    python scripts/tests/run.py --list

Each run is saved under scripts/tests/results/<MODEL>/ as a YAML record plus a
Markdown table (with per-test descriptions) so runs, results and regressions can
be tracked for a specific device model and screen/orientation — see
:mod:`autotest.results`.
"""
from __future__ import annotations

import argparse
import os
import sys

_scripts = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # .../scripts
for _d in (
    _scripts,
    os.path.join(_scripts, "tools"),
    os.path.join(_scripts, "tests"),
    os.path.normpath(os.path.join(_scripts, "..", "subs", "AutoTest")),
):
    if _d not in sys.path:
        sys.path.insert(0, _d)

from appdevice import resolve_devices  # noqa: E402  (the Fulguris app profile)
from autotest import Runner, Suite  # noqa: E402

import url_field_tests as suite  # noqa: E402
import smoke_tests  # noqa: E402
import cursor_tests  # noqa: E402
import rotation_tests  # noqa: E402
import settings_tests  # noqa: E402
import toolbar_hide_tests  # noqa: E402
import back_tests  # noqa: E402
import downloads_tests  # noqa: E402
import downloads_full_tests  # noqa: E402
import bookmarks_tests  # noqa: E402
import intent_tests  # noqa: E402
import intent_send_tests  # noqa: E402
import empty_tab_tests  # noqa: E402
import history_tests  # noqa: E402

# All tests across every suite, plus a merged description map for the reports.
ALL_TESTS = (suite.ALL_TESTS + smoke_tests.ALL_TESTS + cursor_tests.ALL_TESTS
             + rotation_tests.ALL_TESTS + settings_tests.ALL_TESTS
             + toolbar_hide_tests.ALL_TESTS + back_tests.ALL_TESTS
             + downloads_tests.ALL_TESTS + downloads_full_tests.ALL_TESTS
             + bookmarks_tests.ALL_TESTS + intent_tests.ALL_TESTS
             + intent_send_tests.ALL_TESTS + empty_tab_tests.ALL_TESTS
             + history_tests.ALL_TESTS)
TEST_DESCRIPTIONS = {**suite.TEST_DESCRIPTIONS, **smoke_tests.TEST_DESCRIPTIONS,
                     **cursor_tests.TEST_DESCRIPTIONS, **rotation_tests.TEST_DESCRIPTIONS,
                     **settings_tests.TEST_DESCRIPTIONS,
                     **toolbar_hide_tests.TEST_DESCRIPTIONS,
                     **back_tests.TEST_DESCRIPTIONS,
                     **downloads_tests.TEST_DESCRIPTIONS,
                     **downloads_full_tests.TEST_DESCRIPTIONS,
                     **bookmarks_tests.TEST_DESCRIPTIONS,
                     **intent_tests.TEST_DESCRIPTIONS,
                     **intent_send_tests.TEST_DESCRIPTIONS,
                     **empty_tab_tests.TEST_DESCRIPTIONS,
                     **history_tests.TEST_DESCRIPTIONS}

# Named feature groups that can be run as a subset via --group. url_field_tests has
# no groups of its own; cursor_tests defines the cursor feature groups. "cursor" is
# a convenience alias for all of them; "all" is added by the Suite automatically.
FEATURE_GROUPS = dict(smoke_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(cursor_tests.FEATURE_GROUPS)
FEATURE_GROUPS["cursor"] = cursor_tests.ALL_TESTS
FEATURE_GROUPS.update(rotation_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(settings_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(toolbar_hide_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(back_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(downloads_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(downloads_full_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(bookmarks_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(intent_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(intent_send_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(empty_tab_tests.FEATURE_GROUPS)
FEATURE_GROUPS.update(history_tests.FEATURE_GROUPS)

RESULTS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results")


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
    parser.add_argument("--orientation", choices=("portrait", "landscape", "sensor"),
                        help="Force device orientation before running; recorded in the results")
    parser.add_argument("--no-save", action="store_true",
                        help="Do not append the run to scripts/tests/results/")
    parser.add_argument("--notify", action="store_true",
                        help="Show a device notification with the test currently running (dismissed at the end)")
    parser.add_argument("--list", action="store_true", help="List available tests and exit")
    args = parser.parse_args()

    test_suite = Suite(tests=ALL_TESTS, descriptions=TEST_DESCRIPTIONS,
                       groups=FEATURE_GROUPS, default_group="smoke")
    runner = Runner(resolve_devices, test_suite, RESULTS_DIR)

    return runner.run(
        device=args.device,
        use_all=args.all,
        package=args.package,
        test=args.test,
        group=args.group,
        restart=args.restart,
        keep_tabs=args.keep_tabs,
        orientation=args.orientation,
        no_save=args.no_save,
        notify=args.notify,
        list=args.list,
    )


if __name__ == "__main__":
    raise SystemExit(main())
