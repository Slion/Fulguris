#!/usr/bin/env python3
"""Repro: on a busy page the tool bar never auto-hides.

Sets "Hide tool bar after" to 5 seconds, loads the local theme_flipper.html
page (which changes its <meta name="theme-color"> every 2 seconds, mimicking a
busy site like bbc.com: each change is reported through the console, which the
app treats as a tab change and re-arms the hide countdown), waits well past
the timeout and checks whether the tool bar hid.

Toolbar visibility is observed through the mirrored address-field text (title
"flipper" when visible, empty when hidden) and double-checked with screenshots
(scripts/tests/out/). If temporary hideTMO: diagnostics are present in the build
(see docs/features/toolbar-hide-timeout.md, Methodology), their logcat lines are
dumped at the end so the arm/cancel/fire sequence can be reconstructed: with the
bug, repeated `ARM` lines keep restarting the countdown; fixed, there is a single
ARM after load and one HIDE ~timeout s later.

    python scripts/tests/repro_toolbar_hide.py --serial R58R91GBTZK --config portrait

The timeout preference is reset to its default (0) afterwards.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
import adb  # noqa: E402
from framework import AndroidDevice  # noqa: E402

TIMEOUT_KEY = "pref_key_hide_tool_bar_timeout"
DEFAULT_VALUE = "0"
ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
PORT = 8899
PAGE_TITLE = "flipper"  # mirrored into the toolbar label while it is visible
WAIT_AFTER_LOAD = 15.0  # 3x the 5s timeout; a healthy arm must have fired by now
OUT_DIR = os.path.join(os.path.dirname(__file__), "out")


class _NoCacheHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ASSETS_DIR, **kwargs)

    def send_header(self, key, value):
        if key.lower() == "last-modified":
            return
        super().send_header(key, value)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        super().end_headers()

    def log_message(self, *args):
        pass


def _serve() -> None:
    try:
        server = ThreadingHTTPServer(("127.0.0.1", PORT), _NoCacheHandler)
    except OSError:
        # A previous run left the server up (and the page loaded): fine to reuse.
        return
    threading.Thread(target=server.serve_forever, daemon=True).start()


def _set_timeout(device: AndroidDevice, config_file: str, value: str) -> None:
    device.force_stop()
    path = f"shared_prefs/{device.package}_preferences_{config_file}.xml"
    xml = device.read_prefs(path)
    if "<map" not in xml:
        raise RuntimeError(f"prefs file {path} not initialized yet")
    entry = f'<float name="{TIMEOUT_KEY}" value="{value}" />'
    pattern = re.compile(rf'<float name="{re.escape(TIMEOUT_KEY)}" value="[^"]*" />')
    xml = pattern.sub(entry, xml) if pattern.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
    device.write_prefs(path, xml)


def _wait_loaded(device: AndroidDevice, timeout: float = 30.0) -> None:
    """Wait until the field shows the mirrored page title (page up, toolbar visible)."""
    deadline = time.time() + timeout
    last = ""
    while time.time() < deadline:
        try:
            text = device.field_text().strip()
        except Exception:  # noqa: BLE001
            time.sleep(0.5)
            continue
        if text.lower() == PAGE_TITLE:
            return
        last = text
        time.sleep(0.25)
    raise RuntimeError(f"page did not report '{PAGE_TITLE}' (field text: {last!r})")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--config", required=True, help="prefs file suffix: portrait / landscape")
    ap.add_argument("--timeout", default="5", help="timeout value in seconds")
    args = ap.parse_args()

    device = AndroidDevice(args.serial)
    tag = args.serial.replace(":", "_")
    os.makedirs(OUT_DIR, exist_ok=True)
    _serve()
    device.reverse(PORT)

    _set_timeout(device, args.config, args.timeout)
    try:
        adb.logcat(args.serial, "hideTMO", clear=True)
        device.launch()
        time.sleep(1.0)
        device.navigate(f"http://localhost:{PORT}/theme_flipper.html?cb={int(time.time()*1000)}", reset=False)
        _wait_loaded(device)
        t0 = time.time()
        print(f"page settled at t0 (timeout={args.timeout}s, waiting {WAIT_AFTER_LOAD:.0f}s)")
        device.screenshot(os.path.join(OUT_DIR, f"{tag}_t0.png"))

        time.sleep(WAIT_AFTER_LOAD)
        device.screenshot(os.path.join(OUT_DIR, f"{tag}_after.png"))
        text = device.field_text().strip()
        hidden = text == ""
        print(f"after {WAIT_AFTER_LOAD:.0f}s: field={text!r} webview_focused={device.webview_focused()}")
        print(f"TOOL BAR {'HID (expected)' if hidden else 'STILL VISIBLE (BUG REPRODUCED)'}")

        print("\n--- hideTMO log ---")
        log = adb.logcat(args.serial, "hideTMO")
        print(log if log.strip() else "(no hideTMO lines)")
    finally:
        try:
            _set_timeout(device, args.config, DEFAULT_VALUE)
        except Exception as e:  # noqa: BLE001
            print(f"WARNING: could not reset timeout pref: {e}")
        try:
            device.reverse_remove(PORT)
        except Exception:  # noqa: BLE001
            pass
    return 0 if hidden else 1


if __name__ == "__main__":
    sys.exit(main())
