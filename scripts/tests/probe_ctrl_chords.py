#!/usr/bin/env python3
"""Isolate whether the activity receives Ctrl chords at all on EMUI-10.

Ctrl+B (add bookmark) does not open its dialog on the P30 Pro. Ctrl+L and
Ctrl+P go through the SAME isCtrlOnly dispatch path, so:
  * if Ctrl+L focuses the field and Ctrl+P opens the tab drawer  -> B-specific
  * if neither works                                              -> no Ctrl chords reach the activity

Each attempt reports a concrete, observable effect:
  * Ctrl+L -> actionFocusTextField() -> address field becomes focused
  * Ctrl+P -> toggleTabs()            -> tab switcher opens (tabs_button rows)

Usage:
    python scripts/tests/probe_ctrl_chords.py --device SERIAL
"""
from __future__ import annotations

import argparse
import os
import sys
import time

_scripts = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for _d in (_scripts, os.path.join(_scripts, "tools"), os.path.join(_scripts, "tests")):
    if _d not in sys.path:
        sys.path.insert(0, _d)

from framework import keys  # noqa: E402
from appdevice import resolve_devices  # noqa: E402
import adb as tools_adb  # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True, help="adb serial")
    args = ap.parse_args()

    device = resolve_devices(args.device, False)[0]
    print(f"=== Ctrl-chord probe on {device.id} ===")

    device.restart()
    device.navigate("example.com")
    time.sleep(1.5)

    # --- Ctrl+L : focus the address field ---
    print("\n--- Ctrl+L (actionFocusTextField) ---")
    before = device.field_focused()
    device.key_combination(tools_adb.KEY_CTRL_LEFT, 42, wait=1.2)  # 42 = 'L'
    time.sleep(0.5)
    after = device.field_focused()
    print(f"field focused before={before} after={after}  -> {'WORKS' if after and not before else 'no effect'}")
    # reset focus away
    device.key(keys.DPAD_DOWN, wait=0.6)

    # --- Ctrl+P : toggle the tab switcher ---
    print("\n--- Ctrl+P (toggleTabs) ---")
    device.key_combination(tools_adb.KEY_CTRL_LEFT, 25, wait=1.5)  # 25 = 'P'
    time.sleep(0.5)
    tabs = device.tab_entries()
    print(f"tab entries visible: {len(tabs)}  -> {'WORKS' if tabs else 'no effect'}")
    device.key(keys.BACK, wait=0.8)

    # --- Ctrl+B : add bookmark (the failing one) ---
    print("\n--- Ctrl+B (action_add_bookmark) ---")
    device.key_combination(tools_adb.KEY_CTRL_LEFT, keys.KEY_B, wait=2.0)
    time.sleep(0.5)
    texts = [n.text for n in device.nodes() if n.text]
    print("has 'Add bookmark' dialog:", any("Add bookmark" in t for t in texts))
    device.key(keys.BACK, wait=0.8)

    device.launch(wait=4.0)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
