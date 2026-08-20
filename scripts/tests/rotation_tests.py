"""Screen-rotation UI tests, driven over adb.

Fulguris declares android:configChanges="...|orientation|..." on its activities, so
rotating the device must NOT recreate the activity: the current page, its label in
the address bar, and the session must all survive a rotation.

These tests force the display orientation with `settings put system user_rotation`
(see adb.set_orientation), wait until the device actually reports the requested
orientation, and verify the app stayed in the foreground on the same page/label.
The device's original orientation state (auto-rotate + user rotation) is always
restored in a finally block, even on failure.

Run via run.py:

    python scripts/tests/run.py --device SERIAL --group rotation
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb

# A known page whose title (label) differs from its URL.
KNOWN_URL = "example.com"


def _wait_for_orientation(serial: str, want: str, timeout: float = 15.0) -> dict:
    """Wait until the display reports the requested orientation; return its config."""
    ok = adb.wait_until(
        lambda: adb.device_config(serial)["orientation"] == want,
        timeout=timeout, interval=0.5,
    )
    cfg = adb.device_config(serial)
    assert ok, f"device did not rotate to {want}, still {cfg['orientation']}"
    return cfg


def test_repeated_rotations_keep_page(serial: str, package: str, ctx: dict) -> None:
    """Forced portrait<->landscape rotations keep the app alive on the same page."""
    saved = adb.orientation_state(serial)
    try:
        adb.navigate(serial, package, KNOWN_URL)
        label = adb.field_text(serial)
        assert label, "unfocused field should show a label before rotating"

        # A few back-and-forth rotations: each must land in the requested
        # orientation and leave the activity un-recreated (same page, same label).
        for i, want in enumerate(("landscape", "portrait", "landscape"), start=1):
            adb.set_orientation(serial, want)
            cfg = _wait_for_orientation(serial, want)
            assert adb.foreground_package(serial) == package, \
                f"after rotation {i} to {want} the app lost the foreground"
            text = adb.field_text(serial)
            assert text == label, \
                f"after rotation {i} ({cfg['orientation']}, rot {cfg['rotation']}°) the " \
                f"label changed from '{label}' to '{text}' (activity recreated?)"
    finally:
        adb.restore_orientation(serial, *saved)


# ===========================================================================
# Registration
# ===========================================================================

FEATURE_GROUPS = {
    "rotation": [
        test_repeated_rotations_keep_page,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_repeated_rotations_keep_page": "Forced portrait/landscape rotations keep the app on the same page without recreating the activity",
}
