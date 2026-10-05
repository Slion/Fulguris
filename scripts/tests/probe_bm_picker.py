"""Probe: does the DocumentsUI picker's Search find the pushed import file by
name? Tap Search, type the filename, dump the results, tap it, confirm the
import snackbar. One run gives the exact node ids for a deterministic finder."""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

import adb  # noqa: E402
import bookmarks_tests as bm  # noqa: E402
from framework import resolve_devices, keys  # noqa: E402

device = resolve_devices("R58R91GBTZK", False,
                         "net.slions.fulguris.full.agent.debug")[0]


def dump(tag, filt=None):
    print(f"=== {tag} ===", flush=True)
    for n in bm._nodes(device):
        txt = (n.text or "")[:50]
        desc = (n.content_desc or "")[:50]
        rid = (n.resource_id or "").split("/")[-1]
        if filt and not (filt in txt or filt in desc or filt in rid):
            continue
        if rid or txt or desc:
            print(f"  id={rid:<20} {n.bounds} cls={getattr(n,'cls','')[-18:]:<18} "
                  f"text={txt!r} desc={desc!r}", flush=True)


def main():
    bm._close_to_browser(device)
    bm._push_import_file(device, bm.IMPORT_FILE, bm.IMPORT_REMOTE)
    print("pushed + indexed OK", flush=True)

    bm._open_backup_page(device)
    assert bm._tap_text(device, "Import", timeout=10.0, exact=True, index=0)
    time.sleep(3.0)

    # Tap the Search icon.
    s = None
    for n in bm._nodes(device):
        if (n.content_desc == "Search") or (n.resource_id or "").endswith("option_menu_search"):
            s = n
            break
    print("search node:", s.bounds if s else None, flush=True)
    device.tap(s.center[0], s.center[1], wait=1.5)
    dump("after search tap", filt="earch")

    # Type the filename.
    device.type_text("autotest_import_probe", 0.15)
    time.sleep(2.0)
    dump("after typing")

    # Did a tappable result for the exact file appear?
    target = "autotest_import_probe.html"
    for n in bm._nodes(device):
        if n.bounds and target in (n.text or ""):
            print("RESULT FOUND:", n.resource_id, n.bounds, n.text, flush=True)
            device.tap(n.center[0], n.center[1], wait=2.0)
            break
    else:
        print("RESULT NOT FOUND via search", flush=True)
        return

    # Confirm import happened (snackbar is readable, unlike the toast).
    deadline = time.time() + 20.0
    while time.time() < deadline:
        if "were imported" in "\n".join(bm._texts(device)):
            print("IMPORT SNACKBAR SEEN:", [t for t in bm._texts(device) if "import" in t.lower()], flush=True)
            break
        time.sleep(0.7)
    bm._close_to_browser(device)


if __name__ == "__main__":
    main()
