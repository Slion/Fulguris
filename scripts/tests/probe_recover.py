"""Probe: after a fresh launch, where does the app land? Does button_more
show up? How many backs does _close_to_browser need to reach the browser?"""
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


def state(tag):
    n = adb.find_node(device.serial, ":id/button_more")
    texts = [t for t in bm._texts(device) if t][:12]
    print(f"[{tag}] button_more={'YES' if (n and n.bounds) else 'no'}  "
          f"drawer={bm._drawer_open(device)} menu={bm._main_menu_open(device)}  "
          f"texts={texts}", flush=True)


def main():
    # Fresh launch.
    import subprocess
    subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__),
                    "..", "tools", "launch.py"), "--restart",
                    "--device", "R58R91GBTZK"], check=False)
    time.sleep(6.0)
    state("after launch")

    # Try _close_to_browser and observe.
    bm._close_to_browser(device)
    state("after _close_to_browser")

    # Manual backs.
    for i in range(6):
        state(f"before back {i}")
        if adb.find_node(device.serial, ":id/button_more") and \
                not bm._drawer_open(device) and not bm._main_menu_open(device):
            print("at browser, stopping", flush=True)
            break
        device.key(keys.BACK, 1.0)


if __name__ == "__main__":
    main()
