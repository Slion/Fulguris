"""Probe: does the ?slow= throttled transfer actually stay in flight, and how
fast does the on-disk file grow? Samples the sheet row + the partial file size
every 3 s while a throttled download runs.

Run:  python scripts/tests/probe_slow_download.py
"""
from __future__ import annotations

import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

from framework import resolve_devices  # noqa: E402
from downloads_full_tests import (  # noqa: E402
    FILE_SMALL, SLOW_RATE, _ensure_file, _file_url, _start_download,
    _sheet_open, _row_summaries, _sheet_close, _rm_file, _tap_download_button,
    _wait_text, _cleanup_all,
)

SERIAL = "R58R91GBTZK"
devices = resolve_devices(SERIAL, False, "net.slions.fulguris.full.agent.debug")
device = devices[0]


def disk_bytes(name: str) -> int:
    out = device.transport.shell(["shell", "ls", "-l", "/sdcard/Download"], timeout=20)
    for line in out.splitlines():
        if line.endswith(name):
            parts = line.split()
            try:
                return int(parts[4])
            except (ValueError, IndexError):
                return -1
    return -2  # not found


def main():
    _ensure_file(FILE_SMALL)
    # Clean entries via the sheet first: a leftover DownloadManager entry from
    # a previous run holds its file open (rm alone can't remove it on /sdcard),
    # which makes disk checks and the conflict dialog misleading.
    _cleanup_all(device)
    _rm_file(device, FILE_SMALL)
    print("disk after cleanup:", disk_bytes(FILE_SMALL))
    device.settle()

    print(f"navigating to slow URL ({SLOW_RATE} B/s nominal -> 15 MB in ~125 s)...")
    url = _file_url(device, FILE_SMALL, slow=SLOW_RATE)
    print("url:", url)
    device.navigate(url)
    print("dialog up:", _wait_text(device, "Download file?", timeout=20.0))
    print("tap download button:", _tap_download_button(device))

    _sheet_open(device)
    t0 = time.time()
    finished = False
    for i in range(30):
        rows = _row_summaries(device, FILE_SMALL, timeout=3.0)
        size = disk_bytes(FILE_SMALL)
        dt = time.time() - t0
        if rows:
            first = rows[0][1].split("\n")[0]
            summary = rows[0][1].replace("\n", " | ")
            print(f"t={dt:6.1f}s  disk={size:>9}  row={summary}", flush=True)
            # Finished: the first summary line is the total size (no '%' left).
            if "%" not in first and "15" in first and "MB" in first:
                print(f"=> completed at t={dt:.1f}s (row now: {summary!r})")
                finished = True
                break
        else:
            print(f"t={dt:6.1f}s  disk={size:>9}  row=(no row)", flush=True)
        time.sleep(5.0)
    if not finished:
        print("=> still running at end of sampling window")
    _sheet_close(device)


if __name__ == "__main__":
    main()
