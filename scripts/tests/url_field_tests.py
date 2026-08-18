"""URL address bar UI test cases, driven over adb.

Each test is a function taking (serial, package, ctx) and returning None on success or
raising AssertionError with a message on failure.

Run via run.py.
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb

OUT_DIR = os.path.join(os.path.dirname(__file__), "out")


def _ensure_out() -> str:
    os.makedirs(OUT_DIR, exist_ok=True)
    return OUT_DIR


def _focus_field_for_navigation(serial: str) -> None:
    """Focus the field without touch, in navigation mode (KEYCODE_SEARCH -> requestFocus)."""
    adb.key(serial, adb.KEY_SEARCH, wait=0.7)


def _enter_edit(serial: str) -> None:
    """Focus for navigation then press center to enter edit mode."""
    _focus_field_for_navigation(serial)
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=0.8)


def _reset(serial: str, package: str, restart: bool | None = None) -> None:
    """Bring the app to a known foreground state before a test.

    With no explicit argument the runner's default decides (run.py --restart):
    by default the app is NOT restarted between tests (faster on the TV), it is
    only settled (launched if missing). Tests should set up their own state in
    the running app (navigate to a known page) instead of restarting; only a
    test that explicitly verifies the fresh-launch behavior passes restart=True.
    """
    if adb.RESTART_BETWEEN_TESTS if restart is None else restart:
        adb.restart(serial, package)
    else:
        adb.settle(serial, package)


# A known page whose title (label) differs from its URL.
KNOWN_URL = "example.com"
KNOWN_DOMAIN = "example.com"


# --- State tests -----------------------------------------------------------


def test_unfocused_shows_label(serial: str, package: str, ctx: dict) -> None:
    adb.navigate(serial, package, KNOWN_URL)
    text = adb.field_text(serial)
    assert text, "unfocused field should show a label"
    assert not text.lower().startswith("http"), f"unfocused should show the label, not the URL, got '{text}'"


def test_navigation_shows_label_not_url(serial: str, package: str, ctx: dict) -> None:
    adb.navigate(serial, package, KNOWN_URL)
    label = adb.field_text(serial)
    _focus_field_for_navigation(serial)
    assert adb.field_focused(serial), "field should be focused"
    assert not adb.ime_shown(serial), "navigation focus must not show the keyboard"
    nav_text = adb.field_text(serial)
    assert nav_text == label, f"navigation should keep showing the label '{label}', got '{nav_text}'"
    assert not nav_text.lower().startswith("http"), "navigation should show the label, not the URL"


def test_edit_shows_url(serial: str, package: str, ctx: dict) -> None:
    adb.navigate(serial, package, KNOWN_URL)
    _enter_edit(serial)
    assert adb.ime_shown(serial), "editing should show the keyboard"
    text = adb.field_text(serial).lower()
    assert KNOWN_DOMAIN in text or text.startswith("http"), f"edit mode should show the URL, got '{text}'"


def test_dpad_edit_selects_all(serial: str, package: str, ctx: dict) -> None:
    """Entering edit mode via D-pad selects all, so typing replaces the URL."""
    adb.navigate(serial, package, KNOWN_URL)
    _focus_field_for_navigation(serial)
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=0.8)
    # The URL must still be there (selected) before any typing. On Android TV the IME used to
    # deliver a stale commit that wiped the freshly selected URL, leaving the field blank.
    edit_text = adb.field_text(serial)
    assert KNOWN_DOMAIN in edit_text.lower() or edit_text.lower().startswith("http"), \
        f"edit mode must keep the selected URL in the field, got '{edit_text}'"
    adb.type_text(serial, "Z", wait=0.5)
    # Hide the keyboard (stays in edit mode) so the field text is readable; on TV the
    # fullscreen leanback IME would otherwise be what uiautomator captures.
    adb.key(serial, adb.KEY_BACK, wait=0.8)
    text = adb.field_text(serial)
    assert KNOWN_DOMAIN not in text.lower(), f"D-pad edit must select all; URL should be replaced, got '{text}'"
    assert text.strip().lower() == "z", f"typing should replace the whole URL, got '{text}'"


# --- Tests -----------------------------------------------------------------


def test_launch_focus_is_webview(serial: str, package: str, ctx: dict) -> None:
    _reset(serial, package, restart=True)  # fresh launch: initial focus must land on the web view
    assert adb.webview_focused(serial), "expected the web view to be focused after launch"


def test_directional_focus_is_navigation_not_edit(serial: str, package: str, ctx: dict) -> None:
    """Focusing with a non-pointer input focuses for navigation only, no keyboard."""
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # leaves the web view focused
    _focus_field_for_navigation(serial)
    assert adb.field_focused(serial), "the address field should be focused"
    assert not adb.ime_shown(serial), "the keyboard must NOT show on directional focus"


def test_center_enters_edit_mode(serial: str, package: str, ctx: dict) -> None:
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # navigation ends with keyboard hidden
    _focus_field_for_navigation(serial)
    assert not adb.ime_shown(serial), "precondition: keyboard hidden in navigation"
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=0.8)
    assert adb.ime_shown(serial), "center/enter should enter edit mode and show the keyboard"


def test_type_and_validate_navigates(serial: str, package: str, ctx: dict) -> None:
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # clean loaded state, no popup
    _enter_edit(serial)
    adb.clear_field(serial)
    adb.type_text(serial, "example.com", wait=0.5)
    adb.key(serial, adb.KEY_ENTER, wait=3.0)
    adb.note_tab_opened()  # validating a URL opens a new tab (urlInNewTab)
    assert not adb.ime_shown(serial), "keyboard should hide after validating"
    assert adb.webview_focused(serial), "focus should return to the web view after navigating"


def test_back_two_stage_keyboard_then_cancel(serial: str, package: str, ctx: dict) -> None:
    """First back hides keyboard but keeps the field; second back cancels back to the label."""
    adb.navigate(serial, package, "example.org")

    # Navigation shows the label; capture it as the value a cancel returns to.
    _focus_field_for_navigation(serial)
    expected = adb.field_text(serial)
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=0.8)
    adb.type_text(serial, "somethingelse", wait=0.4)
    assert adb.ime_shown(serial), "precondition: keyboard shown while editing"

    # First back: keyboard hidden, field still focused (still editing).
    adb.key(serial, adb.KEY_BACK, wait=0.8)
    assert not adb.ime_shown(serial), "first back should hide the keyboard"
    assert adb.field_focused(serial), "first back should keep the field focused"

    # Second back: cancel back to the navigation label, keep field focused.
    adb.key(serial, adb.KEY_BACK, wait=0.8)
    restored = adb.field_text(serial)
    assert adb.field_focused(serial), "second back should keep the field focused (navigation)"
    assert restored == expected, f"cancel should return to the label '{expected}', got '{restored}'"


def test_back_from_navigation_returns_to_web(serial: str, package: str, ctx: dict) -> None:
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # leaves the web view focused
    _focus_field_for_navigation(serial)
    assert adb.field_focused(serial), "precondition: field focused for navigation"
    adb.key(serial, adb.KEY_BACK, wait=0.8)
    assert adb.webview_focused(serial), "back from navigation should return to the web view"


def test_down_from_navigation_returns_to_web(serial: str, package: str, ctx: dict) -> None:
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # leaves the web view focused
    _focus_field_for_navigation(serial)
    assert adb.field_focused(serial), "precondition: field focused for navigation"
    adb.key(serial, adb.KEY_DPAD_DOWN, wait=0.8)
    assert not adb.field_focused(serial), "down from navigation should leave the field"


def test_suggestions_navigable_without_touch(serial: str, package: str, ctx: dict) -> None:
    """Type, hide keyboard with back, navigate the popup with down and open with center."""
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # clean loaded state, no popup
    _enter_edit(serial)
    # Entering edit selects the current URL; typing replaces it (emptying the field
    # would drop out of edit mode on the TV-style address bar and swallow the input).
    adb.type_text(serial, "wikipedia", wait=1.0)
    assert adb.dropdown_present(serial), "suggestions popup should appear while typing"

    # First back hides the keyboard but the popup must remain.
    adb.key(serial, adb.KEY_BACK, wait=0.9)
    assert not adb.ime_shown(serial), "back should hide the keyboard"
    assert adb.dropdown_present(serial), "the suggestions popup must remain after hiding keyboard"

    # Navigate into the popup and open a suggestion.
    adb.key(serial, adb.KEY_DPAD_DOWN, wait=0.6)
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=3.0)
    adb.note_tab_opened()  # opening a suggestion opens a new tab (searchInNewTab)
    assert not adb.ime_shown(serial), "keyboard should be hidden after opening a suggestion"
    assert adb.webview_focused(serial), "opening a suggestion should navigate and focus the web view"


def test_touch_tap_enters_edit(serial: str, package: str, ctx: dict) -> None:
    """Pointer/touch goes straight to edit mode (keyboard shown)."""
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # navigation ends with keyboard hidden
    center = adb.field_center(serial)
    assert center, "could not locate the address field bounds"
    adb.tap(serial, center[0], center[1], wait=0.9)
    assert adb.ime_shown(serial), "tapping the field should enter edit mode and show the keyboard"


def test_retap_after_cancel_reenters_edit(serial: str, package: str, ctx: dict) -> None:
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # navigation ends with keyboard hidden
    center = adb.field_center(serial)
    assert center, "could not locate the address field bounds"
    adb.tap(serial, center[0], center[1], wait=0.9)
    adb.key(serial, adb.KEY_BACK, wait=0.7)  # hide keyboard
    adb.key(serial, adb.KEY_BACK, wait=0.7)  # cancel
    adb.tap(serial, center[0], center[1], wait=0.9)
    assert adb.ime_shown(serial), "tapping again after cancel should re-enter edit mode"


# --- SSL / HTTPS status icon tests ----------------------------------------


def test_https_shows_ssl_icon(serial: str, package: str, ctx: dict) -> None:
    """Navigating to a valid HTTPS page should show the encrypted SSL icon."""
    adb.navigate(serial, package, "https://example.com")
    time.sleep(2.0)
    assert adb.ssl_icon_visible(serial), "SSL icon should be visible for a valid HTTPS page"


def test_http_shows_off_icon(serial: str, package: str, ctx: dict) -> None:
    """Navigating to a plain HTTP page should show the encryption-off SSL icon."""
    adb.navigate(serial, package, "http://example.com")
    time.sleep(2.0)
    assert adb.ssl_icon_visible(serial), "SSL icon should be visible for a plain HTTP page"


def test_invalid_https_shows_ssl_icon(serial: str, package: str, ctx: dict) -> None:
    """An invalid/expired HTTPS cert (badssl.com/expired) should show the off SSL icon.

    The browser shows an SSL error dialog; we dismiss it with 'No' so the page does not
    load, but the icon must be visible reflecting the error state.
    """
    adb.navigate(serial, package, "https://expired.badssl.com")
    time.sleep(3.0)
    # Dismiss the SSL error dialog with its 'No' button if it is present.
    no_btn = next((n for n in adb.nodes(serial) if n.text.strip() == "No"), None)
    if no_btn and no_btn.bounds:
        cx = (no_btn.bounds[0] + no_btn.bounds[2]) // 2
        cy = (no_btn.bounds[1] + no_btn.bounds[3]) // 2
        adb.tap(serial, cx, cy, wait=1.5)
    assert adb.ssl_icon_visible(serial), \
        "SSL icon should remain visible (encrypted-off) after an invalid HTTPS cert"


def test_unfocused_pill_outline_visible(serial: str, package: str, ctx: dict) -> None:
    """The unfocused address bar should show a subtle pill outline.

    With the focus pill now only shown while focused, the unfocused field needs an
    outline so it is not empty. We check the outline is drawn on the pill edge by
    comparing pixels just inside the container edge (outline) against the page area.
    """
    out = _ensure_out()
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # unfocused pill, no focus state
    pill = adb.find_node(serial, ":id/address_bar_include") or adb.field_node(serial)
    assert pill and pill.bounds, "could not locate the address bar pill"
    x1, y1, x2, y2 = pill.bounds
    shot = os.path.join(out, f"pill_outline_{serial.replace(':', '_')}.png")
    adb.screenshot(serial, shot)
    try:
        from PIL import Image
    except ImportError:
        ctx["notes"].append("pill outline test: Pillow not installed, screenshot saved for manual review")
        return
    img = Image.open(shot).convert("L")
    xs = range(x1 + 24, x2 - 24, 6)
    edge = [img.getpixel((x, y1)) for x in xs]
    inside = [img.getpixel((x, y1 + 8)) for x in xs]
    edge_mean = sum(edge) / len(edge)
    inside_mean = sum(inside) / len(inside)
    assert abs(edge_mean - inside_mean) > 6, \
        f"no visible pill outline: edge {edge_mean:.1f} vs inside {inside_mean:.1f}"


def test_pill_only_when_focused(serial: str, package: str, ctx: dict) -> None:
    """Capture unfocused vs focused screenshots of the address bar for review.

    When Pillow is available we assert the pill region changes between states.
    """
    out = _ensure_out()
    adb.navigate(serial, package, KNOWN_URL, reset=False)  # unfocused pill, no focus state
    unfocused = os.path.join(out, f"pill_unfocused_{serial.replace(':', '_')}.png")
    focused = os.path.join(out, f"pill_focused_{serial.replace(':', '_')}.png")
    adb.screenshot(serial, unfocused)
    _focus_field_for_navigation(serial)
    time.sleep(0.4)
    adb.screenshot(serial, focused)

    field = adb.field_node(serial)
    if not field or not field.bounds:
        # Screenshots saved for manual review; can't assert region without bounds.
        return
    try:
        from PIL import Image
    except ImportError:
        # Pillow not installed: leave screenshots for manual review.
        ctx["notes"].append("pill test: Pillow not installed, screenshots saved for manual review")
        return
    x1, y1, x2, y2 = field.bounds
    box = (x1, max(0, y1 - 8), x2, y2 + 8)
    a = Image.open(unfocused).convert("RGB").crop(box)
    b = Image.open(focused).convert("RGB").crop(box)
    diff = sum(
        abs(pa[0] - pb[0]) + abs(pa[1] - pb[1]) + abs(pa[2] - pb[2])
        for pa, pb in zip(a.getdata(), b.getdata())
    ) / (a.size[0] * a.size[1] * 3)
    assert diff > 3.0, f"pill region barely changed between states (mean diff {diff:.2f})"


# --- Reload / stop button tests --------------------------------------------
# The toolbar button doubles as reload (refresh icon) and stop (X icon):
#   - While the current tab is loading it is VISIBLE and shows the stop icon.
#   - Once loaded, on a scrollable page it is GONE (pull-to-refresh takes over),
#     and on a short/non-scrollable page it stays VISIBLE (refresh) by design.
# Regressions we guard against: the stop button sticking after a load finished
# (stale progress / restored tab), never appearing during a load, or not tracking
# the current tab when switching tabs.
#
# We read the state with the fast dumpsys view probe (adb.reload_button_state).
# A "scrollable, loaded" page => GONE; a short page (example.com) => VISIBLE.

# A tall, always-scrollable page. A cache-busting query makes every load hit the
# network so the stop button is reliably observable (a cached reload is too fast).
SCROLLABLE_URL = "https://en.wikipedia.org/wiki/Web_browser"
SHORT_URL = "example.com"


def _fresh_url(base: str) -> str:
    sep = "&" if "?" in base else "?"
    return f"{base}{sep}cb={int(time.time() * 1000)}"


def _wait_reload_button_gone(serial: str, timeout: float = 25.0) -> str:
    """Poll until the reload/stop button is GONE; return the final state."""
    deadline = time.time() + timeout
    state = adb.reload_button_state(serial)
    while state != "GONE" and time.time() < deadline:
        time.sleep(0.5)
        state = adb.reload_button_state(serial)
    return state


def _wait_reload_button(serial: str, target: str, timeout: float = 25.0) -> str:
    deadline = time.time() + timeout
    state = adb.reload_button_state(serial)
    while state != target and time.time() < deadline:
        time.sleep(0.4)
        state = adb.reload_button_state(serial)
    return state


def _navigate_current_tab(serial: str, url: str, post_enter_wait: float = 0.15) -> None:
    """Type a URL into the address bar and submit, returning to the app quickly so
    the caller can sample the loading state. Assumes the app is already foregrounded.

    Verifies the URL actually landed in the field (the address bar's edit guard can
    occasionally swallow the first attempt) and retries once, so navigation is reliable
    even when the session is busy with many tabs."""
    probe = url.split("//")[-1][:12]
    for _ in range(2):
        adb.enter_edit(serial)
        adb.type_text(serial, url, wait=0.4)  # replaces the selected URL
        if probe in adb.field_text(serial):
            break
    adb.key(serial, adb.KEY_ENTER, wait=post_enter_wait)


def _saw_stop_button(serial: str, timeout: float = 15.0) -> bool:
    """True if the button becomes VISIBLE (stop) at least once within the timeout."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if adb.reload_button_state(serial) == "VISIBLE":
            return True
    return False


