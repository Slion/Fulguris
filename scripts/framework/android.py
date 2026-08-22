"""Android implementation of the :class:`~framework.device.Device` contract.

``AndroidDevice`` binds the contract to one adb device serial + app package. Every
method forwards to the corresponding function in ``scripts/tools/adb.py`` — the
proven low-level driver — with the serial/package already applied. Keeping this a
thin delegation (rather than re-deriving behaviour) means the existing adb logic,
timings and retries are unchanged; this class only presents them as the
platform-neutral, object-oriented surface the framework and tests use.

Android/adb-only extras that have no cross-platform meaning (``reverse`` tunnels,
reading/writing shared-prefs, the raw serial) live here on the subclass, not on
the base ``Device``.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb

from .device import Device, Node
from .transport import AdbTransport


class AndroidDevice(Device):
    def __init__(self, serial: str, package: str | None = None):
        self.serial = serial
        self._package = package or adb.detect_package(serial)
        self.transport = AdbTransport(serial)

    # --- identity ----------------------------------------------------------

    @property
    def id(self) -> str:
        return self.serial

    @property
    def package(self) -> str:
        return self._package

    @property
    def platform(self) -> str:
        return "android"

    def label(self) -> str:
        return adb.device_label(self.serial)

    def config(self) -> dict:
        return adb.device_config(self.serial)

    def is_leanback(self) -> bool:
        return adb.is_leanback(self.serial)

    def foreground_package(self) -> str | None:
        return adb.foreground_package(self.serial)

    # --- input -------------------------------------------------------------

    def key(self, code: int, wait: float = 0.5) -> None:
        adb.key(self.serial, code, wait)

    def key_longpress(self, code: int, wait: float = 0.8) -> None:
        adb.key_longpress(self.serial, code, wait)

    def key_hold(self, code: int, ms: int, wait: float = 0.3) -> None:
        adb.key_hold(self.serial, code, ms, wait)

    def key_combination(self, *codes: int, wait: float = 0.6) -> None:
        adb.key_combination(self.serial, *codes, wait=wait)

    def ctrl_tab(self, wait: float = 0.9) -> None:
        adb.ctrl_tab(self.serial, wait)

    def tap(self, x: int, y: int, wait: float = 0.7) -> None:
        adb.tap(self.serial, x, y, wait)

    def type_text(self, text: str, wait: float = 0.5) -> None:
        adb.type_text(self.serial, text, wait)

    def clear_field(self) -> None:
        adb.clear_field(self.serial)

    def enter_edit(self) -> None:
        adb.enter_edit(self.serial)

    # --- app lifecycle / navigation ---------------------------------------

    def launch(self, wait: float = 5.0) -> None:
        adb.launch(self.serial, self._package, wait)

    def restart(self, wait: float = 5.0) -> None:
        adb.restart(self.serial, self._package, wait)

    def launch_action(self, action: str, wait: float = 2.0) -> None:
        adb.start_action(self.serial, self._package, action, wait)

    def start_component(self, component: str, action: str | None = None, wait: float = 2.0) -> None:
        adb.start_component(self.serial, component, action, wait)

    def force_stop(self) -> None:
        adb.force_stop(self.serial, self._package)

    def settle(self, timeout: float = 60.0) -> bool:
        return adb.settle(self.serial, self._package, timeout)

    def navigate(self, url: str, reset: bool | None = None) -> None:
        adb.navigate(self.serial, self._package, url, reset)

    @property
    def restart_between_tests(self) -> bool:
        return adb.RESTART_BETWEEN_TESTS

    # --- tabs --------------------------------------------------------------

    def open_tab_switcher(self, wait: float = 1.0) -> bool:
        return adb.open_tab_switcher(self.serial, wait)

    def tab_entries(self) -> list[tuple[str, tuple[int, int]]]:
        return adb.tab_entries(self.serial)

    def close_tabs(self, count: int, wait: float = 0.9) -> None:
        adb.close_tabs(self.serial, count, wait)

    def note_tab_opened(self) -> None:
        adb.note_tab_opened()

    # --- UI state ----------------------------------------------------------

    def nodes(self) -> list[Node]:
        return adb.nodes(self.serial)

    def find_node(self, id_suffix: str) -> Node | None:
        return adb.find_node(self.serial, id_suffix)

    def field_node(self) -> Node | None:
        return adb.field_node(self.serial)

    def field_focused(self) -> bool:
        return adb.field_focused(self.serial)

    def field_text(self) -> str:
        return adb.field_text(self.serial)

    def field_center(self) -> tuple[int, int] | None:
        return adb.field_center(self.serial)

    def webview_focused(self) -> bool:
        return adb.webview_focused(self.serial)

    def ime_shown(self) -> bool:
        return adb.ime_shown(self.serial)

    def dropdown_present(self) -> bool:
        return adb.dropdown_present(self.serial)

    def ssl_icon_visible(self) -> bool:
        return adb.ssl_icon_visible(self.serial)

    def reload_button_state(self) -> str:
        return adb.reload_button_state(self.serial)

    def reload_button_visible(self) -> bool:
        return adb.reload_button_visible(self.serial)

    def reload_button_center(self) -> tuple[int, int] | None:
        return adb.reload_button_center(self.serial)

    # --- display -----------------------------------------------------------

    def screen_size(self) -> tuple[int, int]:
        return adb.screen_size(self.serial)

    def screenshot(self, path: str) -> None:
        adb.screenshot(self.serial, path)

    # --- orientation -------------------------------------------------------

    def orientation_state(self):
        return adb.orientation_state(self.serial)

    def set_orientation(self, orientation: str, wait: float = 1.5) -> None:
        adb.set_orientation(self.serial, orientation, wait)

    def restore_orientation(self, *state) -> None:
        adb.restore_orientation(self.serial, *state)

    # --- Android/adb-only extras (not part of the cross-platform contract) --

    def reverse(self, remote_port: int, local_port: int | None = None) -> None:
        """Set up an adb reverse tunnel: device localhost:remote -> host local."""
        self.transport.reverse(remote_port, local_port)

    def reverse_remove(self, remote_port: int) -> None:
        self.transport.reverse_remove(remote_port)

    def read_prefs(self, rel_path: str) -> str:
        """Read a file from the app sandbox via ``run-as`` (e.g. a shared-prefs XML)."""
        return self.transport.shell(["shell", "run-as", self._package, "cat", rel_path])

    def write_prefs(self, rel_path: str, content: str) -> None:
        """Write ``content`` into the app sandbox at ``rel_path`` via a pushed temp file."""
        import subprocess
        import tempfile
        fd, local = tempfile.mkstemp(suffix=".xml")
        try:
            with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as f:
                f.write(content)
            dev_tmp = "/data/local/tmp/fw_prefs.xml"
            subprocess.run(["adb", "-s", self.serial, "push", local, dev_tmp], capture_output=True)
            subprocess.run(
                ["adb", "-s", self.serial, "shell", "run-as", self._package, "cp", dev_tmp, rel_path],
                capture_output=True,
            )
        finally:
            os.remove(local)
