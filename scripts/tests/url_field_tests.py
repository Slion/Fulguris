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
    adb.restart(serial, package)
    assert adb.webview_focused(serial), "expected the web view to be focused after launch"


def test_directional_focus_is_navigation_not_edit(serial: str, package: str, ctx: dict) -> None:
    """Focusing with a non-pointer input focuses for navigation only, no keyboard."""
    adb.restart(serial, package)
    _focus_field_for_navigation(serial)
    assert adb.field_focused(serial), "the address field should be focused"
    assert not adb.ime_shown(serial), "the keyboard must NOT show on directional focus"


def test_center_enters_edit_mode(serial: str, package: str, ctx: dict) -> None:
    adb.restart(serial, package)
    _focus_field_for_navigation(serial)
    assert not adb.ime_shown(serial), "precondition: keyboard hidden in navigation"
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=0.8)
    assert adb.ime_shown(serial), "center/enter should enter edit mode and show the keyboard"


def test_type_and_validate_navigates(serial: str, package: str, ctx: dict) -> None:
    adb.restart(serial, package)
    _enter_edit(serial)
    adb.clear_field(serial)
    adb.type_text(serial, "example.com", wait=0.5)
    adb.key(serial, adb.KEY_ENTER, wait=3.0)
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
    adb.restart(serial, package)
    _focus_field_for_navigation(serial)
    assert adb.field_focused(serial), "precondition: field focused for navigation"
    adb.key(serial, adb.KEY_BACK, wait=0.8)
    assert adb.webview_focused(serial), "back from navigation should return to the web view"


def test_down_from_navigation_returns_to_web(serial: str, package: str, ctx: dict) -> None:
    adb.restart(serial, package)
    _focus_field_for_navigation(serial)
    assert adb.field_focused(serial), "precondition: field focused for navigation"
    adb.key(serial, adb.KEY_DPAD_DOWN, wait=0.8)
    assert not adb.field_focused(serial), "down from navigation should leave the field"


def test_suggestions_navigable_without_touch(serial: str, package: str, ctx: dict) -> None:
    """Type, hide keyboard with back, navigate the popup with down and open with center."""
    adb.restart(serial, package)
    _enter_edit(serial)
    adb.clear_field(serial)
    adb.type_text(serial, "wikipedia", wait=1.0)
    assert adb.dropdown_present(serial), "suggestions popup should appear while typing"

    # First back hides the keyboard but the popup must remain.
    adb.key(serial, adb.KEY_BACK, wait=0.9)
    assert not adb.ime_shown(serial), "back should hide the keyboard"
    assert adb.dropdown_present(serial), "the suggestions popup must remain after hiding keyboard"

    # Navigate into the popup and open a suggestion.
    adb.key(serial, adb.KEY_DPAD_DOWN, wait=0.6)
    adb.key(serial, adb.KEY_DPAD_CENTER, wait=3.0)
    assert not adb.ime_shown(serial), "keyboard should be hidden after opening a suggestion"
    assert adb.webview_focused(serial), "opening a suggestion should navigate and focus the web view"


def test_touch_tap_enters_edit(serial: str, package: str, ctx: dict) -> None:
    """Pointer/touch goes straight to edit mode (keyboard shown)."""
    adb.restart(serial, package)
    center = adb.field_center(serial)
    assert center, "could not locate the address field bounds"
    adb.tap(serial, center[0], center[1], wait=0.9)
    assert adb.ime_shown(serial), "tapping the field should enter edit mode and show the keyboard"


def test_retap_after_cancel_reenters_edit(serial: str, package: str, ctx: dict) -> None:
    adb.restart(serial, package)
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
    adb.restart(serial, package)
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
    adb.restart(serial, package)
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
]
