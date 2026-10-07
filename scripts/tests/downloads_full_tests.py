"""Exhaustive in-app download tests.

Covers the whole download lifecycle of Fulguris, driven through the framework
Device API: the "Download file?" confirmation dialog, the in-flight row states
(percentage + ``bytes / total``), completion (row summary, file on disk),
failure, cancel, and every per-row and sheet-wide action (Clean up / Remove
all / Delete all / Remove and keep / Remove and delete / Delete file).

Files are served from the host over the shared test HTTP server + ``adb reverse``
tunnel (the same machinery as the cursor suite; Fulguris blocks ``file://``).
The system DownloadManager re-names colliding downloads (``name-1.txt``), so
re-downloading a fixed name is a reliable way to detect a second file landing.

    python scripts/tests/run.py --device SERIAL --group downloads-full

Notes / gaps (deliberately not covered here):
* The image long-press "Download" context-menu path needs the cursor teleport
  hook to hit a precise page point; it bypasses the dialog, so it is out of scope.
* The blob: download path shares the same dialog/sheet surface.
* The row "Open" action hands the file to the system (viewer/chooser UI), which
  varies per device — not asserted here.
"""
from __future__ import annotations

import http.server
import os
import re
import socket
import sys
import threading
import time
import urllib.parse

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

from framework import keys  # noqa: E402
from cursor_tests import PORT, _NoCacheHandler  # noqa: E402

# --- local download corpus ---------------------------------------------------

LOCAL_DIR = os.path.join(os.path.dirname(__file__), "local_downloads")
FILE_SMALL = "autotest_small_10mb.bin"      # 15 MB (the name is historical)
NAME_FAILED = "autotest_missing_file.bin"   # 404 on purpose
# A tiny payload for dialog/link tests (no throttling needed — the file must
# land quickly so the completion row appears before the test moves on).
FILE_TINY = "autotest_tiny.bin"

# The app formats every size with Formatter.formatFileSize — SI (decimal) MB,
# so the payload is exactly 15,000,000 bytes and the app shows "15 MB" (a
# binary-MiB file would read e.g. "15.26 MB").
SIZE_BYTES = 15_000_000
SIZE_TEXT = "15 MB"
# The in-flight tests throttle to this rate. Throttling is enforced by
# shrinking the socket send buffer (SO_SNDBUF) so write() blocks: with the
# default multi-MB buffer the kernel accepts the whole file instantly and
# flushes it at line rate, so no amount of pacing in the handler loop works.
# With the small buffer the *effective* rate tracks the nominal one
# (measured ~130 kB/s at a 120 kB/s nominal): 15 MB in ~70 s at 200 kB/s —
# comfortably in flight the whole time the tests inspect it.
SLOW_RATE = 200_000  # bytes/second (nominal)

# In-flight tests throttle this file (``?slow=``) instead of using a huge
# payload: enough time to observe progress, speed and cancel — without
# generating a 100 MB file.


# A dedicated test server on its own port (serving LOCAL_DIR): ?slow=N serves a
# real file at ~N bytes/second (a 10 MB file at 0.25-0.75 MB/s stays in flight
# for 13-40 s: enough to observe the live progress row and cancel it), and a
# plain request is served at full speed by the no-cache base handler. The
# shared cursor-suite server (PORT) serves assets/ only, so it cannot serve the
# payloads.
SLOW_PORT = PORT + 1
_slow_server = None