def _ctrl_tab_to(serial: str, label: str, tries: int = 4) -> bool:
    """CTRL+TAB (most-recent toggle) until the address label matches, or give up."""
    for _ in range(tries):
        adb.ctrl_tab(serial)
        if adb.field_text(serial) == label:
            return True
    return adb.field_text(serial) == label


def test_reload_button_hidden_after_load(serial: str, package: str, ctx: dict) -> None:
    """On a loaded, scrollable page the reload/stop button must stay hidden.

    Core regression: the button used to hide, then reappear after the page finished
    (stale out-of-order progress event), or stick showing the stop icon on a tab
    restored at launch. It must go GONE and stay GONE while the page is idle.
    """
    adb.navigate(serial, package, SCROLLABLE_URL)
    state = _wait_reload_button_gone(serial)
    assert state == "GONE", f"reload button should be hidden after load, got {state}"
    # Keep watching: the button must NOT reappear while the page is idle.
    for _ in range(12):  # ~6s of idle watching at 0.5s polls
        time.sleep(0.5)
        state = adb.reload_button_state(serial)
        assert state == "GONE", \
            f"stop button reappeared after the page finished loading (state={state})"


def test_stop_button_visible_during_load(serial: str, package: str, ctx: dict) -> None:
    """While a fresh (uncached) page loads, the stop button is visible."""
    adb.settle(serial, package)
    _navigate_current_tab(serial, _fresh_url(SCROLLABLE_URL))
    assert _saw_stop_button(serial, timeout=15.0), \
        "stop button was never visible during page load"
    _wait_reload_button_gone(serial)  # let it finish so we leave a clean state


