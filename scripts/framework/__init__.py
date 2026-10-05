"""Back-compat shim for the old ``framework`` package.

The generic, platform-agnostic device-automation framework (the ``Device``
contract, the ``Transport`` protocol, the Android/adb backend, the key symbols,
and the per-run session state) now lives in the **AutoTest** submodule as the
:mod:`autotest` package. This package re-exports that generic surface so the
historical probes and tools that do ``import framework`` /
``from framework import AndroidDevice, keys`` keep working unchanged.

``AndroidDevice`` and ``resolve_devices`` here are the **Fulguris** device
(:class:`appdevice.FulgurisDevice`) — the old ``framework.AndroidDevice`` already
knew the Fulguris app (its address bar, tabs, …), so the alias preserves that
behavior for callers that relied on it. The pure-generic device is
:class:`autotest.android.device.AndroidDevice`.
"""
from __future__ import annotations

import os
import sys

_here = os.path.dirname(os.path.abspath(__file__))          # .../scripts/framework
_scripts = os.path.dirname(_here)                            # .../scripts
_at = os.path.join(_scripts, "..", "subs", "AutoTest")       # .../subs/AutoTest

for _d in (_at, os.path.join(_scripts, "tools"), os.path.join(_scripts, "tests")):
    _d = os.path.normpath(_d)
    if _d not in sys.path:
        sys.path.insert(0, _d)

from autotest import (  # noqa: E402,F401
    keys,
    Device,
    Node,
    Transport,
    AdbTransport,
    ORIENTATIONS,
)
import adb  # noqa: E402  (the back-compat adb shim in scripts/tools)

from appdevice import FulgurisDevice, resolve_devices  # noqa: E402

# The old framework.AndroidDevice was the Fulguris device (it knew the app's
# address bar / tabs / …). Keep that behavior for back-compat callers.
AndroidDevice = FulgurisDevice

__all__ = [
    "keys", "Device", "Node", "Transport", "AdbTransport",
    "AndroidDevice", "resolve_devices", "ORIENTATIONS",
    "reset_between_tests", "set_keep_tabs", "reset_tab_counter",
    "tabs_opened", "keep_tabs",
]


# --- per-run session state (delegated to the adb shim's module globals) -----


def reset_between_tests(restart: bool) -> None:
    """Set whether navigate() without an explicit reset= restarts the app."""
    adb.reset_between_tests(restart)


def set_keep_tabs(keep: bool) -> None:
    """Set whether the runner leaves test-created tabs open (default: close them)."""
    adb.set_keep_tabs(keep)


def reset_tab_counter() -> None:
    """Reset the per-test opened-tab count (called by the runner before each test)."""
    adb.reset_tab_counter()


def tabs_opened() -> int:
    """How many tabs the current test opened (for the runner's end-of-test cleanup)."""
    return adb.TABS_OPENED


def keep_tabs() -> bool:
    """Whether the runner should leave test-created tabs open."""
    return adb.KEEP_TABS
