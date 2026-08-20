"""Android TV cursor-mode UI tests, driven over adb.

These verify the fulguris.cursor component end-to-end on a real device: the long-press hotkey
toggle, D-pad movement, edge scrolling, mouse hover + click dispatch into the WebView, and the
conditional "Cursor" main-menu item. Run via run.py (see below).

The target web page (assets/cursor_target.html) reports what the cursor does to it by changing
its document.title, which Fulguris mirrors into the toolbar label — so we can observe hover,
click coordinates and scrolling over adb without a screenshot (screencap is black on the RPi TV
box). The page is served from the host over an `adb reverse` tunnel, because Fulguris blocks
file:// URLs.

## Feature groups

Tests are grouped so a subset relevant to one feature can be run on its own:

    python scripts/tests/run.py --all --group cursor-toggle     # hotkey on/off, exit focus
    python scripts/tests/run.py --all --group cursor-movement   # D-pad movement, edge scroll
    python scripts/tests/run.py --all --group cursor-click      # hover + click dispatch
    python scripts/tests/run.py --all --group cursor-menu       # menu item visibility/toggle

    python scripts/tests/run.py --all --test cursor             # every cursor test (name match)
    python scripts/tests/run.py --all --group cursor            # every cursor test (all groups)
"""
from __future__ import annotations

import atexit
import os
import re
import subprocess
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
PORT = 8899

_server: ThreadingHTTPServer | None = None
_reversed: set[str] = set()


class _NoCacheHandler(SimpleHTTPRequestHandler):
    """Serve the assets dir and forbid caching, so each navigation fetches a fresh page.

    (The WebView otherwise caches cursor_target.html and revalidates with 304, which would serve
    a stale copy after the page is edited.)
    """

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
    """Start a host HTTP server serving the assets dir once, in a daemon thread."""
    global _server
    if _server is not None:
        return
    _server = ThreadingHTTPServer(("127.0.0.1", PORT), _NoCacheHandler)
    threading.Thread(target=_server.serve_forever, daemon=True).start()
    atexit.register(_teardown)


def _teardown() -> None:
    for serial in list(_reversed):
        try:
            adb._adb(serial, ["reverse", "--remove", f"tcp:{PORT}"])
        except Exception:  # noqa: BLE001
            pass
    if _server is not None:
        _server.shutdown()


def _ensure_reverse(serial: str) -> None:
    """Point the device's localhost:PORT at the host server via an adb reverse tunnel."""
    if serial in _reversed:
        return
    adb._adb(serial, ["reverse", f"tcp:{PORT}", f"tcp:{PORT}"])
    _reversed.add(serial)


# Cursor speed/acceleration/fade are user settings that persist on the device. Reset them to known
# values once per device so movement/fade tests are deterministic regardless of what the user (or a
# previous run) left them at.
_prefs_reset: set[str] = set()
_CURSOR_TEST_PREFS = {
    "pref_key_cursor_speed": 40,
    "pref_key_cursor_acceleration": 20,
    "pref_key_cursor_fade_timeout": 3000,
}


def _prefs_path(package: str) -> str:
    return f"shared_prefs/{package}_preferences.xml"


def _read_prefs(serial: str, package: str) -> str:
    r = subprocess.run(
        ["adb", "-s", serial, "shell", "run-as", package, "cat", _prefs_path(package)],
        capture_output=True, encoding="utf-8", errors="replace",
    )
    return r.stdout or ""


def _write_prefs(serial: str, package: str, content: str) -> None:
    # Write via a temp file pushed to /data/local/tmp (world-readable) then `run-as cp` into the
    # app's shared_prefs — more reliable than piping stdin through `adb shell run-as sh -c`.
    import tempfile
    fd, local = tempfile.mkstemp(suffix=".xml")
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as f:
            f.write(content)
        dev_tmp = "/data/local/tmp/cursor_prefs.xml"
        subprocess.run(["adb", "-s", serial, "push", local, dev_tmp], capture_output=True)
        subprocess.run(["adb", "-s", serial, "shell", "run-as", package, "cp", dev_tmp, _prefs_path(package)],
                       capture_output=True)
    finally:
        os.remove(local)


