"""The Fulguris app profile for the :mod:`autotest` framework.

:class:`FulgurisDevice` is a :class:`~autotest.android.device.AndroidDevice`
subclass that knows the concrete app under test: Fulguris. It adds the
browser-specific behavior that the generic :class:`~autotest.device.Device`
contract deliberately leaves out:

* which package to test and which activities to launch (splash / main / settings);
* how a URL is loaded in the address bar (``navigate`` / edit mode);
* the toolbar view ids (the address field, tabs button, SSL icon, reload button);
* tab open/close (CTRL+W) and the per-test opened-tab count;
* the debug cursor-teleport broadcast;
* the "Fulguris tests" device progress notification.

The test suites in this folder are written against the ``Device`` contract; they
receive a :class:`FulgurisDevice` from :func:`resolve_devices`, so every
``device.navigate(...)`` / ``device.field_text()`` / ``device.close_tabs(...)``
they call resolves to this profile's implementation.
"""
from __future__ import annotations

import os
import sys
import time

# Make the AutoTest submodule importable when this file is loaded as a plain
# module (the test suites and runner import it without an installed package).
_AT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                   "..", "..", "subs", "AutoTest"))
if _AT not in sys.path:
    sys.path.insert(0, _AT)

from autotest import keys  # noqa: E402
from autotest.android import adb  # noqa: E402
from autotest.android.device import AndroidDevice  # noqa: E402
from autotest.node import Node  # noqa: E402
from autotest.session import Session  # noqa: E402

# --- Fulguris app constants -------------------------------------------------

#: Default package / launcher activities for the slionsFullAgentDebug variant.
DEFAULT_PACKAGE = "net.slions.fulguris.full.agent.debug"
LAUNCH_ACTIVITY = "fulguris.activity.SplashActivity"
MAIN_ACTIVITY = "fulguris.activity.MainActivity"

# Toolbar view ids (the address bar and its companions).
VIEW_SEARCH = ":id/search"
VIEW_TABS_BUTTON = ":id/tabs_button"
VIEW_TAB_ENTRY = "textTab"
VIEW_SSL_STATUS = ":id/search_ssl_status"
VIEW_RELOAD_BUTTON = "button_reload"

# Progress-notification identity (kept distinct so it never clashes with the
# framework default).
NOTIFY_PKG = "com.android.shell"
NOTIFY_TAG = "fulguris-test-run"
NOTIFY_ID = 2020
NOTIFY_TITLE = "Fulguris tests"


def detect_package(serial: str) -> str:
    """The installed Fulguris package on ``serial`` (agent build preferred)."""
    return adb.detect_package(serial, "fulguris", (".agent.", ".debug"))


