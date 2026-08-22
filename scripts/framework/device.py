"""The platform-agnostic ``Device`` contract that tests are written against.

A test receives a :class:`Device` and drives it with high-level, semantic calls
(``device.key(keys.DPAD_DOWN)``, ``device.navigate(url)``, ``device.field_text()``)
that carry no assumption about *how* the device is reached. The concrete
:class:`~framework.android.AndroidDevice` implements this over adb today; another
platform only needs to provide its own ``Device`` subclass and transport, and the
existing tests run unchanged.

``Node`` is re-exported from the adb layer so callers have a single import point
for the UI-node shape returned by :meth:`Device.nodes` / :meth:`Device.find_node`.
"""
from __future__ import annotations

import abc
import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
from adb import Node  # noqa: F401  (re-exported as framework.device.Node)

from .transport import Transport


class Device(abc.ABC):
    """A target under test, addressed through a platform-neutral API.

    Implementations bind this contract to a concrete platform + transport (e.g.
    :class:`~framework.android.AndroidDevice` over adb). Every method here is what
    a cross-platform test may rely on; platform-only extras live on the subclass.
    """

    #: The transport used to reach this device (e.g. an :class:`AdbTransport`).
    transport: Transport

    # --- identity ----------------------------------------------------------

    @property
    @abc.abstractmethod
    def id(self) -> str:
        """A stable unique identifier for this device (e.g. an adb serial)."""

    @property
    def safe_id(self) -> str:
        """A filesystem-safe form of :attr:`id`, for artifact filenames."""
        return re.sub(r"[^A-Za-z0-9_.-]", "_", self.id)

    @property
    @abc.abstractmethod
    def package(self) -> str:
        """The app package / bundle id under test."""

    @property
    @abc.abstractmethod
    def platform(self) -> str:
        """Short platform name, e.g. ``"android"``."""

    @abc.abstractmethod
    def label(self) -> str:
        """Human-readable device label for logs (id + friendly name)."""

    @abc.abstractmethod
    def config(self) -> dict:
        """Describe the device + current screen configuration (see config_id)."""

    @abc.abstractmethod
    def is_leanback(self) -> bool:
        """True on a TV-style (D-pad/remote, no touch) device."""

    @abc.abstractmethod
    def foreground_package(self) -> str | None:
        """Package of the top resumed activity, if any."""

    # --- input -------------------------------------------------------------

    @abc.abstractmethod
    def key(self, code: int, wait: float = 0.5) -> None:
        """Send a single key (see :mod:`framework.keys`)."""

    @abc.abstractmethod
    def key_longpress(self, code: int, wait: float = 0.8) -> None:
        """Send a long-press key (sets the platform long-press flag)."""

    @abc.abstractmethod
    def key_hold(self, code: int, ms: int, wait: float = 0.3) -> None:
        """Press and hold a key for ``ms`` milliseconds, then release it."""

    @abc.abstractmethod
    def key_combination(self, *codes: int, wait: float = 0.6) -> None:
        """Send a chord of keys pressed together (e.g. CTRL+TAB)."""

    @abc.abstractmethod
    def ctrl_tab(self, wait: float = 0.9) -> None:
        """Switch to the next tab as a keyboard user would (CTRL+TAB)."""

    @abc.abstractmethod
    def tap(self, x: int, y: int, wait: float = 0.7) -> None:
        """Tap absolute screen coordinates."""

    @abc.abstractmethod
    def type_text(self, text: str, wait: float = 0.5) -> None:
        """Type text into the focused field."""

    @abc.abstractmethod
    def clear_field(self) -> None:
        """Clear the focused edit field."""

    @abc.abstractmethod
    def enter_edit(self) -> None:
        """Focus the address field and enter edit mode (selecting its text)."""

    # --- app lifecycle / navigation ---------------------------------------

    @abc.abstractmethod
    def launch(self, wait: float = 5.0) -> None:
        ...

    @abc.abstractmethod
    def restart(self, wait: float = 5.0) -> None:
        ...

    @abc.abstractmethod
    def launch_action(self, action: str, wait: float = 2.0) -> None:
        """Start the app's main activity with a custom intent action (app must be running)."""

    @abc.abstractmethod
    def start_component(self, component: str, action: str | None = None, wait: float = 2.0) -> None:
        """Start an activity by fully-qualified component (``package/.Activity``)."""

    @abc.abstractmethod
    def force_stop(self) -> None:
        ...

    @abc.abstractmethod
    def settle(self, timeout: float = 60.0) -> bool:
        """Wait until the app is foregrounded and its main UI is ready."""

    @abc.abstractmethod
    def navigate(self, url: str, reset: bool | None = None) -> None:
        """Load ``url`` in the address bar (see the runner's restart policy)."""

    @property
    @abc.abstractmethod
    def restart_between_tests(self) -> bool:
        """Whether navigate() restarts the app when reset is unspecified."""

    # --- tabs --------------------------------------------------------------

    @abc.abstractmethod
    def open_tab_switcher(self, wait: float = 1.0) -> bool:
        ...

    @abc.abstractmethod
    def tab_entries(self) -> list[tuple[str, tuple[int, int]]]:
        ...

    @abc.abstractmethod
    def close_tabs(self, count: int, wait: float = 0.9) -> None:
        ...

    @abc.abstractmethod
    def note_tab_opened(self) -> None:
        """Record that the test opened a tab outside of navigate()."""

    # --- UI state ----------------------------------------------------------

    @abc.abstractmethod
    def nodes(self) -> list[Node]:
        ...

    @abc.abstractmethod
    def find_node(self, id_suffix: str) -> Node | None:
        ...

    @abc.abstractmethod
    def field_node(self) -> Node | None:
        ...

    @abc.abstractmethod
    def field_focused(self) -> bool:
        ...

    @abc.abstractmethod
    def field_text(self) -> str:
        ...

    @abc.abstractmethod
    def field_center(self) -> tuple[int, int] | None:
        ...

    @abc.abstractmethod
    def webview_focused(self) -> bool:
        ...

    @abc.abstractmethod
    def ime_shown(self) -> bool:
        """True if the on-screen keyboard is shown."""

    @abc.abstractmethod
    def dropdown_present(self) -> bool:
        """True if a suggestions/autocomplete popup is present."""

    @abc.abstractmethod
    def ssl_icon_visible(self) -> bool:
        ...

    @abc.abstractmethod
    def reload_button_state(self) -> str:
        """VISIBLE / INVISIBLE / GONE / NOTFOUND for the toolbar reload/stop button."""

    @abc.abstractmethod
    def reload_button_visible(self) -> bool:
        ...

    @abc.abstractmethod
    def reload_button_center(self) -> tuple[int, int] | None:
        ...

    # --- display -----------------------------------------------------------

    @abc.abstractmethod
    def screen_size(self) -> tuple[int, int]:
        ...

    @abc.abstractmethod
    def screenshot(self, path: str) -> None:
        ...

    # --- orientation -------------------------------------------------------

    @abc.abstractmethod
    def orientation_state(self):
        """Snapshot the orientation so it can be restored later."""

    @abc.abstractmethod
    def set_orientation(self, orientation: str, wait: float = 1.5) -> None:
        ...

    @abc.abstractmethod
    def restore_orientation(self, *state) -> None:
        ...
