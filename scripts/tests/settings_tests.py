"""Settings / bottom-sheet UI tests, driven through the framework Device API.

These exercise the in-app settings bottom sheets without needing to walk the
menu with D-pad keys: the app exposes a custom intent
(``fulguris.action.OPEN_CONFIGURATION``) that opens the configuration settings
bottom sheet directly.

Notable regression covered here: opening a configuration screen used to crash
the app with a ClassCastException (x.SliderPreference cannot be cast to
SeekBarPreference) while applying preference defaults, so the sheet never
reached its content.

    python scripts/tests/run.py --all --group settings
    python scripts/tests/run.py --all --test configuration_bottom_sheet
"""
from __future__ import annotations

import time

from framework import keys


def _node_texts(device) -> set[str]:
    return {n.text for n in device.nodes() if n.text}


def _wait_for_node_text(device, text: str, timeout: float = 20.0) -> bool:
    """Poll the UI hierarchy until a node shows ``text`` (or the timeout elapses)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if text in _node_texts(device):
            return True
        time.sleep(1.0)
    return text in _node_texts(device)


def test_configuration_bottom_sheet_opens(device, ctx: dict) -> None:
    """The OPEN_CONFIGURATION intent opens the configuration settings bottom sheet.

    Launches the app, fires the custom intent, and asserts the configuration
    fragment rendered (its "Hide tool bar after" slider row is visible) with
    the app still in the foreground (no crash).
    """
    device.launch()
    device.launch_action("fulguris.action.OPEN_CONFIGURATION", wait=3.0)

    # The configuration fragment only exists past onCreatePreferences, which is
    # exactly where the old ClassCastException crashed.
    assert _wait_for_node_text(device, "Hide tool bar after"), (
        "configuration bottom sheet did not open: 'Hide tool bar after' not found "
        f"(nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    assert device.foreground_package() == device.package, (
        f"app crashed or lost foreground: {device.foreground_package()!r}"
    )

    # Hygiene: pop the configuration fragment, then close the sheet.
    device.key(keys.BACK, 1.2)
    device.key(keys.BACK, 1.2)


FEATURE_GROUPS = {
    "settings": [
        test_configuration_bottom_sheet_opens,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_configuration_bottom_sheet_opens": (
        "The OPEN_CONFIGURATION intent opens the configuration bottom sheet "
        "(regression: ClassCastException in setDefaultIfNeeded)"
    ),
}
