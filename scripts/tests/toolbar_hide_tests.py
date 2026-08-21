"""Tests for the "Hide tool bar after" timeout feature.

The feature (Settings > General > "Hide tool bar after", ``pref_key_hide_tool_bar_timeout``)
auto-hides the tool bar a configurable number of seconds (0-10, 0 = disabled) after a page
has finished loading, and again every time the web view regains input focus. It must:

  * start when the current page transitions from loading to loaded,
  * re-arm when the web view gains input focus (e.g. after editing the address field),
  * NOT be reset by other user interaction,
  * NOT be starved by busy pages that keep firing tab-state callbacks (onPageFinished,
    theme-color reports, late progress>=100 events) - the regression this suite guards,
  * never hide while a page is loading, a menu/panel is open, a video is fullscreen, or
    the address field (or anything but the web view) holds input focus,
  * do nothing when the timeout is 0.

Toolbar visibility is observed over adb through the mirrored address-field text: while the
tool bar is visible the field shows the page title, and once the tool bar has hidden the
field is empty. The pages are served from the host over an ``adb reverse`` tunnel
(Fulguris blocks file://). The "busy page" case uses ``assets/theme_flipper.html``, which
changes its <meta name="theme-color"> every 2 seconds; each change is reported through the
console and the app treats it as a tab change, so a build with the bug re-arms the
countdown forever and never hides, while a fixed build hides ~timeout s after load.

    python scripts/tests/run.py --all --group toolbar-hide
    python scripts/tests/run.py --device SERIAL --group toolbar-hide
    python scripts/tests/run.py --all --test toolbar
"""
from __future__ import annotations

import atexit
import os
import re
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from framework import keys  # noqa: E402

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
PORT = 8899

TIMEOUT_KEY = "pref_key_hide_tool_bar_timeout"
FADE_KEY = "pref_key_cursor_fade_timeout"
DEFAULT_VALUE = "0"
DEFAULT_FADE = "3000"  # the code default; 0 = never fade (deterministic overlay checks)

_server: ThreadingHTTPServer | None = None
_reversed: dict = {}  # device.id -> device, for reverse-tunnel teardown at exit


class _NoCacheHandler(SimpleHTTPRequestHandler):
    """Serve the assets dir and forbid caching, so each navigation fetches a fresh page."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ASSETS_DIR, **kwargs)

    def send_header(self, key, value):  # suppress Last-Modified so the client never sends 304
        if key.lower() == "last-modified":
            return
        super().send_header(key, value)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, *args):  # keep the test output clean
        pass


def _ensure_server() -> None:
    """Start a host HTTP server serving the assets dir once, in a daemon thread.

    If another suite (e.g. cursor_tests) already bound the port serving the same dir,
    reuse it: the bind raises OSError and we simply carry on.
    """
    global _server
    if _server is not None:
        return
    try:
        _server = ThreadingHTTPServer(("127.0.0.1", PORT), _NoCacheHandler)
    except OSError:
        return
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


def _url(name: str) -> str:
    return f"http://localhost:{PORT}/{name}?cb={int(time.time()*1000)}"


def _set_timeout(device, config_file: str, value: str) -> None:
    """Rewrite the hide-timeout float in the configuration prefs file (app stopped)."""
    device.force_stop()
    path = f"shared_prefs/{device.package}_preferences_{config_file}.xml"
    xml = device.read_prefs(path)
    if "<map" not in xml:
        raise RuntimeError(f"prefs file {path} not initialized yet")
    entry = f'<float name="{TIMEOUT_KEY}" value="{value}" />'
    pattern = re.compile(rf'<float name="{re.escape(TIMEOUT_KEY)}" value="[^"]*" />')
    xml = pattern.sub(entry, xml) if pattern.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
    device.write_prefs(path, xml)


def _config_file(device) -> str:
    """The configuration prefs-file suffix is the orientation (portrait/landscape)."""
    return device.config()["orientation"]


def _prepare(device, page: str, value: str) -> None:
    """Stop, set the timeout, start serving, open the tunnel and load the page."""
    _ensure_server()
    _set_timeout(device, _config_file(device), value)
    device.reverse(PORT)
    _reversed[device.id] = device
    device.launch()
    time.sleep(1.0)
    device.navigate(_url(page), reset=False)


def _wait_loaded(device, title: str, timeout: float = 30.0) -> float:
    """Return t0 once the field shows the mirrored page title (loaded + toolbar visible)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if device.field_text().strip().lower() == title:
            return time.time()
        time.sleep(0.25)
    raise AssertionError(f"page did not report '{title}' (field text: {device.field_text()!r})")


