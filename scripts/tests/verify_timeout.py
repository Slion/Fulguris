#!/usr/bin/env python3
"""Manual verification of the "Hide tool bar after" timeout semantics.

The timeout must start when the page is fully loaded and NOT be reset by user
interaction. We verify scenarios on a device by rewriting the configuration
preference (the app is stopped first so it cannot clobber it) and loading a
fast local page over an adb reverse tunnel.

Toolbar visibility is observed through the address field text: when the toolbar
is visible the field shows the page title; when it is hidden the field text is
empty. timeout_target.html sets document.title to "loaded" on its load event
(Fulguris mirrors the title into the field), so "loaded" appearing is our
precise "page fully loaded AND toolbar visible" marker, and the field going
empty is the "toolbar auto-hid" marker.

    --scenario zero         timeout=0  -> toolbar never auto-hides
    --scenario timeout10    timeout=10 -> toolbar hides ~10s after load
    --scenario interaction  timeout=10 -> a D-pad press ~2s after load does NOT
                            reset the timer (hide still ~10s after load, ~8s after press)
    --scenario rearm        timeout=10 -> after the auto-hide, the toolbar is re-shown
                            (back, no history nav), focus is moved off the web view
                            (search key) and back onto it (tap): that focus gain re-arms
                            the countdown (hides again ~10s after the tap, not before)

The timeout is deliberately the slider maximum (10s): device.navigate() takes ~5s
just to type + submit the URL, so shorter timeouts would fire before polling has
started. A fresh query string is appended to the URL, but the hide countdown starts
at the (new) page's load, which lands ~1s after ENTER, so timeout=10 leaves a
~5s polling margin. The hide wait is 17s (well past 10s) so a stale arming
(e.g. the old interaction-reset semantics, which would land ~12-15s after load)
fails clearly instead of reading as "never hid".

    python scripts/tests/verify_timeout.py --serial R58R91GBTZK --config portrait --scenario timeout3
    python scripts/tests/verify_timeout.py --serial 192.168.178.67:5555 --config landscape --scenario interaction

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
import adb
from framework import AndroidDevice, keys

ASSETS_DIR = os.path.join(os.path.dirname(__file__), "assets")
PORT = 8899
TIMEOUT_KEY = "pref_key_hide_tool_bar_timeout"
DEFAULT_VALUE = "0"


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
        # A previous run left the tunnel up (and the page loaded): fine to reuse.
        return
    threading.Thread(target=server.serve_forever, daemon=True).start()


def _run(device: AndroidDevice, config_file: str, value: str, scenario: str) -> bool:
    _set_timeout(device, config_file, value)
    try:
        device.launch()
        time.sleep(1.0)
        # navigate() blocks ~5s (focus + type + ENTER + wait) before returning;
        # with timeout=10 that still leaves a ~5s margin before the auto-hide,
        # so a plain blocking call is race-free (no need to poll while it runs).
        device.navigate(f"http://localhost:{PORT}/timeout_target.html?cb={int(time.time()*1000)}", reset=False)

        t0 = _wait_loaded(device)
        print(f"  page loaded at t0 (timeout={value})")

        if scenario == "zero":
            # No hide within 6s of load; the field must keep showing the title.
            time.sleep(6.0)
            text = device.field_text().strip()
            ok = text.lower() == "loaded"
            print(f"  field after 6s: {text!r}")
            return ok

        if scenario == "interaction":
            time.sleep(2.0)
            if device.field_text().strip() == "":
                print("  toolbar already hid before the interaction - retry scenario")
                return False
            print("  t0+2s: sending D-pad center (user interaction)")
            device.key(keys.DPAD_CENTER, wait=0.5)
            t_press = time.time()
            hidden = _wait_toolbar_hidden(device, 17.0)
            if hidden is None:
                print("  toolbar never hid within 17s of the press")
                return False
            from_load = (t_press + hidden) - t0
            print(f"  toolbar hid {hidden:.2f}s after the press ({from_load:.2f}s after load)")
            # The countdown is anchored at page load (t0), so the hide lands ~10s
            # after load even though the user interacted at t0+2s. With the old
            # (interaction-reset) semantics it would land ~10s after the press,
            # i.e. ~12s after load - outside this window.
            # NOTE: navigate() returns ~3s after the page actually finishes
            # loading (type+ENTER+wait), so the true "10s after load" reads as
            # ~7-8s from t0; 6.5 keeps a margin below that.
            return 6.5 <= from_load <= 11.5

        if scenario == "rearm":
            # Wait for the first (load-anchored) auto-hide...
            hidden1 = _wait_toolbar_hidden(device, 17.0)
            if hidden1 is None:
                print("  toolbar never hid within 17s of load (no first hide to rearm from)")
                return False
            print(f"  toolbar hid {hidden1:.2f}s after load (first hide)")
            # Re-show the toolbar with the back key: with the toolbar hidden, back
            # just re-shows it (no history navigation, no focus change) - the web
            # view keeps focus but the countdown is NOT re-armed yet, so the
            # toolbar stays up.
            device.key(keys.BACK, wait=1.5)
            if device.field_text().strip().lower() != "loaded":
                print(f"  toolbar did not re-appear after back (field={device.field_text()!r})")
                return False
            print("  toolbar re-shown (back), web view still focused")
            # Move the input focus off the web view onto the search field...
            device.key(keys.SEARCH, wait=1.5)
            # ...then tap the web view so it gains focus again: that focus gain
            # re-arms the countdown from scratch (this is the behavior under test).
            w, h = device.screen_size()
            t_rearm = time.time()
            device.tap(w // 2, int(h * 0.30), wait=1.5)
            print(f"  web view tapped (focus regained) at t_rearm (field={device.field_text()!r})")
            # The rearm: the countdown starts from the tap (focus gain), so the
            # next hide lands ~10s after t_rearm (the first hide must NOT have
            # consumed the whole budget, and a show without focus change must NOT
            # have re-armed either).
            hidden2 = _wait_toolbar_hidden(device, 17.0)
            if hidden2 is None:
                print("  toolbar never hid again within 17s of the re-arm")
                return False
            print(f"  toolbar hid {hidden2:.2f}s after the re-arm")
            return 7.5 <= hidden2 <= 11.5

        # timeout10
        hidden = _wait_toolbar_hidden(device, 17.0)
        if hidden is None:
            print("  toolbar never hid within 17s of load")
            return False
        print(f"  toolbar hid {hidden:.2f}s after load")
        # Same ~3s navigate() latency applies: true "10s after load" reads as
        # ~7-8s from t0.
        return 6.5 <= hidden <= 11.5
    finally:
        # Restore the default so the device is left in a known state.
        try:
            _set_timeout(device, config_file, DEFAULT_VALUE)
        except Exception as e:  # noqa: BLE001
            print(f"  WARNING: could not reset timeout pref: {e}")


def _set_timeout(device: AndroidDevice, config_file: str, value: str) -> None:
    """Rewrite the hide-timeout float in the configuration prefs file (app stopped)."""
    device.force_stop()
    path = f"shared_prefs/{device.package}_preferences_{config_file}.xml"
    xml = device.read_prefs(path)
    if "<map" not in xml:
        raise RuntimeError(f"prefs file {path} not initialized yet")
    entry = f'<float name="{TIMEOUT_KEY}" value="{value}" />'
    pattern = re.compile(rf'<float name="{re.escape(TIMEOUT_KEY)}" value="[^"]*" />')
    xml = pattern.sub(entry, xml) if pattern.search(xml) else xml.replace("</map>", f"    {entry}\n</map>")
    device.write_prefs(path, xml)


def _wait_loaded(device: AndroidDevice, timeout: float = 25.0) -> float:
    """Return t0 once the page reports 'loaded' (loaded + toolbar visible)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if device.field_text().strip().lower() == "loaded":
            return time.time()
        time.sleep(0.25)
    raise RuntimeError(f"page did not report 'loaded' (field text: {device.field_text()!r})")


def _wait_toolbar_hidden(device: AndroidDevice, timeout: float) -> float | None:
    """Seconds from now until the toolbar hides (field text goes empty), or None."""
    start = time.time()
    while time.time() - start < timeout:
        if device.field_text().strip() == "":
            return time.time() - start
        time.sleep(0.2)
    return None








def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--config", required=True, help="prefs file suffix: portrait / landscape / <custom name>")
    parser.add_argument("--scenario", required=True, choices=["zero", "timeout10", "interaction", "rearm"])
    args = parser.parse_args()

    device = AndroidDevice(args.serial)
    _serve()
    device.reverse(PORT)
    try:
        print(f"[{args.serial}] scenario={args.scenario} config={args.config}")
        value = "0" if args.scenario == "zero" else "10"
        ok = _run(device, args.config, value, args.scenario)
    finally:
        try:
            device.reverse_remove(PORT)
        except Exception:  # noqa: BLE001
            pass
    print("PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
