#!/usr/bin/env python3
"""Diagnostic: reset the hide-timeout pref to 0, load the local page, and print
the address field text over time so we can see what it shows while the toolbar
is visible (title vs URL vs empty)."""
from __future__ import annotations

import os
import re
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb
from framework import AndroidDevice

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
PORT = 8899
SERIAL = sys.argv[1] if len(sys.argv) > 1 else "R58R91GBTZK"
CONFIG = sys.argv[2] if len(sys.argv) > 2 else "portrait"
VALUE = sys.argv[3] if len(sys.argv) > 3 else "0"
TIMEOUT_KEY = "pref_key_hide_tool_bar_timeout"


class H(SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=ASSETS_DIR, **k)

    def log_message(self, *a):
        pass


def main() -> int:
    device = AndroidDevice(SERIAL)
    device.force_stop()
    path = f"shared_prefs/{device.package}_preferences_{CONFIG}.xml"
    xml = device.read_prefs(path)
    entry = f'<float name="{TIMEOUT_KEY}" value="{VALUE}" />'
    pattern = re.compile(rf'<float name="{re.escape(TIMEOUT_KEY)}" value="[^"]*" />')
    xml = pattern.sub(entry, xml) if pattern.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
    device.write_prefs(path, xml)
    print(f"set {TIMEOUT_KEY}={VALUE} in {path}")

    server = ThreadingHTTPServer(("127.0.0.1", PORT), H)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    device.reverse(PORT)
    device.launch()
    time.sleep(3)
    from framework import keys
    device.key(keys.SEARCH, wait=1.2)
    time.sleep(0.8)
    url = f"http://localhost:{PORT}/timeout_target.html?cb={int(time.time()*1000)}"
    print("navigating (threaded)...")
    threading.Thread(target=lambda: device.navigate(url, reset=False), daemon=True).start()
    for i in range(1, 26):
        time.sleep(1.0)
        print(f"t={i:2d}s  field={device.field_text()!r}")
    try:
        device.reverse_remove(PORT)
    except Exception:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
