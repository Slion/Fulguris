#!/usr/bin/env python3
"""Dump the toolbar overflow menu structure (all nodes + resource ids)."""
from __future__ import annotations
import argparse, os, sys, time
_scripts = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for _d in (_scripts, os.path.join(_scripts, "tools"), os.path.join(_scripts, "tests")):
    if _d not in sys.path:
        sys.path.insert(0, _d)
from appdevice import resolve_devices  # noqa: E402
import adb as tools_adb  # noqa: E402

def main() -> int:
    ap = argparse.ArgumentParser(); ap.add_argument("--device", required=True)
    a = ap.parse_args()
    device = resolve_devices(a.device, False)[0]
    device.restart(); device.navigate("https://www.example.org/"); time.sleep(1.5)
    menu_btn = None
    deadline = time.time() + 20.0
    while time.time() < deadline:
        menu_btn = tools_adb.find_node(device.serial, ":id/button_more")
        if menu_btn and menu_btn.bounds: break
        time.sleep(1.0)
    device.tap(*menu_btn.center, wait=2.0)
    nodes = device.nodes()
    print(f"total nodes: {len(nodes)}")
    for n in nodes:
        rid = (n.resource_id or "").split("/")[-1]
        if rid and (rid.startswith("menuItem") or "menu" in rid.lower() or "bookmark" in rid.lower() or "Button" in rid or "TextView" in n.cls):
            print(f"  id={rid:40s} cls={n.cls:35s} text={n.text!r:40s} bounds={n.bounds}")
    device.key(4, wait=1.0)  # back
    device.launch(wait=4.0)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
