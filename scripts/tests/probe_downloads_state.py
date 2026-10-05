"""Diagnostic: inspect the live download state, reverse tunnels, and server behavior.

Run:  python scripts/tests/probe_downloads_state.py
"""
from __future__ import annotations

import os
import sys
import time
import urllib.request

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

from framework import resolve_devices  # noqa: E402
from downloads_full_tests import (  # noqa: E402
    _ensure_server, _ensure_slow_server, PORT, SLOW_PORT, LOCAL_DIR, FILE_SMALL,
    NAME_FAILED, _ensure_file, _file_url,
)

SERIAL = "R58R91GBTZK"
devices = resolve_devices(SERIAL, False, "net.slions.fulguris.full.agent.debug")
device = devices[0]


def main():
    print("== host server state ==")
    print("ASSETS served on", PORT, " -> LOCAL_DIR served on", SLOW_PORT)
    print("LOCAL_DIR =", LOCAL_DIR)
    for name in (FILE_SMALL, NAME_FAILED):
        p = os.path.join(LOCAL_DIR, name)
        print(f"  {name}: exists={os.path.exists(p)} size={os.path.getsize(p) if os.path.exists(p) else '-'}")

    print("\n== ensure servers ==")
    _ensure_file(FILE_SMALL, 10)
    _ensure_server()
    _ensure_slow_server()
    time.sleep(0.3)

    print("\n== host-side fetch (localhost) ==")
    for url in (
        f"http://127.0.0.1:{SLOW_PORT}/{FILE_SMALL}?cb=1",
        f"http://127.0.0.1:{SLOW_PORT}/{FILE_SMALL}?slow=768000&cb=1",
        f"http://127.0.0.1:{SLOW_PORT}/{NAME_FAILED}?fail=1&cb=1",
    ):
        try:
            with urllib.request.urlopen(url, timeout=8) as r:
                body = r.read(24)
                print(f"  {r.status} {url}\n     CT={r.headers.get('Content-Type')} CL={r.headers.get('Content-Length')} first={body[:24]!r}")
        except Exception as e:
            print(f"  ERR {url}: {e!r}")

    print("\n== _file_url for each mode ==")
    print("  plain :", _file_url(device, FILE_SMALL))
    print("  slow  :", _file_url(device, FILE_SMALL, slow=768000))
    print("  fail  :", _file_url(device, NAME_FAILED, fail=True))

    print("\n== device reverse tunnel list ==")
    out = device.transport.shell(["shell", "adb", "reverse", "--list"])
    print("  raw:", repr(out))

    print("\n== device reachability of the dedicated port (toybox curl) ==")
    for port in (SLOW_PORT,):
        try:
            code = device.transport.shell(["shell", "toybox", "curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", f"http://localhost:{port}/"], timeout=15)
        except Exception as e:
            code = f"ERR {e!r}"
        print(f"  localhost:{port}/ -> {code!r}")

    print("\n== device /sdcard/Download ==")
    print(device.transport.shell(["shell", "ls", "-l", "/sdcard/Download"]))

    print("\n== current node text (top 30) ==")
    texts = [n.text for n in device.nodes() if n.text]
    print(" ", sorted(texts)[:30])


if __name__ == "__main__":
    main()