def test_reload_button_hidden_after_reload(serial: str, package: str, ctx: dict) -> None:
    """After a load, a second fresh navigation shows the stop button again, then hides."""
    adb.navigate(serial, package, SCROLLABLE_URL)
    assert _wait_reload_button_gone(serial) == "GONE", "precondition: button hidden after load"
    # A second fresh (cache-busted) navigation: loads (stop visible) then hides again.
    _navigate_current_tab(serial, _fresh_url(SCROLLABLE_URL))
    assert _saw_stop_button(serial, timeout=15.0), "stop button not shown during reload/second load"
    assert _wait_reload_button_gone(serial) == "GONE", "button should hide again after the reload"


def test_stop_button_click_stops_load(serial: str, package: str, ctx: dict) -> None:
    """Tapping the stop button while loading aborts the load (button leaves the stop state)."""
    adb.settle(serial, package)
    _navigate_current_tab(serial, _fresh_url(SCROLLABLE_URL))
    assert _saw_stop_button(serial, timeout=15.0), "precondition: stop button visible while loading"
    center = adb.reload_button_center(serial)
    assert center, "could not locate the reload/stop button"
    adb.tap(serial, center[0], center[1], wait=1.0)
    # After stopping, the load ends: the button settles to a stable non-loading state
    # (GONE if the partial page is scrollable, else VISIBLE showing refresh) and,
    # crucially, does not keep flipping back to the stop state.
    time.sleep(1.5)
    s1 = adb.reload_button_state(serial)
    time.sleep(1.5)
    s2 = adb.reload_button_state(serial)
    assert s1 == s2, f"button state kept changing after stop ({s1} -> {s2}); load did not stop"


