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

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

# A known page whose title (label) differs from its URL.
KNOWN_URL = "example.com"


def _wait_for_orientation(device, want: str, timeout: float = 15.0) -> dict:
    """Wait until the display reports the requested orientation; return its config."""
    import time
    deadline = time.time() + timeout
    while time.time() < deadline and device.config()["orientation"] != want:
        time.sleep(0.5)
    cfg = device.config()
    assert cfg["orientation"] == want, f"device did not rotate to {want}, still {cfg['orientation']}"
    return cfg


def test_repeated_rotations_keep_page(device, ctx: dict) -> None:
    """Forced portrait<->landscape rotations keep the app alive on the same page."""
    saved = device.orientation_state()
    try:
        device.navigate(KNOWN_URL)
        label = device.field_text()
        assert label, "unfocused field should show a label before rotating"

        # A few back-and-forth rotations: each must land in the requested
        # orientation and leave the activity un-recreated (same page, same label).
        for i, want in enumerate(("landscape", "portrait", "landscape"), start=1):
            device.set_orientation(want)
            cfg = _wait_for_orientation(device, want)
            assert device.foreground_package() == device.package, \
                f"after rotation {i} to {want} the app lost the foreground"
            text = device.field_text()
            assert text == label, \
                f"after rotation {i} ({cfg['orientation']}, rot {cfg['rotation']}°) the " \
                f"label changed from '{label}' to '{text}' (activity recreated?)"
    finally:
        device.restore_orientation(*saved)


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
