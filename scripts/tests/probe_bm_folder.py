"""Probe: what does the bookmarks drawer look like for FOLDER rows (which
layout/ids do they expose), and does the pushed import file show up in the
DocumentsUI picker?"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

import adb  # noqa: E402
import bookmarks_tests as bm  # noqa: E402

device = bm.__dict__["device"] if "device" in bm.__dict__ else None
from framework import resolve_devices  # noqa: E402

device = resolve_devices("R58R91GBTZK", False,
                         "net.slions.fulguris.full.agent.debug")[0]


def dump_all(tag: str):
    print(f"=== {tag} ===", flush=True)
    for n in bm._nodes(device):
        rid = (n.resource_id or "").split("/")[-1]
        desc = (n.content_desc or "")[:40]
        txt = (n.text or "")[:60]
        if rid or txt or desc:
            print(f"  id={rid:<22} {n.bounds} text={txt!r} desc={desc!r}", flush=True)


def main():
    bm._close_to_browser(device)
    # 1) Drawer: dump every node so we can see folder rows vs bookmark rows.
    bm._open_bookmarks_drawer(device)
    print("drawer entries:", bm._drawer_entries(device), flush=True)
    dump_all("drawer")
    bm._close_drawer(device)

    # 2) Import file: push + scan, then check what the shell sees.
    bm._push_import_file(device, bm.IMPORT_FILE, bm.IMPORT_REMOTE)
    out = device.transport.shell(["shell", "ls", "-la", "/sdcard/Download"], timeout=15)
    print("--- ls /sdcard/Download ---", flush=True)
    print("\n".join(l for l in out.splitlines() if "autotest_import" in l or "FulgurisBookmarks" in l), flush=True)

    # 3) Open the real SAF picker via settings and dump it.
    bm._open_backup_page(device)
    assert bm._tap_text(device, "Import", timeout=10.0, exact=True, index=0)
    time.sleep(3.0)
    dump_all("picker after 3s")
    # Scroll the picker down to see if the file is further down.
    for i in range(4):
        bm._scroll_view(device, 1, down=True)
    dump_all("picker after 4 scrolls")


if __name__ == "__main__":
    main()