class _ThrottledHandler(_NoCacheHandler):
    """The dedicated test server (SLOW_PORT): throttled streams + failed 404s.

    ?slow=N   — a real file served at ~N bytes/second (a 10 MB file at 0.5 MB/s
                stays in flight ~20 s: enough to observe progress, speed and
                cancel; an unthrottled LAN transfer finishes in ~0.1 s). The
                pacing is DEADLINE-BASED (sleep until the schedule time for the
                next chunk, not a fixed sleep after each write): a per-write
                sleep lets the OS TCP send buffer accumulate, and the kernel
                then flushes it to the device at full speed — the file lands
                in ~5 s regardless of the rate. The deadline scheme absorbs
                that buffering, so the *delivered* rate tracks N.
                Range requests are honoured (a 206 from the byte on) because
                the Android DownloadManager resumes/continues via Range.
    ?fail=1   — a 404 with a binary (octet-stream) body: a *plain* 404 from
                the shared server would answer with an HTML error page, which
                the WebView just renders, so no download would start at all.
                (Verified: the WebView never shows the download dialog for a
                404 response — it just renders the error page — so no failed
                download row can be produced this way.)
    ?slow=N&fail_after=M — the throttled stream is dropped mid-way, after M
                bytes (TCP reset): a REAL in-flight failure. This is what
                produces a failed row ('Error: 0' — the app hard-codes 0 for
                the reason) plus a partial file, which 'Clean up' removes.
    """

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=LOCAL_DIR, **kwargs)

    def _stream_throttled(self, path: str, rate: float, start: int = 0,
                          fail_after: int | None = None) -> None:
        size = os.path.getsize(path)
        remaining = size - start
        # Content-Length is the FULL size so the download looks legitimate;
        # for ?fail_after the connection is simply dropped before that many
        # bytes are delivered.
        self.send_response(206 if start else 200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(remaining))
        if start:
            self.send_header("Content-Range", f"bytes {start}-{size - 1}/{size}")
        self.end_headers()
        # Pacing that actually works against the TCP stack: write SMALL chunks
        # and enforce the rate on the FEED (sleep until the cumulative schedule
        # time for the bytes written so far). With large chunks the kernel send
        # buffer fills ahead of us and then flushes at line rate — the whole
        # file lands in seconds regardless of the nominal rate. Small chunks
        # keep the buffer nearly empty, so the device's delivery rate tracks
        # the feed rate.
        # Shrink the socket send buffer: with the default (multi-MB) buffer the
        # kernel accepts our writes instantly and drains them to the device at
        # line rate — write() never blocks, the deadline sleep stays ~0, and
        # the whole file lands in seconds regardless of the nominal rate. With
        # a small buffer, write() blocks as soon as the buffer is full, so the
        # *feed* into the socket is what paces the transfer.
        try:
            self.connection.setsockopt(
                socket.SOL_SOCKET, socket.SO_SNDBUF, 64 * 1024)
        except OSError:
            pass
        # DownloadManager RETRIES an interrupted download, resuming via Range.
        # fail_after is ABSOLUTE (from byte 0): if the client resumes at/after
        # that point, drop the connection immediately so the retry dies too
        # (otherwise a retry would happily finish the file and the download
        # would "succeed").
        if fail_after is not None and start >= fail_after:
            print(f"[slow-server] FAIL-AFTER: resume at {start} >= {fail_after}; "
                  f"dropping immediately", flush=True)
            try:
                self.connection.close()
            except OSError:
                pass
            return
        chunk = 8 * 1024
        t0 = time.time()
        sent = 0
        print(f"[slow-server] throttle START rate={rate} remaining={remaining} "
              f"start={start}" + (f" fail_after={fail_after}" if fail_after else ""),
              flush=True)
        with open(path, "rb") as f:
            f.seek(start)
            iters = 0
            while sent < remaining:
                n = min(chunk, remaining - sent)
                self.wfile.write(f.read(n))
                self.wfile.flush()
                sent += n
                delay = t0 + sent / rate - time.time()
                if delay > 0:
                    time.sleep(delay)
                iters += 1
                if fail_after is not None and start + sent >= fail_after:
                    print(f"[slow-server] FAIL-AFTER: dropping connection after "
                          f"{sent} bytes @ {time.time() - t0:.1f}s", flush=True)
                    # Abandon the connection hard: the client (DownloadManager)
                    # sees the transfer interrupted and marks the download
                    # failed.
                    try:
                        self.connection.close()
                    except OSError:
                        pass
                    return
                if iters % 512 == 0:  # every 4 MB
                    print(f"[slow-server]   {sent} bytes @ {time.time() - t0:.1f}s "
                          f"(eff {sent / max(0.001, time.time() - t0) / 1024:.0f} kB/s)",
                          flush=True)
        print(f"[slow-server] served {sent} bytes in {time.time() - t0:.1f}s "
              f"(rate param={rate}, start={start})", flush=True)

    def _range_start(self) -> int:
        header = self.headers.get("Range") or ""
        m = re.match(r"bytes=(\d+)-", header)
        return int(m.group(1)) if m else 0

    def do_GET(self):
        print(f"[slow-server] GET {self.path} from {self.client_address}", flush=True)
        url = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(url.query)
        # ?attach=1 adds a Content-Disposition: attachment header (with
        # filename) to an otherwise-plain file response; ?attach=0 is the
        # same file with NO content-disposition. Used to test which response
        # shapes trigger the download dialog (the WebView only calls
        # onDownloadStart for attachment-marked responses).
        path = self.translate_path(url.path)
        if os.path.isfile(path) and "attach" in params and "slow" not in params \
                and "fail" not in params:
            attach = params["attach"][0] == "1"
            size = os.path.getsize(path)
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(size))
            if attach:
                base = os.path.basename(path).split(".")[0]
                self.send_header("Content-Disposition", f'attachment; filename="{base}.bin"')
            self.end_headers()
            with open(path, "rb") as f:
                self.wfile.write(f.read())
            return
        if "fail" in params:
            body = b"not found\n"
            self.send_response(404)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        path = self.translate_path(url.path)
        if "slow" in params and os.path.isfile(path):
            fail_after = None
            if "fail_after" in params:
                fail_after = int(float(params["fail_after"][0]))
            # A ?fail_after stream that is RESUMED (Range request = a
            # DownloadManager retry) gets a 404: that is what flips the
            # entry from Paused (an interrupted download stays Paused
            # indefinitely — the manager never fails it on its own) to a
            # real FAILED status, which is what the tests assert on.
            start = self._range_start()
            if fail_after is not None and start > 0:
                print(f"[slow-server] RESUME of a fail_after stream: "
                      f"answering 404 (forces failure)", flush=True)
                self.send_response(404)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            try:
                self._stream_throttled(path, max(1.0, float(params["slow"][0])),
                                       start, fail_after)
            except (ConnectionAbortedError, BrokenPipeError) as e:
                print(f"[slow-server] client aborted: {e!r}", flush=True)
            return
        super().do_GET()


def _ensure_slow_server() -> None:
    global _slow_server
    if _slow_server is not None:
        return
    _slow_server = http.server.ThreadingHTTPServer(("127.0.0.1", SLOW_PORT), _ThrottledHandler)
    threading.Thread(target=_slow_server.serve_forever, daemon=True).start()


def _file_url(device, name: str, slow: float | None = None, fail: bool = False,
              fail_after: int | None = None) -> str:
    """URL for a test file.

    Plain: fast, cache-suppressing (``?cb=`` defeats caching).
    ``slow=N``: throttled to ~N bytes/s via the dedicated server (port+1).
    ``fail``: a 404 with a binary body (kept for reference — the WebView never
    shows the download dialog for a 404 response, it renders the error page).
    ``fail_after=M`` (requires ``slow``): the throttled stream is dropped after
    M bytes — a real in-flight failure.

    All modes go through the DEDICATED server (SLOW_PORT), whose handler serves
    ``LOCAL_DIR`` (the test payloads). The shared server (``PORT``) serves
    ``assets/`` only, so a payload URL on it 404s. The device reaches the port
    through an ``adb reverse`` tunnel (localhost on the device -> host).
    """
    _ensure_slow_server()
    device.reverse(SLOW_PORT)
    cb = int(time.time() * 1000)
    if fail:
        return "http://localhost:%d/%s?fail=1&cb=%d" % (SLOW_PORT, name, cb)
    if slow is not None:
        url = "http://localhost:%d/%s?slow=%d&cb=%d" % (SLOW_PORT, name, int(slow), cb)
        if fail_after is not None:
            url += "&fail_after=%d" % int(fail_after)
        return url
    return "http://localhost:%d/%s?cb=%d" % (SLOW_PORT, name, cb)


def _ensure_file(name: str, size: int | None = None) -> None:
    """Create a deterministic ``size``-byte file (default SIZE_BYTES) in the
    served directory (idempotent)."""
    os.makedirs(LOCAL_DIR, exist_ok=True)
    path = os.path.join(LOCAL_DIR, name)
    want = size if size is not None else SIZE_BYTES
    if os.path.exists(path) and os.path.getsize(path) == want:
        return
    with open(path, "wb") as f:
        remaining = want
        while remaining > 0:
            f.write(os.urandom(min(1024 * 1024, remaining)))
            remaining -= 1024 * 1024


# --- UI helpers (uiautomator-based, polling; rows stagger in) -----------------


def _nodes(device):
    return device.nodes()


def _texts(device):
    return [n.text for n in _nodes(device) if n.text]


