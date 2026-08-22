"""Probe whether a long press of the action key (DPAD center) opens the WebView context menu on
the element under the cursor.

Loads the cursor harness's context_target.html (a full-screen link), turns the cursor on,
sends a system long-press of the action key (DPAD center), and reports:
  - the page title (the link sets it to "ctx" on a DOM `contextmenu` event),
  - the window list (a context menu / popup window would show here),
  - the on-screen node texts (context-menu item labels),
  - a screenshot (scripts/tests/out/context_after.png) for a visual check.

    python scripts/tests/probe_context_menu.py --device 192.168.178.67:5555
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb  # noqa: E402
import cursor_tests  # noqa: E402
import framework  # noqa: E402
from framework import keys  # noqa: E402

SERIAL = "192.168.178.67:5555"
OUT = os.path.join(os.path.dirname(__file__), "out", "context_after.png")


def main() -> None:
    device = framework.AndroidDevice(SERIAL)
    device.settle()
    print(f"fg={device.foreground_package()!r}")

    cursor_tests._load_page(device, "context_target.html")
    print(f"loaded   title={cursor_tests._title(device)!r}")
    print(f"before   overlay={'yes' if cursor_tests._overlay_present(device) else 'no'}  "
          f"dropdown={device.dropdown_present()}")

    cursor_tests._toggle(device)
    print(f"cursor on overlay={'yes' if cursor_tests._overlay_present(device) else 'no'}")

    # Send the real action-key long press and check whether dispatchLongPress fired.
    # A deliberate 1.5 s hold (the action-key threshold is 1 s; the OS's ~400 ms
    # FLAG_LONG_PRESS is deliberately ignored).
    print("overlay present before key:", cursor_tests._overlay_present(device))
    print("sending action-key (DPAD center) 1.5 s hold ...")
    device.key_hold(keys.DPAD_CENTER, 1500)
    print(f"after    title={cursor_tests._title(device)!r}")
    log = adb._adb(device.id, ["shell", "logcat", "-d"])
    for line in log.splitlines():
        if "Cursor:" in line and "KEY" not in line:
            print(f"log: {line[-110:]}")
    print(f"         overlay={'yes' if cursor_tests._overlay_present(device) else 'no'}  "
          f"dropdown={device.dropdown_present()}")

    windows = adb._adb(device.id, ["shell", "dumpsys", "window", "windows"])
    print("window list (context/popup/menu lines):")
    for line in windows.splitlines():
        low = line.lower()
        if any(k in low for k in ("context", "popup", "menu", "toast")):
            print(f"   {line.strip()[:160]}")

    print("node texts on screen:")
    for n in device.nodes():
        if n.text.strip():
            print(f"   {n.cls.split('.')[-1]:16} {n.text.strip()!r}  id={n.resource_id.split('/')[-1]}")

    device.screenshot(OUT)
    print(f"screenshot -> {os.path.relpath(OUT)}")


if __name__ == "__main__":
    main()
