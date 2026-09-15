"""Android TV cursor UI tests, driven through the framework Device API.

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
    python scripts/tests/run.py --all --group cursor-context    # action-key long press opens the context menu

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
# previous run) left them at. Stored as floats (the x.SliderPreference persists floats); fade is in
# seconds.
_prefs_reset: set = set()  # device ids already reset
# action_hold is reset too: the hesitant-press test needs it > 0.6 s (so a 600 ms press is a
# click, not the context-menu long press) while the context-menu tests hold 1.5 s (still a long
# press). 1.0 s (the default) satisfies both; a leftover 0.5 s would make the hesitant press a
# long press. Stored as a float (the x.SliderPreference persists floats).
_CURSOR_TEST_PREFS = {
    "pref_key_cursor_speed": 40,
    "pref_key_cursor_acceleration": 20,
    "pref_key_cursor_fade_timeout": 3,
    "pref_key_cursor_action_hold": 1,
}


def _prefs_path(package: str) -> str:
    return f"shared_prefs/{package}_preferences.xml"


def _config_prefs_path(package: str, orientation: str) -> str:
    """The configuration-scoped prefs file (Settings keeps per-orientation values here)."""
    return f"shared_prefs/{package}_preferences_{orientation}.xml"


def _set_float_pref(xml: str, key: str, value: str) -> str:
    """Replace (or append) a single float/int pref entry in a prefs XML string.

    Matches a float entry (integer OR decimal, e.g. 40 or 40.0) OR a legacy int entry so a stale
    value is replaced in place, never duplicated.
    """
    entry = f'<float name="{key}" value="{value}" />'
    pat = re.compile(rf'<(int|float) name="{re.escape(key)}" value="-?\d+(?:\.\d+)?" />')
    return pat.sub(entry, xml) if pat.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")


def _reset_cursor_prefs(device, overrides: dict | None = None) -> None:
    """Force the cursor prefs to known test values (host-side rewrite of the prefs XML).

    Also disables the "Hide tool bar after" auto-hide timeout in the *configuration-scoped* prefs
    file: every cursor test reads page state from the toolbar label, so a persisted hide timeout
    (e.g. 5 s, as was left on the SHIELD) would auto-hide the toolbar ~5 s after every page load
    and make every test read an empty label. The toolbar-hide suite self-manages its own timeout
    per test, so resetting it to 0 here cannot collide. The app is stopped for both rewrites so it
    cannot overwrite the files on exit.

    ``overrides`` lets a test re-apply with different values (e.g. fade timeout 0) on a device
    already reset with the defaults — it forces a fresh rewrite of the main prefs.
    """
    if device.id in _prefs_reset and not overrides:
        return
    _prefs_reset.add(device.id)
    # App must be stopped so it doesn't overwrite the files on exit and reloads our values next launch.
    device.force_stop()
    xml = device.read_prefs(_prefs_path(device.package))
    if "<map" in xml:
        prefs = {**_CURSOR_TEST_PREFS, **(overrides or {})}
        for key, val in prefs.items():
            xml = _set_float_pref(xml, key, str(val))
        device.write_prefs(_prefs_path(device.package), xml)
    else:
        return  # main prefs not initialized yet; the code defaults will apply
    # Configuration-scoped file: disable the toolbar auto-hide for the run's orientation.
    orientation = device.config()["orientation"]
    config_xml = device.read_prefs(_config_prefs_path(device.package, orientation))
    if "<map" in config_xml:
        config_xml = _set_float_pref(config_xml, "pref_key_hide_tool_bar_timeout", "0")
        device.write_prefs(_config_prefs_path(device.package, orientation), config_xml)


def _load_page(device, page: str, prefs: dict | None = None) -> None:
    """Serve and open one of the assets pages, leaving the cursor OFF.

    ``prefs`` optionally overrides the cursor test prefs (applied before the app reads them on
    launch), e.g. a 0 fade timeout so the overlay stays laid out while shown.
    """
    _ensure_server()
    _ensure_reverse(device)
    _reset_cursor_prefs(device, prefs)
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
    """The cursor overlay view is only laid out (present in the hierarchy) while the cursor is on."""
    return device.find_node(":id/cursorOverlay") is not None


def _toggle(device) -> None:
    """Toggle the cursor via the long-press hotkey (play/pause) and let the fade settle."""
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
    assert _overlay_present(device), "long-press hotkey should turn the cursor on (overlay shown)"
    _toggle(device)
    assert not _overlay_present(device), "long-press hotkey should turn the cursor off (overlay hidden)"


def test_cursor_toggle_exit_focuses_menu_button(device, ctx: dict) -> None:
    _load_target(device)
    _toggle(device)
    assert _overlay_present(device), "the cursor should be on"
    _toggle(device)
    assert not _overlay_present(device), "the cursor should be off"
    # Turning the cursor off moves focus to the toolbar more/menu button for predictable D-pad nav.
    assert _focused_resource_id(device).endswith(":id/button_more"), \
        f"turning the cursor off should focus the menu button, focus was '{_focused_resource_id(device)}'"


# The INTENT_OPEN_CONFIGURATION action opens the options bottom sheet (with the configuration
# settings as its root child fragment) straight from the command line — the same path the user
# hits from the menu, so this is a faithful, deterministic way to open + close the sheet.
OPEN_CONFIGURATION_ACTION = "fulguris.action.OPEN_CONFIGURATION"


def test_cursor_survives_options_sheet_dismiss(device, ctx: dict) -> None:
    """Opening then dismissing the options bottom sheet must not leave the cursor suspended.

    Regression: dismissing a BottomSheetDialogFragment steals the activity's window focus when it
    opens (which suspends the cursor) but does NOT reliably re-deliver onWindowFocusChanged(true)
    to the activity when it closes, so the cursor stayed suspended (dead) until the next app
    restart. The activity now resumes the cursor from the sheet's own close (onCancel /
    setOnDismissListener), which this test guards: the overlay (present only while the cursor is on
    AND not suspended) must be back after the sheet is dismissed.
    """
    # Fade must be 0 so the shown-but-idle overlay stays laid out (present) and _overlay_present
    # is a reliable "on and not suspended" signal rather than a fade race.
    _load_page(device, "cursor_target.html", {"pref_key_cursor_fade_timeout": 0})
    assert not _overlay_present(device), "cursor should start off"

    _toggle(device)
    assert _overlay_present(device), "the cursor should be on before the sheet"

    # Open the options bottom sheet (adds the configuration child fragment to the back stack).
    device.launch_action(OPEN_CONFIGURATION_ACTION, wait=2.5)
    assert not _overlay_present(device), \
        "the overlay should be suspended (hidden) while the bottom sheet is up"

    # Dismiss: BACK #1 pops the configuration child fragment (sheet stays at root, still up);
    # BACK #2 is at the root of the back stack, so the dialog cancels and closes.
    device.key(keys.BACK, wait=1.5)
    assert not _overlay_present(device), \
        "the overlay should still be suspended while the sheet is still open at root"
    device.key(keys.BACK, wait=1.5)

    # The sheet is now fully dismissed: the cursor must have resumed, not stayed suspended.
    assert _overlay_present(device), \
        "the cursor should be resumed (overlay present) after the bottom sheet is dismissed"

    # The cursor must actually be responsive again, not merely present: a click still lands.
    coords = _click_coords(device)
    assert coords is not None, "a click after the sheet dismiss should reach the page"
    _toggle(device)


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


def test_cursor_movement_gamepad_dpad_yields_to_focus_nav(device, ctx: dict) -> None:
    # A two-stick gamepad's right stick is the cursor's intended driver (it works at any time),
    # so the gamepad's own D-pad must NOT steer the cursor even while the cursor is on — it keeps
    # its normal focus-navigation role. The D-pad of a stick-less remote / adb's virtual keyboard
    # must still drive the cursor. The hat is pushed via sendevent on the gamepad's input node so
    # the D-pad event carries the gamepad as its source device (the input reader synthesizes the
    # virtual D-pad key from the ABS_HAT axis).
    node = None
    for name in ("xbox", "gamepad", "dualshock", "dualsense", "steam controller", "nintendo"):
        node = device.find_input_node(name)
        if node:
            break
    if not node:
        ctx["notes"].append("no gamepad connected — gamepad D-pad yield test skipped")
        return
    _load_target(device)
    # Control 1 (cursor OFF): the hat must produce input that reaches the app — focus moves away
    # from the web view. If it doesn't, the hat produces no D-pad input at all on this device and
    # the yield is not verifiable here (skip rather than fail).
    focus0 = _focused_resource_id(device)
    device.inject_hat_press(node, 1, 0)  # hat right
    device.inject_hat_press(node, 0, 1)  # hat down
    focus1 = _focused_resource_id(device)
    if focus1 == focus0:
        ctx["notes"].append(
            f"hat press changed no focus on this device ({node}) — gamepad D-pad yield not verifiable here")
        return
    # The actual check (cursor ON): a held gamepad D-pad DOWN must not drive the cursor to the
    # bottom edge — no page scroll. It is yielded to focus navigation instead.
    _toggle(device)
    device.inject_hat_hold(node, 0, 1, hold=3.5)
    title = _title(device)
    assert not re.fullmatch(r"sy\d+", title.strip()), \
        f"the gamepad D-pad must not move the cursor (the right stick drives it), but the page scrolled: '{title}'"
    # Control 2: the stick-less D-pad (adb's virtual keyboard) must still drive the cursor with
    # the cursor on — driving to the bottom edge scrolls the page.
    for _ in range(140):
        device.key(keys.DPAD_DOWN, wait=0.03)
    title = _title(device)
    m = re.fullmatch(r"sy(\d+)", title.strip())
    assert m and int(m.group(1)) > 0, \
        f"the stick-less D-pad must still move the cursor while the cursor is on, title was '{title}'"
    _toggle(device)


# ===========================================================================
# Feature: cursor fade
# ===========================================================================


def test_cursor_fade_hides_then_wakes(device, ctx: dict) -> None:
    _load_target(device)
    _toggle(device)  # cursor on; fade timeout was reset to 3000ms
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


def test_cursor_click_hesitant_press_still_clicks(device, ctx: dict) -> None:
    # A human "short click" on a remote is routinely held 400-700 ms — long enough for the OS to
    # start flagging the key as a long press, but the user still means a click. The action key must
    # therefore NOT reclassify a <~1 s hold as the context-menu long press (regression: it used to
    # fire at the system ~400 ms threshold and opened the menu instead of clicking).
    _load_target(device)
    _toggle(device)
    device.key_hold(keys.DPAD_CENTER, 600)  # a clearly short, but realistically held, press
    title = _title(device).strip()
    assert re.fullmatch(r"\d+,\d+", title), \
        f"a 600 ms held select press is still a click and must land on the page, title was '{title}'"
    _toggle(device)


def _field_center(device) -> tuple[int, int] | None:
    """The on-screen center of the URL/address field (a toolbar control), or None."""
    return device.field_center()


def test_cursor_confirm_over_ui_activates_control_under_cursor(device, ctx: dict) -> None:
    # Bug: with a toolbar widget holding input focus, pressing the confirm key (A / select) while
    # the cursor was over a DIFFERENT toolbar control activated the *focused* widget instead of the
    # control under the cursor. The confirm key over the browser UI must activate the control the
    # cursor points at, regardless of which widget holds focus.
    #
    # The URL/address field is the target: it is a wide toolbar control sitting above the web
    # content, and activating it has an unambiguous, readable effect — it gains input focus. After
    # turning the cursor off focus lands on the toolbar menu button, so this yields
    # cursor-over-address-field + focus-on-menu-button: if the confirm key went to the focused
    # widget the address field would NOT focus, but it must (the cursor is on top of it).
    _load_target(device)
    _toggle(device)          # cursor on
    _toggle(device)          # cursor off -> focus moves to button_more
    assert _focused_resource_id(device).endswith(":id/button_more"), \
        f"setup: focus should be on the toolbar menu button, was '{_focused_resource_id(device)}'"
    _toggle(device)          # cursor on again; enabling does not move focus (stays on button_more)
    assert _overlay_present(device), "the cursor should be on"
    field_center = _field_center(device)
    assert field_center is not None, "the URL/address field must be present in the toolbar"
    # Place the cursor exactly over the address field (a D-pad press count can't do this
    # deterministically across devices — the per-press step is DPI-dependent and key events get
    # dropped over network adb).
    device.cursor_teleport(*field_center)
    device.key(keys.DPAD_CENTER, wait=1.2)  # the confirm key (A / DPAD center / ENTER)
    assert device.field_focused(), (
        "the confirm key with the cursor over the URL field must activate that field (the control "
        "under the cursor), not the focused widget; the address field did not gain focus")


def test_cursor_confirm_on_over_web_ignores_stray_focus(device, ctx: dict) -> None:
    # With the cursor explicitly ON over web content, the confirm key (A / DPAD center / ENTER)
    # must click the page under the cursor -- even when Android focus is stranded on a toolbar
    # widget. That stranded state is the exact one users hit: focus is on the WebView after a
    # load, turning the cursor OFF moves it to the toolbar menu button (button_more), and
    # turning it back ON leaves it there (enabling never moves focus).
    # Regression (the user-reported bug): the confirm key was yielded to the focused widget in
    # this state, so a select press opened the browser menu instead of clicking the page under
    # the cursor. The yield now only applies to the passive right-stick ghost (shown, never
    # enabled); an enabled cursor is always the user's active input and acts at the cursor.
    _load_target(device)
    _toggle(device)          # cursor on
    _toggle(device)          # cursor off -> focus moves to button_more
    assert _focused_resource_id(device).endswith(":id/button_more"), \
        f"setup: focus should be on the toolbar menu button, was '{_focused_resource_id(device)}'"
    _toggle(device)          # cursor on again (centered on the page); enabling does not move focus
    assert _overlay_present(device), "the cursor should be on"
    assert _focused_resource_id(device).endswith(":id/button_more"), \
        f"enabling the cursor must not move focus; focus should still be on button_more, was '{_focused_resource_id(device)}'"
    device.key(keys.DPAD_CENTER, wait=1.2)  # the confirm key (A / DPAD center / ENTER)
    title = _title(device)
    assert re.fullmatch(r"\d+,\d+", title.strip()), \
        ("with the cursor ON over the page the confirm key must click the page under the cursor, "
         f"even though focus is stranded on the menu button; title was '{title}'")
    menu_open = device.find_node(":id/menuItemExit") is not None or \
        device.find_node(":id/menuItemSettings") is not None
    assert not menu_open, "the confirm key must not activate the focused menu button (the menu opened)"


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
    assert not _overlay_present(device), "the cursor should start off"
    _open_main_menu(device)
    item = device.find_node(":id/menuItemCursor")
    assert item and item.bounds, "Cursor menu item not found in the main menu"
    x1, y1, x2, y2 = item.bounds
    device.tap((x1 + x2) // 2, (y1 + y2) // 2, wait=1.0)
    assert _overlay_present(device), "tapping the Cursor menu item should turn the cursor on"
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
# Feature: media keys and gamepad shoulder buttons act as a mouse wheel while
# the cursor is on screen
# ===========================================================================


def test_cursor_wheel_ff_rewind_scrolls(device, ctx: dict) -> None:
    # cursor_target.html is 220vh and reports window.scrollY as 'sy<n>' on scroll.
    _load_target(device)
    _toggle(device)  # cursor on; centered, page at the top (sy=0)
    # With the cursor on, rewind is a mouse wheel scroll DOWN at the cursor.
    device.key(keys.MEDIA_REWIND, wait=0.9)
    t1 = _title(device)
    m1 = re.fullmatch(r"sy(\d+)", t1.strip())
    assert m1 and int(m1.group(1)) > 0, f"rewind with the cursor on should wheel-scroll down, title was '{t1}'"
    down = int(m1.group(1))
    # Fast-forward is a mouse wheel scroll UP.
    device.key(keys.MEDIA_FAST_FORWARD, wait=0.9)
    device.key(keys.MEDIA_FAST_FORWARD, wait=0.9)
    t2 = _title(device)
    m2 = re.fullmatch(r"sy(\d+)", t2.strip())
    assert m2 and int(m2.group(1)) < down, f"fast-forward with the cursor on should wheel-scroll back up, was '{t1}' now '{t2}'"
    # The gamepad shoulder buttons are the same wheel (a standard gamepad has no media
    # keys): RB scrolls down, LB scrolls up, at the cursor.
    device.key(keys.BUTTON_R1, wait=0.9)
    t3 = _title(device)
    m3 = re.fullmatch(r"sy(\d+)", t3.strip())
    assert m3 and int(m3.group(1)) > int(m2.group(1)), f"RB with the cursor on should wheel-scroll down, was '{t2}' now '{t3}'"
    device.key(keys.BUTTON_L1, wait=0.9)
    device.key(keys.BUTTON_L1, wait=0.9)
    t4 = _title(device)
    m4 = re.fullmatch(r"sy(\d+)", t4.strip())
    assert m4 and int(m4.group(1)) < int(m3.group(1)), f"LB with the cursor on should wheel-scroll up, was '{t3}' now '{t4}'"
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


def test_cursor_youtube_scrubber_seek_after_idle(device, ctx: dict) -> None:
    # Tests the real-world case: controls auto-hide after the cursor stops moving, then
    # the next click should still seek. dispatchHover() before the click (+ 80 ms delay)
    # re-shows controls before the BUTTON_PRESS event lands on the scrubber.
    if not device.is_leanback():
        ctx["notes"].append("YouTube idle-hover test skipped (leanback/TV only — cursor is a D-pad/remote feature)")
        return
    _load_page(device, "yt_scrub.html")
    _toggle(device)
    assert _title(device).strip() == "ctrl-shown", "cursor enable should show player controls"
    for _ in range(50):
        device.key(keys.DPAD_DOWN, wait=0.03)
    # Nudge up off the clamped bottom edge FIRST: the cursor clamps its hotspot to the exact
    # overlay bottom, which maps just BELOW the CSS viewport (DPI-dependent -- e.g. the SHIELD's
    # 1080p override over a 4K panel), so a click parked there lands outside the page (no pointer
    # event at all) and the title stays 'ctrl-hidden'. A few steps up lands back inside the bar.
    # Doing this BEFORE the sleep keeps the test's "after idle" meaning: the last hover is the
    # up-nudge, so the 4 s sleep below auto-hides the controls again, and only the pre-click
    # hover re-shows them before the click lands.
    for _ in range(15):
        device.key(keys.DPAD_UP, wait=0.03)
    time.sleep(4.0)  # longer than yt_scrub.html's 3 s auto-hide (controls re-hide after the up-nudge)
    # Controls auto-hid. The pre-click hover (dispatchHover) + 80 ms delay gives YouTube
    # time to re-show controls before BUTTON_PRESS fires, so the click still seeks.
    device.key(keys.DPAD_CENTER, wait=1.0)  # extra wait for the 80 ms delay + DOM update
    title = _title(device).strip()
    assert title.startswith("seek@"), \
        f"click should seek even after controls auto-hid (hover+delay re-shows them), title was '{title}'"
    _toggle(device)


# ===========================================================================# Feature: context menu (action-key long press)
# ===========================================================================


def test_cursor_context_menu_action_long_press(device, ctx: dict) -> None:
    """Long-pressing the action key (select / DPAD center) performs a long press at the cursor
    and opens the WebView's context menu for the element under it. Verified by the presence of
    the link context dialog: its "Copy link" row shows the link URL as secondary text
    (locale-independent, so it's a robust assertion target regardless of device language)."""
    link_url = "https://example.com/"
    _load_page(device, "context_target.html")
    _toggle(device)
    # Do NOT assert _overlay_present() here: the cursor fades out after the 3 s inactivity
    # timeout (overlay becomes GONE and drops out of the uiautomator dump), and each dump
    # itself takes ~2-3 s — so the check races the fade and flakes. The action-key branch is
    # guarded by `enabled || shown` and dispatchLongPress() wakes the cursor, so the cursor
    # is on even while the overlay is faded; the context menu below is the real signal.
    device.key_hold(keys.DPAD_CENTER, 1500)  # a deliberate hold (past the 1 s action-key threshold)
    nodes = device.nodes()
    assert any(n.text == link_url for n in nodes), \
        "action-key long press should open the link context menu (a node should carry the link URL), " \
        f"node texts were {[n.text for n in nodes if n.text]}"
    device.key(keys.BACK, wait=0.8)  # dismiss the context dialog
    assert not any(n.text == link_url for n in device.nodes()), \
        "BACK should dismiss the link context menu"
    _toggle(device)


def test_cursor_context_menu_repeated_long_press_touch_stays_clean(device, ctx: dict) -> None:
    """Repeated long presses on the SAME page (no reload between them) must each deliver a fresh
    touch to the page. longpress_log.html records every pointer/touch/contextmenu event into the
    toolbar title; a healthy press logs a fresh `pd` (pointerdown). dispatchLongPress() ends the
    synthetic touch with ACTION_UP, but by then the browser has already claimed the gesture
    (pointercancel) — an UP for a canceled pointer corrupts the WebView's touch state, so a
    later press's touch is swallowed (only `contextmenu` fires, no `pd`). Assert every press
    produced a `pd`."""
    presses = 3
    _load_page(device, "longpress_log.html")
    _toggle(device)
    for _ in range(presses):
        device.key_hold(keys.DPAD_CENTER, 1500)  # deliberate hold (past the 1 s threshold)
        device.key(keys.BACK, wait=1.2)  # dismiss the context dialog (real href => dialog shows)
    title = _title(device).strip()
    pd_count = len(re.findall(r"\bpd\d*", title))
    assert pd_count >= presses, \
        f"each of the {presses} long presses should deliver a fresh touch (pointerdown) to the page, " \
        f"but only {pd_count} did — the WebView's touch state is stuck (UP-after-cancel); log={title!r}"
    device.key(keys.BACK, wait=0.5)
    _toggle(device)


# ===========================================================================# Registration
# ===========================================================================

FEATURE_GROUPS = {
    "cursor-toggle": [
        test_cursor_toggle_hotkey_shows_and_hides_overlay,
        test_cursor_toggle_exit_focuses_menu_button,
        test_cursor_survives_options_sheet_dismiss,
    ],
    "cursor-movement": [
        test_cursor_movement_dpad_right_moves_right,
        test_cursor_movement_dpad_down_moves_down,
        test_cursor_movement_edge_scrolls_page,
        test_cursor_movement_gamepad_dpad_yields_to_focus_nav,
    ],
    "cursor-fade": [
        test_cursor_fade_hides_then_wakes,
    ],
    "cursor-click": [
        test_cursor_click_hover_fires_mouseover,
        test_cursor_click_activates_under_cursor,
        test_cursor_click_drag_target_seeks,
        test_cursor_click_hesitant_press_still_clicks,
        test_cursor_confirm_over_ui_activates_control_under_cursor,
        test_cursor_confirm_on_over_web_ignores_stray_focus,
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
        test_cursor_youtube_scrubber_seek_after_idle,
    ],
    "cursor-context": [
        test_cursor_context_menu_action_long_press,
        test_cursor_context_menu_repeated_long_press_touch_stays_clean,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_cursor_toggle_hotkey_shows_and_hides_overlay": "Long-press play/pause toggles the cursor overlay on and off",
    "test_cursor_toggle_exit_focuses_menu_button": "Turning the cursor off moves focus to the toolbar menu button",
    "test_cursor_survives_options_sheet_dismiss": "Opening then dismissing the options bottom sheet does not leave the cursor suspended (resumes on sheet close)",
    "test_cursor_movement_dpad_right_moves_right": "D-pad right moves the cursor right (click X increases)",
    "test_cursor_movement_dpad_down_moves_down": "D-pad down moves the cursor down (click Y increases)",
    "test_cursor_movement_edge_scrolls_page": "Pushing past the bottom edge scrolls the page",
    "test_cursor_movement_gamepad_dpad_yields_to_focus_nav": "With the cursor on, a two-stick gamepad's D-pad is yielded to focus navigation (the right stick drives the cursor) while the stick-less D-pad still moves it",
    "test_cursor_fade_hides_then_wakes": "The cursor fades out after the inactivity timeout and wakes on movement",
    "test_cursor_click_hover_fires_mouseover": "Enabling the cursor fires a mouse hover on the page",
    "test_cursor_click_activates_under_cursor": "Select press dispatches a click the page receives at the cursor",
    "test_cursor_click_drag_target_seeks": "A cursor click seeks a scrub bar via mousedown(mouse) or touch drag, like YouTube's timeline",
    "test_cursor_click_hesitant_press_still_clicks": "A realistically held (~600 ms) select press still clicks — only a deliberate ~1 s hold opens the context menu",
    "test_cursor_confirm_over_ui_activates_control_under_cursor": "With the cursor over a toolbar control, the confirm key (A / select) activates the control under the cursor, not the widget holding focus",
    "test_cursor_confirm_on_over_web_ignores_stray_focus": "With the cursor ON over the page and focus stranded on a toolbar widget, the confirm key (A / select) clicks the page under the cursor instead of activating the focused widget",
    "test_cursor_menu_item_visible_on_leanback": "The Cursor main-menu item is shown on Android TV",
    "test_cursor_menu_item_toggles_mode": "Tapping the Cursor menu item turns the cursor on",
    "test_cursor_fullscreen_click_reaches_custom_view": "In HTML5 fullscreen the cursor is visible and its click reaches the fullscreen view",
    "test_cursor_media_play_pause": "The media play/pause key pauses and resumes the page video",
    "test_cursor_wheel_ff_rewind_scrolls": "With the cursor on, fast-forward/rewind and the gamepad shoulder buttons (LB/RB) wheel-scroll the page up/down at the cursor",
    "test_cursor_youtube_scrubber_seek": "A cursor click seeks a YouTube-style auto-hiding scrubber (hover keeps controls alive, click seeks)",
    "test_cursor_youtube_scrubber_seek_after_idle": "Click seeks even after controls auto-hid (dispatchHover+delay re-shows them before BUTTON_PRESS lands) (leanback only)",
    "test_cursor_context_menu_action_long_press": "Long-press the action key (select / DPAD center) opens the WebView context menu for the element under the cursor",
    "test_cursor_context_menu_repeated_long_press_touch_stays_clean": "Repeated long presses (same page) each deliver a fresh touch — the synthetic long press must not leave the WebView's touch state stuck",
}
