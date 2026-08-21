#!/usr/bin/env python3
"""Field test: "Hide tool bar after" against real websites on Android TV (v6).

Drives the installed app through busy/real pages (BBC, Wikipedia, YouTube home,
a YouTube video) and verifies the tool bar auto-hides ~timeout s after each page
has *fully* loaded, in plain mode AND with cursor mode active. Phase 2 replays
the exact user-reported stuck case: after an auto-hide, BACK re-shows the tool
bar (doBackAction -> showActionBar) while the web view already holds focus, and
the tool bar must auto-hide *again*. Phase 3 (cursor + a YouTube video) plays
the user's "click on another video in the right rail" workflow with the cursor.

Harness fixes learned from v3/v4 runs:

  * Cursor mode is toggled by TAPPING the "Cursor" item in the main menu
    (:id/menuItemCursor) - the proven cursor-suite method. v4 sent the long-press
    hotkey while the menu popup was still open, and the popup window consumed the
    key before the activity's dispatchKeyEvent, so the toggle silently failed.
    (The hotkey is still exercised once, cursor-OFF, in the end-of-run sanity.)
  * The run starts from a CLEAN tab slate: the app restores the previous
    session's tabs on launch, and v4's stale localhost:8899 tabs showed
    "Error response" pages, so the measured hide belonged to a dead tab.
  * Phase 3 (cursor click on a right-rail video) is a CLOSED LOOP: the toolbar
    label mirrors the cursor's click coordinates (the cursor_target.html oracle
    technique), so the test reads the cursor position after each D-pad burst and
    steers it into the recommendation column instead of guessing a fixed burst.
    Fixed bursts were proven unreliable: a 60-press burst over network adb drops
    most key events, so the travel is a fraction of the theoretical distance.
  * A click that navigates (phase 3) is detected by a title DIFFERENT from the
    old page's (which was already stable, so one read suffices) - never by
    guessing at tile titles.

Measurement (learned the hard way on the slow RPi TV):

  * "Fully loaded" = the mirrored page title is STABLE (STABLE_READS identical
    consecutive reads), and must contain the expected host (so a stale/wrong
    page is reported as a load failure instead of measured).
  * The hide is measured FROM PAGE LOAD (the app arms the countdown at the
    loading->loaded edge), with generous late tolerance - not from some later
    "anchor" that takes ~7 s of slow uiautomator dumps and re-arms mid-cycle.
  * An empty address field only means "toolbar hid" AFTER load is confirmed -
    during load the field is empty simply because the title is not reported yet.
  * Cursor fade is DISABLED for the run (pref_key_cursor_fade_timeout=0) so the
    overlay never goes GONE and cursor mode is detectable at any moment.
  * The cookie-consent banner (BBC) is dismissed while the page is still
    loading, so its tap cannot re-arm the countdown.

Preference files (verified in code):
  * hideToolBarTimeout -> ConfigurationPreferences -> the orientation-suffixed
    file ({package}_preferences_portrait / _landscape).
  * cursorFadeTimeoutMs -> UserPreferences (@UserPrefs) -> the default,
    unsuffixed file ({package}_preferences).

Every scenario captures screenshots under scripts/tests/out/ for vision
verification. Both preferences are reset to their defaults afterwards and the
run ends with a cursor on/off sanity check (leaving the mode off).

    python scripts/tests/toolbar_field_test.py                # default: the TV
    python scripts/tests/toolbar_field_test.py --sites bbc,wikipedia
"""
from __future__ import annotations

import argparse
import os
import re
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from framework import AndroidDevice, keys  # noqa: E402  (also puts scripts/tools on sys.path)
import adb  # noqa: E402

TIMEOUT_KEY = "pref_key_hide_tool_bar_timeout"
FADE_KEY = "pref_key_cursor_fade_timeout"
DEFAULT_TIMEOUT = "0"
DEFAULT_FADE = "3000"   # the code default (ms); 0 = never fade
OUT_DIR = os.path.join(os.path.dirname(__file__), "out")

SITES = {
    "bbc": "https://www.bbc.com/",
    "wikipedia": "https://en.wikipedia.org/wiki/Main_Page",
    "youtube": "https://www.youtube.com/",
    "youtube_video": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
}

