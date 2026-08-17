"""Verify the reload/stop button fix.

Loads a sequence of pages in the current tab, waits for each to finish, and then
samples the reload button visibility (via fast dumpsys) while checking the logcat
for:
  - STALE progress events being ignored
  - any spurious "BUTTON BECAME VISIBLE" after a load completed

Usage: python scripts/tools/verify_reload_fix.py [--device SERIAL]
"""
import argparse
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adb

VIEW_RE = re.compile(
    r"\{[0-9a-f]+ ([VIG])[A-Z.]* [A-Z.]* \d+,\d+-\d+,\d+ #[0-9a-f]+ app:id/button_reload\}"
)

PAGES = [
    "https://en.wikipedia.org/wiki/Web_browser",
    "https://www.bbc.com/news",
    "https://github.com",
    "https://www.mozilla.org",
]


def reload_state(serial):
    out = adb._adb(serial, ["shell", "dumpsys", "activity", "top"])
    m = VIEW_RE.search(out)
    if not m:
        return "NOTFOUND"
    flag = m.group(1)
    return {"V": "VISIBLE", "I": "INVISIBLE", "G": "GONE"}[flag]


def wait_idle(serial, seconds=6):
    """Sample reload button state for `seconds` after load, report any VISIBLE."""
    samples = []
    end = time.time() + seconds
    while time.time() < end:
        samples.append(reload_state(serial))
        time.sleep(0.25)
    return samples


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--device")
    ap.add_argument("--settle", type=int, default=8, help="wait after each page load (s)")
    args = ap.parse_args()

    serial = adb.resolve_devices(args.device, use_all=False)[0]
    package = adb.detect_package(serial)
    print(f"device={serial} package={package}")

    # clear logcat
    adb._adb(serial, ["logcat", "-c"])

    adb.restart(serial, package)
    adb.settle(serial, package)

    for url in PAGES:
        print(f"\n=== loading {url} ===")
        adb.navigate(serial, package, url)
        # give the page time to fully load + settle
        time.sleep(args.settle)
        samples = wait_idle(serial, seconds=6)
        visible_count = sum(1 for s in samples if s == "VISIBLE")
        print(f"  reload during 6s idle: {visible_count}/{len(samples)} VISIBLE")
        print(f"  sequence: {' '.join(samples)}")

    time.sleep(2)
    log = adb._adb(serial, ["logcat", "-d"])
    outpath = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out", "verify_reload_logcat.txt")
    os.makedirs(os.path.dirname(outpath), exist_ok=True)
    with open(outpath, "w", encoding="utf-8") as f:
        f.write(log)

    print("\n=== RELOADDBG summary ===")
    stale = 0
    became = 0
    for line in log.splitlines():
        if "RELOADDBG" not in line:
            continue
        if "STALE (ignored)" in line:
            stale += 1
        if "BECAME VISIBLE" in line:
            became += 1
            print("  BECAME VISIBLE:", line.strip())
    print(f"  STALE events ignored: {stale}")
    print(f"  BECAME VISIBLE transitions: {became}")
    print(f"  full log: {outpath}")


if __name__ == "__main__":
    main()
