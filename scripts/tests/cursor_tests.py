"""Android TV cursor-mode UI tests, driven through the framework Device API.

These verify the fulguris.cursor component end-to-end on a real device: the long-press hotkey
toggle, D-pad movement, edge scrolling, mouse hover + click dispatch into the WebView, and the
conditional "Cursor" main-menu item. Run via run.py (see below).

The target web page (assets/cursor_target.html) reports what the cursor does to it by changing
its document.title, which Fulguris mirrors into the toolbar label — so we can observe hover,
click coordinates and scrolling over adb without a screenshot (screencap is black on the RPi TV
box). The page is served from the host over an `adb reverse` tunnel, because Fulguris blocks
file:// URLs.

Tests take a :class:`framework.Device`; the cursor suite is Android-only, so it uses the
Android-specific extras (`device.reverse`, `device.read_prefs`/`write_prefs`) where needed.

## Feature groups

Tests are grouped so a subset relevant to one feature can be run on its own:

    python scripts/tests/run.py --all --group cursor-toggle     # hotkey on/off, exit focus
    python scripts/tests/run.py --all --group cursor-movement   # D-pad movement, edge scroll
    python scripts/tests/run.py --all --group cursor-click      # hover + click dispatch
    python scripts/tests/run.py --all --group cursor-menu       # menu item visibility/toggle
    python scripts/tests/run.py --all --group cursor-youtube    # YouTube-style scrubber seek

    python scripts/tests/run.py --all --test cursor             # every cursor test (name match)
    python scripts/tests/run.py --all --group cursor            # every cursor test (all groups)
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
from framework import keys

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
PORT = 8899

_server: ThreadingHTTPServer | None = None
_reversed: dict = {}  # device.id -> device, for reverse-tunnel teardown at exit


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
    for device in list(_reversed.values()):
        try:
            device.reverse_remove(PORT)
        except Exception:  # noqa: BLE001
            pass
    if _server is not None:
        _server.shutdown()


def _ensure_reverse(device) -> None:
    """Point the device's localhost:PORT at the host server via an adb reverse tunnel."""
    if device.id in _reversed:
        return
    device.reverse(PORT)
    _reversed[device.id] = device


# Cursor speed/acceleration/fade are user settings that persist on the device. Reset them to known
# values once per device so movement/fade tests are deterministic regardless of what the user (or a
# previous run) left them at.
_prefs_reset: set = set()  # device ids already reset
_CURSOR_TEST_PREFS = {
    "pref_key_cursor_speed": 40,
    "pref_key_cursor_acceleration": 20,
    "pref_key_cursor_fade_timeout": 3000,
}


def _prefs_path(package: str) -> str:
    return f"shared_prefs/{package}_preferences.xml"


def _reset_cursor_prefs(device) -> None:
    """Force the cursor speed/accel/fade prefs to known test values (host-side rewrite of the XML)."""
    if device.id in _prefs_reset:
        return
    _prefs_reset.add(device.id)
    # App must be stopped so it doesn't overwrite the file on exit and reloads our values next launch.
    device.force_stop()
    xml = device.read_prefs(_prefs_path(device.package))
    if "<map" not in xml:
        return  # prefs not initialized yet; the code defaults will apply
    for key, val in _CURSOR_TEST_PREFS.items():
        entry = f'<int name="{key}" value="{val}" />'
        pat = re.compile(rf'<int name="{re.escape(key)}" value="-?\d+" />')
        xml = pat.sub(entry, xml) if pat.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
    device.write_prefs(_prefs_path(device.package), xml)


def _load_page(device, page: str) -> None:
    """Serve and open one of the assets pages, leaving cursor mode OFF."""
    _ensure_server()
    _ensure_reverse(device)
    _reset_cursor_prefs(device)
    # Make sure we start from a clean, cursor-off state.
    if _overlay_present(device):
        _toggle(device)
    # Cache-bust so a stale copy is never used even if no-store were ignored.
    url = f"http://localhost:{PORT}/{page}?cb={int(time.time() * 1000)}"
    device.navigate(url, reset=True)


def _load_target(device) -> None:
    """Serve and open the cursor target page (hover/click/scroll reporting)."""
    _load_page(device, "cursor_target.html")


# --- Cursor helpers --------------------------------------------------------