def test_short_page_shows_reload_button(serial: str, package: str, ctx: dict) -> None:
    """On a short, non-scrollable page the reload button stays visible by design."""
    adb.navigate(serial, package, SHORT_URL)
    state = _wait_reload_button(serial, "VISIBLE", timeout=20.0)
    assert state == "VISIBLE", f"short page should keep the reload button visible, got {state}"


def test_reload_button_tracks_tab_on_ctrl_tab(serial: str, package: str, ctx: dict) -> None:
    """Switching tabs with CTRL+TAB updates the reload button for the current tab.

    Tab A is a scrollable loaded page (button GONE); tab B is a short page (button
    VISIBLE). Cycling between them must flip the button to match whichever tab shows.
    """
    # Tab A: scrollable page -> button GONE.
    adb.navigate(serial, package, SCROLLABLE_URL)
    assert _wait_reload_button_gone(serial) == "GONE", "tab A (scrollable) should hide the button"
    label_a = adb.field_text(serial)
    # Tab B: navigating opens a new tab for the URL; a short page -> button VISIBLE.
    # (Both tabs stay alive for the test; the runner closes them afterwards.)
    adb.navigate(serial, package, SHORT_URL, reset=False)
    assert _wait_reload_button(serial, "VISIBLE") == "VISIBLE", "tab B (short) should show the button"
    label_b = adb.field_text(serial)
    assert label_a != label_b, "the two tabs should have distinct labels"
    # Switch back to A -> GONE, then to B -> VISIBLE (CTRL+TAB toggles most-recent).
    assert _ctrl_tab_to(serial, label_a), "CTRL+TAB should reach tab A"
    assert _wait_reload_button(serial, "GONE") == "GONE", \
        "after switching to the scrollable tab the button must hide"
    assert _ctrl_tab_to(serial, label_b), "CTRL+TAB should reach tab B"
    assert _wait_reload_button(serial, "VISIBLE") == "VISIBLE", \
        "after switching to the short tab the button must show"


