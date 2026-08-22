"""One-off probe: pull the APK actually installed on the device and scan its dex
for the CursorController strings, to rule out a stale on-device build.

Usage: python scripts/tests/probe_device_apk.py --device SERIAL
"""

from __future__ import annotations

import argparse
import os
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from tools import adb  # noqa: E402

PKG = "net.slions.fulguris.full.download.debug"

# strings that distinguish successive builds (most recent first)
MARKERS = [
    ("entry marker (latest)",        b"ASSIST branch entered"),
    ("hAP entry log",                b"handleAssistLongPress action="),
    ("granular long-press log",      b"long press ABORT: targetProvider() was null"),
    ("KEY trace act= format",        b"act="),
    ("old long=${event} trace",      b"long=${event.isLongPress}"),
]


def scan(path: str) -> dict:
    z = zipfile.ZipFile(path)
    blobs = {n: z.read(n) for n in z.namelist() if n.endswith(".dex")}
    return {label: any(m in b for b in blobs.values()) for label, m in
            [(l, m) for l, m in MARKERS]}


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--device", required=True)
    args = p.parse_args()

    out = os.path.join(tempfile.gettempdir(), "fulguris_device.apk")
    raw = adb._adb(args.device, ["shell", "pm", "path", PKG]).strip()
    base = raw[len("package:"):] if raw.startswith("package:") else raw
    print(f"pulling {base} from {args.device} ...")
    adb._adb(args.device, ["pull", base, out], timeout=120)
    if not os.path.exists(out):
        print("pull failed")
        return 1
    print(f"pulled -> {out} ({os.path.getsize(out)} bytes)")
    found = scan(out)
    for label, present in found.items():
        print(f"  [{'HAS ' if present else 'MISS'}] {label}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
