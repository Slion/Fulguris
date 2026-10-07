"""System back (key / gesture) regression tests, driven through the framework Device API.

Regression covered: since targetSdk 36 the system back is dispatched through the
OnBackPressedDispatcher and no longer calls the legacy Activity.onBackPressed()
override. Activities that only overrode the legacy method (WebBrowserActivity,
SettingsActivity) would finish and send the app to the background, or skip their
custom back logic (nested settings breadcrumbs, the URL field's two-stage edit
exit).

The local back_a/back_b pages are served over an ``adb reverse`` tunnel because
Fulguris blocks file:// URLs; each page's title is mirrored into the toolbar
label so we can assert which page is shown without a screenshot.

    python scripts/tests/run.py --device SERIAL --group back
    python scripts/tests/run.py --device SERIAL --test back
"""
from __future__ import annotations

import atexit
import os
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from framework import keys

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
PORT = 8901

_server: ThreadingHTTPServer | None = None
_reversed: dict = {}  # device.id -> device, for reverse-tunnel teardown at exit


class _NoCacheHandler(SimpleHTTPRequestHandler):
    """Serve the assets dir without caching so each navigation gets a fresh page."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ASSETS_DIR, **kwargs)

    def send_header(self, key, value):
        if key.lower() == "last-modified":
            return
        super().send_header(key, value)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, *args):
        pass


def _ensure_server() -> None:
    global _server
    if _server is not None:
        return
    _server = ThreadingHTTPServer(("127.0.0.1", PORT), _NoCacheHandler)
    threading.Thread(target=_server.serve_forever, daemon=True).start()
    atexit.register(_teardown)


def _teardown() -> None:
    for device in list(_reversed.values()):
        try:
            device.reverse_remove(PORT)
        except Exception:  # noqa: BLE001
            pass
    if _server is not None:
        _server.shutdown()


def _ensure_reverse(device) -> None:
    if device.id in _reversed:
        return
    device.reverse(PORT)
    _reversed[device.id] = device


def _page_url(name: str) -> str:
    # Cache-bust so the WebView never reuses a previous load of the same asset.
    return f"http://localhost:{PORT}/{name}?cb={int(time.time() * 1000)}"


def _wait_for_field(device, want: str, timeout: float = 20.0) -> str:
    """Poll the toolbar label until it contains ``want``; returns the last text seen."""
    deadline = time.time() + timeout
    text = device.field_text()
    while time.time() < deadline:
        if want in text:
            return text
        time.sleep(0.5)
        text = device.field_text()
    return text


def _node_texts(device) -> set[str]:
    return {n.text for n in device.nodes() if n.text}


def _wait_for_node_text(device, text: str, timeout: float = 20.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if text in _node_texts(device):
            return True
        time.sleep(1.0)
    return text in _node_texts(device)


def _tap_node_text(device, text: str) -> None:
    """Tap the center of the first node whose text equals ``text``."""
    for node in device.nodes():
        if node.text == text and node.bounds:
            x1, y1, x2, y2 = node.bounds
            device.tap((x1 + x2) // 2, (y1 + y2) // 2, wait=1.2)
            return
    raise AssertionError(f"no node with text '{text}' "
                         f"(nodes: {sorted(_node_texts(device))[:40]!r})")


# --- Browser activity --------------------------------------------------------


def test_back_two_tabs_keeps_foreground(device, ctx: dict) -> None:
    """Back with a tab to close closes the top tab (revealing the one below),
    not finish the activity and send the app to the background.

    Regression: the back gesture/key bypassed the legacy onBackPressed()
    override on API 35+ targets, so the activity finished and the app went to
    the background (launcher visible).
    """
    _ensure_server()
    _ensure_reverse(device)
    device.restart()
    device.navigate(_page_url("back_a.html"))
    text = _wait_for_field(device, "back-page-a")
    assert "back-page-a" in text, f"page A did not load (field='{text}')"
    device.navigate(_page_url("back_b.html"))
    text = _wait_for_field(device, "back-page-b")
    assert "back-page-b" in text, f"page B did not load (field='{text}')"

    device.key(keys.BACK, wait=1.5)
    assert device.foreground_package() == device.package, (
        f"back sent the app to the background (top: {device.foreground_package()!r})"
    )
    text = _wait_for_field(device, "back-page-a", timeout=5.0)
    assert "back-page-a" in text, (
        f"back did not close the top tab and reveal the tab below (field='{text}')"
    )


def test_back_last_tab_stays_foreground(device, ctx: dict) -> None:
    """Back on the last tab with no page history closes the tab (start page),
    not finish the activity."""
    _ensure_server()
    _ensure_reverse(device)
    device.restart()
    device.navigate(_page_url("back_a.html"))
    text = _wait_for_field(device, "back-page-a")
    assert "back-page-a" in text, f"page did not load (field='{text}')"

    device.key(keys.BACK, wait=1.5)
    assert device.foreground_package() == device.package, (
        f"back sent the app to the background (top: {device.foreground_package()!r})"
    )
    text = device.field_text()
    assert "back-page-a" not in text, (
        f"back on the last tab should have closed the tab (field='{text}')"
    )


def test_back_editing_two_stage_exit(device, ctx: dict) -> None:
    """Back while editing the URL field exits in two stages: the first press
    hides the keyboard (field keeps the URL), the second cancels back to the
    label. Covers the OnBackPressedCallback routing into
    SearchView.performBackAction() (the key path goes through onKeyPreIme)."""
    _ensure_server()
    _ensure_reverse(device)
    device.restart()
    device.navigate(_page_url("back_a.html"))
    text = _wait_for_field(device, "back-page-a")
    assert "back-page-a" in text, f"page did not load (field='{text}')"
    label = text

    center = device.field_center()
    assert center, "could not locate the address field bounds"
    device.tap(center[0], center[1], wait=1.0)
    # EMUI 10 lags the mInputShown flag 0-3 s behind the keyboard appearing;
    # poll rather than single-shot (see IME_SHOW_TIMEOUT in url_field_tests).
    assert device.ime_shown(timeout=5.0), "tapping the field should enter edit mode and show the keyboard"

    device.key(keys.BACK, wait=1.0)
    assert not device.ime_shown(), "first back should hide the keyboard"
    assert "http" in device.field_text().lower(), (
        f"first back should keep the URL while editing (field='{device.field_text()}')"
    )

    device.key(keys.BACK, wait=1.0)
    restored = device.field_text()
    assert restored == label, (
        f"second back should cancel back to the label '{label}', got '{restored}'"
    )


# --- Settings activity -------------------------------------------------------


def test_settings_back_from_nested_no_crash(device, ctx: dict) -> None:
    """Back from a nested settings screen (Appearance > Portrait) must pop the
    nested screen back to Appearance and never crash or get stuck. In both
    layouts the nested fragment is on the child back stack, so the first back
    pops it; the second back either finishes the activity (single-pane) or
    closes the detail pane (dual-pane), in which case a third back exits.

    The regressions guarded here: SettingsActivity's back handling used to
    route through the OnBackPressedDispatcher into itself (infinite recursion
    / StackOverflow) after the targetSdk 36 dispatcher fix, before that fix
    the API 35+ back gesture skipped the legacy override entirely, and the
    pop path only removed the title crumb without popping the fragment (back
    could never exit the activity).
    Requires the device locale to be English (like the other node-text tests).
    """
    device.settle()
    device.start_component(f"{device.package}/fulguris.activity.SettingsActivity", wait=3.0)
    assert _wait_for_node_text(device, "Appearance"), (
        f"settings root did not render (nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    _tap_node_text(device, "Appearance")
    assert _wait_for_node_text(device, "Portrait"), (
        f"Appearance screen did not render (nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    _tap_node_text(device, "Portrait")
    assert _wait_for_node_text(device, "Hide tool bar after"), (
        f"Portrait screen did not render (nodes: {sorted(_node_texts(device))[:40]!r})"
    )

    # The nested Portrait fragment is on the child back stack in both layouts,
    # so back must pop it back to Appearance (and never crash).
    device.key(keys.BACK, wait=2.0)
    assert device.foreground_package() == device.package, (
        f"back from the nested settings screen crashed or left the app "
        f"(top: {device.foreground_package()!r})"
    )
    assert _wait_for_node_text(device, "Appearance"), (
        f"back should have popped the Portrait screen back to Appearance "
        f"(nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    assert not _wait_for_node_text(device, "Hide tool bar after", timeout=3.0), (
        "back from Portrait should have replaced the Portrait screen"
    )

    # Second back: single-pane finishes the activity; dual-pane closes the
    # detail pane back to the root list. Either way: no crash, not stuck on
    # the Appearance screen.
    device.key(keys.BACK, wait=2.0)
    assert device.foreground_package() == device.package, (
        f"second back crashed or left the app (top: {device.foreground_package()!r})"
    )
    if _wait_for_node_text(device, "Appearance", timeout=3.0):
        # Still in settings (dual-pane: detail pane closed) — a third back must
        # then exit the activity.
        device.key(keys.BACK, wait=2.0)
        assert device.foreground_package() == device.package, (
            "third back should have exited settings without crashing"
        )
    # Hygiene: leave the app on the browser for the next test.
    device.launch()


FEATURE_GROUPS = {
    "back": [
        test_back_two_tabs_keeps_foreground,
        test_back_last_tab_stays_foreground,
        test_back_editing_two_stage_exit,
        test_settings_back_from_nested_no_crash,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_back_two_tabs_keeps_foreground": (
        "Back with a tab to close closes the top tab, not finish the app "
        "(regression: predictive back bypassing onBackPressed)"
    ),
    "test_back_last_tab_stays_foreground": (
        "Back on the last tab closes it (start page), not finish the app"
    ),
    "test_back_editing_two_stage_exit": (
        "Back while editing the URL field: first press hides the keyboard, "
        "second press cancels back to the label"
    ),
    "test_settings_back_from_nested_no_crash": (
        "Back from a nested settings screen (Appearance > Portrait) pops the "
        "nested screen back to the parent and keeps back usable until it exits, "
        "without crashing or getting stuck"
    ),
    # Kept so the renamed test's stale results entry from earlier runs doesn't
    # trigger a "no TEST_DESCRIPTIONS entry" warning.
    "test_settings_back_nested_pops_not_finish": (
        "Renamed to test_settings_back_from_nested_no_crash"
    ),
}
