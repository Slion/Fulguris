"""Probe: how does the bookmarks drawer actually open?

Steps: back out -> open main menu -> dump nodes -> tap the 'Bookmarks' row ->
wait -> dump nodes + screenshot. Prints everything so we can see what the
menu looks like, what the 'Bookmarks' row is, and what opens (drawer? bottom
sheet? nothing?).
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

import adb  # noqa: E402
from framework import keys, resolve_devices  # noqa: E402

device = resolve_devices("R58R91GBTZK", False,
                         "net.slions.fulguris.full.agent.debug")[0]


def dump(tag: str):
    print(f"=== {tag} ===", flush=True)
    for n in device.nodes():
        if not (n.text or n.content_desc or (n.resource_id or "").split("/")[-1]):
            continue
        rid = (n.resource_id or "").split("/")[-1]
        print(f"  id={rid:<28} cls={n.cls:<22} en={int(n.enabled)} "
              f"f={int(n.focused)} {n.bounds} desc={n.content_desc!r} "
              f"{n.text!r}", flush=True)


def main():
    device.settle()
    for _ in range(5):
        device.key(keys.BACK, 1.0)
    device.settle()

    more = adb.find_node(device.serial, ":id/button_more")
    print("button_more:", more and more.bounds, flush=True)
    device.tap(*more.center, wait=1.5)
    dump("main menu")

    # Find the 'Bookmarks' row.
    nodes = device.nodes()
    bm = [n for n in nodes if n.text == "Bookmarks" and n.bounds]
    print("Bookmarks rows:", [(n.center, n.cls) for n in bm], flush=True)
    if not bm:
        return
    device.tap(*bm[-1].center, wait=2.0)
    dump("after tapping Bookmarks")
    time.sleep(1.5)
    dump("1.5s later")


if __name__ == "__main__":
    main()