def _overlay_present(device) -> bool:
    """The cursor overlay view is only laid out (present in the hierarchy) while cursor mode is on."""
    return device.find_node(":id/cursorOverlay") is not None


def _toggle(device) -> None:
    """Toggle cursor mode via the long-press hotkey (play/pause) and let the fade settle."""
    device.key_longpress(keys.MEDIA_PLAY_PAUSE, wait=1.0)


def _title(device) -> str:
    return device.field_text()


def _click_coords(device) -> tuple[int, int] | None:
    """Press select and read back the click coordinates the page reports, or None if no click."""
    device.key(keys.DPAD_CENTER, wait=0.8)
    m = re.fullmatch(r"(\d+),(\d+)", _title(device).strip())
    return (int(m.group(1)), int(m.group(2))) if m else None


def _focused_resource_id(device) -> str:
    for n in device.nodes():
        if n.focused:
            return n.resource_id
    return ""


# ===========================================================================
# Feature: cursor toggle hotkey
# ===========================================================================


def test_cursor_toggle_hotkey_shows_and_hides_overlay(device, ctx: dict) -> None:
    _load_target(device)
    assert not _overlay_present(device), "cursor overlay should be hidden before enabling"
    _toggle(device)
    assert _overlay_present(device), "long-press hotkey should turn cursor mode on (overlay shown)"
    _toggle(device)
    assert not _overlay_present(device), "long-press hotkey should turn cursor mode off (overlay hidden)"


def test_cursor_toggle_exit_focuses_menu_button(device, ctx: dict) -> None:
    _load_target(device)
    _toggle(device)
    assert _overlay_present(device), "cursor mode should be on"
    _toggle(device)
    assert not _overlay_present(device), "cursor mode should be off"
    # Exiting cursor mode moves focus to the toolbar more/menu button for predictable D-pad nav.
    assert _focused_resource_id(device).endswith(":id/button_more"), \
        f"exiting cursor mode should focus the menu button, focus was '{_focused_resource_id(device)}'"


# ===========================================================================
# Feature: cursor movement
# ===========================================================================


def test_cursor_movement_dpad_right_moves_right(device, ctx: dict) -> None:
    _load_target(device)
    _toggle(device)
    center = _click_coords(device)
    assert center is not None, "click at center should report coordinates"
    for _ in range(8):
        device.key(keys.DPAD_RIGHT, wait=0.15)
    moved = _click_coords(device)
    assert moved is not None, "click after moving should report coordinates"
    assert moved[0] > center[0] + 10, f"D-pad right should increase X: {center} -> {moved}"
    assert abs(moved[1] - center[1]) <= 10, f"D-pad right should not change Y much: {center} -> {moved}"
    _toggle(device)


def test_cursor_movement_dpad_down_moves_down(device, ctx: dict) -> None:
    _load_target(device)
    _toggle(device)
    center = _click_coords(device)
    assert center is not None, "click at center should report coordinates"
    for _ in range(4):
        device.key(keys.DPAD_DOWN, wait=0.15)
    moved = _click_coords(device)
    assert moved is not None, "click after moving should report coordinates"
    assert moved[1] > center[1] + 10, f"D-pad down should increase Y: {center} -> {moved}"
    _toggle(device)


def test_cursor_movement_edge_scrolls_page(device, ctx: dict) -> None:
    _load_target(device)
    _toggle(device)
    # Drive to the bottom edge and keep pushing; once clamped, further pushes scroll the page via a
    # synthetic mouse wheel at the cursor point. Enough presses to traverse from center to the edge
    # even at a modest speed and with the odd dropped key event on a slow network device.
    for _ in range(140):
        device.key(keys.DPAD_DOWN, wait=0.03)
    title = _title(device)
    m = re.fullmatch(r"sy(\d+)", title.strip())
    assert m and int(m.group(1)) > 0, f"pushing past the bottom edge should scroll the page, title was '{title}'"
    _toggle(device)


# ===========================================================================
# Feature: cursor fade
# ===========================================================================