def _wait_text(device, text, timeout=25.0):
    """True once any node's text contains ``text`` (substring, case-sensitive)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if any(text in t for t in _texts(device)):
            return True
        time.sleep(0.7)
    return any(text in t for t in _texts(device))


def _tap_text(device, text, timeout=10.0, exact=False, index=0):
    """Tap the ``index``-th node whose text is (or contains) ``text``.

    ``index=-1`` is the LAST match (e.g. the option dialog's 'Cancel download'
    row when the confirmation dialog's 'Cancel' button may also be on screen).
    Returns False if it never appeared.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        matches = [n for n in _nodes(device) if n.bounds
                   and (n.text == text if exact else text in n.text)]
        if matches and -len(matches) <= index < len(matches):
            cx, cy = matches[index].center
            device.tap(cx, cy, wait=1.2)
            return True
        time.sleep(0.7)
    return False


def _sheet_open(device):
    """Open the downloads sheet (the 'ACTIONS' category header is unique to it)."""
    device.launch_action("fulguris.action.OPEN_DOWNLOADS", wait=2.5)
    assert _wait_text(device, "ACTIONS", timeout=15.0), (
        "downloads sheet did not open (no 'ACTIONS' header; "
        f"nodes: {sorted(_texts(device))[:40]!r})"
    )
    time.sleep(1.0)  # rows stagger in


def _ensure_sheet(device):
    """Open the downloads sheet if it is not already up (the row nodes only
    exist in the view hierarchy while the sheet is shown)."""
    if not _wait_text(device, "DOWNLOADS", timeout=2.0):
        _sheet_open(device)


def _sheet_rows(device):
    """The sheet's preference rows as (title_text, enabled, node) — the 'title'
    TextViews of the preference list (the 'summary' TextViews are the status
    lines). Section headers (ACTIONS / DOWNLOADS) have no leading indent and
    are excluded by their full-width bounds."""
    rows = []
    for n in device.nodes():
        if n.resource_id.endswith("id/title") and n.bounds and n.text:
            # Section headers span almost the full width; rows start at x=135.
            if n.bounds[0] > 60:
                rows.append((n.text, n.enabled, n))
    return rows


def _sheet_row(device, title, timeout=10.0):
    """(enabled, node) for the sheet row titled ``title``, or (None, None)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for text, enabled, n in _sheet_rows(device):
            if text == title:
                return enabled, n
        time.sleep(0.5)
    for text, enabled, n in _sheet_rows(device):
        if text == title:
            return enabled, n
    return None, None


def _sheet_action_expanded(device):
    """Expand the 'Advanced' row (Clean up / Remove all / Delete all live under
    it as an expandable sub-category). Idempotent."""
    enabled, n = _sheet_row(device, "Advanced")
    if n is None:
        return
    # If the sub-rows are already visible, the 'Advanced' row may still be
    # present — tapping again would collapse them, so check first.
    _, sub = _sheet_row(device, "Clean up", timeout=1.0)
    if sub is None:
        device.tap(n.center[0], n.center[1], wait=1.5)


def _tap_sheet_row(device, title, timeout=10.0):
    """Tap the sheet row titled ``title``. False if it never appears."""
    enabled, n = _sheet_row(device, title, timeout)
    if n is None:
        return False
    device.tap(n.center[0], n.center[1], wait=1.5)
    return True


def _sheet_close(device):
    device.key(keys.BACK, 1.2)


def _row_titles(device):
    """The download row title nodes (indented 'title' TextViews of the
    DOWNLOADS section — below the 'DOWNLOADS' header node)."""
    nodes = _nodes(device)
    header = None
    for n in nodes:
        if n.resource_id.endswith("id/title") and n.text == "DOWNLOADS" and n.bounds:
            header = n
            break
    if header is None:
        return []
    return [n for n in nodes
            if n is not header and n.resource_id.endswith("id/title") and n.bounds
            and n.bounds[0] > 60 and n.bounds[1] > header.bounds[3]
            and n.text]


def _row_matches(device, name, timeout=30.0):
    """The download row title node(s) titled exactly ``name``, polling."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        matches = [n for n in _row_titles(device) if n.text == name]
        if matches:
            return matches
        time.sleep(0.7)
    return [n for n in _row_titles(device) if n.text == name]


def _row_summaries(device, name, timeout=30.0):
    """(title, summary) for every download row titled exactly ``name``.

    The sheet is a PreferenceScreen: each row is a title node with its summary
    directly below (the preference's status text, possibly two lines). The
    summary is the nearest text node whose top sits just under the title's
    bottom (the status line of the row, not the next row's title). The sheet
    is opened first — the rows are not in the hierarchy while it is closed.
    """
    _ensure_sheet(device)
    nodes = _nodes(device)
    rows = [n for n in _row_titles(device) if n.text == name]
    deadline = time.time() + timeout
    while not rows:
        time.sleep(0.7)
        if time.time() > deadline:
            break
        nodes = _nodes(device)
        rows = [n for n in _row_titles(device) if n.text == name]
    result = []
    for row in rows:
        best, best_d = None, 10 ** 9
        for m in nodes:
            if m is row or not m.text or not m.bounds or not m.bounds[1]:
                continue
            d = m.bounds[1] - row.bounds[3]
            if 0 <= d < 140 and d < best_d:
                best, best_d = m, d
        result.append((row.text, best.text if best else ""))
    return result


def _row_progressing(summary: str) -> bool:
    return bool(re.search(r"\b\d{1,3}%", summary)) or "Downloading…" in summary


def _parse_status(summary: str):
    """Break a row summary into (pct, speed_part, bytes_part).

    The summary is a two-line preference text:
      in flight:  ``"45% • 1.2 MB/s\n4.5 MB / 10.0 MB"`` (speed after ~10 s)
      completed:  ``"10.0 MB\n<date>"``   failed: ``"Error: 0\n<date>"``
    """
    m = re.search(r"\b(\d{1,3})%", summary)
    pct = int(m.group(1)) if m else None
    first_line = summary.split("\n")[0]
    speed_part = None
    if "•" in first_line:
        speed_part = first_line.split("•", 1)[1].strip()
    bytes_part = None
    m = re.search(r"([\d.]+\s*[KMG]?B?)\s*/\s*([\d.]+\s*[KMG]?B?)", summary)
    if m:
        bytes_part = f"{m.group(1)} / {m.group(2)}"
    return pct, speed_part, bytes_part


def _tap_row(device, name, timeout=15.0):
    """Tap the first row titled ``name`` (opens its per-download options dialog)."""
    rows = _row_matches(device, name, timeout)
    if not rows:
        return False
    cx, cy = rows[0].center
    device.tap(cx, cy, wait=1.5)
    return True


def _tap_download_button(device, timeout=15.0):
    """Tap the download dialog's 'Download' button.

    Both the Fulguris 'Download file?' dialog and the system 'Download file
    again?' conflict dialog carry a positive 'Download' button. The dialog
    title ('Download file?') and the message also CONTAIN the word, so the
    button is matched by EXACT text; of the exact matches the button is the
    last node in hierarchy order (title and message come first). The dialog
    animates in, so a short settle is needed after it is present: a tap during
    the animation can land on the scrim and CANCEL the dialog (seen in the
    wild).
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        nodes = _nodes(device)
        dialog_up = any(n.text in ("Download file?", "Download file again?")
                        for n in nodes)
        matches = [n for n in nodes
                   if n.bounds and n.text == "Download" and n.enabled]
        if dialog_up and matches:
            time.sleep(0.8)  # let the dialog finish animating before tapping
            cx, cy = matches[-1].center
            device.tap(cx, cy, wait=1.5)
            return True
        time.sleep(0.7)
    return False


def _start_download(device, name, url=None):
    """Navigate to a local file URL and confirm the download dialog.

    Two dialogs can appear, both with a 'Download' button:
      'Download file?'       — the normal Fulguris confirmation.
      'Download file again?' — the system conflict dialog, shown when the
                                destination file already exists on disk.
    (There is NO 'Downloading: …' snackbar for normal file downloads — that
    one is blob-only — so success is confirmed via the in-flight row.)
    Returns True once the in-flight row for ``name`` is visible in the sheet.
    """
    # A leftover file (e.g. from a previously crashed run, which left no sheet
    # entry to 'Delete all') would flip the dialog to the conflict variant.
    # Start from a clean slate.
    _rm_file(device, name)
    last_err = "unknown failure"
    for attempt in (1, 2):
        device.navigate(url or _file_url(device, name))
        if not _wait_text(device, "Download file", timeout=20.0):
            last_err = (f"no download dialog appeared for {name} "
                        f"(nodes: {sorted(_texts(device))[:40]!r})")
        elif not _tap_download_button(device):
            last_err = "no 'Download' button in the dialog"
        else:
            rows = _row_summaries(device, name, timeout=30.0)
            if rows:
                return True
            last_err = (f"no row for {name} appeared in the downloads sheet "
                        f"after confirming (nodes: {sorted(_texts(device))[:40]!r})")
        # Recover from whatever state the attempt left (dialog still up, sheet
        # open, cancelled by a stray tap) and try the whole flow once more.
        device.key(keys.BACK, 1.0)
        device.key(keys.BACK, 1.0)
        device.settle()
    assert False, last_err


def _wait_complete(device, name, timeout=180.0):
    """Wait until the (first) row for ``name`` stops showing progress."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        rows = _row_summaries(device, name, timeout=2.0)
        if rows and not _row_progressing(rows[0][1]) and "File not found" not in rows[0][1]:
            return rows[0]
        time.sleep(1.0)
    rows = _row_summaries(device, name, timeout=2.0)
    return rows[0] if rows else (None, None)


def _disk(device, name):
    """True if ``name`` exists in the device's public Downloads directory."""
    out = device.transport.shell(["shell", "ls", "-l", "/sdcard/Download"], timeout=20)
    return any(re.search(r"\s%s$" % re.escape(name), line) for line in out.splitlines())


def _disk_names(device, stem):
    """Names in /sdcard/Download containing ``stem`` (for the system's -1 re-naming)."""
    out = device.transport.shell(["shell", "ls", "-l", "/sdcard/Download"], timeout=20)
    return [l.split()[-1] for l in out.splitlines() if stem in l and l.split()[-1]]


def _rm_file(device, name):
    """Best effort: delete ``name`` from /sdcard/Download (a leftover from a
    crashed run has no sheet entry, so 'Delete all' would not remove it, and it
    would make a fresh download show the system's 'Download file again?'
    conflict dialog instead of 'Download file?')."""
    try:
        device.transport.shell(["shell", "rm", "-f", f"/sdcard/Download/{name}"], timeout=20)
    except Exception as e:  # noqa: BLE001 - hygiene only
        print(f"  note: _rm_file({name}) failed: {e!r}")


def _cleanup_all(device):
    """Best effort: wipe every download entry and its files via 'Delete all'.

    'Delete all' lives under the expandable 'Advanced' row. This is a reset
    helper (called before every test), so failures here are logged but not
    raised — the test's own assertions will surface a broken sheet.
    """
    try:
        _sheet_open(device)
        if _wait_text(device, "Your download list is empty", timeout=6.0):
            _sheet_close(device)
            return
        _sheet_action_expanded(device)
        if _tap_sheet_row(device, "Delete all", timeout=8.0):
            assert _tap_text(device, "Delete", timeout=8.0, exact=True), "no 'Delete' confirm button"
            _wait_text(device, "Your download list is empty", timeout=20.0)
    except Exception as e:  # noqa: BLE001 - cleanup must never mask the real assertion
        print(f"  note: _cleanup_all failed ({e!r}) — the test proceeds anyway")
    finally:
        _sheet_close(device)


# --- tests --------------------------------------------------------------------


def test_downloads_sheet_empty_state(device, ctx: dict) -> None:
    """A fresh sheet shows the empty state and only 'Open folder' is actionable."""
    device.settle()
    _cleanup_all(device)
    _sheet_open(device)
    assert _wait_text(device, "Your download list is empty", timeout=20.0), (
        f"expected the empty-state text (nodes: {sorted(_texts(device))[:40]!r})"
    )
    # 'Open folder' is always enabled on the empty sheet.
    of_enabled, of_node = _sheet_row(device, "Open folder")
    assert of_node is not None, "'Open folder' row missing from the empty sheet"
    assert of_enabled, "'Open folder' should be enabled on the empty sheet"
    # The list-wide actions (under the expandable 'Advanced' row) are all
    # disabled while the list is empty.
    _sheet_action_expanded(device)
    for label in ("Clean up", "Remove all", "Delete all"):
        enabled, row = _sheet_row(device, label)
        assert row is not None, f"'{label}' row missing after expanding 'Advanced'"
        assert not enabled, f"'{label}' should be disabled while the list is empty"
    _sheet_close(device)


def test_downloads_dialog_and_completion(device, ctx: dict) -> None:
    """File-URL download: dialog -> in-flight row -> completed row + file on disk.

    Asserts the dialog copy (including the known size), the "Downloading:"
    snackbar, the in-flight row (percentage + ``bytes / total``), the completed
    row summary (the formatted file size) and the file on disk.
    """
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    device.settle()

    # Throttled so the in-flight row is observable for well over a minute: the
    # speed part (• …/s) only appears once the fragment's 10 s sample window
    # holds two samples, so the transfer must outlive that comfortably.
    if not _start_download(device, FILE_SMALL, url=_file_url(device, FILE_SMALL, slow=SLOW_RATE)):
        _cleanup_all(device)
        assert False, "download did not start"

    # The in-flight row (percentage + 'bytes / total') while the transfer runs.
    title, summary = _row_summaries(device, FILE_SMALL, timeout=30.0)[0]
    deadline = time.time() + 30.0
    while time.time() < deadline and not _row_progressing(summary):
        title, summary = _row_summaries(device, FILE_SMALL, timeout=2.0)[0]
    assert _row_progressing(summary), (
        f"row never showed progress while the download was in flight "
        f"(title: {title!r}, summary: {summary!r})"
    )
    pct, speed_part, bytes_part = _parse_status(summary)
    assert pct is not None, f"row should show a percentage while running, got {summary!r}"
    # The total renders as '15 MB' or '15.00 MB' (Formatter.formatFileSize); the decimal
    # separator follows the device locale ('.' in en-GB, ',' in en-DE), so accept both.
    assert bytes_part and re.search(r"/\s*15([.,]0+)?\s*MB$", bytes_part), (
        f"row should show 'bytes / 15 MB' while running, got {summary!r}"
    )
    # The speed part (• …/s) appears only once the 10 s sample window has two
    # samples — poll for it while the throttled transfer is still alive.
    deadline = time.time() + 30.0
    saw_speed = False
    while time.time() < deadline:
        rows = _row_summaries(device, FILE_SMALL, timeout=2.0)
        if rows:
            _, sp, _ = _parse_status(rows[0][1])
            if sp and re.search(r"\d", sp):
                saw_speed = True
                break
        time.sleep(1.0)
    assert saw_speed, "the in-flight row never showed the speed (• …/s) part"

    # Completion: the row stops showing progress and reports its total size.
    title, summary = _wait_complete(device, FILE_SMALL, timeout=180.0)
    assert title, "row disappeared before completion"
    assert not _row_progressing(summary), f"row still shows progress: {summary!r}"
    assert re.search(r"\b15([.,]0+)?\s*MB", summary), (
        f"completed row should report its size ('15 MB'), got summary {summary!r}"
    )
    assert _disk(device, FILE_SMALL), f"{FILE_SMALL} is missing from /sdcard/Download"

    # The dialog copy (title + 'will be saved to' + known size) on a fast run:
    # the confirmation dialog is the only place that shows the size before the
    # download starts, so trigger it again (the first file was just removed
    # below — do the copy check FIRST, on the second dialog, then abort it).
    # (Kept after completion: the row/dialog state is stable here.)
    assert _tap_row(device, FILE_SMALL), "row not tappable for cleanup"
    assert _tap_text(device, "Remove and delete", timeout=10.0), "no 'Remove and delete' option"
    assert _tap_text(device, "Delete", timeout=8.0, exact=True), "no confirm button"
    assert not _row_matches(device, FILE_SMALL, timeout=10.0), "row still present after remove"
    assert not _disk(device, FILE_SMALL), "file should be gone after 'Remove and delete'"
    _sheet_close(device)

    # Dialog copy, on a fresh (unthrottled) navigation: title, message and the
    # known size from Content-Length — then abort via Cancel (nothing is saved).
    device.navigate(_file_url(device, FILE_SMALL))
    assert _wait_text(device, "Download file?", timeout=20.0), "second dialog did not appear"
    assert _wait_text(device, "will be saved to your downloads folder", timeout=5.0), (
        "dialog message copy missing"
    )
    assert _wait_text(device, "Size: 15", timeout=5.0), (
        "the dialog should report the known file size ('Size: 15[.00] MB') "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    assert _tap_text(device, "Cancel", timeout=10.0), "no 'Cancel' button in the dialog"
    time.sleep(1.0)


def test_downloads_in_progress_row_states(device, ctx: dict) -> None:
    """A throttled download stays in flight long enough to observe 'NN%', the
    'bytes / total' counter and the speed part, then completes on its own."""
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    device.settle()

    if not _start_download(device, FILE_SMALL, url=_file_url(device, FILE_SMALL, slow=SLOW_RATE)):
        _cleanup_all(device)
        assert False, "download did not start"
    # Throttled: in flight for a minute or more (see SLOW_RATE note).
    title, summary = _row_summaries(device, FILE_SMALL, timeout=30.0)[0]
    deadline = time.time() + 90.0
    saw_pct = saw_bytes = False
    while time.time() < deadline:
        pct, speed_part, bytes_part = _parse_status(summary)
        if pct is not None:
            saw_pct = True
        if bytes_part:
            saw_bytes = True
        if saw_pct and saw_bytes and speed_part:
            break
        title, summary = _row_summaries(device, FILE_SMALL, timeout=2.0)[0]
        time.sleep(1.0)
    assert saw_pct, f"no percentage ever shown while in flight (last: {summary!r})"
    assert saw_bytes, f"no 'bytes / total' counter ever shown while in flight (last: {summary!r})"
    # Let it finish naturally, then clean up.
    title, summary = _wait_complete(device, FILE_SMALL, timeout=240.0)
    assert title, "row disappeared before completion"
    assert re.search(r"\b15([.,]0+)?\s*MB", summary), (
        f"completed row should report its size ('15 MB'), got summary {summary!r}"
    )
    _cleanup_all(device)


def test_downloads_failure_mid_download(device, ctx: dict) -> None:
    """A download whose stream dies mid-way ends in a failed 'Error: 0' row.

    The stream is throttled and dropped after ~5 MB of 15 MB. (A 404 can't
    be used directly: the WebView never shows the download dialog for a 404
    response — it renders the error page instead, so no download row is ever
    created.)

    Mechanism, verified on device: a bare mid-stream drop leaves the entry
    PAUSED indefinitely (DownloadManager never fails a paused entry on its
    own, and shows no 'Download failed' snackbar for it). The manager does
    retry, though — with a Range request — and the test server answers a
    resumed ?fail_after stream with a 404, which is what finally fails the
    entry. The app renders the failed row's reason as a hard-coded 0
    ('Error: 0', see formatSummary); once it is genuinely failed, the
    'Download failed' snackbar is expected as well.

    The failed row is left in place for the Clean-up test (it runs next) —
    that's what 'Clean up' is for. The app does NOT delete the partial file
    of a failed entry, so it is removed here directly (Clean-up's job is
    entries; the next test's _cleanup_all/_rm_file would otherwise meet a
    file with no entry).
    """
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    _rm_file(device, FILE_SMALL)
    device.settle()

    url = _file_url(device, FILE_SMALL, slow=SLOW_RATE, fail_after=5_000_000)
    device.navigate(url)
    assert _wait_text(device, "Download file?", timeout=20.0), "dialog did not appear"
    assert _tap_download_button(device), "no 'Download' button in the dialog"
    # ~25 s to the drop, then the manager's retry hits the 404 and fails the
    # entry (its own backoff is ~tens of seconds on top).
    saw_failure_snackbar = _wait_text(device, "Download failed", timeout=90.0)
    rows = _row_summaries(device, FILE_SMALL, timeout=30.0)
    assert saw_failure_snackbar or (rows and "Error: 0" in rows[0][1]), (
        "neither the 'Download failed' snackbar nor an 'Error: 0' row appeared "
        f"(rows: {rows!r}, nodes: {sorted(_texts(device))[:40]!r})"
    )
    if rows:
        assert "Error: 0" in rows[0][1], f"failed row summary should be 'Error: 0', got {rows[0][1]!r}"
    _rm_file(device, FILE_SMALL)  # the app keeps the partial file of a failed entry


def test_downloads_cancel_running(device, ctx: dict) -> None:
    """Cancelling a running download removes the row AND the partial file.

    Throttled so the download is still alive when we cancel it. The row's
    'Cancel download' option is matched LAST (index=-1): the 'Download file?'
    dialog's 'Cancel' button may still be on screen and would match first.
    """
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    device.settle()

    if not _start_download(device, FILE_SMALL, url=_file_url(device, FILE_SMALL, slow=SLOW_RATE)):
        _cleanup_all(device)
        assert False, "download did not start"
    # Throttled: in flight for a minute or more — plenty of time to open the
    # sheet and cancel. (The row already exists: _start_download opened it.)
    _ensure_sheet(device)
    time.sleep(8.0)  # let a meaningful partial file accumulate
    assert _disk(device, FILE_SMALL), "the partial file should exist while running"
    assert _tap_row(device, FILE_SMALL), "row not found to cancel"
    assert _wait_text(device, "Cancel download", timeout=10.0), "no 'Cancel download' option"
    assert _tap_text(device, "Cancel download", timeout=10.0, index=-1), "tapping 'Cancel download' failed"
    time.sleep(2.5)
    assert not _row_matches(device, FILE_SMALL, timeout=15.0), "row should be gone after cancelling"
    assert not _disk(device, FILE_SMALL), "the partial file should be deleted on cancel"
    _sheet_close(device)


def test_downloads_clean_up_failed(device, ctx: dict) -> None:
    """'Clean up' removes exactly the failed/orphaned entries (and their files).

    Depends on the mid-download failure test (run just before) leaving a failed
    row, so the 'Clean up' row (under 'Advanced') is enabled.
    """
    device.settle()
    _sheet_open(device)
    _sheet_action_expanded(device)
    enabled, row = _sheet_row(device, "Clean up", timeout=10.0)
    assert row is not None, "no 'Clean up' row after expanding 'Advanced'"
    assert enabled, (
        f"'Clean up' should be enabled while a failed row is present "
        f"(rows: {_row_titles(device)!r})"
    )
    assert _tap_sheet_row(device, "Clean up"), "tapping 'Clean up' failed"
    # Confirmation: 'Clean up downloads?' with a positive 'Clean up' button.
    assert _wait_text(device, "Clean up downloads?", timeout=10.0), "no confirm dialog"
    assert _tap_text(device, "Clean up", timeout=10.0, exact=True), "no 'Clean up' confirm button"
    assert not _row_matches(device, FILE_SMALL, timeout=15.0), "the failed row should be removed by Clean up"
    _sheet_close(device)


def test_downloads_delete_file_orphans_entry(device, ctx: dict) -> None:
    """'Delete file' deletes the file but keeps the entry (row goes orphaned)."""
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    device.settle()
    _start_download(device, FILE_SMALL)
    _wait_complete(device, FILE_SMALL, timeout=180.0)
    assert _disk(device, FILE_SMALL), "file should exist before deletion"

    assert _tap_row(device, FILE_SMALL), "row not tappable"
    assert _tap_text(device, "Delete file", timeout=10.0), "no 'Delete file' option"
    assert _tap_text(device, "Delete", timeout=8.0, exact=True), "no confirm button"
    rows = _row_summaries(device, FILE_SMALL, timeout=20.0)
    assert rows, "the entry should remain after 'Delete file'"
    assert "File not found" in rows[0][1], (
        f"orphaned row should read 'File not found', got summary {rows[0][1]!r}"
    )
    assert not _disk(device, FILE_SMALL), "file should be gone after 'Delete file'"

    # Hygiene: remove the orphaned entry.
    assert _tap_row(device, FILE_SMALL)
    if not _tap_text(device, "Remove from list", timeout=8.0):
        assert _tap_text(device, "Remove and delete", timeout=8.0)
        assert _tap_text(device, "Delete", timeout=8.0, exact=True)
    assert not _row_matches(device, FILE_SMALL, timeout=10.0), "orphaned row should be gone"


def test_downloads_remove_and_keep(device, ctx: dict) -> None:
    """'Remove and keep' deletes the entry but keeps the file on disk."""
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    device.settle()
    _start_download(device, FILE_SMALL)
    _wait_complete(device, FILE_SMALL, timeout=180.0)
    assert _disk(device, FILE_SMALL)

    assert _tap_row(device, FILE_SMALL), "row not tappable"
    assert _tap_text(device, "Remove and keep", timeout=10.0), "no 'Remove and keep' option"
    assert _tap_text(device, "Remove", timeout=8.0, exact=True), "no confirm button"
    assert not _row_matches(device, FILE_SMALL, timeout=10.0), "row should be gone"
    assert _disk(device, FILE_SMALL), "file should be KEPT after 'Remove and keep'"
    device.transport.shell(["shell", "rm", "-f", f"/sdcard/Download/{FILE_SMALL}"], timeout=20)


def test_downloads_remove_and_delete(device, ctx: dict) -> None:
    """'Remove and delete' deletes both the entry and the file."""
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    device.settle()
    _start_download(device, FILE_SMALL)
    _wait_complete(device, FILE_SMALL, timeout=180.0)
    assert _disk(device, FILE_SMALL)

    assert _tap_row(device, FILE_SMALL), "row not tappable"
    assert _tap_text(device, "Remove and delete", timeout=10.0), "no 'Remove and delete' option"
    assert _tap_text(device, "Delete", timeout=8.0, exact=True), "no confirm button"
    assert not _row_matches(device, FILE_SMALL, timeout=10.0), "row should be gone"
    assert not _disk(device, FILE_SMALL), "file should be gone after 'Remove and delete'"


def test_downloads_remove_all_keeps_files(device, ctx: dict) -> None:
    """'Remove all' clears every entry but keeps the files on disk."""
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    device.settle()

    _start_download(device, FILE_SMALL)
    _wait_complete(device, FILE_SMALL, timeout=180.0)
    assert _disk(device, FILE_SMALL)

    _sheet_open(device)
    _sheet_action_expanded(device)
    assert _tap_sheet_row(device, "Remove all", timeout=10.0), "no 'Remove all' row"
    assert _wait_text(device, "Remove all downloads?", timeout=10.0), "no confirm dialog"
    assert _tap_text(device, "Remove", timeout=8.0, exact=True), "no 'Remove' confirm button"
    assert _wait_text(device, "Your download list is empty", timeout=20.0), (
        "sheet should be empty after 'Remove all'"
    )
    _sheet_close(device)
    assert _disk(device, FILE_SMALL), "file should be KEPT after 'Remove all'"
    device.transport.shell(["shell", "rm", "-f", f"/sdcard/Download/{FILE_SMALL}"], timeout=20)


def test_downloads_delete_all(device, ctx: dict) -> None:
    """'Delete all' clears every entry AND deletes the files."""
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    device.settle()

    _start_download(device, FILE_SMALL)
    _wait_complete(device, FILE_SMALL, timeout=180.0)
    assert _disk(device, FILE_SMALL)

    _sheet_open(device)
    _sheet_action_expanded(device)
    assert _tap_sheet_row(device, "Delete all", timeout=10.0), "no 'Delete all' row"
    assert _wait_text(device, "Delete all downloads?", timeout=10.0), "no confirm dialog"
    assert _tap_text(device, "Delete", timeout=8.0, exact=True), "no 'Delete' confirm button"
    assert _wait_text(device, "Your download list is empty", timeout=20.0), (
        "sheet should be empty after 'Delete all'"
    )
    _sheet_close(device)
    assert not _disk(device, FILE_SMALL), "file should be DELETED after 'Delete all'"


def test_downloads_same_url_twice_creates_second_file(device, ctx: dict) -> None:
    """Downloading the same URL twice creates two entries; the file is re-named."""
    _ensure_file(FILE_SMALL)
    _cleanup_all(device)
    device.settle()
    stem = FILE_SMALL.rsplit(".", 1)[0]  # autotest_small_10mb

    _start_download(device, FILE_SMALL)
    _wait_complete(device, FILE_SMALL, timeout=180.0)
    assert _disk(device, FILE_SMALL)

    # Close the sheet first: _wait_complete leaves it open (row helpers keep
    # it open), and navigating with the sheet in front blocks the WebView from
    # showing the second download dialog. navigate() itself also backs out of
    # any focused address field before typing.
    _sheet_close(device)
    device.settle()

    # The system re-names the second file (e.g. autotest_small_10mb-1.bin).
    # The second navigation shows the conflict dialog ('Download file again?')
    # because the first file still exists — same 'Download' button.
    device.navigate(_file_url(device, FILE_SMALL))
    assert _wait_text(device, "Download file", timeout=25.0), "second dialog did not appear"
    assert _tap_download_button(device), "no 'Download' button in the second dialog"
    _wait_complete(device, FILE_SMALL, timeout=240.0)

    names = _disk_names(device, stem)
    assert len(names) >= 2, (
        f"expected two files after downloading the same URL twice, found {names!r}"
    )
    assert any(re.search(r"-1\.", n) for n in names), (
        f"the second file should carry the system's '-1' suffix, found {names!r}"
    )

    # Hygiene: wipe entries and both files.
    _cleanup_all(device)
    for n in names:
        device.transport.shell(["shell", "rm", "-f", f"/sdcard/Download/{n}"], timeout=20)


# --- link-pattern tests -------------------------------------------------------
#
# Real web pages offer downloads as plain LINKS with a `download` attribute
# (and/or a Content-Disposition: attachment response header), not as blob:
# URLs. The download-attribute filename must be honored — it is the name the
# site asked the user to save the file as. The page (assets/
# download_links.html) serves every pattern from the same tiny file so only
# the headers/attributes differ.

from cursor_tests import _ensure_server as _ensure_assets_server  # noqa: E402

LINKS_PAGE = "http://localhost:%d/download_links.html" % PORT
ATTR_FILENAME = "autotest_plain_attr.txt"   # download attribute on pattern 1
JS_ATTR_FILENAME = "autotest_js_attr.txt"   # JS anchor download attribute (5)


def _links_ready(device) -> None:
    """Bring up both servers, reverse both ports and land on the links page."""
    _ensure_file(FILE_TINY, size=64 * 1024)
    _ensure_assets_server()
    _ensure_slow_server()
    device.reverse(PORT)
    device.reverse(SLOW_PORT)
    _cleanup_all(device)
    device.navigate(LINKS_PAGE)
    if not _wait_text(device, "Download link patterns", timeout=15.0):
        assert False, (
            f"links page did not load (nodes: {sorted(_texts(device))[:40]!r})"
        )


def _tap_link(device, link_id: str) -> None:
    for n in _nodes(device):
        if (n.resource_id or "").endswith(link_id) and n.bounds and n.enabled:
            device.tap(n.center[0], n.center[1], wait=2.0)
            return
    assert False, (
        f"link with id suffix {link_id!r} not found "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )


def _back_to_links(device) -> None:
    """Return to the links page after a link tap (dialog cancelled / page navigated)."""
    # Cancel the confirmation dialog if it is still up, then go back.
    if _wait_text(device, "Download file?", timeout=3.0):
        assert _tap_text(device, "Cancel", timeout=10.0), "no 'Cancel' in dialog"
        time.sleep(1.0)
    device.navigate(LINKS_PAGE)
    assert _wait_text(device, "Download link patterns", timeout=15.0), "links page not reloaded"


def test_download_link_attr_filename(device, ctx: dict) -> None:
    """<a download="name.txt"> with NO Content-Disposition header.

    The most common link-download pattern on the web (GitHub release buttons,
    forum attachments, etc.). The dialog must offer the file under the
    `download`-attribute name — NOT the URL basename — and confirming must
    save the file under that name.
    """
    _links_ready(device)
    _rm_file(device, ATTR_FILENAME)
    _rm_file(device, FILE_TINY)
    _tap_link(device, "p_attr")
    assert _wait_text(device, "Download file?", timeout=20.0), (
        f"no download dialog for a <a download> link "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    assert _wait_text(device, ATTR_FILENAME, timeout=10.0), (
        f"dialog must propose the download-attribute filename "
        f"{ATTR_FILENAME!r}, not the URL basename "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    assert _tap_download_button(device), "no 'Download' button"
    _sheet_open(device)
    title, summary = _wait_complete(device, ATTR_FILENAME, timeout=60.0)
    assert title == ATTR_FILENAME, (
        f"downloads sheet row must be titled {ATTR_FILENAME!r}, got {title!r} "
        f"(summary: {summary!r})"
    )
    _sheet_close(device)
    assert _disk(device, ATTR_FILENAME), (
        f"{ATTR_FILENAME} is missing from /sdcard/Download "
        f"(dir: {_disk_names(device, 'autotest_')})"
    )


def test_download_link_js_anchor_filename(device, ctx: dict) -> None:
    """JS-created anchor (a.click()) with a download attribute, NO Content-
    Disposition. The GitHub-style programmatic download must also honor the
    attribute filename.
    """
    _links_ready(device)
    _rm_file(device, JS_ATTR_FILENAME)
    _rm_file(device, FILE_TINY)
    _tap_link(device, "p_js_attr")
    assert _wait_text(device, "Download file?", timeout=20.0), (
        f"no download dialog for a JS-created <a download> anchor "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    assert _wait_text(device, JS_ATTR_FILENAME, timeout=10.0), (
        f"dialog must propose the JS download-attribute filename "
        f"{JS_ATTR_FILENAME!r}, not the URL basename "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    assert _tap_download_button(device), "no 'Download' button"
    _sheet_open(device)
    title, _ = _wait_complete(device, JS_ATTR_FILENAME, timeout=60.0)
    assert title == JS_ATTR_FILENAME, (
        f"downloads sheet row must be titled {JS_ATTR_FILENAME!r}, got {title!r}"
    )
    _sheet_close(device)
    assert _disk(device, JS_ATTR_FILENAME), (
        f"{JS_ATTR_FILENAME} is missing from /sdcard/Download "
        f"(dir: {_disk_names(device, 'autotest_')})"
    )


def test_download_link_content_disposition_filename(device, ctx: dict) -> None:
    """Content-Disposition: attachment filename, NO download attribute.

    The server-provided name is the baseline (known-working) pattern — it must
    keep working: dialog + file under the header's filename.
    """
    cd_name = "autotest_tiny.bin"  # the handler sends the request basename
    _links_ready(device)
    _rm_file(device, cd_name)
    _tap_link(device, "p_cd")
    assert _wait_text(device, "Download file?", timeout=20.0), (
        f"no download dialog for a Content-Disposition: attachment link "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    assert _wait_text(device, cd_name, timeout=10.0), (
        f"dialog must propose {cd_name!r} (nodes: {sorted(_texts(device))[:40]!r})"
    )
    assert _tap_download_button(device), "no 'Download' button"
    _sheet_open(device)
    title, _ = _wait_complete(device, cd_name, timeout=60.0)
    assert title == cd_name, (
        f"downloads sheet row must be titled {cd_name!r}, got {title!r}"
    )
    _sheet_close(device)
    assert _disk(device, cd_name), (
        f"{cd_name} is missing from /sdcard/Download "
        f"(dir: {_disk_names(device, 'autotest_')})"
    )


FEATURE_GROUPS = {
    "downloads-links": [
        test_download_link_attr_filename,
        test_download_link_js_anchor_filename,
        test_download_link_content_disposition_filename,
    ],
    "downloads-full": [
        test_downloads_sheet_empty_state,
        test_downloads_dialog_and_completion,
        test_downloads_in_progress_row_states,
        # failure_mid_download deliberately LEAVES its failed row behind for
        # clean_up_failed (the next test) — nothing that calls _cleanup_all()
        # may run between the two, or the row is gone by the time clean-up
        # checks for it.
        test_downloads_failure_mid_download,
        test_downloads_clean_up_failed,
        test_downloads_cancel_running,
        test_downloads_delete_file_orphans_entry,
        test_downloads_remove_and_keep,
        test_downloads_remove_and_delete,
        test_downloads_remove_all_keeps_files,
        test_downloads_delete_all,
        test_downloads_same_url_twice_creates_second_file,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_download_link_attr_filename": (
        "<a download='name.txt'> link, no Content-Disposition: dialog + row + "
        "file use the download-attribute filename, not the URL basename"
    ),
    "test_download_link_js_anchor_filename": (
        "JS-created <a download> anchor (a.click()), no Content-Disposition: "
        "the attribute filename is honored"
    ),
    "test_download_link_content_disposition_filename": (
        "Content-Disposition: attachment link, no download attribute: the "
        "server's filename is honored (baseline)"
    ),
    "test_downloads_sheet_empty_state": (
        "A fresh downloads sheet shows the empty state; only 'Open folder' is enabled"
    ),
    "test_downloads_dialog_and_completion": (
        "File-URL download: dialog copy + known size, in-flight row (percentage + "
        "'bytes / total' + speed), completed row (size), file on disk"
    ),
    "test_downloads_in_progress_row_states": (
        "While a download runs, the row shows 'NN%' and the '• speed' sample; "
        "cancelling removes the row and the partial file"
    ),
    "test_downloads_failure_mid_download": (
        "A stream that dies mid-download ends in an 'Error: 0' row / 'Download failed' "
        "snackbar (a 404 can't be used: no dialog is shown for error responses)"
    ),
    "test_downloads_cancel_running": (
        "'Cancel download' on a running download removes the row and the partial file"
    ),
    "test_downloads_clean_up_failed": (
        "'Clean up' removes the failed/orphaned entries only"
    ),
    "test_downloads_delete_file_orphans_entry": (
        "'Delete file' deletes the file, keeps the entry, and orphans it ('File not found')"
    ),
    "test_downloads_remove_and_keep": (
        "'Remove and keep' deletes the entry but keeps the file on disk"
    ),
    "test_downloads_remove_and_delete": (
        "'Remove and delete' deletes both the entry and the file"
    ),
    "test_downloads_remove_all_keeps_files": (
        "'Remove all' clears every entry but keeps the files on disk"
    ),
    "test_downloads_delete_all": (
        "'Delete all' clears every entry and deletes the files"
    ),
    "test_downloads_same_url_twice_creates_second_file": (
        "Downloading the same URL twice creates two entries; the system re-names the file '-1'"
    ),
}
