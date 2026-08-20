"""Transport layer: how the framework physically reaches a target device.

A ``Transport`` is the low-level pipe used to run commands, move files and grab
the screen on a device. Today the only implementation is :class:`AdbTransport`
(Android Debug Bridge), but isolating the pipe behind this small protocol is what
lets the rest of the framework stay transport-agnostic — a future iOS/web/serial
backend only has to provide its own ``Transport`` (and matching ``Device``).

The Android transport is a thin wrapper over the battle-tested ``adb`` helper
module in ``scripts/tools/adb.py`` so behaviour (retries, UTF-8 decoding, …) is
unchanged; this package only re-layers it.
"""
from __future__ import annotations

import os
import sys
from typing import Protocol, runtime_checkable

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb  # low-level adb plumbing (command execution, install, screencap)


@runtime_checkable
class Transport(Protocol):
    """The minimal pipe a :class:`~framework.device.Device` needs to a target."""

    #: Short name of the transport, e.g. ``"adb"``.
    name: str

    def shell(self, args: list[str], timeout: int = 30) -> str:
        """Run a command on the device and return its stdout."""
        ...

    def screencap(self, path: str) -> None:
        """Capture the device screen to a PNG at ``path`` on the host."""
        ...


class AdbTransport:
    """Android Debug Bridge transport, bound to one device serial."""

    name = "adb"

    def __init__(self, serial: str):
        self.serial = serial

    def shell(self, args: list[str], timeout: int = 30) -> str:
        """Run a raw adb command (e.g. ``["shell", "wm", "size"]``)."""
        return adb._adb(self.serial, args, timeout)

    def install(self, apk: str) -> bool:
        return adb.install_apk(self.serial, apk)

    def screencap(self, path: str) -> None:
        adb.screenshot(self.serial, path)

    def reverse(self, remote_port: int, local_port: int | None = None) -> None:
        """Forward ``localhost:remote_port`` on the device to the host's ``local_port``."""
        local_port = local_port if local_port is not None else remote_port
        self.shell(["reverse", f"tcp:{remote_port}", f"tcp:{local_port}"])

    def reverse_remove(self, remote_port: int) -> None:
        self.shell(["reverse", "--remove", f"tcp:{remote_port}"])