def _reset_cursor_prefs(serial: str, package: str) -> None:
    """Force the cursor speed/accel/fade prefs to known test values (host-side rewrite of the XML)."""
    if serial in _prefs_reset:
        return
    _prefs_reset.add(serial)
    # App must be stopped so it doesn't overwrite the file on exit and reloads our values next launch.
    adb.force_stop(serial, package)
    xml = _read_prefs(serial, package)
    if "<map" not in xml:
        return  # prefs not initialized yet; the code defaults will apply
    for key, val in _CURSOR_TEST_PREFS.items():
        entry = f'<int name="{key}" value="{val}" />'
        pat = re.compile(rf'<int name="{re.escape(key)}" value="-?\d+" />')
        xml = pat.sub(entry, xml) if pat.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
    _write_prefs(serial, package, xml)


def _load_page(serial: str, package: str, page: str) -> None:
    """Serve and open one of the assets pages, leaving cursor mode OFF."""
    _ensure_server()
    _ensure_reverse(serial)
    _reset_cursor_prefs(serial, package)
    # Make sure we start from a clean, cursor-off state.
    if _overlay_present(serial):
        _toggle(serial)
    # Cache-bust so a stale copy is never used even if no-store were ignored.
    url = f"http://localhost:{PORT}/{page}?cb={int(time.time() * 1000)}"
    adb.navigate(serial, package, url, reset=True)


def _load_target(serial: str, package: str) -> None:
    """Serve and open the cursor target page (hover/click/scroll reporting)."""
    _load_page(serial, package, "cursor_target.html")


# --- Cursor helpers --------------------------------------------------------


def _overlay_present(serial: str) -> bool:
    """The cursor overlay view is only laid out (present in the hierarchy) while cursor mode is on."""
    return adb.find_node(serial, ":id/cursorOverlay") is not None


def _toggle(serial: str) -> None:
    """Toggle cursor mode via the long-press hotkey and let the fade settle."""
    adb.key_longpress(serial, adb.KEY_MEDIA_FAST_FORWARD, wait=1.0)


def _title(serial: str) -> str:
    return adb.field_text(serial)


def _click_coords(serial: str) -> tuple[int, int] | None:
    """Press select and read back the click coordinates the page reports, or None if no click."""
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=0.8)
    m = re.fullmatch(r"(\d+),(\d+)", _title(serial).strip())
    return (int(m.group(1)), int(m.group(2))) if m else None


def _focused_resource_id(serial: str) -> str:
    for n in adb.nodes(serial):
        if n.focused:
            return n.resource_id
    return ""


# ===========================================================================
# Feature: cursor toggle / hotkey
# ===========================================================================