# The title of every site must contain this, so a stale/wrong page (v4's
# "Error response" tabs) is reported as a load failure instead of measured.
EXPECT_HOST = {"bbc": "bbc", "wikipedia": "wikipedia", "youtube": "youtube", "youtube_video": "youtube"}

# (site key, cursor mode on?, click another video?) - the original bug (bbc) first.
# NOTE: the navigation is ALWAYS done with the cursor OFF (see main()): in cursor
# mode the confirming ENTER becomes a cursor click, so a typed URL cannot be
# submitted. A real user is on a page before turning the cursor on, which is
# exactly what this ordering models.
SCENARIOS = [
    ("bbc", False, False),
    ("wikipedia", False, False),
    ("youtube", False, False),
    ("youtube_video", False, False),
    ("bbc", True, False),
    ("wikipedia", True, False),
    ("youtube", True, False),
    ("youtube_video", True, True),   # the user's exact case: cursor + click another video
]

LOAD_TIMEOUT = 300.0   # the RPi TV can need a while for heavy pages
STABLE_READS = 2       # consecutive identical title reads => "fully loaded"
OBSERVE_AFTER = 60.0   # how long to keep watching for the hide after load
HIDE_TOLERANCE_EARLY = 2.5
HIDE_TOLERANCE_LATE = 8.0
CLICK_NAV_TIMEOUT = 120.0  # phase 3: how long to wait for the click to navigate (one heavy load took 300s on the RPi)


def _set_pref(device, key: str, value: str, kind: str, suffixed: bool) -> None:
    """Rewrite a pref in the right prefs file (app stopped).

    suffixed=True  -> {package}_preferences_{orientation}.xml (ConfigurationPreferences)
    suffixed=False -> {package}_preferences.xml              (UserPreferences/@UserPrefs)
    """
    device.force_stop()
    cfg = device.config()["orientation"]
    suffix = f"_{cfg}" if suffixed else ""
    path = f"shared_prefs/{device.package}_preferences{suffix}.xml"
    xml = device.read_prefs(path)
    if "<map" not in xml:
        raise RuntimeError(f"prefs file {path} not initialized yet")
    entry = f'<{kind} name="{key}" value="{value}" />'
    pattern = re.compile(rf'<{kind} name="{re.escape(key)}" value="[^"]*" />')
    xml = pattern.sub(entry, xml) if pattern.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
    device.write_prefs(path, xml)


def _shot(device, serial: str, tag: str) -> str:
    path = os.path.join(OUT_DIR, f"{serial.replace(':', '_')}_{tag}.png")
    device.screenshot(path)
    print(f"    shot: {path}")
    return path


def _cursor_on(device) -> bool:
    """Cursor mode is on iff its overlay view is present in the hierarchy.

    Only reliable while cursor fade is disabled (pref_key_cursor_fade_timeout=0),
    which the run sets up before any check.
    """
    return device.find_node(":id/cursorOverlay") is not None


def _focused_node(device) -> str:
    """Resource id (or class) of the node that holds input focus, for diagnosis."""
    for n in device.nodes():
        if n.focused:
            rid = n.resource_id.replace(device.package + "/", "") if n.resource_id else n.cls
            return rid or n.cls
    return "?"


def _find_button_by_text(device, texts: tuple[str, ...]):
    for n in device.nodes():
        t = (n.text or "").strip()
        if t and t.lower() in texts:
            return n
    return None