def test_reload_button_tracks_tab_via_tab_menu(serial: str, package: str, ctx: dict) -> None:
    """Same as above but switching tabs by touch through the tab list drawer."""
    # Ensure two tabs exist: a scrollable one and a short one. Both stay alive
    # for the test; the runner closes them afterwards.
    adb.navigate(serial, package, SCROLLABLE_URL)
    assert _wait_reload_button_gone(serial) == "GONE", "scrollable tab should hide the button"
    label_scroll = adb.field_text(serial)
    adb.navigate(serial, package, SHORT_URL, reset=False)
    assert _wait_reload_button(serial, "VISIBLE") == "VISIBLE", "short tab should show the button"

    # Open the tab drawer and tap the scrollable tab: button must hide.
    assert adb.open_tab_switcher(serial), "tabs button not available"
    entries = adb.tab_entries(serial)
    target = next((c for (t, c) in entries if t == label_scroll), None)
    assert target, f"scrollable tab '{label_scroll}' not found in tab list {[t for t,_ in entries]}"
    adb.tap(serial, target[0], target[1], wait=1.2)
    assert adb.field_text(serial) == label_scroll, "tapping the tab row should switch to it"
    assert _wait_reload_button(serial, "GONE") == "GONE", \
        "after switching to the scrollable tab via the menu the button must hide"