class FulgurisDevice(AndroidDevice):
    """An Android device running Fulguris, with its app-specific capabilities."""

    def __init__(self, serial: str, package: str | None = None,
                 session: Session | None = None):
        super().__init__(serial, package or detect_package(serial), session)
        self.launch_activity = LAUNCH_ACTIVITY
        self.main_activity = MAIN_ACTIVITY

    # --- app lifecycle (Fulguris specifics) --------------------------------

    def _start_splash(self) -> None:
        adb.start_component(self.serial, f"{self._package}/{self.launch_activity}", wait=2.0)

    def launch(self, wait: float = 5.0) -> None:
        self._start_splash()
        if not self.settle():
            print(f"WARNING: {self._package} did not settle on {self.label()}")

    def restart(self, wait: float = 5.0) -> None:
        adb.force_stop(self.serial, self._package)
        time.sleep(0.5)
        self.launch(wait)
        # A fresh launch restores the previous session, so any tabs this test
        # "opened" before the restart no longer exist; only count tabs opened
        # after it (keeps the runner's end-of-test cleanup accurate).
        self.session.reset_opened()

    def settle(self, timeout: float = 60.0) -> bool:
        """Wait until Fulguris is foregrounded and its address bar is ready.

        Launches the app if it is not already foregrounded. The address field
        being present in the view hierarchy means the main browser screen is up
        and can receive input.
        """
        def ready() -> bool:
            if adb.foreground_package(self.serial) != self._package:
                self._start_splash()
                return False
            try:
                return adb.view_present(self.serial, VIEW_SEARCH.removeprefix(":id/"))
            except Exception:  # noqa: BLE001 - adb hiccup, keep polling
                return False

        return adb.wait_until(ready, timeout, interval=0.5)

    def launch_action(self, action: str, wait: float = 2.0) -> None:
        """Start the main activity with a custom intent action (app must be running).

        The main activity is ``singleTask`` so this goes through its ``onNewIntent``.
        """
        adb.start_component(self.serial, f"{self._package}/{self.main_activity}", action, wait)

    # --- address bar -------------------------------------------------------

    def enter_edit(self) -> None:
        """Focus the address field for navigation then enter edit mode.

        After entering edit mode the field selects all its text (the address
        bar's edit guard runs ~400ms in), so a subsequent ``type_text`` replaces
        the current URL. We deliberately do NOT empty the field first: on the
        TV-style address bar an empty field drops out of edit mode and shows the
        label again, which would swallow the typed characters. Replacing the
        selection avoids that whole class of flakiness.
        """
        self.key(keys.SEARCH, 0.7)      # focus for navigation
        self.key(keys.DPAD_CENTER, 0.9)  # enter edit mode; guard selects all text

    def navigate(self, url: str, reset: bool | None = None) -> None:
        """Load the given URL, replacing whatever the edit field already holds.

        Waits for the app to be foregrounded and settled before sending any keys.
        When ``reset`` is None, the run-wide default (see
        :class:`~autotest.session.Session`) decides: restart the app for a clean,
        deterministic state, or just settle on the already-running app (faster).

        In no-restart mode the address field is first returned to the unfocused
        label state (back: hide keyboard / cancel edit / leave the field) so the
        previous test's field state cannot leak into the URL typing below.

        A typed URL opens a NEW tab by default, so this increments the session's
        opened-tab count; the runner closes those tabs again after the test
        unless keep-tabs is set.
        """
        if (self.session.restart_between_tests if reset is None else reset):
            self.restart()
        else:
            self.settle()
            for _ in range(3):
                if not self.field_focused():
                    break
                self.key(keys.BACK, 0.8)
        self.enter_edit()
        self.type_text(url, 0.4)  # replaces the selected URL
        self.key(keys.ENTER, 3.0)
        self.session.note_opened()

    def field_node(self) -> Node | None:
        return adb.find_node(self.serial, VIEW_SEARCH)

    def field_focused(self) -> bool:
        n = self.field_node()
        return bool(n and n.focused)

    def field_text(self) -> str:
        n = self.field_node()
        return n.text if n else ""

    def field_center(self) -> tuple[int, int] | None:
        n = self.field_node()
        if not n or not n.bounds:
            return None
        x1, y1, x2, y2 = n.bounds
        return (x1 + x2) // 2, (y1 + y2) // 2

    # --- tabs --------------------------------------------------------------

    def open_tab_switcher(self, wait: float = 1.0) -> bool:
        """Open the tab list by tapping the toolbar tabs button. Returns False if absent."""
        n = adb.find_node(self.serial, VIEW_TABS_BUTTON)
        if not n or not n.bounds:
            return False
        x1, y1, x2, y2 = n.bounds
        adb.tap(self.serial, (x1 + x2) // 2, (y1 + y2) // 2, wait)
        return True

    def tab_entries(self) -> list[tuple[str, tuple[int, int]]]:
        """(title, center) for each tab row in the open tab switcher (`textTab` views)."""
        result: list[tuple[str, tuple[int, int]]] = []
        for nd in adb.nodes(self.serial):
            if nd.resource_id.endswith(VIEW_TAB_ENTRY) and nd.bounds:
                x1, y1, x2, y2 = nd.bounds
                result.append((nd.text, ((x1 + x2) // 2, (y1 + y2) // 2)))
        return result

    def close_tabs(self, count: int, wait: float = 0.9) -> None:
        """Close ``count`` tabs with CTRL+W. Cheap: key chords only, no uiautomator."""
        for _ in range(count):
            adb.key_combination(self.serial, adb.KEY_CTRL_LEFT, adb.KEY_CTRL_W, wait=wait)

    # --- address-bar status views -----------------------------------------

    def ssl_icon_visible(self) -> bool:
        """True if the SSL status icon in the address bar is currently visible.

        uiautomator omits GONE views from the dump, so presence with a non-empty
        bounds box means the icon is shown.
        """
        n = adb.find_node(self.serial, VIEW_SSL_STATUS)
        if not n or not n.bounds:
            return False
        w = n.bounds[2] - n.bounds[0]
        h = n.bounds[3] - n.bounds[1]
        return w > 0 and h > 0

    def reload_button_state(self) -> str:
        """Current state of the toolbar reload/stop button: VISIBLE/INVISIBLE/GONE/NOTFOUND."""
        flag = adb.view_flag(self.serial, VIEW_RELOAD_BUTTON.removeprefix(":id/"))
        if flag is None:
            return "NOTFOUND"
        return {"V": "VISIBLE", "I": "INVISIBLE", "G": "GONE"}[flag]

    def reload_button_visible(self) -> bool:
        return self.reload_button_state() == "VISIBLE"

    def reload_button_center(self) -> tuple[int, int] | None:
        """Screen center of the toolbar reload/stop button, for tapping it.

        uiautomator does not expose the button itself, but it sits immediately to
        the left of the tabs button with the same size, so we derive its position
        from tabs_button (which uiautomator does report).
        """
        n = adb.find_node(self.serial, VIEW_TABS_BUTTON)
        if not n or not n.bounds:
            return None
        x1, y1, x2, y2 = n.bounds
        w = x2 - x1
        return x1 - w // 2, (y1 + y2) // 2

    # --- Fulguris debug hooks ---------------------------------------------

    def cursor_teleport(self, x: float, y: float, wait: float = 0.6) -> None:
        """Teleport the on-screen cursor to screen point ``(x, y)`` (debug builds only).

        The cursor overlay is the top-most child of the root and is laid out at the
        screen origin, so overlay-local coordinates equal screen coordinates. A
        D-pad press count can't deterministically land the cursor over a specific
        control across devices, so tests use this to place it exactly over a
        control and then exercise the confirm key / hover against it. No-op on
        release builds (the receiver is not registered there).
        """
        adb.broadcast(self.serial, self._package,
                      f"{self._package}.action.cursor_test_teleport",
                      {"x": x, "y": y}, wait)

    # --- progress notification --------------------------------------------

    def post_notification(self, text: str) -> None:
        adb.post_notification(self.serial, text, pkg=NOTIFY_PKG,
                              tag=NOTIFY_TAG, nid=NOTIFY_ID, title=NOTIFY_TITLE)

    def dismiss_notification(self) -> None:
        adb.dismiss_notification(self.serial, pkg=NOTIFY_PKG,
                                 tag=NOTIFY_TAG, nid=NOTIFY_ID)


def resolve_devices(device: str | None, use_all: bool,
                    package: str | None = None) -> list[FulgurisDevice]:
    """Resolve the selected target(s) into :class:`FulgurisDevice` objects.

    Mirrors the adb device selection (exiting with a message when ambiguous);
    when ``package`` is None it is auto-detected per device.
    """
    serials = adb.resolve_devices(device, use_all)
    return [FulgurisDevice(serial, package) for serial in serials]