def _accept_cookies_once(device, serial: str, tag: str) -> None:
    """One shot: dismiss a cookie-consent banner if one is up (e.g. BBC's)."""
    for label in ("i agree", "accept all", "allow all", "allow and continue"):
        n = _find_button_by_text(device, (label,))
        if n and n.bounds:
            x1, y1, x2, y2 = n.bounds
            _shot(device, serial, f"{tag}_cookies")
            device.tap((x1 + x2) // 2, (y1 + y2) // 2, wait=2.0)
            print(f"    cookie banner: tapped {label!r}")
            return


def _tab_count(device) -> int:
    """Number of open tabs (0 if the switcher could not be opened)."""
    if not device.open_tab_switcher(wait=1.5):
        return 0
    time.sleep(1.0)
    count = len(device.tab_entries())
    device.key(keys.BACK, wait=0.8)
    return count


def _clean_tabs(device) -> None:
    """Close every tab restored from the previous session (v4's stale-tab fix)."""
    for _ in range(25):
        count = _tab_count(device)
        if count == 0:
            return
        device.close_tabs(count, wait=0.5)
        time.sleep(1.0)
    print("    !! could not reach a clean tab slate")


def _wait_loaded(device, serial: str, tag: str, expect: str) -> tuple[float, str]:
    """Wait until a page title containing `expect` is stable. Returns (seconds, title).

    The cookie banner is dismissed while the page is still loading, so its tap
    cannot re-arm the countdown afterwards.
    """
    t0 = time.time()
    last = ""
    same = 0
    last_cookie_check = 0.0
    while time.time() - t0 < LOAD_TIMEOUT:
        text = device.field_text().strip()
        if text and expect in text.lower():
            same = same + 1 if text == last else 1
            if same >= STABLE_READS:
                return time.time() - t0, text
            last = text
        elif text:
            same = 0  # a real title, but not the expected page - keep waiting
            last = text
        else:
            same = 0
            # uiautomator dumps are slow on the RPi (and a known hang-flake):
            # check for the cookie banner at most every 5 s
            if time.time() - last_cookie_check >= 5.0:
                last_cookie_check = time.time()
                _accept_cookies_once(device, serial, f"{tag}_load")
        time.sleep(1.0)
    return time.time() - t0, last


def _hotkey_toggle(device) -> None:
    """Long-press play/pause with the menu CLOSED - the proven cursor-suite
    method (16/16). v4's failure was the key sent through the OPEN menu popup,
    which consumed it; with the menu closed it reaches the activity."""
    if device.field_text().strip() == "" and not _cursor_on(device):
        _reshow_toolbar(device)  # back with the menu open would close it instead
    device.key_longpress(keys.MEDIA_PLAY_PAUSE, wait=1.5)


def _open_menu_cursor_item(device, serial: str, tag: str):
    """Fallback: open the main menu and return the Cursor item node.

    Retries the whole sequence: on the slow RPi the uiautomator dumps can take
    longer than the "Hide tool bar after" window, so the tool bar can hide
    (and its more button go GONE) between the re-show and the menu dump - the
    first v6 run died exactly this way in the sanity check.
    """
    for attempt in range(3):
        _reshow_toolbar(device)
        n = device.find_node(":id/button_more")
        if not n or not n.bounds:
            print(f"    menu attempt {attempt + 1}: more button not found (toolbar hidden?)")
            continue
        x1, y1, x2, y2 = n.bounds
        device.tap((x1 + x2) // 2, (y1 + y2) // 2, wait=2.0)
        item = device.find_node(":id/menuItemCursor")
        if item and item.bounds:
            return item
        _shot(device, serial, f"{tag}_menu_attempt{attempt + 1}")  # diagnostic
        device.key(keys.BACK, wait=0.8)  # close the menu, if it opened
    return None


def _ensure_cursor(device, serial: str, tag: str, want: bool) -> bool:
    """Make sure cursor mode matches `want`. Returns True on success.

    Hotkey-first in both directions (proven by the cursor suite with the menu
    closed); the menu item is the fallback for turning it ON.
    """
    if _cursor_on(device) == want:
        print(f"    cursor mode already {'on' if want else 'off'}")
        return True
    _hotkey_toggle(device)
    if _cursor_on(device) != want:
        if want:
            item = _open_menu_cursor_item(device, serial, tag)
            if item:
                _shot(device, serial, f"{tag}_menu")
                ix1, iy1, ix2, iy2 = item.bounds
                device.tap((ix1 + ix2) // 2, (iy1 + iy2) // 2, wait=1.5)
            else:
                raise RuntimeError("cursor could not be enabled: hotkey and menu item both failed")
        else:
            _hotkey_toggle(device)  # the first hotkey flipped the wrong way; try once more
    state = _cursor_on(device)
    ok = state == want
    print(f"    cursor mode {'on' if state else 'off'} (expected {'on' if want else 'off'}) {'OK' if ok else 'MISMATCH'}")
    return ok


def _reshow_toolbar(device, timeout: float = 12.0) -> bool:
    """Re-show a hidden toolbar with the back key (doBackAction -> showActionBar)."""
    if device.field_text().strip():
        return True
    device.key(keys.BACK, wait=1.0)
    t0 = time.time()
    while time.time() - t0 < timeout and not device.field_text().strip():
        time.sleep(0.5)
    return bool(device.field_text().strip())


def _wait_for_hide(device, serial: str, tag: str, budget: float) -> tuple[float | None, str]:
    """Wait until the toolbar hides. Returns (seconds or None, focus-at-end)."""
    t0 = time.time()
    while time.time() - t0 < budget:
        if not device.field_text().strip():
            _shot(device, serial, f"{tag}_hid_{time.time() - t0:.1f}s")
            return time.time() - t0, _focused_node(device)
        time.sleep(0.5)
    return None, _focused_node(device)


def _hide_verdict(device, hide_at: float | None, timeout_s: float, focus: str, never_msg: str) -> tuple[str, str]:
    """Phase 1 verdict. The bug under test is the tool bar NOT timing out
    (stuck / never-hid); a hide that reads "early" is NOT a bug - on real pages
    the app arms at its own load edge (main-frame progress-100), which fires
    before the mirrored title stabilizes, so the hide legitimately lands before
    title-stable+timeout_s. Only a LATE hide (starvation) is flagged."""
    if hide_at is None:
        return never_msg, f"webview_focused={device.webview_focused()}, focused={focus!r}"
    delta = hide_at - timeout_s
    if delta <= HIDE_TOLERANCE_LATE:
        note = f"(hid {hide_at:.1f}s after load, {delta:+.1f}s vs the {timeout_s:g}s timeout)"
        return "PASS", note if delta < -HIDE_TOLERANCE_EARLY else ""
    return "SUSPICIOUS (hid late - starvation?)", f"(hid {hide_at:.1f}s after load)"


def _rehide_verdict(device, hide_at: float | None, timeout_s: float, focus: str) -> tuple[str, str]:
    """Phase 2 verdict: the bug is STUCK-FOREVER, so any hide within the budget
    passes. The exact timing is deliberately not enforced: on a real site (e.g.
    YouTube) the app's load edge fires when the main document arrives, long
    before the heavy subresources settle, so a pending countdown can
    legitimately fire before re-show+timeout_s."""
    if hide_at is None:
        return "NEVER-HID after back re-show (STUCK - the reported bug)", \
            f"webview_focused={device.webview_focused()}, focused={focus!r}"
    delta = hide_at - timeout_s
    note = f"(hid {hide_at:.1f}s after the re-show, {delta:+.1f}s vs the {timeout_s:g}s timeout)"
    return "RE-HIDE PASS", note


def phase_first_hide(device, serial: str, tag: str, timeout_s: float) -> tuple[str, str]:
    """Phase 1: from page load, the toolbar must auto-hide ~timeout_s s later."""
    hide_at, focus = _wait_for_hide(device, serial, tag, OBSERVE_AFTER)
    return _hide_verdict(device, hide_at, timeout_s, focus, "NEVER-HID (bug)")


def phase_back_reshow(device, serial: str, tag: str, timeout_s: float, cursor: bool) -> tuple[str, str]:
    """Phase 2: the user-reported stuck case.

    After the auto-hide, BACK re-shows the toolbar while the web view already
    holds focus (in cursor mode the overlay is non-focusable, so it ALWAYS
    does). showActionBar must re-arm the consumed countdown, so the toolbar
    auto-hides again. Pre-fix it stayed visible forever.
    """
    time.sleep(3.0)  # settle: a BACK must not race a still-loading page (its title is not reported yet)
    if not _reshow_toolbar(device):
        return "RESHOW-FAIL (bug)", "back did not re-show the tool bar"
    _shot(device, serial, f"{tag}_reshown")
    # The toolbar is visible again; the re-armed countdown alone must hide it
    # again - no further key needed.
    hide_at, focus = _wait_for_hide(device, serial, f"{tag}_re", OBSERVE_AFTER)
    return _rehide_verdict(device, hide_at, timeout_s, focus)


def _wait_new_page(device, serial: str, tag: str, old_title: str, budget: float) -> bool:
    """Wait for a click to navigate to a new page (phase 3).

    Detected by a title different from `old_title` (which was already stable,
    so a single read suffices - a transient title during load cannot match it).
    """
    t0 = time.time()
    while time.time() - t0 < budget:
        text = device.field_text().strip()
        if not text:
            # the toolbar may have hidden in the meantime; bring it back to read the title
            _reshow_toolbar(device)
            text = device.field_text().strip()
        if text and text != old_title:
            _shot(device, serial, f"{tag}_new_page")
            print(f"    navigated to new page: {text!r}")
            return True
        time.sleep(1.0)
    return False


# Phase-3 closed-loop steering target: the FIRST right-rail recommendation card
# on the TV's 1920x1200 YouTube video layout (a ~640x520 px clickable card at
# screen x ~1380-1920, y ~90-570). The zone below is in SCREEN space; the
# The zone below is the first rail card's THUMBNAIL in SCREEN space, inset so
# any point in it is solidly on the clickable card (the card's left edge is at
# ~1390 and its thumbnail ends at ~430 on the RPi's 1920x1200 layout, so a
# 1450-1820 x 150-400 box is entirely inside). A whole-card zone is wrong: the
# loop converges to the zone's NEAREST edge, so an oversized zone lands on its
# far corner - off the card (a 1380x570 zone landed at the 1383,568 corner,
# 7px left of the card, and missed). The loop converts the zone to OVERLAY
# space (where the app's "Cursor: click at target (x, y)" log reports, and
# where the cursor actually moves) by subtracting the cursor overlay's
# screen-space origin, read once from the view hierarchy.
RAIL_X_MIN, RAIL_X_MAX = 1450, 1820
RAIL_Y_MIN, RAIL_Y_MAX = 150, 400
PX_PER_PRESS = 8.0       # observed travel per D-pad press (drops included)
STEER_BURST = 10         # max presses per burst (bursts are sized proportionally)
DEAD_BAND = 10           # within this of the zone, a single-press nudge replaces a burst
STEER_WAIT = 0.12        # s between presses (the proven suite cadence is 0.15)


def _log_cursor_pos(serial: str) -> tuple[float, float] | None:
    """The last 'Cursor: click at target (x, y)' line in logcat, in OVERLAY
    coordinates - the exact space the cursor moves in. None when the line is
    absent (Timber debug off, or the click never reached the page)."""
    last = None
    for line in adb.logcat(serial, "Cursor: click at target").splitlines():
        m = re.search(r"click at target \(([-\d.]+), ([-\d.]+)\)", line)
        if m:
            last = (float(m.group(1)), float(m.group(2)))
    return last


def _steer_to_rail(device, serial: str, tag: str, old_title: str) -> bool:
    """Closed loop over the APP'S OWN LOG (the oracle triangulated 2026-08-22):
    select-press -> the activity logs the click point -> burst D-pad keys
    toward the rail zone (sized proportionally to the remaining distance) ->
    repeat, until the cursor is inside the zone. Returns True when the cursor
    is on a rail card. The loop is logcat-only (no uiautomator in the hot
    path - the RPi dump flake). A read click that lands on a rail card
    navigates on its own; that is the phase-3 success too, and
    _wait_new_page detects it after the loop."""
    # The overlay origin in screen space = the offset between the two
    # coordinate systems. Read once from the view hierarchy (the overlay is
    # present while cursor mode is on and fade is disabled, as in this run).
    off_x = off_y = 0.0
    ov = device.find_node(":id/cursorOverlay")
    if ov and ov.bounds:
        off_x, off_y = ov.bounds[0], ov.bounds[1]
    print(f"    overlay origin in screen space = ({off_x},{off_y})")
    zx0, zx1 = RAIL_X_MIN - off_x, RAIL_X_MAX - off_x
    zy0, zy1 = RAIL_Y_MIN - off_y, RAIL_Y_MAX - off_y   # zone in overlay space
    adb.logcat(serial, "Cursor:", clear=True)  # fresh slate for the click lines
    x = y = None
    misses = 0
    last_pos = None
    stuck = 0
    for step in range(40):
        device.key(keys.DPAD_CENTER, wait=0.6)   # a real click: the app logs its point
        pos = _log_cursor_pos(serial)
        if pos:
            x, y = pos
            misses = 0
            stuck = stuck + 1 if pos == last_pos else 0
            last_pos = pos
            print(f"    step {step:2d}: cursor at overlay ({x:.0f},{y:.0f})")
        else:
            misses += 1
        if x is not None and zx0 <= x <= zx1 and zy0 <= y <= zy1:
            print(f"    cursor at ({x:.0f},{y:.0f}) - on a rail card")
            return True
        if misses >= 3:
            _shot(device, serial, f"{tag}_steer_nolog")
            print("    !! no 'click at target' in logcat - cannot steer (is Timber debug on?)")
            return False
        if x is None:
            continue
        if stuck >= 3:
            _shot(device, serial, f"{tag}_steer_stuck")
            print(f"    !! cursor stuck at ({x:.0f},{y:.0f}) - bursts are not getting through")
            return False
        # Burst toward the zone CENTER (not its nearest edge - that parks on the
        # corner, off the card). Proportional to the remaining distance, with a
        # single-press nudge inside the dead band so a landing a hair short still
        # converges instead of parking.
        cx, cy = (zx0 + zx1) / 2, (zy0 + zy1) / 2
        if abs(cx - x) <= DEAD_BAND:
            dx = 0
        else:
            d = int(abs(cx - x) / PX_PER_PRESS) + 1
            dx = (1 if cx > x else -1) * min(STEER_BURST, d)
        if abs(cy - y) <= DEAD_BAND:
            dy = 0
        else:
            d = int(abs(cy - y) / PX_PER_PRESS) + 1
            dy = (1 if cy > y else -1) * min(STEER_BURST, d)
        for _ in range(abs(dx)):
            device.key(keys.DPAD_RIGHT if dx > 0 else keys.DPAD_LEFT, wait=STEER_WAIT)
        for _ in range(abs(dy)):
            device.key(keys.DPAD_UP if dy < 0 else keys.DPAD_DOWN, wait=STEER_WAIT)
    _shot(device, serial, f"{tag}_steer_gave_up")
    print(f"    steering gave up; last known overlay position {('(%d,%d)' % (x, y)) if x is not None else 'unknown'}")
    return False


def phase_click_another_video(device, serial: str, tag: str, timeout_s: float, old_title: str) -> tuple[str, str]:
    """Phase 3: with the cursor, move it onto a right-rail recommendation and
    click it - the user's "click on another youtube video" workflow. The new
    page must load AND the toolbar (re-shown by the navigation) must hide again.
    """
    time.sleep(3.0)  # settle, as in phase 2
    # Steer the cursor onto the FIRST recommendation card in the right rail and
    # click it - closed loop over the app's own logcat (see _steer_to_rail;
    # fixed bursts drop too many key events over network adb to be reliable).
    # A failed steer is not a dead end: the position reads are themselves cursor
    # clicks, so one may have already navigated - _wait_new_page detects either
    # outcome.
    if not _steer_to_rail(device, serial, tag, old_title):
        print("    !! steering could not confirm the cursor on a rail card - clicking anyway")
    _shot(device, serial, f"{tag}_cursor_at_rail")
    device.key(keys.DPAD_CENTER, wait=2.0)
    if not _wait_new_page(device, serial, tag, old_title, CLICK_NAV_TIMEOUT):
        return "CLICK-MISS (not a bug)", f"the cursor click did not navigate (title still {old_title!r} or unknown)"
    hide_at, focus = _wait_for_hide(device, serial, f"{tag}_new", OBSERVE_AFTER)
    return _rehide_verdict(device, hide_at, timeout_s, focus)


def _cursor_sanity(device, serial: str) -> None:
    """End-of-run check: menu toggle ON, hotkey OFF (leaves the mode off)."""
    _reshow_toolbar(device)
    on = _ensure_cursor(device, serial, "sanity", want=True)
    _shot(device, serial, "sanity_on")
    off = _ensure_cursor(device, serial, "sanity", want=False)
    print(f"cursor sanity: menu-on={on} hotkey-off={off} -> {'OK' if on and off else 'TOGGLE BROKEN'}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--serial", default="192.168.178.67:5555", help="device serial (default: the RPi TV)")
    ap.add_argument("--timeout", type=float, default=10.0, help='"Hide tool bar after" value in seconds (default 10, the feature max)')
    ap.add_argument("--sites", default=",".join(SITES), help="comma-separated site keys to include")
    args = ap.parse_args()

    wanted = {s.strip() for s in args.sites.split(",") if s.strip()}
    scenarios = [s for s in SCENARIOS if s[0] in wanted]

    device = AndroidDevice(args.serial)
    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"=== toolbar field test v6 on {device.label()}  timeout={args.timeout}s ===")
    # hide timeout -> orientation-suffixed file; cursor fade -> default file.
    _set_pref(device, TIMEOUT_KEY, f"{args.timeout:g}", "float", suffixed=True)
    _set_pref(device, FADE_KEY, "0", "int", suffixed=False)
    device.launch()
    time.sleep(3.0)

    results: list[tuple[str, str, str]] = []
    try:
        # Prove the toggle + detection work before trusting any scenario.
        _cursor_sanity(device, args.serial)
        # v4 fix: close the tabs restored from the previous session.
        _clean_tabs(device)
        for i, (site, cursor, click) in enumerate(scenarios, 1):
            url = SITES[site]
            name = "cursor-ON " if cursor else "cursor-OFF"
            print(f"[{i}/{len(scenarios)}] {site} ({name}) {url}")
            try:
                # Always navigate with the cursor OFF: the confirming ENTER is a
                # cursor click in cursor mode, so the URL could not be submitted.
                device.navigate(url, reset=False)
                t_load, title = _wait_loaded(device, args.serial, f"s{i:02d}_{site}", EXPECT_HOST[site])
                if not title or EXPECT_HOST[site] not in title.lower():
                    raise RuntimeError(f"page did not report a stable '{EXPECT_HOST[site]}' title (got {title!r} - stale tab or load failure?)")
                mode = "cursor" if cursor else "plain"
                print(f"    fully loaded at {t_load:.1f}s  title={title!r}")
                _shot(device, args.serial, f"s{i:02d}_{site}_{mode}_loaded")
                if cursor:
                    # Now, like a real user, turn the cursor on (on the loaded page)
                    # and make sure the toolbar is up before the first observation.
                    if not _ensure_cursor(device, args.serial, f"s{i:02d}_{site}", want=True):
                        print("    !! cursor state could not be set - the verdict for this scenario is plain-mode only")
                    _reshow_toolbar(device)

                tag = f"s{i:02d}_{site}_{mode}"
                v1, n1 = phase_first_hide(device, args.serial, tag, args.timeout)
                results.append((site, mode, f"{v1}{(' - ' + n1) if n1 else ''}"))
                print(f"    -> phase1: {v1}{(' - ' + n1) if n1 else ''}")
                if v1 != "PASS":
                    continue

                v2, n2 = phase_back_reshow(device, args.serial, tag, args.timeout, cursor)
                results.append((site, f"{mode}-reshow", f"{v2}{(' - ' + n2) if n2 else ''}"))
                print(f"    -> phase2: {v2}{(' - ' + n2) if n2 else ''}")
                if "PASS" not in v2:  # both "PASS" and "RE-HIDE PASS" count
                    continue

                if click:
                    v3, n3 = phase_click_another_video(device, args.serial, tag, args.timeout, title)
                    results.append((site, f"{mode}-click-video", f"{v3}{(' - ' + n3) if n3 else ''}"))
                    print(f"    -> phase3: {v3}{(' - ' + n3) if n3 else ''}")
            except Exception as e:  # noqa: BLE001
                print(f"    !! scenario error: {e}")
                results.append((site, "cursor" if cursor else "plain", f"ERROR {e}"))
            finally:
                # Leave the toolbar visible and cursor off for the next scenario.
                _reshow_toolbar(device)
                if cursor:
                    try:
                        _ensure_cursor(device, args.serial, f"s{i:02d}_{site}_off", want=False)
                    except Exception as e:  # noqa: BLE001
                        print(f"    !! could not disable cursor mode: {e}")
    finally:
        _set_pref(device, TIMEOUT_KEY, DEFAULT_TIMEOUT, "float", suffixed=True)
        _set_pref(device, FADE_KEY, DEFAULT_FADE, "int", suffixed=False)
        print(f"timeout pref reset to {DEFAULT_TIMEOUT}, cursor fade reset to {DEFAULT_FADE}")

    print("\n=== summary ===")
    failed = False
    for site, phase, status in results:
        print(f"  {status}")
        print(f"      {site:<14} {phase}")
        if not status.startswith(("PASS", "RE-HIDE PASS", "CLICK-MISS")):
            failed = True
    print("RESULT:", "FAILURES PRESENT" if failed else "ALL PASS")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
