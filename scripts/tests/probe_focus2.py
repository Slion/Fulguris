#!/usr/bin/env python3
"""Probe web-view / search-field focus transitions around the address field.

Usage: python scripts/tests/probe_focus2.py --serial <serial>

Loads a local page, then reports webview focus and toolbar state (via the
mirrored field text) as we: (1) after load, (2) focus the search field (SEARCH
key), (3) tap the web view body, (4) focus the search field again, (5) press
BACK. Used to design the hide-tool-bar focus-gain rearm test.
"""
from __future__ import annotations

import argparse
import os
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb  # noqa: E402
from framework import AndroidDevice, keys  # noqa: E402

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
PORT = 8899


class _NoCacheHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ASSETS_DIR, **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        super().end_headers()

    def log_message(self, *args):
        pass


def _serve() -> None:
    try:
        server = ThreadingHTTPServer(("127.0.0.1", PORT), _NoCacheHandler)
    except OSError:
        return
    threading.Thread(target=server.serve_forever, daemon=True).start()


def row(device: AndroidDevice, label: str) -> None:
    try:
        field = device.field_text()
    except Exception:  # noqa: BLE001
        field = "<err>"
    try:
        focused_nodes = [
            f"{(n.resource_id or n.cls).split('/')[-1]}({n.cls.split('.')[-1]})"
            for n in adb.nodes(device.serial)
            if n.focused
        ]
    except Exception as e:  # noqa: BLE001
        focused_nodes = [f"<err {e}>"]
    print(f"  {label:30s} webview={device.webview_focused()!s:5s} field_focus={adb.field_focused(device.serial)!s:5s} field={field!r}")
    print(f"  {'':30s} focused_nodes={focused_nodes}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    args = ap.parse_args()

    device = AndroidDevice(args.serial)
    _serve()
    device.reverse(PORT)
    try:
        device.force_stop()
        time.sleep(1.5)
        device.launch()
        time.sleep(1.0)
        device.navigate(f"http://localhost:{PORT}/timeout_target.html?cb={int(time.time()*1000)}", reset=False)
        for _ in range(30):
            if device.field_text().strip().lower() == "loaded":
                break
            time.sleep(0.5)
        row(device, "1. after load")

        device.key(keys.SEARCH, wait=1.2)
        row(device, "2. SEARCH key (field focus)")

        w, h = device.screen_size()
        device.tap(w // 2, int(h * 0.30), wait=1.2)
        row(device, "3. tap web view body")

        device.key(keys.SEARCH, wait=1.2)
        row(device, "4. SEARCH key again")

        device.key(keys.BACK, wait=1.2)
        row(device, "5. BACK")

        device.key(keys.BACK, wait=1.2)
        row(device, "6. BACK again")
    finally:
        try:
            device.reverse_remove(PORT)
        except Exception:  # noqa: BLE001
            pass


if __name__ == "__main__":
    main()
