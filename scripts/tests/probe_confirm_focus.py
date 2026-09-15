"""Probe: the confirm key (D-pad center) reaching the FOCUSED control instead of the
cursor target after a cursor off->on toggle (focus left on the toolbar menu button).

Reproduces the user report:
    launch app, cursor on, cursor off (focus -> button_more), cursor on,
    press D-pad center -> the browser menu opens instead of clicking the page under
    the cursor.

Steps (mirrors scripts/tests/cursor_tests.py):
  1. load cursor_target.html (reports hover / click coords / scroll in document.title)
  2. enable logging pref (so Timber "Cursor:" lines land in logcat)
  3. toggle on, off, on
  4. report focused node + field text at each step
  5. press D-pad center, report field text (a page click would change the title to
     "<x>,<y>") and whether the main menu opened (a :id/menuItem* node appears)
  6. print the recent "Cursor:" logcat lines

Usage:  python scripts/tests/probe_confirm_focus.py [serial]
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))

from framework.android import AndroidDevice  # noqa: E402
from framework import keys  # noqa: E402
from cursor_tests import _load_target, _toggle, _title, _overlay_present, _focused_resource_id  # noqa: E402


def focused_summary(device) -> str:
    parts = []
    for n in device.nodes():
        if n.focused:
            parts.append(f"{n.resource_id or n.cls} text={n.text!r}")
    return "; ".join(parts) if parts else "(no focused node)"


def menu_open(device) -> bool:
    return device.find_node(":id/menuItemExit") is not None or \
        device.find_node(":id/menuItemSettings") is not None


def main() -> None:
    if len(sys.argv) > 1:
        device = AndroidDevice(sys.argv[1])
    else:
        from framework import resolve_devices
        devices = resolve_devices(device=None, use_all=True)
        if not devices:
            raise SystemExit("no devices")
        device = devices[0]
    print(f"device: {device.id}  package: {device.package}")

    # Enable the logs pref so the "Cursor:" Timber lines show up in logcat.
    import re as _re
    device.force_stop()
    from cursor_tests import _prefs_path
    xml = device.read_prefs(_prefs_path(device.package))
    if "<map" in xml:
        entry = '<boolean name="pref_key_logs" value="true" />'
        pat = _re.compile(r'<boolean name="pref_key_logs" value="[^"]*" />')
        xml = pat.sub(entry, xml) if pat.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
        device.write_prefs(_prefs_path(device.package), xml)
        print("logs pref: enabled")
    else:
        print("logs pref: main prefs not initialized, skipping")

    device.restart()
    device.settle()
    _load_target(device)
    print(f"after load:     focus=<{focused_summary(device)}>  field={_title(device)!r}")

    _toggle(device)
    print(f"cursor ON  #1:  overlay={_overlay_present(device)}  focus=<{focused_summary(device)}>")

    _toggle(device)
    print(f"cursor OFF:     overlay={_overlay_present(device)}  focus=<{focused_summary(device)}>")

    _toggle(device)
    print(f"cursor ON  #2:  overlay={_overlay_present(device)}  focus=<{focused_summary(device)}>")

    before = _title(device)
    device.key(keys.DPAD_CENTER, wait=1.5)
    after = _title(device)
    menu = menu_open(device)
    print(f"after select:   field before={before!r} after={after!r}  menu_open={menu}")
    print(f"focus now:      <{focused_summary(device)}>")

    if menu:
        device.key(keys.BACK, wait=1.0)

    # Dump the recent Cursor: lines from logcat.
    try:
        out = device.transport.shell(
            ["shell", "logcat", "-d", "-t", "400", "-s", "Cursor:*", "Chromium:*"]
        )
        print("\n--- logcat (Cursor) ---")
        print(out[-6000:])
    except Exception as e:  # noqa: BLE001
        print(f"logcat read failed: {e}")

    _toggle(device)  # leave cursor off


if __name__ == "__main__":
    main()