def test_cursor_fade_hides_then_wakes(device, ctx: dict) -> None:
    _load_target(device)
    _toggle(device)  # cursor mode on; fade timeout was reset to 3000ms
    assert _overlay_present(device), "cursor should be visible right after enabling"
    time.sleep(4.5)  # longer than the fade timeout + fade animation
    assert not _overlay_present(device), "cursor should fade out after the inactivity timeout"
    device.key(keys.DPAD_RIGHT, wait=0.6)  # any movement wakes it
    assert _overlay_present(device), "moving the cursor should fade it back in"
    _toggle(device)


# ===========================================================================
# Feature: cursor click dispatch
# ===========================================================================


def test_cursor_click_hover_fires_mouseover(device, ctx: dict) -> None:
    _load_target(device)
    assert _title(device) == "start", f"page should start with title 'start', was '{_title(device)}'"
    _toggle(device)  # enabling centers the cursor and dispatches an initial mouse hover
    assert _title(device) == "hover", \
        f"enabling cursor should fire a mouse hover on the page, title was '{_title(device)}'"
    _toggle(device)


def test_cursor_click_activates_under_cursor(device, ctx: dict) -> None:
    _load_target(device)
    _toggle(device)
    coords = _click_coords(device)
    assert coords is not None, \
        f"select press should dispatch a click the page receives, title was '{_title(device)}'"
    _toggle(device)


def test_cursor_click_drag_target_seeks(device, ctx: dict) -> None:
    # A cursor click must register on drag-only targets like YouTube's scrub bar (which need a real
    # pointerdown -> pointermove -> pointerup, not a bare tap). The bar spans the vertical middle, so
    # the freshly-centered cursor lands on it.
    _load_page(device, "scrub_target.html")
    _toggle(device)
    device.key(keys.DPAD_CENTER, wait=0.8)
    assert _title(device).startswith("seek@"), \
        f"a cursor click on a drag-only scrub bar should seek, title was '{_title(device)}'"
    _toggle(device)


# ===========================================================================
# Feature: cursor menu integration / visibility
# ===========================================================================


