"""Zero-tab (empty state) tests, driven through the framework Device API.

When the last tab is closed the browser stays alive and shows an empty state
(the large Fulguris logo on the theme background, no "New tab" button) instead
of exiting. The address field is the way to get back in by typing a URL or
search. Explicit exits (menu Exit, Ctrl+Q) still close the browser.

    python scripts/tests/run.py --device SERIAL --group empty-tab
    python scripts/tests/run.py --device SERIAL --test empty_tab
"""
from __future__ import annotations

import time

from framework import keys


def _node_texts(device) -> list[str]:
    return [n.text for n in device.nodes() if n.text]


def _webview_present(device) -> bool:
    """True if a WebView node is in the view hierarchy (a tab is showing)."""
    return any(n.cls == "android.webkit.WebView" for n in device.nodes())


def _wait_for_webview(device, timeout: float = 20.0) -> bool:
    """Poll until a WebView appears (or the app crashes / the timeout elapses)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if _webview_present(device):
            return True
        if device.foreground_package() != device.package:
            return False
        time.sleep(1.0)
    return _webview_present(device)


def _empty_state_visible(device) -> bool:
    """True if the zero-tab empty state view is showing (a sized, laid-out node).

    uiautomator omits GONE views from the dump, so a present node with a non-zero
    bounds box means the empty state (the large logo) is on screen.
    """
    n = device.find_node(":id/emptyTabsView")
    if not n or not n.bounds:
        return False
    w = n.bounds[2] - n.bounds[0]
    h = n.bounds[3] - n.bounds[1]
    return w > 0 and h > 0


def _wait_for_empty_state(device, timeout: float = 30.0) -> bool:
    """Poll until the zero-tab empty state is visible (or the app crashes)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if _empty_state_visible(device):
            return True
        if device.foreground_package() != device.package:
            return False
        time.sleep(1.0)
    return _empty_state_visible(device)


def _menu_item_ids(device) -> set[str]:
    """Suffixes of the resource-ids of every node currently in the hierarchy."""
    return {n.resource_id.split("/")[-1] for n in device.nodes()}


