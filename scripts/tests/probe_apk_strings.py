"""One-off probe: confirm the built APK actually contains the CursorController
strings we just added (guards against a stale incremental build).

Usage: python scripts/tests/probe_apk_strings.py
"""

import os
import sys
import zipfile

APK = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "app", "build", "outputs", "apk", "slionsFullDownload", "debug",
    "Fulguris-v2.0.10-slions-full-download-debug.apk",
)

# (label, byte-string to look for, expect-present?)
CHECKS = [
    ("KEY trace (act= format)",  b"act=",                     True),
    ("en=/shown= in trace",       b"en=",                      True),
    ("ASSIST branch log",         b"ASSIST ignored (cursor off)", True),
    ("ASSIST branch ENTER marker", b"ASSIST branch entered",       True),
    ("hAP entry log",             b"handleAssistLongPress action=", True),
    ("assist fire log",           b"ASSIST long-press fired -> dispatchLongPress", True),
    ("assist no-fire log",        b"ASSIST down rep=",          True),
    ("dispatchLongPress log",     b"long press (context menu) at target", True),
    ("dispatchLongPress abort log", b"long press ABORT: targetProvider() was null", True),
    ("old temp KEY trace",        b"long=${event.isLongPress}",  False),
]


def main() -> int:
    if not os.path.exists(APK):
        print(f"APK not found: {APK}")
        return 1
    z = zipfile.ZipFile(APK)
    blobs = {n: z.read(n) for n in z.namelist() if n.endswith(".dex")}
    print(f"APK: {APK}")
    print(f"{len(blobs)} dex files, {sum(len(b) for b in blobs.values())} bytes total")
    print()
    ok = True
    for label, needle, expect in CHECKS:
        hits = [n for n, b in blobs.items() if needle in b]
        present = bool(hits)
        good = (present == expect)
        ok = ok and good
        print(f"  [{'OK ' if good else 'BAD'}] {label!r}: "
              f"{'found' if present else 'absent'} (expected "
              f"{'present' if expect else 'absent'})"
              f"{f' in {hits}' if hits else ''}")
    print()
    print("APK MATCHES SOURCE" if ok else "APK STALE / MISMATCH")
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
