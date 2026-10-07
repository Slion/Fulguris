"""In-tab page-history tests, driven through the framework Device API.

The existing ``back`` suite covers what happens when back has *no* in-page
history (it closes the top tab / the last tab). These tests cover the other
branch of the same handler in
:meth:`fulguris.activity.WebBrowserActivity.doBackAction`: when the current tab
*does* have in-page history (``currentTab.canGoBack()``) and the web view holds
input focus, the system back key must navigate the tab's own history (``goBack``)
rather than closing the tab. A regression here would make back close a page the
user still wanted, or finish the activity.

The two pages are served over an ``adb reverse`` tunnel (Fulguris blocks
``file://``); each mirrors its title into the toolbar label so we can read which
page is current without a screenshot. Navigation between the pages is done by
touch-tapping the in-page link (``input tap`` works on both touch and leanback
devices), which stays in the SAME tab and builds the in-page history.

    python scripts/tests/run.py --device SERIAL --group history
    python scripts/tests/run.py --device SERIAL --test in_tab_back
"""
from __future__ import annotations

import atexit
import os
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from framework import keys  # noqa: E402

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
PORT = 8902  # distinct from back_tests (8901) so the two groups never collide

_server: ThreadingHTTPServer | None = None
_reversed: dict = {}  # device.id -> True, for reverse-tunnel teardown at exit


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
    try:
        _server = ThreadingHTTPServer(("127.0.0.1", PORT), _NoCacheHandler)
    except OSError:
        return  # another group already bound it serving the same dir
    threading.Thread(target=_server.serve_forever, daemon=True).start()
    atexit.register(_teardown)


def _teardown() -> None:
    for device_id in list(_reversed):
        try:
            _reverse_devices[device_id].reverse_remove(PORT)
        except Exception:  # noqa: BLE001
            pass
    if _server is not None:
        _server.shutdown()


_reverse_devices: dict = {}  # device.id -> device


def _ensure_reverse(device) -> None:
    if device.id in _reversed:
        return
    device.reverse(PORT)
    _reversed[device.id] = True
    _reverse_devices[device.id] = device


def _page_url(name: str) -> str:
    return f"http://localhost:{PORT}/{name}?cb={int(time.time() * 1000)}"


def _wait_for_field(device, want: str, timeout: float = 25.0) -> str:
    deadline = time.time() + timeout
    text = device.field_text()
    while time.time() < deadline:
        if want in text:
            return text
        time.sleep(0.5)
        text = device.field_text()
    return text


def _webview_present(device) -> bool:
    return any(n.cls == "android.webkit.WebView" for n in device.nodes())


def _tap_text(device, text: str) -> None:
    """Touch-tap the first node whose text equals ``text`` (works on touch + leanback)."""
    for node in device.nodes():
        if node.text == text and node.bounds and node.center:
            device.tap(node.center[0], node.center[1], wait=1.5)
            return
    raise AssertionError(f"no node with text '{text}' to tap")


def test_in_tab_back_navigates_history(device, ctx: dict) -> None:
    """With in-page history, the system back key navigates the tab back, not close it.

    Load page A, tap its in-page link to page B (same tab, so A is now in history),
    then press back: the web view is focused and canGoBack() is true, so the tab must
    go back to A (label returns to 'history-a') and the tab must NOT be closed.
    """
    _ensure_server()
    _ensure_reverse(device)
    device.restart()
    device.navigate(_page_url("history_a.html"))
    text = _wait_for_field(device, "history-a")
    assert "history-a" in text, f"page A did not load (field='{text}')"

    # Tap the in-page link: stays in this tab and pushes A onto the in-page history.
    _tap_text(device, "go-to-history-b")
    text = _wait_for_field(device, "history-b")
    assert "history-b" in text, (
        f"tapping the link should have navigated to page B in the same tab "
        f"(field='{text}')"
    )
    assert device.webview_focused(), (
        "the web view should hold focus on the loaded page (the back key's "
        "goBack path requires it)"
    )

    # Back with in-page history: goBack() to A, NOT close the tab / finish the app.
    device.key(keys.BACK, wait=2.0)
    assert device.foreground_package() == device.package, (
        f"back navigated out of the app instead of the page history "
        f"(top: {device.foreground_package()!r})"
    )
    text = _wait_for_field(device, "history-a", timeout=10.0)
    assert "history-a" in text, (
        f"back should have gone back to page A within the tab (field='{text}')"
    )
    assert _webview_present(device), (
        "back closed the tab instead of navigating its history: no WebView left"
    )


def test_in_tab_back_not_confused_with_field_edit(device, ctx: dict) -> None:
    """Back with in-page history must not be swallowed by an unfocused address field.

    After loading a page with history, the address field is in its unfocused label
    state (not editing). Pressing back must still take the page-history step; the
    two-stage edit exit only applies while the field is actually editing.
    """
    _ensure_server()
    _ensure_reverse(device)
    device.navigate(_page_url("history_a.html"))
    assert "history-a" in _wait_for_field(device, "history-a"), "page A did not load"
    _tap_text(device, "go-to-history-b")
    assert "history-b" in _wait_for_field(device, "history-b"), "page B did not load"

    # The field is unfocused (label state) after load; confirm we are not editing.
    device.settle(timeout=10.0)
    device.key(keys.BACK, wait=2.0)
    assert device.foreground_package() == device.package, (
        f"back left the app (top: {device.foreground_package()!r})"
    )
    text = _wait_for_field(device, "history-a", timeout=10.0)
    assert "history-a" in text, (
        f"back from an unfocused field should still step page history to A "
        f"(field='{text}')"
    )


FEATURE_GROUPS = {
    "history": [
        test_in_tab_back_navigates_history,
        test_in_tab_back_not_confused_with_field_edit,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_in_tab_back_navigates_history": (
        "With in-page history the back key navigates the tab back to the previous "
        "page (goBack), it does not close the tab or finish the app"
    ),
    "test_in_tab_back_not_confused_with_field_edit": (
        "Back with in-page history and an unfocused address field still steps the "
        "page history (the two-stage edit exit does not swallow it)"
    ),
}
