"""Probe: dump the add-bookmark dialog's button node ids + a folder rename
dialog's, so we can tap the positive button by resource id (locale-proof)
instead of matching 'OK'/'Okay' text.
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

import adb  # noqa: E402
from framework import keys, resolve_devices  # noqa: E402
import bookmarks_tests as bm  # noqa: E402

device = resolve_devices("R58R91GBTZK", False,
                         "net.slions.fulguris.full.agent.debug")[0]


def dump_buttons(tag: str):
    print(f"=== {tag} (buttons + texts) ===", flush=True)
    for n in device.nodes():
        rid = (n.resource_id or "").split("/")[-1]
        if rid.startswith("button") and n.bounds:
            print(f"  id={rid:<14} en={int(n.enabled)} {n.bounds} {n.text!r}", flush=True)
    # also show any short standalone texts that look like dialog buttons
    for n in device.nodes():
        if n.text and n.bounds and len(n.text) <= 8:
            rid = (n.resource_id or "").split("/")[-1]
            print(f"  txt id={rid:<14} {n.bounds} {n.text!r}", flush=True)


def main():
    device.settle()
    bm._close_to_browser(device)
    device.navigate("https://www.example.org/")
    device.key_combination(adb.KEY_CTRL_LEFT, keys.KEY_B, wait=1.5)
    print("dialog up:", bm._wait_text(device, "Add bookmark", timeout=15.0, exact=True), flush=True)
    dump_buttons("add-bookmark dialog")
    # back out of the dialog
    device.key(keys.BACK, 1.0)
    device.key(keys.BACK, 1.0)


if __name__ == "__main__":
    main()
