"""Probe: what row state does a mid-stream connection drop produce, and how
long until DownloadManager gives up (Paused -> Failed)?

Starts a ?slow=200000&fail_after=5000000 download (drop at ~25 s), then
samples the row every 5 s for up to 5 minutes. Prints the row summary, the
snackbar text (if any), and the disk size each tick so we can see the
Paused->Failed transition and whether a 'Download failed' snackbar shows.
"""
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools"))
from framework import resolve_devices  # noqa: E402
from downloads_full_tests import (  # noqa: E402
    FILE_SMALL, SLOW_RATE, _ensure_file, _file_url, _cleanup_all, _rm_file,
    _tap_download_button, _wait_text, _row_summaries, _sheet_open,
    _sheet_close, _texts,
)

device = resolve_devices("R58R91GBTZK", False,
                         "net.slions.fulguris.full.agent.debug")[0]


def _disk_size():
    out = device.transport.shell(["shell", "ls", "-l", "/sdcard/Download"], timeout=20)
    for line in out.splitlines():
        parts = line.split()
        if parts and parts[-1] == FILE_SMALL:
            try:
                return int(parts[4])
            except ValueError:
                return -1
    return -2


def main():
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    _rm_file(device, FILE_SMALL)
    device.settle()

    url = _file_url(device, FILE_SMALL, slow=SLOW_RATE, fail_after=5_000_000)
    print("url:", url, flush=True)
    device.navigate(url)
    print("dialog up:", _wait_text(device, "Download file", timeout=20.0), flush=True)
    print("tap:", _tap_download_button(device), flush=True)
    _sheet_open(device)

    t0 = time.time()
    for i in range(60):
        rows = _row_summaries(device, FILE_SMALL, timeout=2.0)
        row = rows[0][1].replace("\n", " | ") if rows else "(no row)"
        dt = time.time() - t0
        print(f"t={dt:6.1f}s  disk={_disk_size():>9}  row={row}", flush=True)
        if rows and "Error" in rows[0][1]:
            print(f"=> FAILED row at t={dt:.1f}s", flush=True)
            break
        time.sleep(5.0)
    # Show what's on screen (snackbar etc.)
    print("final nodes:", sorted(_texts(device))[:40], flush=True)
    _sheet_close(device)
    _rm_file(device, FILE_SMALL)


if __name__ == "__main__":
    main()
