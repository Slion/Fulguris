"""Probe: dump the downloads sheet layout (bounds, ids, enabled) on the Samsung.

Run: python scripts/tests/probe_downloads_sheet.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

from appdevice import resolve_devices  # noqa: E402
from framework import keys  # noqa: E402

DEV = resolve_devices("R58R91GBTZK", False, None)[0]

DEV.settle()
DEV.key(keys.BACK, 1.0)
DEV.launch_action("fulguris.action.OPEN_DOWNLOADS", wait=3.0)
time.sleep(2.0)
w, h = DEV.screen_size()
print(f"screen: {w}x{h}")


def dump(label):
    nodes = DEV.nodes()
    print(f"=== {label} ({len(nodes)} nodes) ===")
    for n in nodes:
        if not (n.text or n.content_desc):
            continue
        rid = n.resource_id.rsplit("/", 1)[-1] if n.resource_id else ""
        b = n.bounds
        bstr = f"[{b[0]},{b[1]}][{b[2]},{b[3]}]" if b else "-"
        print(f"  id={rid[:40]:40s} cls={n.cls.split('.')[-1][:16]:16s} en={int(n.enabled)} f={int(n.focused)} {bstr:24s} desc={n.content_desc[:30]!r} {n.text[:50]!r}")
    return nodes


nodes = dump("initial")
# Find and tap the 'Advanced' row, then re-dump to see the revealed actions.
adv = None
for n in nodes:
    if n.text == "Advanced" and n.bounds:
        adv = n
        break
if adv:
    DEV.tap((adv.bounds[0] + adv.bounds[2]) // 2, (adv.bounds[1] + adv.bounds[3]) // 2, wait=2.0)
    dump("after tapping Advanced")
    nodes2 = DEV.nodes()
    for want in ("Clean up", "Remove all", "Delete all"):
        hits = [n for n in nodes2 if n.text == want]
        print(f"  -> {want!r}: {len(hits)} node(s)", [(n.enabled, n.bounds) for n in hits])
else:
    print("no 'Advanced' row found")
