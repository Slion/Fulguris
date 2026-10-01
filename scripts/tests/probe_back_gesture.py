"""Probe: system back (edge gesture / back key) must NOT send the app to
background when there is page/tab history to go back through.

Repro for the targetSdk 36 / predictive-back regression: WebBrowserActivity
only overrode legacy Activity.onBackPressed(), which the API 35+ back
dispatcher no longer invokes for the gesture (or the key on 36), so the
activity finished and the app went to the background.

Run:  python scripts/tests/probe_back_gesture.py --device SERIAL
"""
import argparse
import http.server
import os
import socketserver
import sys
import threading

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.normpath(os.path.join(HERE, "..")))

from framework import AndroidDevice, keys

PORT = 8377
ASSETS = os.path.join(HERE, "assets")
PAGE_A = f"http://127.0.0.1:{PORT}/back_a.html"
PAGE_B = f"http://127.0.0.1:{PORT}/back_b.html"

_httpd = None  # keep a module-level reference so the thread/socket aren't GC'd


def _start_server():
    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *a, **k):
            super().__init__(*a, directory=ASSETS, **k)

        def log_message(self, fmt, *args):
            pass  # keep output clean

    global _httpd

    class _Server(socketserver.ThreadingTCPServer):
        allow_reuse_address = True
        daemon_threads = True

    _httpd = _Server(("127.0.0.1", PORT), Handler)
    threading.Thread(target=_httpd.serve_forever, daemon=True).start()


def _wait_loaded(device, want, timeout=15.0):
    """Wait until the toolbar field mirrors `want` (the page title)."""
    import time
    deadline = time.time() + timeout
    while time.time() < deadline:
        if want in device.field_text():
            return True
        time.sleep(0.5)
    return want in device.field_text()


def edge_back_gesture(device):
    """Send a system edge-back gesture; try a few swipe variants until the
    app reacts (field changes or the app leaves the foreground)."""
    w, h = device.screen_size()
    y = h // 2
    for frac, dur in ((3, 300), (2, 400), (4, 200)):
        before = device.field_text()
        device.transport.shell(
            ["shell", "input", "swipe", "0", str(y), str(w // frac), str(y),
             str(dur)])
        device.settle(3.0)
        if device.field_text() != before or device.foreground_package() != device.package:
            return


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--device", required=True)
    args = ap.parse_args()

    device = AndroidDevice(args.device)
    failures = []

    _start_server()
    device.transport.reverse(PORT)
    device.restart()
    if not _wait_loaded(device, "home") and not device.settle(3.0):
        pass

    # Open tab A then tab B (typed URLs open a NEW tab); with B on top, the
    # back action must close tab B (revealing A) — not exit the app.
    device.navigate(PAGE_A)
    if not _wait_loaded(device, "back-page-a"):
        device.navigate(PAGE_A, reset=False)  # one retry (cold start race)
    if not _wait_loaded(device, "back-page-a"):
        print(f"FAIL: page A did not load (field='{device.field_text()}')")
        sys.exit(1)
    device.navigate(PAGE_B)
    if not _wait_loaded(device, "back-page-b"):
        print(f"FAIL: page B did not load (field='{device.field_text()}')")
        sys.exit(1)

    # Edge-back gesture (informational only — `input swipe` from the edge does
    # not reliably register as a system back gesture on every device; the
    # deterministic repro is the back key below, which goes through the same
    # OnBackPressedDispatcher on API 35+).
    fg = device.foreground_package()
    print(f"before gesture: foreground={fg} field='{device.field_text()}'")
    edge_back_gesture(device)
    fg = device.foreground_package()
    field = device.field_text()
    print(f"after gesture:  foreground={fg} field='{field}'")
    if fg != device.package:
        failures.append(f"back gesture sent app to background (foreground={fg})")

    # Back key with a tab to close must close it, not exit the app.
    # Make sure tab B is on top (restore it if the gesture above closed it).
    if "back-page-b" not in device.field_text():
        device.navigate(PAGE_B, reset=False)
    if not _wait_loaded(device, "back-page-b"):
        print(f"FAIL: tab B (2nd) did not load (field='{device.field_text()}')")
        sys.exit(1)
    before = device.field_text()
    device.key(keys.BACK, wait=1.0)
    after = device.field_text()
    fg = device.foreground_package()
    print(f"after back key: foreground={fg} field='{after}' (was '{before}')")
    # The regression: back finished the activity and sent the app to the
    # background. With a tab to close, the app MUST stay foreground and the
    # visible page must change (the tab is closed / history is traversed).
    if fg != device.package:
        failures.append(f"back key sent app to background (foreground={fg})")
    if after == before:
        failures.append(f"back key was a no-op (field stayed '{after}')")

    _httpd.server_close()
    if failures:
        print("FAIL")
        for f in failures:
            print(f"  - {f}")
        sys.exit(1)
    print("PASS")


if __name__ == "__main__":
    main()
