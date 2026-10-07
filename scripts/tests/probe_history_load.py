#!/usr/bin/env python3
"""Probe the history-test localhost page load on a device: start the asset
server, reverse-tunnel it, navigate to it, and report the toolbar field text
plus the app's foreground state. Diagnoses 'page A did not load (field="")'.

Usage:
    python scripts/tests/probe_history_load.py --device SERIAL
"""
from __future__ import annotations

import argparse
import os
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

_scripts = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for _d in (_scripts, os.path.join(_scripts, "tools"), os.path.join(_scripts, "tests")):
    if _d not in sys.path:
        sys.path.insert(0, _d)

from appdevice import resolve_devices  # noqa: E402
import adb as tools_adb  # noqa: E402

PORT = 8902
ASSETS = os.path.join(os.path.dirname(__file__), "assets")


class _H(SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=ASSETS, **k)

    def send_header(self, key, value):
        if key.lower() == "last-modified":
            return
        super().send_header(key, value)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")

    def log_message(self, *a):
        pass


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", required=True)
    a = ap.parse_args()
    device = resolve_devices(a.device, False)[0]
    print(f"=== history-load probe on {device.id} ===")

    srv = ThreadingHTTPServer(("127.0.0.1", PORT), _H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    device.reverse(PORT)
    time.sleep(0.5)

    # Confirm the tunnel from the device side.
    out = tools_adb._adb(device.serial, ["shell", "curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                                         f"http://localhost:{PORT}/history_a.html"], timeout=20)
    print("device-side curl http_code:", out.strip())

    device.restart()
    url = f"http://localhost:{PORT}/history_a.html?cb={int(time.time() * 1000)}"
    print("navigating to", url)
    device.navigate(url)

    for i in range(12):
        fg = device.foreground_package()
        ft = device.field_text()
        print(f"  t={i * 2.5:4.1f}s fg={fg!r} field={ft!r}")
        if "history-a" in ft:
            print("OK: page A loaded")
            break
        time.sleep(2.5)

    device.reverse_remove(PORT)
    srv.shutdown()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
