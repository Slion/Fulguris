"""A small, platform-agnostic device-automation framework.

Tests and tools talk to a :class:`Device` (semantic, platform-neutral calls);
each ``Device`` is backed by a :class:`~framework.transport.Transport` (the pipe
to the target). Today the only implementation is Android over adb
(:class:`AndroidDevice` / :class:`AdbTransport`), but every test is written to the
``Device`` contract, so adding another platform is additive — no test changes.

Public surface::

    from framework import resolve_devices, keys
    for device in resolve_devices(spec, use_all):
        device.navigate("example.com")
        device.key(keys.DPAD_DOWN)

Runner/session configuration (restart-between-tests, tab hygiene) is exposed here
as thin functions so the runner has one import; they currently delegate to the
adb layer's process-wide state.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb

from . import keys
from .android import AndroidDevice
from .device import Device, Node
from .transport import AdbTransport, Transport

__all__ = [
    "keys",
    "Device",
    "Node",
    "AndroidDevice",
    "Transport",
    "AdbTransport",
    "resolve_devices",
    "reset_between_tests",
    "set_keep_tabs",
    "reset_tab_counter",
    "tabs_opened",
    "keep_tabs",
    "ORIENTATIONS",
]

# Orientation names accepted by set_orientation / the runner's --orientation flag.
ORIENTATIONS = adb.ORIENTATIONS


def resolve_devices(device: str | None, use_all: bool, package: str | None = None) -> list[Device]:
    """Resolve the selected target(s) into :class:`Device` objects.

    Mirrors the adb device selection (exiting with a message when ambiguous) and
    wraps each serial in an :class:`AndroidDevice`. When another platform is added
    this is where its devices would be discovered and wrapped too.
    """
    serials = adb.resolve_devices(device, use_all)
    return [AndroidDevice(serial, package) for serial in serials]


# --- runner/session configuration (process-wide) ---------------------------


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