def test_cursor_toggle_hotkey_shows_and_hides_overlay(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    assert not _overlay_present(serial), "cursor overlay should be hidden before enabling"
    _toggle(serial)
    assert _overlay_present(serial), "long-press hotkey should turn cursor mode on (overlay shown)"
    _toggle(serial)
    assert not _overlay_present(serial), "long-press hotkey should turn cursor mode off (overlay hidden)"


def test_cursor_toggle_exit_focuses_menu_button(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    _toggle(serial)
    assert _overlay_present(serial), "cursor mode should be on"
    _toggle(serial)
    assert not _overlay_present(serial), "cursor mode should be off"
    # Exiting cursor mode moves focus to the toolbar more/menu button for predictable D-pad nav.
    assert _focused_resource_id(serial).endswith(":id/button_more"), \
        f"exiting cursor mode should focus the menu button, focus was '{_focused_resource_id(serial)}'"


# ===========================================================================
# Feature: cursor movement
# ===========================================================================


def test_cursor_movement_dpad_right_moves_right(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    _toggle(serial)
    center = _click_coords(serial)
    assert center is not None, "click at center should report coordinates"
    for _ in range(8):
        adb.key(serial, adb.KEY_DPAD_RIGHT, wait=0.15)
    moved = _click_coords(serial)
    assert moved is not None, "click after moving should report coordinates"
    assert moved[0] > center[0] + 10, f"D-pad right should increase X: {center} -> {moved}"
    assert abs(moved[1] - center[1]) <= 10, f"D-pad right should not change Y much: {center} -> {moved}"
    _toggle(serial)


def test_cursor_movement_dpad_down_moves_down(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    _toggle(serial)
    center = _click_coords(serial)
    assert center is not None, "click at center should report coordinates"
    for _ in range(4):
        adb.key(serial, adb.KEY_DPAD_DOWN, wait=0.15)
    moved = _click_coords(serial)
    assert moved is not None, "click after moving should report coordinates"
    assert moved[1] > center[1] + 10, f"D-pad down should increase Y: {center} -> {moved}"
    _toggle(serial)


def test_cursor_movement_edge_scrolls_page(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    _toggle(serial)
    # Drive to the bottom edge and keep pushing; once clamped, further pushes scroll the page via a
    # synthetic mouse wheel at the cursor point. Enough presses to traverse from center to the edge
    # even at a modest speed and with the odd dropped key event on a slow network device.
    for _ in range(140):
        adb.key(serial, adb.KEY_DPAD_DOWN, wait=0.03)
    title = _title(serial)
    m = re.fullmatch(r"sy(\d+)", title.strip())
    assert m and int(m.group(1)) > 0, f"pushing past the bottom edge should scroll the page, title was '{title}'"
    _toggle(serial)


# ===========================================================================
# Feature: cursor fade
# ===========================================================================


def test_cursor_fade_hides_then_wakes(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    _toggle(serial)  # cursor mode on; fade timeout was reset to 3000ms
    assert _overlay_present(serial), "cursor should be visible right after enabling"
    time.sleep(4.5)  # longer than the fade timeout + fade animation
    assert not _overlay_present(serial), "cursor should fade out after the inactivity timeout"
    adb.key(serial, adb.KEY_DPAD_RIGHT, wait=0.6)  # any movement wakes it
    assert _overlay_present(serial), "moving the cursor should fade it back in"
    _toggle(serial)


# ===========================================================================
# Feature: cursor click dispatch
# ===========================================================================


def test_cursor_click_hover_fires_mouseover(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    assert _title(serial) == "start", f"page should start with title 'start', was '{_title(serial)}'"
    _toggle(serial)  # enabling centers the cursor and dispatches an initial mouse hover
    assert _title(serial) == "hover", \
        f"enabling cursor should fire a mouse hover on the page, title was '{_title(serial)}'"
    _toggle(serial)


def test_cursor_click_activates_under_cursor(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    _toggle(serial)
    coords = _click_coords(serial)
    assert coords is not None, \
        f"select press should dispatch a click the page receives, title was '{_title(serial)}'"
    _toggle(serial)


def test_cursor_click_drag_target_seeks(serial: str, package: str, ctx: dict) -> None:
    # A cursor click must register on drag-only targets like YouTube's scrub bar (which need a real
    # pointerdown -> pointermove -> pointerup, not a bare tap). The bar spans the vertical middle, so
    # the freshly-centered cursor lands on it.
    _load_page(serial, package, "scrub_target.html")
    _toggle(serial)
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=0.8)
    assert _title(serial).startswith("seek@"), \
        f"a cursor click on a drag-only scrub bar should seek, title was '{_title(serial)}'"
    _toggle(serial)


# ===========================================================================
# Feature: cursor menu integration / visibility
# ===========================================================================


def _open_main_menu(serial: str) -> None:
    n = adb.find_node(serial, ":id/button_more")
    assert n and n.bounds, "toolbar more button not found"
    x1, y1, x2, y2 = n.bounds
    adb.tap(serial, (x1 + x2) // 2, (y1 + y2) // 2, wait=1.0)


def test_cursor_menu_item_visible_on_leanback(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    if not adb.is_leanback(serial):
        ctx["notes"].append("cursor menu visibility test skipped (device is not leanback)")
        return
    _open_main_menu(serial)
    present = adb.find_node(serial, ":id/menuItemCursor") is not None
    adb.key(serial, adb.KEY_BACK, wait=0.6)  # close menu
    assert present, "the Cursor menu item should be visible in the main menu on Android TV"


def test_cursor_menu_item_toggles_mode(serial: str, package: str, ctx: dict) -> None:
    _load_target(serial, package)
    if not adb.is_leanback(serial):
        ctx["notes"].append("cursor menu toggle test skipped (device is not leanback)")
        return
    assert not _overlay_present(serial), "cursor mode should start off"
    _open_main_menu(serial)
    item = adb.find_node(serial, ":id/menuItemCursor")
    assert item and item.bounds, "Cursor menu item not found in the main menu"
    x1, y1, x2, y2 = item.bounds
    adb.tap(serial, (x1 + x2) // 2, (y1 + y2) // 2, wait=1.0)
    assert _overlay_present(serial), "tapping the Cursor menu item should turn cursor mode on"
    _toggle(serial)  # leave it off


# ===========================================================================
# Feature: cursor in HTML5 fullscreen (e.g. YouTube fullscreen)
# ===========================================================================


def test_cursor_fullscreen_click_reaches_custom_view(serial: str, package: str, ctx: dict) -> None:
    _load_page(serial, package, "fullscreen_target.html")
    # A tap provides the user gesture HTML5 requestFullscreen needs; this fires onShowCustomView.
    w, h = adb.screen_size(serial)
    adb.tap(serial, w // 2, h // 2, wait=1.5)
    assert _title(serial) == "fs-on", f"tapping should enter fullscreen, title was '{_title(serial)}'"
    _toggle(serial)  # turn the cursor on while fullscreen
    assert _overlay_present(serial), "the cursor overlay should be visible over the fullscreen view"
    for _ in range(3):
        adb.key(serial, adb.KEY_DPAD_RIGHT, wait=0.15)
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=0.8)
    assert _title(serial).startswith("fsclick@"), \
        f"a click in fullscreen must reach the fullscreen view, title was '{_title(serial)}'"
    _toggle(serial)
    adb.key(serial, adb.KEY_BACK, wait=1.0)  # leave fullscreen


# ===========================================================================
# Feature: hardware media keys drive the page video
# ===========================================================================


def test_cursor_media_play_pause(serial: str, package: str, ctx: dict) -> None:
    _load_page(serial, package, "media_target.html")
    for _ in range(12):
        if _title(serial) == "playing":
            break
        time.sleep(0.5)
    assert _title(serial) == "playing", f"the test video should autoplay, title was '{_title(serial)}'"
    adb.key(serial, adb.KEY_MEDIA_PLAY_PAUSE, wait=1.2)
    assert _title(serial) == "paused", f"media play/pause should pause the video, title was '{_title(serial)}'"
    adb.key(serial, adb.KEY_MEDIA_PLAY_PAUSE, wait=1.2)
    assert _title(serial) == "playing", f"media play/pause should resume the video, title was '{_title(serial)}'"


# ===========================================================================
# Registration
# ===========================================================================

FEATURE_GROUPS = {
    "cursor-toggle": [
        test_cursor_toggle_hotkey_shows_and_hides_overlay,
        test_cursor_toggle_exit_focuses_menu_button,
    ],
    "cursor-movement": [
        test_cursor_movement_dpad_right_moves_right,
        test_cursor_movement_dpad_down_moves_down,
        test_cursor_movement_edge_scrolls_page,
    ],
    "cursor-fade": [
        test_cursor_fade_hides_then_wakes,
    ],
    "cursor-click": [
        test_cursor_click_hover_fires_mouseover,
        test_cursor_click_activates_under_cursor,
        test_cursor_click_drag_target_seeks,
    ],
    "cursor-menu": [
        test_cursor_menu_item_visible_on_leanback,
        test_cursor_menu_item_toggles_mode,
    ],
    "cursor-fullscreen": [
        test_cursor_fullscreen_click_reaches_custom_view,
    ],
    "cursor-media": [
        test_cursor_media_play_pause,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_cursor_toggle_hotkey_shows_and_hides_overlay": "Long-press fast-forward toggles the cursor overlay on and off",
    "test_cursor_toggle_exit_focuses_menu_button": "Exiting cursor mode moves focus to the toolbar menu button",
    "test_cursor_movement_dpad_right_moves_right": "D-pad right moves the cursor right (click X increases)",
    "test_cursor_movement_dpad_down_moves_down": "D-pad down moves the cursor down (click Y increases)",
    "test_cursor_movement_edge_scrolls_page": "Pushing past the bottom edge scrolls the page",
    "test_cursor_fade_hides_then_wakes": "The cursor fades out after the inactivity timeout and wakes on movement",
    "test_cursor_click_hover_fires_mouseover": "Enabling the cursor fires a mouse hover on the page",
    "test_cursor_click_activates_under_cursor": "Select press dispatches a click the page receives at the cursor",
    "test_cursor_click_drag_target_seeks": "A cursor click seeks a drag-only scrub bar (down/move/up), like YouTube's timeline",
    "test_cursor_menu_item_visible_on_leanback": "The Cursor main-menu item is shown on Android TV",
    "test_cursor_menu_item_toggles_mode": "Tapping the Cursor menu item turns cursor mode on",
    "test_cursor_fullscreen_click_reaches_custom_view": "In HTML5 fullscreen the cursor is visible and its click reaches the fullscreen view",
    "test_cursor_media_play_pause": "The media play/pause key pauses and resumes the page video",
}