def _open_main_menu(device) -> None:
    n = device.find_node(":id/button_more")
    assert n and n.bounds, "toolbar more button not found"
    x1, y1, x2, y2 = n.bounds
    device.tap((x1 + x2) // 2, (y1 + y2) // 2, wait=1.0)


def test_cursor_menu_item_visible_on_leanback(device, ctx: dict) -> None:
    _load_target(device)
    if not device.is_leanback():
        ctx["notes"].append("cursor menu visibility test skipped (device is not leanback)")
        return
    _open_main_menu(device)
    present = device.find_node(":id/menuItemCursor") is not None
    device.key(keys.BACK, wait=0.6)  # close menu
    assert present, "the Cursor menu item should be visible in the main menu on Android TV"


def test_cursor_menu_item_toggles_mode(device, ctx: dict) -> None:
    _load_target(device)
    if not device.is_leanback():
        ctx["notes"].append("cursor menu toggle test skipped (device is not leanback)")
        return
    assert not _overlay_present(device), "cursor mode should start off"
    _open_main_menu(device)
    item = device.find_node(":id/menuItemCursor")
    assert item and item.bounds, "Cursor menu item not found in the main menu"
    x1, y1, x2, y2 = item.bounds
    device.tap((x1 + x2) // 2, (y1 + y2) // 2, wait=1.0)
    assert _overlay_present(device), "tapping the Cursor menu item should turn cursor mode on"
    _toggle(device)  # leave it off


# ===========================================================================
# Feature: cursor in HTML5 fullscreen (e.g. YouTube fullscreen)
# ===========================================================================


def test_cursor_fullscreen_click_reaches_custom_view(device, ctx: dict) -> None:
    _load_page(device, "fullscreen_target.html")
    # A tap provides the user gesture HTML5 requestFullscreen needs; this fires onShowCustomView.
    w, h = device.screen_size()
    device.tap(w // 2, h // 2, wait=1.5)
    assert _title(device) == "fs-on", f"tapping should enter fullscreen, title was '{_title(device)}'"
    _toggle(device)  # turn the cursor on while fullscreen
    assert _overlay_present(device), "the cursor overlay should be visible over the fullscreen view"
    for _ in range(3):
        device.key(keys.DPAD_RIGHT, wait=0.15)
    device.key(keys.DPAD_CENTER, wait=0.8)
    assert _title(device).startswith("fsclick@"), \
        f"a click in fullscreen must reach the fullscreen view, title was '{_title(device)}'"
    _toggle(device)
    device.key(keys.BACK, wait=1.0)  # leave fullscreen


# ===========================================================================
# Feature: hardware media keys drive the page video
# ===========================================================================


def test_cursor_media_play_pause(device, ctx: dict) -> None:
    _load_page(device, "media_target.html")
    for _ in range(12):
        if _title(device) == "playing":
            break
        time.sleep(0.5)
    assert _title(device) == "playing", f"the test video should autoplay, title was '{_title(device)}'"
    device.key(keys.MEDIA_PLAY_PAUSE, wait=1.2)
    assert _title(device) == "paused", f"media play/pause should pause the video, title was '{_title(device)}'"
    device.key(keys.MEDIA_PLAY_PAUSE, wait=1.2)
    assert _title(device) == "playing", f"media play/pause should resume the video, title was '{_title(device)}'"


# ===========================================================================
# Feature: media keys act as a mouse wheel while the cursor is on screen
# ===========================================================================


def test_cursor_wheel_ff_rewind_scrolls(device, ctx: dict) -> None:
    # cursor_target.html is 220vh and reports window.scrollY as 'sy<n>' on scroll.
    _load_target(device)
    _toggle(device)  # cursor on; centered
    # In cursor mode fast-forward is a mouse wheel scroll DOWN at the cursor.
    device.key(keys.MEDIA_FAST_FORWARD, wait=0.9)
    t1 = _title(device)
    m1 = re.fullmatch(r"sy(\d+)", t1.strip())
    assert m1 and int(m1.group(1)) > 0, f"fast-forward in cursor mode should wheel-scroll down, title was '{t1}'"
    down = int(m1.group(1))
    # Rewind is a mouse wheel scroll UP.
    device.key(keys.MEDIA_REWIND, wait=0.9)
    device.key(keys.MEDIA_REWIND, wait=0.9)
    t2 = _title(device)
    m2 = re.fullmatch(r"sy(\d+)", t2.strip())
    assert m2 and int(m2.group(1)) < down, f"rewind in cursor mode should wheel-scroll back up, was '{t1}' now '{t2}'"
    _toggle(device)


def test_cursor_youtube_scrubber_seek(device, ctx: dict) -> None:
    # yt_scrub.html faithfully models YouTube's player chrome: controls that
    # auto-hide and wake on hover (pointermove/mousemove), plus a progress bar
    # that seeks on pointerdown at clientX -- but only while controls are shown.
    # This exercises the real-world path: the cursor's hover must keep the
    # controls alive AND its click must land a seeking pointerdown on the bar.
    _load_page(device, "yt_scrub.html")
    _toggle(device)  # cursor on; the centred hover should wake the controls
    assert _title(device).strip() == "ctrl-shown", \
        f"cursor hover should wake the auto-hiding player controls, title was '{_title(device)}'"
    # Drive the cursor down into the bottom progress bar, clicking as we descend.
    # The first click that lands on the bar (controls still shown thanks to the
    # hover from each move) reports seek@<pct>. Clicking on the way in lands near
    # the top of the bar, so this is DPI-independent and avoids the very-bottom
    # edge. bar-miss would mean the controls had hidden (hover not keeping alive).
    title = ""
    for _ in range(12):
        for _ in range(5):
            device.key(keys.DPAD_DOWN, wait=0.03)
        device.key(keys.DPAD_CENTER, wait=0.6)
        title = _title(device).strip()
        if title.startswith("seek@") or title == "bar-miss":
            break
    assert title.startswith("seek@"), \
        f"cursor click on the YouTube-style scrubber should seek, last title was '{title}'"
    _toggle(device)


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
    "cursor-wheel": [
        test_cursor_wheel_ff_rewind_scrolls,
    ],
    "cursor-youtube": [
        test_cursor_youtube_scrubber_seek,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_cursor_toggle_hotkey_shows_and_hides_overlay": "Long-press play/pause toggles the cursor overlay on and off",
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
    "test_cursor_wheel_ff_rewind_scrolls": "In cursor mode fast-forward/rewind wheel-scroll the page down/up at the cursor",
    "test_cursor_youtube_scrubber_seek": "A cursor click seeks a YouTube-style auto-hiding scrubber (hover keeps controls alive, click seeks)",
}
