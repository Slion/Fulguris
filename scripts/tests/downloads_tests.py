"""Downloads bottom sheet tests, driven through the framework Device API.

These exercise the in-app downloads sheet without needing to walk the menu
with D-pad keys: the app exposes a custom intent
(``fulguris.action.OPEN_DOWNLOADS``) that opens the downloads bottom sheet
directly (same pattern as OPEN_CONFIGURATION used by settings_tests).

Notable regression covered here: on some Android 10 devices (Huawei/EMUI)
opening the Downloads sheet while a download is in progress crashed the app
with NoSuchMethodError — the ContentObserver called a 3-arg super.onChange
that EMUI's framework.jar does not expose (GitHub issue #802).

    python scripts/tests/run.py --device SERIAL --group downloads
    python scripts/tests/run.py --device SERIAL --test downloads_sheet_survives
"""
from __future__ import annotations

import re
import time

from framework import keys

# 100 MB test file: in progress long enough on a normal line that the download
# is still active while the sheet is open. (If a very fast connection finishes
# it before the sheet opens, the "Downloading…" assertion below fails loudly
# rather than passing vacuously.)
DOWNLOAD_URL = "https://sgp-ping.vultr.com/vultr.com.100MB.bin"

OPEN_DOWNLOADS_ACTION = "fulguris.action.OPEN_DOWNLOADS"


def _node_texts(device) -> list[str]:
    return [n.text for n in device.nodes() if n.text]


def _has_text(device, text: str) -> bool:
    """True if any node's text contains ``text``.

    Substring match because some rows group several labels in one node (the
    sheet's action row reads "Clean up, Remove all, Delete all").
    """
    return any(text in t for t in _node_texts(device))


def _wait_for_node_text(device, text: str, timeout: float = 20.0) -> bool:
    """Poll the UI hierarchy until a node shows ``text`` (or the timeout elapses).

    Bails out early if the app loses the foreground (it crashed) so a red run
    fails fast with a useful message instead of polling for the full timeout.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        if _has_text(device, text):
            return True
        if device.foreground_package() != device.package:
            return False
        time.sleep(1.0)
    return _has_text(device, text)


def _download_in_progress(device) -> bool:
    """True if the sheet shows a download in progress.

    With a known total size the preference summary reads
    ``NN% • speed\nbytes / total`` (see ``DownloadPreference.formatSummary``);
    without one it falls back to ``Downloading…``.
    """
    for t in _node_texts(device):
        if "Downloading…" in t:
            return True
        if re.search(r"\b\d{1,3}%\s*•", t):
            return True
    return False


def _wait_for_download_progress(device, timeout: float = 30.0) -> bool:
    """Poll until a download shows as in progress (or the app crashes / timeout)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if _download_in_progress(device):
            return True
        if device.foreground_package() != device.package:
            return False
        time.sleep(1.0)
    return _download_in_progress(device)


def _fatal_excerpt(device, max_lines: int = 5) -> str:
    """A short excerpt of any FATAL EXCEPTION lines, for failure messages."""
    out = device.logcat("FATAL EXCEPTION")
    return "\n    ".join(l for l in out.splitlines() if l.strip())[:500] if out else ""


def test_downloads_sheet_survives_active_download(device, ctx: dict) -> None:
    """Opening the Downloads sheet during an active download must not crash.

    Reproduces GitHub issue #802: a download in progress fires ContentObserver
    notifications; on EMUI Android 10 the first one crashed the app inside
    onChange (NoSuchMethodError on the 3-arg super call). The sheet must stay
    open, show the download in progress, and the app must stay in the
    foreground.
    """
    device.settle()
    device.key(keys.BACK, 1.0)  # drop any leftover sheet/keyboard state
    device.logcat("", clear=True)
    device.navigate(DOWNLOAD_URL)

    # Navigating to the .bin URL pops Fulguris's "Download file?" confirmation
    # dialog — accept it so the download actually starts (the dialog is the
    # default entry point; the blob-URL variant has its own early dialog).
    assert _wait_for_node_text(device, "Download file?"), (
        "download confirmation dialog did not appear after navigating "
        f"(nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    device.tap_text("Download", timeout=10.0)

    # Open the downloads sheet straight from the command line (same path the
    # user hits from the menu).
    device.launch_action(OPEN_DOWNLOADS_ACTION, wait=3.0)
    assert _wait_for_node_text(device, "Clean up"), (
        "downloads bottom sheet did not open: 'Clean up' not found "
        f"(nodes: {sorted(_node_texts(device))[:40]!r})"
    )

    # The download must actually be in progress while the sheet is open, or the
    # content observer would never fire and the bug could not be reproduced.
    # (English device locale, matching the rest of the suite.)
    assert _wait_for_download_progress(device, timeout=30.0), (
        "no download in progress was ever visible in the sheet; cannot verify "
        f"issue #802 (nodes: {sorted(_node_texts(device))[:40]!r})"
    )
    assert device.foreground_package() == device.package, (
        f"app crashed or lost foreground while the sheet opened: "
        f"{device.foreground_package()!r}\n{_fatal_excerpt(device)}"
    )

    # Stay on the sheet through several more progress notifications; the crash
    # fires on the first notification after the sheet is open, so if we get
    # this far the app survived.
    for _ in range(5):
        time.sleep(1.0)
        if device.foreground_package() != device.package:
            break
    assert device.foreground_package() == device.package, (
        f"app crashed while the downloads sheet was open during an active "
        f"download (issue #802): {device.foreground_package()!r}\n"
        f"{_fatal_excerpt(device)}"
    )

    # The crash is a FATAL EXCEPTION — double-check the log for it either way.
    fatal = device.logcat("FATAL EXCEPTION")
    assert not fatal, f"FATAL EXCEPTION logged during the test:\n{fatal[:500]}"

    # Hygiene: close the sheet.
    device.key(keys.BACK, 1.2)


FEATURE_GROUPS = {
    "downloads": [
        test_downloads_sheet_survives_active_download,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_downloads_sheet_survives_active_download": (
        "Opening the downloads sheet while a download is in progress must not "
        "crash the app (regression: NoSuchMethodError in ContentObserver, "
        "issue #802)"
    ),
}
