"""Basic smoke tests: is the app alive and usable at all?

A fast sanity layer (no real sites, no slow loads, no cursor machinery). It is
the DEFAULT selection when ``run.py`` gets neither ``--test`` nor ``--group``,
and can also be selected explicitly:

    python scripts/tests/run.py --device SERIAL --group smoke
    python scripts/tests/run.py --all --group smoke

Covers the minimum life signs: the app launches and reaches its main UI, a web
site loads, the settings activity opens, and the app survives being put in the
background (recents and home) and brought back to the foreground.
"""
from __future__ import annotations

import time

from framework import keys

# A page that always resolves and loads quickly (offline-tolerant on-device).
KNOWN_URL = "example.com"

# How long to leave the app in the background between backgrounding it and
# bringing it back, so the background/foreground transition actually settles
# (and the test exercises a real background period, not an instant return).
BACKGROUND_SECONDS = 3.0


def _node_texts(device) -> set[str]:
    return {n.text for n in device.nodes() if n.text}


def _wait_for_node_text(device, text: str, timeout: float = 15.0) -> bool:
    """Poll the UI hierarchy until a node shows ``text`` (or the timeout elapses)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if text in _node_texts(device):
            return True
        time.sleep(0.5)
    return text in _node_texts(device)


def test_smoke_launch(device, ctx: dict) -> None:
    """The app launches and reaches the main browser UI in the foreground."""
    device.launch()
    assert device.foreground_package() == device.package, (
        f"app not in the foreground after launch: {device.foreground_package()!r}"
    )


def test_smoke_open_website(device, ctx: dict) -> None:
    """Navigating to a web site loads and the address bar shows its label."""
    device.navigate(KNOWN_URL)
    assert device.foreground_package() == device.package, (
        "app lost the foreground while loading the site "
        f"(top: {device.foreground_package()!r})"
    )
    label = device.field_text()
    assert label, "the address field should show the loaded page's label"


def test_smoke_open_settings(device, ctx: dict) -> None:
    """The settings activity opens (via its component) and renders its content."""
    device.settle()
    device.start_component(f"{device.package}/fulguris.activity.SettingsActivity", wait=3.0)
    assert device.foreground_package() == device.package, (
        "settings activity crashed or did not come to the foreground "
        f"(top: {device.foreground_package()!r})"
    )
    assert _wait_for_node_text(device, "Settings"), (
        f"settings screen did not render (nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    device.key(keys.BACK, wait=1.5)  # back to the browser (parent activity)


def test_smoke_background_app_switch(device, ctx: dict) -> None:
    """KEYCODE_APP_SWITCH backgrounds the app (where the platform supports it).

    On phones the key opens recents and the app leaves the foreground. On
    Android TV (leanback) the recents/app-switcher UI is unsupported and the
    system ignores the key — there the backgrounding is skipped with a note and
    only the bring-up half is verified (see the APP_SWITCH probe on the RPi TV:
    topResumedActivity stayed the app's MainActivity).
    """
    device.settle()
    assert device.foreground_package() == device.package, "precondition: app in the foreground"
    device.key(keys.APP_SWITCH, wait=BACKGROUND_SECONDS)
    if device.foreground_package() == device.package:
        ctx["notes"].append(
            "APP_SWITCH is a no-op on this platform (recents unsupported — e.g. "
            "Android TV); verified bring-up only"
        )
    device.launch()
    assert device.foreground_package() == device.package, (
        f"app did not return to the foreground: {device.foreground_package()!r}"
    )


def test_smoke_background_home(device, ctx: dict) -> None:
    """KEYCODE_HOME backgrounds the app; the activity intent brings it back."""
    device.settle()
    assert device.foreground_package() == device.package, "precondition: app in the foreground"
    device.key(keys.HOME, wait=BACKGROUND_SECONDS)
    assert device.foreground_package() != device.package, (
        f"HOME should have backgrounded the app, still {device.foreground_package()!r}"
    )
    # am start -n <package>/<LAUNCH_ACTIVITY> — the activity intent, not the
    # app-switcher UI.
    device.launch()
    assert device.foreground_package() == device.package, (
        f"the activity intent did not bring the app back to the foreground: "
        f"{device.foreground_package()!r}"
    )


FEATURE_GROUPS = {
    "smoke": [
        test_smoke_launch,
        test_smoke_open_website,
        test_smoke_open_settings,
        test_smoke_background_app_switch,
        test_smoke_background_home,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_smoke_launch": "The app launches and reaches the main browser UI in the foreground",
    "test_smoke_open_website": "Navigating to a web site loads and the address bar shows its label",
    "test_smoke_open_settings": "The settings activity opens via its component and renders its content",
    "test_smoke_background_app_switch": (
        "KEYCODE_APP_SWITCH backgrounds the app; launching brings it back to the front"
    ),
    "test_smoke_background_home": (
        "KEYCODE_HOME backgrounds the app; the activity intent brings it back to the front"
    ),
}