def _tap_id(device, id_suffix: str) -> None:
    """Tap the center of the node whose resource-id ends with ``id_suffix``."""
    n = device.find_node(id_suffix)
    assert n and n.bounds, f"node {id_suffix!r} not found to tap"
    device.tap((n.bounds[0] + n.bounds[2]) // 2, (n.bounds[1] + n.bounds[3]) // 2)


def _open_more_menu(device) -> None:
    """Tap the toolbar's more/menu button to open the main menu."""
    _tap_id(device, ":id/button_more")


def _open_webpage_menu(device) -> None:
    """Open the 'Web page' (tab) menu: open the main menu, then tap the
    'Web page' switcher that lives in it."""
    _open_more_menu(device)
    device.settle(timeout=20.0)
    _tap_id(device, ":id/menuItemTabMenu")
    device.settle(timeout=20.0)


# Tab/web-page-specific menu items that must NOT be present in the 'Web page'
# menu when no tab is open — there is no page to operate on. The 'Web page'
# switcher (menuItemTabMenu) is deliberately NOT in this list: the tab menu is
# user-configurable, so it must stay reachable even with no tab.
_TABBED_MENU_ITEMS = [
    "menuItemPageHistory", "menuItemDomainSettings",
    "menuItemFind", "menuItemPrint", "menuItemReaderMode", "menuItemDesktopMode",
    "menuItemDarkMode", "menuItemAddToHome", "menuItemAddBookmark", "menuItemShare",
    "menuItemAdBlock", "menuItemTranslate", "menuItemPageRequests", "menuItemConsole",
    "menuItemCookies", "menuItemForceReload", "menuItemLaunchApp", "menuItemPip",
    "menuItemCursor",
]


def _close_all_tabs(device, timeout: float = 30.0) -> None:
    """Close every open tab until the zero-tab empty state is reached.

    Uses the ``fulguris.action.CLOSE_ALL_TABS`` debug intent (the app must be
    running), which closes all tabs at once and leaves the empty state.
    """
    if _empty_state_visible(device):
        return
    device.launch_action("fulguris.action.CLOSE_ALL_TABS", wait=3.0)
    assert _wait_for_empty_state(device, timeout=timeout), (
        "closing all tabs did not reach the zero-tab empty state: the "
        f"empty-state view is not showing (nodes: {sorted(_node_texts(device))[:40]!r})"
    )


def test_empty_tab_shows_logo(device, ctx: dict) -> None:
    """Closing the last tab must NOT close the app.

    The browser shows the empty state (large Fulguris logo, no "New tab"
    button) and stays in the foreground. Typing a URL in the address field
    opens a fresh tab.
    """
    device.settle()
    device.key(keys.BACK, 1.0)  # drop any leftover sheet/keyboard state
    _close_all_tabs(device)
    assert _empty_state_visible(device), (
        "zero-tab empty state did not appear: the empty-state view is not "
        f"showing (nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    assert device.foreground_package() == device.package, (
        f"closing the last tab closed the app: foreground is "
        f"{device.foreground_package()!r}"
    )

    # The address field must be empty (no tab to show a label for), not the
    # app-name fallback of the toolbar label.
    field = device.field_text()
    assert field in ("", "Search"), (
        f"address field should be empty with no tabs, got {field!r}"
    )

    # The address field is the way back in: typing a URL must open a tab.
    device.navigate("example.com", reset=False)
    assert _wait_for_webview(device), (
        "typing a URL with no tabs open did not open a tab: no WebView in "
        f"the hierarchy (nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    assert device.foreground_package() == device.package, (
        f"app crashed when creating a tab from the empty state: "
        f"{device.foreground_package()!r}"
    )

    # Hygiene: back to the zero-tab state (the state this feature is about).
    _close_all_tabs(device)
    _wait_for_empty_state(device, timeout=30.0)


def test_empty_tab_menu_hides_tab_items(device, ctx: dict) -> None:
    """With zero tabs the 'Web page' menu switcher stays reachable (the tab
    menu is user-configurable) but the tab-specific items are hidden (no page
    to operate on); they reappear once a tab is open."""
    device.settle()
    device.key(keys.BACK, 1.0)
    _close_all_tabs(device)
    assert _empty_state_visible(device), "zero-tab empty state did not appear"

    # The 'Web page' switcher must stay in the main menu even with no tab.
    _open_more_menu(device)
    device.settle(timeout=20.0)
    assert "menuItemTabMenu" in _menu_item_ids(device), (
        "the 'Web page' menu switcher should stay reachable with no tab open"
    )

    # ...and the tab menu opens (the switcher dismisses the main menu and
    # reopens the popup in tab-menu mode), but its tab-specific items are hidden.
    _tap_id(device, ":id/menuItemTabMenu")
    device.settle(timeout=20.0)
    menu_ids = _menu_item_ids(device)
    device.key(keys.BACK, 0.8)  # close the (single) tab-menu popup
    leaked = [i for i in _TABBED_MENU_ITEMS if i in menu_ids]
    assert not leaked, (
        "tab-specific menu items should be hidden with no tab, but these are "
        f"present: {leaked!r}"
    )

    # Regression: with a tab open the tab-specific items come back.
    device.navigate("example.com", reset=False)
    assert _wait_for_webview(device), "a tab did not open after typing a URL"
    _open_webpage_menu(device)
    tab_ids = _menu_item_ids(device)
    device.key(keys.BACK, 0.8)
    visible_tab_items = [i for i in _TABBED_MENU_ITEMS if i in tab_ids]
    assert visible_tab_items, (
        f"tab-specific menu items missing with a tab open (expected at least "
        f"one of {_TABBED_MENU_ITEMS!r}): {sorted(tab_ids)!r}"
    )

    # Hygiene: back to the zero-tab state (what this feature is about).
    _close_all_tabs(device)
    _wait_for_empty_state(device, timeout=30.0)


def test_empty_tab_tabs_button_creates_tab(device, ctx: dict) -> None:
    """With zero tabs, tapping the tabs button must create a tab (it is the
    way back in), rather than opening an empty tab list / web-page menu."""
    device.settle()
    device.key(keys.BACK, 1.0)
    _close_all_tabs(device)
    assert _empty_state_visible(device), "zero-tab empty state did not appear"

    _tap_id(device, ":id/tabs_button")
    device.settle(timeout=20.0)
    assert _wait_for_webview(device), (
        "tapping the tabs button with no tab did not create a tab: no WebView "
        f"in the hierarchy (nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    assert device.foreground_package() == device.package, (
        f"app crashed when the tabs button created a tab: "
        f"{device.foreground_package()!r}"
    )

    # Hygiene: back to the zero-tab state.
    _close_all_tabs(device)
    _wait_for_empty_state(device, timeout=30.0)


def test_empty_tab_back_key_backgrounds_app(device, ctx: dict) -> None:
    """With zero tabs, back must background the app (not crash, not reopen a tab)."""
    device.settle()
    _close_all_tabs(device)
    assert _empty_state_visible(device), (
        "zero-tab empty state did not appear: the empty-state view is not "
        f"showing (nodes: {sorted(_node_texts(device))[:40]!r})"
    )

    device.key(keys.BACK, 2.0)
    # The activity finishes: the app is no longer in the foreground (it did
    # not crash — a crash would show a crash dialog or the launcher package,
    # which is also "not foreground", so just require it left the foreground).
    assert device.foreground_package() != device.package, (
        "back with zero tabs should background the app, but it is still "
        f"foregrounded: {device.foreground_package()!r}"
    )

    # Hygiene: bring the app back to a normal state.
    device.launch(wait=5.0)


FEATURE_GROUPS = {
    "empty-tab": [
        test_empty_tab_shows_logo,
        test_empty_tab_menu_hides_tab_items,
        test_empty_tab_tabs_button_creates_tab,
        test_empty_tab_back_key_backgrounds_app,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_empty_tab_shows_logo": (
        "Closing the last tab keeps the app alive on the empty state (large "
        "logo, no 'New tab' button); typing a URL in the field opens a fresh tab"
    ),
    "test_empty_tab_back_key_backgrounds_app": (
        "With zero tabs open, the back key backgrounds the app instead of "
        "crashing or reopening a tab"
    ),
    "test_empty_tab_menu_hides_tab_items": (
        "With zero tabs the 'Web page' switcher stays reachable but the tab "
        "menu hides its tab-specific items; they reappear once a tab is open"
    ),
    "test_empty_tab_tabs_button_creates_tab": (
        "With zero tabs, tapping the tabs button creates a tab (the way back "
        "in) instead of opening an empty tab list"
    ),
}