def _wait_webview_focused(device, timeout: float = 10.0) -> None:
    """Wait until the web view holds input focus (the countdown is armed and can fire)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if device.webview_focused():
            return
        time.sleep(0.25)
    raise AssertionError("web view never gained input focus after load")


def _wait_toolbar_hidden(device, timeout: float) -> float | None:
    """Seconds from now until the toolbar hides (field text goes empty), or None."""
    start = time.time()
    while time.time() - start < timeout:
        if device.field_text().strip() == "":
            return time.time() - start
        time.sleep(0.2)
    return None


def _set_cursor_fade(device, value: str) -> None:
    """Rewrite the cursor fade timeout (app stopped).

    cursorFadeTimeoutMs lives in UserPreferences (@UserPrefs) - the default,
    unsuffixed prefs file - not the orientation-suffixed configuration file.
    """
    device.force_stop()
    path = f"shared_prefs/{device.package}_preferences.xml"
    xml = device.read_prefs(path)
    if "<map" not in xml:
        return  # prefs not initialized yet; the code default applies
    entry = f'<int name="{FADE_KEY}" value="{value}" />'
    pattern = re.compile(rf'<int name="{re.escape(FADE_KEY)}" value="-?\d+" />')
    xml = pattern.sub(entry, xml) if pattern.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
    device.write_prefs(path, xml)


def _cursor_overlay(device) -> bool:
    """The cursor overlay is present in the hierarchy only while cursor mode is on."""
    return device.find_node(":id/cursorOverlay") is not None


def _cursor_toggle(device) -> None:
    """Toggle cursor mode via the long-press play/pause hotkey (menu closed)."""
    device.key_longpress(keys.MEDIA_PLAY_PAUSE, wait=1.2)


def _finish(device) -> None:
    """Restore the default timeout so the device is left in a known state."""
    try:
        _set_timeout(device, _config_file(device), DEFAULT_VALUE)
    except Exception as e:  # noqa: BLE001
        print(f"  WARNING: could not reset timeout pref: {e}")


def test_toolbar_hides_after_timeout(device, ctx: dict) -> None:
    """With a 10 s timeout the tool bar hides ~10 s after the page has loaded."""
    _prepare(device, "timeout_target.html", "10")
    try:
        t0 = _wait_loaded(device, "loaded")
        hidden = _wait_toolbar_hidden(device, 17.0)
        assert hidden is not None, "tool bar never hid within 17 s of load"
        # navigate() returns a few seconds after the true load, so "10 s after load"
        # reads as ~7-8 s from t0; this window catches both under- and over-shooting.
        assert 6.5 <= hidden <= 12.0, f"tool bar hid {hidden:.2f} s after load (expected ~10 s)"
    finally:
        _finish(device)


def test_toolbar_not_starved_on_busy_page(device, ctx: dict) -> None:
    """A busy page that keeps firing tab-state callbacks must not starve the countdown.

    The theme-color flipper re-arms the countdown on every 2 s theme change. Before the
    fix this kept restarting the 5 s countdown forever, so the tool bar never hid; the fix
    arms only on the load->loaded edge, so the tool bar hides ~5 s after load regardless.
    """
    _prepare(device, "theme_flipper.html", "5")
    try:
        _wait_loaded(device, "flipper")
        # The web view must hold focus for the countdown to fire; the theme-color flips can
        # briefly perturb focus, so wait for it to settle (a fixed build keeps it held, so the
        # countdown armed at load runs to completion). On a buggy build the countdown is
        # re-armed by the spurious tab-state callbacks and never completes.
        _wait_webview_focused(device)
        hidden = _wait_toolbar_hidden(device, 13.0)
        assert hidden is not None, (
            "tool bar never hid on the busy (theme-color flipping) page - the countdown is "
            "being re-armed by spurious tab-state callbacks"
        )
        assert hidden <= 10.0, f"tool bar took {hidden:.2f} s to hide on the busy page (expected ~5 s)"
    finally:
        _finish(device)


def test_toolbar_not_reset_by_interaction(device, ctx: dict) -> None:
    """A D-pad press after load must not restart the countdown (it stays anchored at load)."""
    _prepare(device, "timeout_target.html", "10")
    try:
        t0 = _wait_loaded(device, "loaded")
        time.sleep(2.0)
        if device.field_text().strip() == "":
            raise AssertionError("tool bar already hid before the interaction - retry the test")
        device.key(keys.DPAD_CENTER, wait=0.5)
        t_press = time.time()
        hidden = _wait_toolbar_hidden(device, 17.0)
        assert hidden is not None, "tool bar never hid within 17 s of the press"
        from_load = (t_press + hidden) - t0
        # Anchored at load (~10 s after), not at the press (~2 s after load, which would
        # read as ~12 s after load under the old interaction-reset semantics).
        assert 6.5 <= from_load <= 11.5, (
            f"tool bar hid {hidden:.2f} s after the press ({from_load:.2f} s after load); "
            "expected the countdown to stay anchored at load (~10 s after load)"
        )
    finally:
        _finish(device)


def test_toolbar_rearms_on_focus_gain(device, ctx: dict) -> None:
    """After a first auto-hide, regaining web-view focus restarts the countdown."""
    _prepare(device, "timeout_target.html", "10")
    try:
        t0 = _wait_loaded(device, "loaded")
        hidden1 = _wait_toolbar_hidden(device, 17.0)
        assert hidden1 is not None, "no first auto-hide to re-arm from"
        # Re-show the tool bar (back, no history navigation / focus change)...
        device.key(keys.BACK, wait=1.5)
        if device.field_text().strip().lower() != "loaded":
            raise AssertionError(f"tool bar did not re-appear after back (field={device.field_text()!r})")
        # ...move focus onto the search field, then back onto the web view: that focus
        # gain re-arms the countdown from scratch (the behavior under test).
        device.key(keys.SEARCH, wait=1.5)
        w, h = device.screen_size()
        device.tap(w // 2, int(h * 0.30), wait=1.5)
        hidden2 = _wait_toolbar_hidden(device, 17.0)
        assert hidden2 is not None, "tool bar never hid again within 17 s of the re-arm"
        assert 7.5 <= hidden2 <= 12.0, f"tool bar hid {hidden2:.2f} s after the re-arm (expected ~10 s)"
    finally:
        _finish(device)


def test_toolbar_disabled_at_zero(device, ctx: dict) -> None:
    """A timeout of 0 disables the feature: the tool bar never auto-hides."""
    _prepare(device, "timeout_target.html", "0")
    try:
        _wait_loaded(device, "loaded")
        time.sleep(6.0)
        text = device.field_text().strip()
        assert text.lower() == "loaded", (
            f"tool bar hid despite a 0 (disabled) timeout (field={text!r})"
        )
    finally:
        _finish(device)


def test_toolbar_rehides_after_back_reshow(device, ctx: dict) -> None:
    """After an auto-hide, back re-shows the tool bar and it must auto-hide again.

    On a page without in-page history, back hits doBackAction -> showActionBar
    while the web view keeps input focus (back does not move focus). The
    countdown was consumed by the first hide, so only a re-arm at re-show time
    can make the tool bar hide again. Before the fix showActionBar() only
    restored visibility, so the tool bar stayed stuck.
    """
    _prepare(device, "timeout_target.html", "10")
    try:
        _wait_loaded(device, "loaded")
        hidden1 = _wait_toolbar_hidden(device, 17.0)
        assert hidden1 is not None, "no first auto-hide to re-show from"
        device.key(keys.BACK, wait=1.5)
        if device.field_text().strip().lower() != "loaded":
            raise AssertionError(f"tool bar did not re-appear after back (field={device.field_text()!r})")
        hidden2 = _wait_toolbar_hidden(device, 17.0)
        assert hidden2 is not None, (
            "tool bar stayed visible after back re-showed it - the hide countdown was "
            "consumed by the first hide and never re-armed when the tool bar came back"
        )
        assert hidden2 <= 13.0, f"tool bar took {hidden2:.2f} s to hide after the re-show (expected ~10 s)"
    finally:
        _finish(device)


def test_cursor_toolbar_rehides_after_back_reshow(device, ctx: dict) -> None:
    """Cursor mode on the TV: back-reshow after an auto-hide must auto-hide again.

    The cursor overlay is not focusable and the D-pad drives the cursor (not
    focus navigation), so the web view holds input focus the whole time - the
    same conditions as test_toolbar_rehides_after_back_reshow, but with cursor
    mode active, which is how the stuck case is hit in the wild.
    """
    if not device.is_leanback():
        ctx["notes"].append("cursor re-hide test skipped (device is not leanback)")
        return
    # Fade disabled so the overlay (and thus cursor mode) is detectable at any
    # moment and the toggle state can be asserted reliably.
    _set_cursor_fade(device, "0")
    _prepare(device, "timeout_target.html", "10")
    try:
        _wait_loaded(device, "loaded")
        _cursor_toggle(device)
        if not _cursor_overlay(device):
            raise AssertionError("cursor mode could not be enabled for the test")
        hidden1 = _wait_toolbar_hidden(device, 17.0)
        assert hidden1 is not None, "no first auto-hide in cursor mode"
        device.key(keys.BACK, wait=1.5)
        if device.field_text().strip().lower() != "loaded":
            raise AssertionError(f"tool bar did not re-appear after back (field={device.field_text()!r})")
        hidden2 = _wait_toolbar_hidden(device, 17.0)
        assert hidden2 is not None, (
            "cursor mode: tool bar stayed visible after back re-showed it"
        )
        assert hidden2 <= 13.0, f"cursor mode: tool bar took {hidden2:.2f} s to hide after the re-show"
    finally:
        if _cursor_overlay(device):
            _cursor_toggle(device)
        _set_cursor_fade(device, DEFAULT_FADE)
        _finish(device)


FEATURE_GROUPS = {
    "toolbar-hide": [
        test_toolbar_hides_after_timeout,
        test_toolbar_not_starved_on_busy_page,
        test_toolbar_not_reset_by_interaction,
        test_toolbar_rearms_on_focus_gain,
        test_toolbar_rehides_after_back_reshow,
        test_cursor_toolbar_rehides_after_back_reshow,
        test_toolbar_disabled_at_zero,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_toolbar_hides_after_timeout": (
        "With a 10 s timeout the tool bar auto-hides ~10 s after the page finishes loading"
    ),
    "test_toolbar_not_starved_on_busy_page": (
        "A busy page that keeps firing tab-state callbacks (theme-color changes) does not "
        "starve the countdown - the tool bar still hides ~timeout s after load"
    ),
    "test_toolbar_not_reset_by_interaction": (
        "A D-pad press after load does not reset the countdown (it stays anchored at load)"
    ),
    "test_toolbar_rearms_on_focus_gain": (
        "After a first auto-hide, regaining web-view input focus restarts the countdown"
    ),
    "test_toolbar_rehides_after_back_reshow": (
        "After an auto-hide, back re-shows the tool bar (web view keeps focus) and it "
        "auto-hides again - the countdown is re-armed at re-show"
    ),
    "test_cursor_toolbar_rehides_after_back_reshow": (
        "In cursor mode (TV) the same back-reshow cycle auto-hides again - the "
        "non-focusable cursor overlay must not prevent the re-arm"
    ),
    "test_toolbar_disabled_at_zero": (
        "A timeout of 0 disables the feature (the tool bar never auto-hides)"
    ),
}