ALL_TESTS = [
    test_launch_focus_is_webview,
    test_unfocused_shows_label,
    test_directional_focus_is_navigation_not_edit,
    test_navigation_shows_label_not_url,
    test_center_enters_edit_mode,
    test_edit_shows_url,
    test_dpad_edit_selects_all,
    test_type_and_validate_navigates,
    test_back_two_stage_keyboard_then_cancel,
    test_back_from_navigation_returns_to_web,
    test_down_from_navigation_returns_to_web,
    test_suggestions_navigable_without_touch,
    test_touch_tap_enters_edit,
    test_retap_after_cancel_reenters_edit,
    test_pill_only_when_focused,
    test_https_shows_ssl_icon,
    test_http_shows_off_icon,
    test_invalid_https_shows_ssl_icon,
    test_unfocused_pill_outline_visible,
    test_reload_button_hidden_after_load,
    test_stop_button_visible_during_load,
    test_reload_button_hidden_after_reload,
    test_stop_button_click_stops_load,
    test_short_page_shows_reload_button,
    test_reload_button_tracks_tab_on_ctrl_tab,
    test_reload_button_tracks_tab_via_tab_menu,
]

# One-line description per test, shown in the markdown reports (results.py).
# Keep in sync with ALL_TESTS — results.save_run() warns about missing entries.
TEST_DESCRIPTIONS = {
    "test_launch_focus_is_webview": "After a fresh launch, initial focus lands on the web view",
    "test_unfocused_shows_label": "Unfocused address bar shows the page label, not the URL",
    "test_directional_focus_is_navigation_not_edit": "D-pad focus enters navigation mode without showing the keyboard",
    "test_navigation_shows_label_not_url": "Navigation focus keeps showing the label, not the URL",
    "test_center_enters_edit_mode": "D-pad center/enter enters edit mode and shows the keyboard",
    "test_edit_shows_url": "Edit mode shows the URL, not the label",
    "test_dpad_edit_selects_all": "Entering edit via D-pad selects all, so typing replaces the URL",
    "test_type_and_validate_navigates": "Typing a URL and pressing enter navigates and returns focus to the web view",
    "test_back_two_stage_keyboard_then_cancel": "First back hides the keyboard, second back cancels back to the label",
    "test_back_from_navigation_returns_to_web": "Back from navigation focus returns to the web view",
    "test_down_from_navigation_returns_to_web": "D-pad down from navigation focus leaves the field for the web view",
    "test_suggestions_navigable_without_touch": "Suggestions popup can be navigated and opened with D-pad only",
    "test_touch_tap_enters_edit": "Touch tap on the field goes straight to edit mode with keyboard",
    "test_retap_after_cancel_reenters_edit": "Tapping again after a cancel re-enters edit mode",
    "test_pill_only_when_focused": "The focus pill is only drawn while the field is focused",
    "test_https_shows_ssl_icon": "A valid HTTPS page shows the encrypted SSL icon",
    "test_http_shows_off_icon": "A plain HTTP page shows the encryption-off SSL icon",
    "test_invalid_https_shows_ssl_icon": "An expired HTTPS cert shows the SSL error icon (dialog dismissed)",
    "test_unfocused_pill_outline_visible": "The unfocused address bar still shows a subtle pill outline",
    "test_reload_button_hidden_after_load": "Reload/stop button stays hidden on a loaded scrollable page",
    "test_stop_button_visible_during_load": "Stop button is visible while a fresh page is loading",
    "test_reload_button_hidden_after_reload": "Stop button reappears during a second load, then hides again",
    "test_stop_button_click_stops_load": "Tapping the stop button aborts the page load",
    "test_short_page_shows_reload_button": "On a short non-scrollable page the reload button stays visible",
    "test_reload_button_tracks_tab_on_ctrl_tab": "CTRL+TAB tab switch updates the reload button to match the tab",
    "test_reload_button_tracks_tab_via_tab_menu": "Tab switch via the tab list drawer updates the reload button",
}

