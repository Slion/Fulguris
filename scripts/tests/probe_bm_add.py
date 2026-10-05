"""Probe: open the add-bookmark dialog, fill it, tap button1, and watch what
happens (toast? dialog still up? bookmark in the drawer?)."""
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


def dump(tag: str):
    print(f"=== {tag} ===", flush=True)
    for n in bm._nodes(device):
        rid = (n.resource_id or "").split("/")[-1]
        if n.text and (n.text or n.content_desc):
            print(f"  id={rid:<20} en={int(n.enabled)} {n.bounds} {n.text!r}", flush=True)


def foc() -> str:
    for n in bm._nodes(device):
        if getattr(n, "focused", False):
            return (n.resource_id or "").split("/")[-1]
    return "<none>"


def dump_fields(tag: str):
    print(f"--- {tag} (focus={foc()}) ---", flush=True)
    for rid in ("bookmark_title", "bookmark_url", "bookmark_folder"):
        n = adb.find_node(device.serial, f":id/{rid}")
        print(f"  {rid:<16} text={getattr(n, 'text', None)!r}", flush=True)


def main():
    bm._close_to_browser(device)
    device.navigate("https://www.example.org/")
    device.key_combination(adb.KEY_CTRL_LEFT, keys.KEY_B, wait=1.5)
    print("dialog up:", bm._wait_text(device, "Add bookmark", timeout=15.0, exact=True), flush=True)
    dump_fields("PRE (defaults)")

    # Step 1: tap title, then WAIT to see if focus jumps on its own (IME action).
    t = adb.find_node(device.serial, ":id/bookmark_title")
    device.tap(t.center[0], t.center[1], wait=1.0)
    for d in (0.5, 1.0, 2.0, 3.0):
        time.sleep(d - 0.5 if d != 0.5 else 0.5)
        print(f"focus at t+{d:.1f}s:", foc(), flush=True)

    # Step 2: Ctrl+A reliability (does the field text get selected/does focus stay?).
    device.key_combination(adb.KEY_CTRL_LEFT, keys.A, wait=0.8)
    print("focus after Ctrl+A:", foc(), flush=True)
    dump_fields("after Ctrl+A")

    # Step 3: single DEL to delete the selection, then type.
    device.key(keys.DEL, 0.4)
    print("focus after DEL:", foc(), flush=True)
    dump_fields("after DEL")
    device.type_text("AutoTest probe bm", 0.25)
    print("focus after type:", foc(), flush=True)
    dump_fields("after title fill")

    # Step 4: repeat for URL with the Ctrl+A / DEL / type sequence.
    u = adb.find_node(device.serial, ":id/bookmark_url")
    device.tap(u.center[0], u.center[1], wait=1.2)
    time.sleep(2.0)
    print("focus at url t+2.0s:", foc(), flush=True)
    device.key_combination(adb.KEY_CTRL_LEFT, keys.A, wait=0.8)
    device.key(keys.DEL, 0.4)
    print("focus after url DEL:", foc(), flush=True)
    device.type_text("https://www.example.org/autotest-probe", 0.25)
    print("focus after url type:", foc(), flush=True)
    dump_fields("after url fill")
    print("tapped OK:", bm._tap_ok(device), flush=True)
    for i in range(5):
        time.sleep(1.0)
        texts = [n.text for n in bm._nodes(device) if n.text]
        has_dialog = any("Add bookmark" in t for t in texts)
        snack = [t for t in texts if "ookmark" in t or "xists" in t or "rror" in t]
        print(f"t+{i+1}s dialog_up={has_dialog} snack={snack}", flush=True)

    bm._close_to_browser(device)
    bm._open_bookmarks_drawer(device)
    print("drawer entries:", bm._drawer_entries(device), flush=True)
    bm._close_drawer(device)


if __name__ == "__main__":
    main()
