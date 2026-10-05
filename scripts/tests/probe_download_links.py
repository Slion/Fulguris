"""Probe (round 2): find the REAL download bugs.

* Bug A: octet-stream + NO Content-Disposition + NO download attr — does the
  WebView download (dialog) or navigate (raw file / blank page)?
* Bug B: when the `download` attribute names the file (download="x.txt") but
  the server sends NO Content-Disposition, does the dialog's proposed filename
  come from the attribute (x.txt) or from the URL basename (autotest_tiny.bin)?
* Bug C: the JS-created anchor (GitHub pattern) — actually clicked this time.
"""
from __future__ import annotations

import os
import re
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

import adb  # noqa: E402
from framework import keys, resolve_devices  # noqa: E402
from cursor_tests import PORT, _ensure_server  # noqa: E402
import downloads_full_tests as dl  # noqa: E402

SERIAL = "R58R91GBTZK"
PKG = "net.slions.fulguris.full.agent.debug"
device = resolve_devices(SERIAL, False, PKG)[0]

dl._ensure_file(dl.FILE_TINY, size=64 * 1024)
_ensure_server()
dl._ensure_slow_server()
device.reverse(PORT)
device.reverse(dl.SLOW_PORT)
LINKS_PAGE = "http://localhost:%d/download_links.html" % PORT


def nodes():
    return device.nodes()


def texts():
    return [n.text for n in nodes() if n.text]


def dialog_up() -> bool:
    return any("Download file" in s for s in texts())


def dialog_message() -> str:
    """The dialog's message node (contains the proposed filename + type + size)."""
    best, bestlen = "", 0
    for n in nodes():
        if n.text and ("MB" in n.text or "kB" in n.text or n.text.endswith("B")) \
                and ("•" in n.text or "/" in n.text or "." in n.text):
            if len(n.text) > bestlen:
                best, bestlen = n.text, len(n.text)
    return best


def tap_by_id(device, link_id) -> bool:
    for n in nodes():
        if (n.resource_id or "").endswith(link_id) and n.bounds:
            device.tap(n.center[0], n.center[1], wait=2.0)
            return True
    return False


def tap_by_text(device, text) -> bool:
    for n in nodes():
        if n.bounds and n.text and text in n.text:
            device.tap(n.center[0], n.center[1], wait=2.0)
            return True
    return False


def cancel_dialog(device) -> None:
    deadline = time.time() + 5.0
    while time.time() < deadline:
        if dialog_up():
            for n in nodes():
                if n.bounds and n.text == "Cancel" and n.enabled:
                    device.tap(n.center[0], n.center[1], wait=1.2)
                    return
        time.sleep(0.4)


def reset_page(device) -> None:
    device.key(keys.BACK, 1.0)
    time.sleep(0.5)
    if dialog_up():
        cancel_dialog(device)
    device.navigate(LINKS_PAGE)
    time.sleep(2.5)


def address_bar() -> str:
    for n in nodes():
        if (n.resource_id or "").split("/")[-1] == "search" and n.text:
            return n.text
    return ""


def run(tag, tap):
    print(f"\n=== {tag} ===", flush=True)
    print("tapped:", tap(), flush=True)
    time.sleep(4.0)
    if dialog_up():
        print("  -> DIALOG. message:", repr(dialog_message()), flush=True)
        print("  -> address bar:", repr(address_bar()), flush=True)
        cancel_dialog(device)
    else:
        print("  -> no dialog. address bar:", repr(address_bar()), flush=True)
        print("  -> page texts:", [t for t in texts() if len(t) < 40][:8], flush=True)
    reset_page(device)


def main():
    device.navigate(LINKS_PAGE)
    time.sleep(3.0)
    print("links page address:", repr(address_bar()), flush=True)

    # Bug A: no attr, no CD (pattern 4) — should it download or navigate?
    run("Bug A: plain link, octet-stream, NO CD, NO download attr",
        lambda: tap_by_id(device, "p_plain"))

    # Bug B: download attr names the file, server sends NO CD (pattern 1).
    # Correct filename should be autotest_plain_attr.txt (from the attribute).
    run("Bug B: download attr='autotest_plain_attr.txt', server NO CD",
        lambda: tap_by_id(device, "p_attr"))

    # Bug C: JS-created anchor, download attr (GitHub pattern).
    run("Bug C: JS-created anchor, download attr, server NO CD",
        lambda: tap_by_text(device, "5: JS download attr"))


if __name__ == "__main__":
    main()
