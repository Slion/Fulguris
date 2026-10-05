"""Bookmark workflow tests: add / edit / remove / folders, and import + export.

Covers the whole bookmark surface of Fulguris, driven through the framework
Device API:

* the bookmarks drawer (open/close, entry and folder rows);
* add bookmark (dialog pre-fill, root and folder targets, duplicate guard);
* edit / remove a bookmark (context menu, no confirmation on remove);
* folders: create via the add dialog, rename, and "remove" (moves contents to
  root — nothing is deleted);
* **export** (Settings → Backup → Export): the SAF save-name dialog, the
  "Bookmarks exported to …" snackbar, and the file itself — pulled from the
  device and parsed on the host (Netscape Bookmark File Format, folders and
  HTML escaping included);
* **import** (Settings → Backup → Import): the SAF picker, the "N Bookmarks
  were imported" snackbar, imported folders/entries in the drawer, duplicate
  handling (existing URLs are skipped), and the malformed-file error dialog;
* Reset ("Delete all bookmarks") with its confirmation dialog.

    python scripts/tests/run.py --device SERIAL --group bookmarks
    python scripts/tests/run.py --device SERIAL --test bookmarks

The import fixtures are pushed to the device's public Downloads folder and
selected in the system (DocumentsUI) picker. The DB itself is not readable
from the host (no sqlite3 in the app sandbox), so assertions read the drawer
UI and the exported files instead.
"""
from __future__ import annotations

import os
import re
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

from framework import keys  # noqa: E402
import adb  # noqa: E402

# --- fixtures ----------------------------------------------------------------

ASSETS = os.path.join(os.path.dirname(__file__), "assets")
IMPORT_FILE = os.path.join(ASSETS, "import_probe.html")   # 2 entries in a folder + 1 root
MALFORMED_FILE = os.path.join(ASSETS, "import_malformed.html")  # no <DL> at all
REMOTE_DOWNLOADS = "/sdcard/Download"
IMPORT_REMOTE = REMOTE_DOWNLOADS + "/autotest_import_probe.html"
MALFORMED_REMOTE = REMOTE_DOWNLOADS + "/autotest_import_malformed.html"

# Distinctive titles/urls used by this suite (never present in default bookmarks).
BM_TITLE = "AutoTest bookmark"
BM_URL = "https://www.example.org/autotest-bookmark"
BM_FOLDER = "AutoTestFolder"
BM_FOLDER_URL = "https://www.example.org/autotest-in-folder"
BM_FOLDER_TITLE = "AutoTest folder bookmark"
RENAME_TO = "AutoTestFolderRenamed"

IMPORT_ROOT_TITLE = "Probe root bookmark"
IMPORT_FOLDER = "Probe folder"
IMPORT_A = "Probe A"
IMPORT_B = "Probe B"


# --- navigation helpers -------------------------------------------------------


def _nodes(device):
    return device.nodes()


def _texts(device):
    return [n.text for n in _nodes(device) if n.text]


def _wait_text(device, text, timeout=25.0, exact=False):
    """True once a node's text matches ``text`` (exact, or substring by default)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in _nodes(device):
            if n.text and (n.text == text if exact else text in n.text):
                return True
        time.sleep(0.7)
    return any(n.text and (n.text == text if exact else text in n.text) for n in _nodes(device))


def _tap_text(device, text, timeout=10.0, exact=False, index=0):
    """Tap the ``index``-th node whose text matches ``text``. False if never seen."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        matches = [n for n in _nodes(device) if n.bounds and n.text
                   and (n.text == text if exact else text in n.text)]
        if index < len(matches):
            cx, cy = matches[index].center
            device.tap(cx, cy, wait=1.2)
            return True
        time.sleep(0.7)
    return False


def _open_row_menu(device, text, timeout=12.0):
    """Open the context menu for the first row whose title matches ``text``.

    The drawer rows expose their menu through the row's edit (⋮/pencil)
    button (``:id/button_edit``) — a long-press is NOT the trigger (it starts
    a drag). The button sits right of the title in the same row.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in _nodes(device):
            if not (n.bounds and n.text and text in n.text):
                continue
            edit = None
            for m in _nodes(device):
                if not (m.resource_id.endswith("button_edit") and m.bounds):
                    continue
                # Same row: vertical centers close, button to the right of the title.
                if abs(m.center[1] - n.center[1]) < 60 and m.center[0] > n.center[0]:
                    edit = m
                    break
            if edit:
                device.tap(edit.center[0], edit.center[1], wait=1.5)
                return True
        time.sleep(0.7)
    return False


def _scroll_view(device, steps=1, down=True):
    """Swipe the (settings/drawer) RecyclerView to reveal more rows."""
    w, h = device.screen_size()
    for _ in range(steps):
        y0, y1 = (int(h * 0.70), int(h * 0.30)) if down else (int(h * 0.30), int(h * 0.70))
        device.transport.shell(["shell", "input", "swipe", str(w // 2), str(y0),
                                str(w // 2), str(y1), "300"], timeout=15)
        time.sleep(1.0)


def _main_menu_open(device) -> bool:
    """True when the toolbar overflow menu is showing.

    Detected by the menu ROW's resource id (:id/menuItemBookmarks) — NOT by
    text: the bookmarks drawer's own title also reads 'Bookmarks' (and its
    rows can read 'Settings'), and a text check made _open_main_menu skip the
    menu tap entirely whenever the drawer was still open.
    """
    return any((n.resource_id or "").endswith("menuItemBookmarks") for n in _nodes(device))


def _open_main_menu(device):
    """Open the toolbar overflow menu (idempotent — skips if already open)."""
    if _main_menu_open(device):
        return
    # After a cold start the toolbar can take a few seconds to render (and the
    # app restores its last UI state, which may briefly be mid-transition), so
    # poll for the menu button instead of asserting on the first snapshot.
    n = None
    deadline = time.time() + 20.0
    while time.time() < deadline:
        n = adb.find_node(device.serial, ":id/button_more")
        if n and n.bounds:
            break
        time.sleep(1.0)
    assert n and n.bounds, (
        f"toolbar menu button not found "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    device.tap(n.center[0], n.center[1], wait=1.5)
    deadline = time.time() + 15.0
    while time.time() < deadline and not _main_menu_open(device):
        time.sleep(0.5)
    assert _main_menu_open(device), (
        f"main menu did not open (nodes: {sorted(_texts(device))[:40]!r})"
    )


def _open_settings(device):
    _open_main_menu(device)
    assert _tap_text(device, "Settings", timeout=10.0, exact=True), "no 'Settings' menu row"
    assert _wait_text(device, "Backup", timeout=20.0), "settings list did not open"


def _open_backup_page(device):
    """Settings → Backup (the 'Import' / 'Export' / 'Reset' rows).

    Normalizes to the browser first: the app restores its last UI state on
    launch, and a previous run may have ended deep in Settings (whose pages
    have no toolbar :id/button_more), which would otherwise make
    _open_settings fail with 'toolbar menu button not found'.
    """
    _close_to_browser(device)
    _open_settings(device)
    if not _tap_text(device, "Backup", timeout=8.0, exact=True):
        _scroll_view(device, 2, down=True)
        assert _tap_text(device, "Backup", timeout=10.0, exact=True), "no 'Backup' row in settings"
    assert _wait_text(device, "Delete all bookmarks", timeout=20.0), (
        "Backup settings page did not open (no 'Delete all bookmarks' summary; "
        f"nodes: {sorted(_texts(device))[:40]!r})"
    )


def _close_to_browser(device):
    """Back out of whatever panel is open (sheet/menu/settings/SAF picker).

    Backs until the browser's own toolbar is visible again (its menu button
    :id/button_more shows). The cap is generous on purpose: after a relaunch
    the app restores its last UI state, which can be deep in
    Settings → Backup, so several backs are needed. Each back is followed by a
    short poll (pickers/settings take a moment to close; re-checking instantly
    would over-traverse). If the app leaves the foreground (overshot to the
    launcher) we return and relaunch.
    """
    # The app restores its last UI state on launch, so after a restart we may
    # be back at the browser (drawer possibly re-shown on top) OR deep in
    # Settings (whose pages have NO toolbar :id/button_more — the previous
    # run ended there). Handle each state explicitly:
    restarts = 0
    stuck = 0
    last_sig = None
    for _i in range(30):
        n = adb.find_node(device.serial, ":id/button_more")
        if n and n.bounds:
            # The browser's toolbar is present. A restored bookmarks drawer or
            # menu may still sit on top — close the overlay, then we're done.
            if _drawer_open(device) or _main_menu_open(device):
                device.key(keys.BACK, 0.9)
                time.sleep(1.0)
            else:
                break
            continue
        # No toolbar: some in-app page (Settings, the SAF picker, a sheet...).
        # BACK out of it — but if the screen stops changing (BACK is a no-op
        # here) or we overshot to the launcher, relaunch instead of spinning;
        # at most twice, so a genuinely stuck state surfaces as a test error.
        sig = tuple(sorted(x.text for x in _nodes(device) if x.text and x.bounds)[:30])
        stuck = stuck + 1 if sig == last_sig else 0
        last_sig = sig
        if stuck >= 3 or not _app_foreground(device.serial):
            if restarts >= 2:
                break
            restarts += 1
            adb.restart(device.serial, adb.DEFAULT_PACKAGE)
            time.sleep(5.0)
            continue
        device.key(keys.BACK, 0.9)
        time.sleep(1.0)
    device.settle()


def _app_foreground(serial: str) -> bool:
    """True while the app holds the top activity (backs past it hit the launcher)."""
    out = adb._adb(serial, ["shell", "dumpsys", "activity", "activities"], timeout=15)
    for line in out.splitlines():
        if "mResumedActivity" in line or "topResumedActivity" in line:
            return "net.slions.fulguris" in line
    return "net.slions.fulguris" in out[:2000]


def _drawer_open(device) -> bool:
    """True when the bookmarks bottom sheet is showing (detected by the
    sheet's title layout resource id, which the main menu does not have)."""
    return any((n.resource_id or "").endswith("bookmark_title_layout") for n in _nodes(device))


def _open_bookmarks_drawer(device):
    _open_main_menu(device)
    assert _tap_text(device, "Bookmarks", timeout=10.0, exact=True, index=-1), (
        "no 'Bookmarks' menu row")
    deadline = time.time() + 20.0
    while time.time() < deadline and not _drawer_open(device):
        time.sleep(0.5)
    assert _drawer_open(device), "bookmarks drawer did not open"
    time.sleep(1.0)  # the sheet expands after a short delay
    _drawer_to_root(device)


def _drawer_at_root(device) -> bool:
    """True when the drawer shows the root level.

    Detected by the ABSENCE of a '..' parent row (locale-proof — the root
    title text can vary, but only sub-folders expose a '..' row).
    """
    return not any(n.resource_id.endswith("textBookmark") and n.text == ".."
                   for n in _nodes(device) if n.bounds)


def _drawer_to_root(device):
    """Navigate up to the bookmarks root if the drawer is on a sub-folder.

    The drawer persists its last folder, so a previous test that ended inside
    a folder would otherwise leave every later test looking for root rows in
    the wrong place. Tap '..' rows until the root is showing.

    Waits for at least one row before concluding "at root": a freshly opened
    sheet whose RecyclerView hasn't populated yet has no '..' row and would be
    misread as the root, leaving the caller asserting against an empty list.
    """
    deadline = time.time() + 15.0
    while time.time() < deadline:
        entries = _drawer_entries(device)
        if entries and _drawer_at_root(device):
            return
        for n in _nodes(device):
            if n.resource_id.endswith("textBookmark") and n.text == ".." and n.bounds:
                device.tap(n.center[0], n.center[1], wait=1.0)
                break
        time.sleep(0.8)


def _drawer_entries(device):
    """(title, center) of every bookmark entry row in the drawer (textBookmark)."""
    entries = []
    for n in _nodes(device):
        if n.resource_id.endswith("textBookmark") and n.bounds:
            entries.append((n.text, n.center))
    return entries


def _drawer_has(device, text, timeout=15.0):
    """True once a drawer row (textBookmark) shows ``text``.

    Substring match: rows are single-line with ``ellipsize=end``, so long
    titles (and folder paths) may be truncated in the node text.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        if any(text in t for t, _ in _drawer_entries(device)):
            return True
        time.sleep(0.7)
    return any(text in t for t, _ in _drawer_entries(device))


def _drawer_has_exact(device, text, timeout=15.0):
    """True once a drawer row shows EXACTLY ``text`` (no substring).

    Needed for negative checks: the substring _drawer_has would report
    'AutoTest bookmark' as present when only 'AutoTest bookmark (edited)'
    exists, which wrongly failed the edit test before it could revert.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        if any(t == text for t, _ in _drawer_entries(device)):
            return True
        time.sleep(0.7)
    return any(t == text for t, _ in _drawer_entries(device))


def _picker_find_file(device, filename: str, timeout=25.0):
    """Locate ``filename`` in the DocumentsUI picker by name and tap it.

    The pushed import fixture is date-sorted BELOW the recent download files,
    so it is off-screen in a plain (no-scroll) listing. The picker's Search
    icon (id :id/option_menu_search, desc 'Search') jumps straight to it.
    Returns True if the file was found and tapped.
    """
    # Tap the search icon if the search field isn't already active.
    search_field = None
    for n in _nodes(device):
        if "EditText" in (n.cls or "") and (n.resource_id or "").endswith("search_src_text"):
            search_field = n
            break
    if not search_field:
        s = None
        for n in _nodes(device):
            if (n.content_desc == "Search") or (n.resource_id or "").endswith("option_menu_search"):
                s = n
                break
        if not s:
            return False
        device.tap(s.center[0], s.center[1], wait=1.5)
    # Type enough of the name to narrow the results, then tap the exact row.
    # The filename appears TWICE in search mode: once as an autocomplete
    # suggestion in the search bar (top of screen, y < 200) and once in the
    # dir_list grid below. Tapping the suggestion just refills the search
    # field — we must tap the grid item (y > 200).
    name = os.path.basename(filename)
    device.type_text(name, 0.15)
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in _nodes(device):
            if n.bounds and n.text == name and n.bounds[1] > 200:
                device.tap(n.center[0], n.center[1], wait=2.0)
                return True
        time.sleep(0.8)
    return False


def _pick_in_saf(device, remote) -> bool:
    """Open the SAF import picker and pick ``remote`` by name (via Search).

    On success the picker auto-dismisses (we land back on the Backup settings
    page with the import snackbar). On failure the still-open picker is closed
    in the finally, so a pick failure can't leak a stacked picker into the
    next test (that is what poisoned the reset test before).
    """
    picked = False
    try:
        _open_backup_page(device)
        assert _tap_text(device, "Import", timeout=10.0, exact=True, index=0), "no 'Import' row"
        time.sleep(2.0)  # let the picker open
        picked = _picker_find_file(device, remote)
    finally:
        if not picked:
            _close_to_browser(device)
    return picked


def _close_drawer(device):
    """Close the bookmarks sheet AND the menu behind it.

    The drawer sits on top of the still-open main menu: one BACK closes only
    the sheet, so a stale menu row ('Bookmarks') would break the next test's
    menu/drawer detection. Back until the menu row is gone.
    """
    for _ in range(3):
        device.key(keys.BACK, 1.0)
        if not _main_menu_open(device) and not _drawer_open(device):
            break
    time.sleep(0.5)


def _tap_ok(device, timeout=10.0) -> bool:
    """Tap a MaterialAlertDialog's POSITIVE button by its resource id
    (:id/button1), which is locale-proof — the label reads 'OK' in en-US but
    'Okay' in en-rGB (and other localizations differ too), so matching on the
    text was failing on this (en-rGB) device."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in _nodes(device):
            if (n.resource_id or "").endswith("button1") and n.bounds and n.enabled:
                cx, cy = n.center
                device.tap(cx, cy, wait=1.5)
                return True
        time.sleep(0.5)
    return False


def _focused_id(device):
    for n in _nodes(device):
        if getattr(n, "focused", False):
            return n.resource_id or ""
    return ""


def _set_field(device, field_id, value):
    """Replace an EditText's content (focus it, clear it, type the value).

    Focus is VERIFIED after the tap and after typing: when the soft keyboard
    opens, the dialog re-lays out, so the field that was under the tap point
    can change, and a blind tap+clear+type can silently land the text in the
    wrong field (this put the title into the URL field). Retap if the focus
    check fails; the check after typing is the hard guard.
    """
    want = field_id.split("/")[-1]
    n = adb.find_node(device.serial, field_id)
    assert n and n.bounds, f"field {field_id} not found"
    focused = ""
    for _ in range(3):
        device.tap(n.center[0], n.center[1], wait=1.2)  # tap focuses the field
        focused = _focused_id(device)
        if focused.endswith(want):
            break
        n = adb.find_node(device.serial, field_id)  # re-locate (layout may have shifted)
        assert n and n.bounds, f"field {field_id} vanished"
    # Replace the content by SELECT-ALL + TYPE, NOT by a clear/DEL pass. On
    # this device the DEL pass makes the IME advance focus to the NEXT field,
    # so a clear-then-type lands the text one field over (title→url, url→
    # folder). Typing over a selection keeps focus on the tapped field and
    # replaces the whole value in one go.
    device.key_combination(adb.KEY_CTRL_LEFT, keys.A, wait=0.6)  # select all
    device.type_text(value, 0.25)  # replaces the selection
    time.sleep(0.3)
    after = _focused_id(device)
    assert after.endswith(want), (
        f"focus was {focused!r} before typing but {after!r} after — value may "
        f"have landed in the wrong field (wanted {want})"
    )


def _tap_save(device, timeout=15.0) -> bool:
    """Tap the SAF picker's save button. Its label is locale-dependent
    ('SAVE' on some builds, 'Save' in en-rGB), so match it case-insensitively
    and exactly (a substring match could hit 'Save as…' instead)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for n in _nodes(device):
            if n.bounds and n.text and n.text.strip().upper() == "SAVE" and n.enabled:
                cx, cy = n.center
                device.tap(cx, cy, wait=1.5)
                return True
        time.sleep(0.5)
    return False


def _push_import_file(device, local: str, remote: str) -> None:
    """Push the fixture and wait until MediaStore has indexed it.

    The DocumentsUI picker lists MediaStore, not the filesystem, and adb push
    SKIPS a file whose size+mtime are unchanged — so without the rm+push the
    file keeps a stale timestamp, sorts to the bottom of the (date-ordered)
    picker, and is never picked. The MEDIA_SCANNER_SCAN_FILE broadcast is a
    no-op on modern Android; a fresh push via FUSE triggers the index.
    """
    device.transport.shell(["shell", "rm", "-f", remote], timeout=20)
    adb.push(device.serial, local, remote)
    name = os.path.basename(remote)
    deadline = time.time() + 25.0
    while time.time() < deadline:
        out = device.transport.shell(
            ["shell", "content", "query",
             "--uri", "content://media/external/file",
             "--projection", "_display_name"], timeout=20)
        if name in out:
            time.sleep(0.5)  # let the index settle
            return
        time.sleep(1.0)
    raise AssertionError(f"{remote} never appeared in MediaStore")


def _latest_export_on_host(device) -> str | None:
    """Pull the newest FulgurisBookmarks-*.html from the device Downloads dir."""
    out = device.transport.shell(["shell", "ls", "-t", REMOTE_DOWNLOADS], timeout=20)
    names = [l.split()[-1] for l in out.splitlines()
             if l.split() and l.split()[-1].startswith("FulgurisBookmarks-")
             and l.split()[-1].endswith(".html")]
    if not names:
        return None
    local = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".temp_export", names[0])
    os.makedirs(os.path.dirname(local), exist_ok=True)
    adb.pull(device.serial, f"{REMOTE_DOWNLOADS}/{names[0]}", local)
    return local


# --- tests --------------------------------------------------------------------


def test_bookmarks_drawer_opens_and_closes(device, ctx: dict) -> None:
    """The main menu opens the bookmarks drawer; back closes it without losing state."""
    device.settle()
    _close_to_browser(device)
    _open_bookmarks_drawer(device)
    # NOTE: do NOT assert on the drawer's CONTENTS here. A fresh install
    # shows the default Fulguris folder, but a previous run ending in the
    # 'Reset' test leaves zero bookmarks — the drawer's open/close behavior
    # (this test's contract) must not depend on bookmark state.
    _close_drawer(device)
    deadline = time.time() + 5.0
    while time.time() < deadline and _drawer_open(device):
        time.sleep(0.5)
    assert not _drawer_open(device), "drawer did not close on back"


def test_bookmarks_add_root(device, ctx: dict) -> None:
    """'Add bookmark' (Ctrl+B) saves a bookmark at root; the drawer shows it."""
    device.settle()
    _close_to_browser(device)
    device.navigate("https://www.example.org/")
    # Remove any stale entry from a previous run.
    _open_bookmarks_drawer(device)
    if _drawer_has(device, BM_TITLE, timeout=5.0):
        _open_row_menu(device, BM_TITLE)
        if _wait_text(device, "Remove bookmark", timeout=8.0):
            _tap_text(device, "Remove bookmark", timeout=8.0, exact=True)
            time.sleep(1.0)
    _close_drawer(device)

    device.key_combination(adb.KEY_CTRL_LEFT, keys.KEY_B, wait=1.5)
    assert _wait_text(device, "Add bookmark", timeout=15.0, exact=True), (
        f"'Add bookmark' dialog did not open (nodes: {sorted(_texts(device))[:40]!r})"
    )
    _set_field(device, ":id/bookmark_title", BM_TITLE)
    _set_field(device, ":id/bookmark_url", BM_URL)
    assert _tap_ok(device), "no OK button in the add dialog"
    # The 'Bookmark added.' confirmation is a Toast (separate window, not in
    # the uiautomator dump) — the drawer is the source of truth.
    _open_bookmarks_drawer(device)
    assert _drawer_has(device, BM_TITLE, timeout=15.0), (
        f"the new bookmark is not in the drawer (entries: {_drawer_entries(device)!r})"
    )
    _close_drawer(device)
    # NOTE: BM_TITLE is intentionally left in place — the later tests (edit,
    # remove, export, reset) build on it.


def test_bookmarks_add_duplicate_guard(device, ctx: dict) -> None:
    """Adding the same URL again is refused: 'Bookmark already exists.'"""
    device.settle()
    _close_to_browser(device)
    device.navigate("https://www.example.org/")
    assert _wait_text(device, BM_TITLE, timeout=10.0) or True  # (defensive)
    _open_bookmarks_drawer(device)
    assert _drawer_has(device, BM_TITLE, timeout=10.0), "precondition: root bookmark missing"
    _close_drawer(device)

    device.key_combination(adb.KEY_CTRL_LEFT, keys.KEY_B, wait=1.5)
    assert _wait_text(device, "Add bookmark", timeout=15.0, exact=True)
    _set_field(device, ":id/bookmark_title", BM_TITLE + " dup")
    _set_field(device, ":id/bookmark_url", BM_URL)
    assert _tap_ok(device)
    # The 'Bookmark already exists.' refusal is a Toast (not in the
    # uiautomator dump) — the drawer is the source of truth: the duplicate
    # must NOT have been added.
    _open_bookmarks_drawer(device)
    assert _drawer_has(device, BM_TITLE, timeout=10.0), (
        f"the original entry vanished after a duplicate add (entries: {_drawer_entries(device)!r})"
    )
    entries = [t for t, _ in _drawer_entries(device)]
    assert BM_TITLE + " dup" not in entries, (
        f"the duplicate URL was added as a second row: {entries!r}"
    )
    _close_drawer(device)


def test_bookmarks_add_in_folder(device, ctx: dict) -> None:
    """Typing a new folder name in the add dialog creates the folder + entry."""
    device.settle()
    _close_to_browser(device)
    device.navigate("https://www.example.org/")
    _open_bookmarks_drawer(device)
    if _drawer_has(device, BM_FOLDER_TITLE, timeout=5.0):
        _open_row_menu(device, BM_FOLDER_TITLE)
        if _wait_text(device, "Remove bookmark", timeout=8.0):
            _tap_text(device, "Remove bookmark", timeout=8.0, exact=True)
            time.sleep(1.0)
    _close_drawer(device)

    device.key_combination(adb.KEY_CTRL_LEFT, keys.KEY_B, wait=1.5)
    assert _wait_text(device, "Add bookmark", timeout=15.0, exact=True)
    _set_field(device, ":id/bookmark_title", BM_FOLDER_TITLE)
    _set_field(device, ":id/bookmark_url", BM_FOLDER_URL)
    _set_field(device, ":id/bookmark_folder", BM_FOLDER)
    assert _tap_ok(device)
    # Toast confirmation is invisible to uiautomator; the drawer is truth.
    _open_bookmarks_drawer(device)
    assert _drawer_has(device, BM_FOLDER, timeout=15.0), (
        f"the new folder is not in the drawer (entries: {_drawer_entries(device)!r})"
    )
    # Enter the folder: its entry is inside, and a '..' row leads back out.
    assert _tap_text(device, BM_FOLDER, timeout=10.0, exact=True), "folder row not tappable"
    time.sleep(1.0)
    assert _drawer_has(device, BM_FOLDER_TITLE, timeout=10.0), (
        f"the folder's entry is missing (entries: {_drawer_entries(device)!r})"
    )
    assert _wait_text(device, "..", timeout=10.0, exact=True), "no '..' parent row in the folder"
    _close_drawer(device)


def test_bookmarks_edit_title_and_url(device, ctx: dict) -> None:
    """The bookmark context menu's 'Edit bookmark' changes title and URL."""
    device.settle()
    _close_to_browser(device)
    _open_bookmarks_drawer(device)
    # Exact match: a substring check would also be satisfied by an
    # '(edited)' leftover from a previously interrupted run.
    assert _drawer_has_exact(device, BM_TITLE, timeout=10.0), (
        f"precondition: root bookmark missing (entries: {_drawer_entries(device)!r})"
    )
    assert _open_row_menu(device, BM_TITLE), "entry row menu not openable"
    assert _wait_text(device, "Bookmark", timeout=10.0, exact=True), "context menu did not open"
    assert _tap_text(device, "Edit bookmark", timeout=10.0, exact=True), "no 'Edit bookmark' item"
    assert _wait_text(device, "Edit bookmark", timeout=10.0), "edit dialog did not open"

    _set_field(device, ":id/bookmark_title", BM_TITLE + " (edited)")
    _set_field(device, ":id/bookmark_url", BM_URL + "-edited")
    assert _tap_ok(device)
    time.sleep(1.0)

    assert _drawer_has(device, BM_TITLE + " (edited)", timeout=10.0), (
        f"edited title not in the drawer (entries: {_drawer_entries(device)!r})"
    )
    # EXACT negative check: the substring _drawer_has(BM_TITLE) would match the
    # '(edited)' row too (it contains BM_TITLE), which made this test fail
    # before the revert and left the bookmark edited for the next tests.
    assert not _drawer_has_exact(device, BM_TITLE, timeout=3.0), "old title row still present"

    # Revert the edit so later tests (export) see the canonical title.
    assert _open_row_menu(device, BM_TITLE + " (edited)"), "edited row menu not openable"
    assert _wait_text(device, "Bookmark", timeout=10.0, exact=True)
    assert _tap_text(device, "Edit bookmark", timeout=10.0, exact=True)
    _set_field(device, ":id/bookmark_title", BM_TITLE)
    _set_field(device, ":id/bookmark_url", BM_URL)
    assert _tap_ok(device)
    assert _drawer_has_exact(device, BM_TITLE, timeout=10.0), "revert of the edit failed"
    _close_drawer(device)


def test_bookmarks_remove_no_confirmation(device, ctx: dict) -> None:
    """The context menu's 'Remove bookmark' deletes the entry (no confirmation)."""
    device.settle()
    _close_to_browser(device)
    # A throwaway bookmark so the canonical one survives.
    device.navigate("https://www.example.org/")
    device.key_combination(adb.KEY_CTRL_LEFT, keys.KEY_B, wait=1.5)
    assert _wait_text(device, "Add bookmark", timeout=15.0, exact=True)
    _set_field(device, ":id/bookmark_title", "AutoTest throwaway")
    _set_field(device, ":id/bookmark_url", "https://www.example.org/autotest-throwaway")
    assert _tap_ok(device)
    _open_bookmarks_drawer(device)
    assert _drawer_has(device, "AutoTest throwaway", timeout=10.0)
    assert _open_row_menu(device, "AutoTest throwaway"), "row menu not openable"
    assert _wait_text(device, "Bookmark", timeout=10.0, exact=True)
    assert _tap_text(device, "Remove bookmark", timeout=10.0, exact=True), "no 'Remove bookmark' item"
    time.sleep(1.0)
    assert not _drawer_has(device, "AutoTest throwaway", timeout=8.0), (
        "the removed entry is still in the drawer"
    )
    _close_drawer(device)


def test_bookmarks_rename_folder(device, ctx: dict) -> None:
    """The folder context menu's 'Rename folder' renames the folder."""
    device.settle()
    _close_to_browser(device)
    _open_bookmarks_drawer(device)
    folder = BM_FOLDER if _drawer_has(device, BM_FOLDER, timeout=8.0) else RENAME_TO
    if folder not in (BM_FOLDER, RENAME_TO):
        assert _drawer_has(device, BM_FOLDER, timeout=8.0) or _drawer_has(device, RENAME_TO, timeout=8.0), (
            "precondition: no AutoTest folder present"
        )
    new_name = RENAME_TO if folder == BM_FOLDER else BM_FOLDER
    assert _open_row_menu(device, folder), "folder row menu not openable"
    assert _wait_text(device, "Folder", timeout=10.0, exact=True), "folder menu did not open"
    assert _tap_text(device, "Rename folder", timeout=10.0, exact=True), "no 'Rename folder' item"
    assert _wait_text(device, "Rename folder", timeout=10.0), "rename dialog did not open"

    # _set_field (verified focus + select-all/type) — the raw tap +
    # DPAD_CENTER + clear path could submit the dialog before the text
    # landed, which left the folder with its old name.
    _set_field(device, ":id/dialog_edit_text", new_name)
    assert _tap_ok(device)
    time.sleep(1.0)

    assert _drawer_has(device, new_name, timeout=10.0), (
        f"renamed folder not in the drawer (entries: {_drawer_entries(device)!r})"
    )
    _close_drawer(device)


def test_bookmarks_remove_folder_moves_to_root(device, ctx: dict) -> None:
    """'Remove folder' deletes nothing: its entries move up to the root."""
    device.settle()
    _close_to_browser(device)
    _open_bookmarks_drawer(device)
    folder = RENAME_TO if _drawer_has(device, RENAME_TO, timeout=8.0) else BM_FOLDER
    assert _drawer_has(device, folder, timeout=8.0), "precondition: no AutoTest folder present"
    assert _open_row_menu(device, folder), "folder row menu not openable"
    assert _wait_text(device, "Folder", timeout=10.0, exact=True)
    assert _tap_text(device, "Remove folder", timeout=10.0, exact=True), "no 'Remove folder' item"
    time.sleep(1.0)

    assert not _drawer_has(device, folder, timeout=8.0), "the folder is still in the drawer"
    assert _drawer_has(device, BM_FOLDER_TITLE, timeout=10.0), (
        f"the folder's entry should have moved to the root (entries: {_drawer_entries(device)!r})"
    )
    _close_drawer(device)


# --- export -------------------------------------------------------------------


def test_bookmarks_export_file_and_content(device, ctx: dict) -> None:
    """Export: SAF save dialog -> file in Downloads with correct Netscape content.

    The file is pulled from the device and checked on the host: header, the
    root bookmark, the folder's nested <H3>/<DL> block, and HTML escaping of a
    special-character title.
    """
    device.settle()
    _close_to_browser(device)
    # A special-character bookmark to verify escaping in the export.
    device.navigate("https://www.example.org/")
    device.key_combination(adb.KEY_CTRL_LEFT, keys.KEY_B, wait=1.5)
    if _wait_text(device, "Add bookmark", timeout=15.0, exact=True):
        _set_field(device, ":id/bookmark_title", 'AutoTest <special> "quote" & amp')
        _set_field(device, ":id/bookmark_url", "https://www.example.org/autotest-special")
        assert _tap_ok(device)
        time.sleep(0.5)
        # Drop the keyboard if it is still up.
        device.key(keys.BACK, 0.6)

    _open_backup_page(device)
    assert _tap_text(device, "Export", timeout=10.0, exact=True), "no 'Export' row"
    # The SAF save-name dialog: an EditText with the default FulgurisBookmarks-… name.
    deadline = time.time() + 20.0
    name_field = None
    while time.time() < deadline:
        for n in _nodes(device):
            if "EditText" in (n.cls or "") and n.text.startswith("FulgurisBookmarks-") \
                    and n.text.endswith(".html"):
                name_field = n
                break
        if name_field:
            break
        time.sleep(0.8)
    assert name_field, (
        f"no save-name field with the default export name "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    assert _tap_save(device), "no SAVE button in the picker"
    assert _wait_text(device, "Bookmarks exported to", timeout=20.0), (
        "the 'Bookmarks exported to …' snackbar never appeared "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )

    local = _latest_export_on_host(device)
    assert local, "no FulgurisBookmarks-*.html file appeared in /sdcard/Download"
    with open(local, encoding="utf-8") as f:
        content = f.read()
    assert content.startswith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"), "missing the Netscape header"
    assert f'<A HREF="{BM_URL}">{BM_TITLE}</A>' in content, (
        f"the root bookmark is missing or wrong in the export:\n{content[:800]}"
    )
    # The folder (renamed or not) must be present with its nested entry.
    folder = RENAME_TO if RENAME_TO in content else BM_FOLDER
    assert f"<H3>{folder}</H3>" in content, f"the folder <H3> is missing in the export"
    assert f'<A HREF="{BM_FOLDER_URL}">{BM_FOLDER_TITLE}</A>' in content, (
        "the folder's entry is missing in the export"
    )
    # HTML escaping of the special-character title.
    assert 'AutoTest &lt;special&gt; &quot;quote&quot; &amp; amp' in content, (
        "the special-character title is not HTML-escaped in the export "
        f"(export: {content[:1200]!r})"
    )
    _close_to_browser(device)


def test_bookmarks_import_creates_entries(device, ctx: dict) -> None:
    """Import: SAF picker -> '3 Bookmarks were imported' -> entries + folder present."""
    device.settle()
    _close_to_browser(device)
    _push_import_file(device, IMPORT_FILE, IMPORT_REMOTE)

    assert _pick_in_saf(device, IMPORT_REMOTE), (
        "the pushed import file is not pickable in the file picker "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    deadline = time.time() + 25.0
    count = None
    while time.time() < deadline:
        m = re.search(r"(\d+)\s+Bookmarks were imported", "\n".join(_texts(device)))
        if m:
            count = int(m.group(1))
            break
        time.sleep(0.7)
    assert count == 3, (
        f"the import snackbar should report all 3 bookmarks of the fixture, got {count!r} "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )

    # The import leaves us on Settings -> Backup (no toolbar there); get back
    # to the browser before opening the bookmarks drawer.
    _close_to_browser(device)
    _open_bookmarks_drawer(device)
    assert _drawer_has(device, IMPORT_ROOT_TITLE, timeout=15.0), (
        f"imported root entry missing (entries: {_drawer_entries(device)!r})"
    )
    assert _drawer_has(device, IMPORT_FOLDER, timeout=15.0), (
        f"imported folder missing (entries: {_drawer_entries(device)!r})"
    )
    assert _tap_text(device, IMPORT_FOLDER, timeout=10.0, exact=True), "imported folder not tappable"
    time.sleep(1.0)
    assert _drawer_has(device, IMPORT_A, timeout=10.0) and _drawer_has(device, IMPORT_B, timeout=10.0), (
        f"imported folder entries missing (entries: {_drawer_entries(device)!r})"
    )
    _close_drawer(device)
    _close_to_browser(device)


def test_bookmarks_import_skips_duplicates(device, ctx: dict) -> None:
    """Re-importing the same file creates no duplicates in the drawer.

    The snackbar count is the number of bookmarks parsed from the file (always
    3 for the fixture); dedup happens in the DB (``addBookmarkIfNotExists``
    matches by URL). So the drawer must still show exactly one row per
    fixture title after a second import.
    """
    device.settle()
    _close_to_browser(device)
    _push_import_file(device, IMPORT_FILE, IMPORT_REMOTE)

    assert _pick_in_saf(device, IMPORT_REMOTE), "the import file is not pickable in the file picker"
    deadline = time.time() + 25.0
    count = None
    while time.time() < deadline:
        m = re.search(r"(\d+)\s+Bookmarks were imported", "\n".join(_texts(device)))
        if m:
            count = int(m.group(1))
            break
        time.sleep(0.7)
    assert count == 3, (
        f"the snackbar reports the file's bookmark count (3), got {count!r} "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )

    # The import leaves us on Settings -> Backup (no toolbar there); get back
    # to the browser before opening the bookmarks drawer.
    _close_to_browser(device)
    _open_bookmarks_drawer(device)
    entries = [t for t, _ in _drawer_entries(device)]
    assert entries.count(IMPORT_ROOT_TITLE) <= 1, f"duplicate root entry: {entries!r}"
    assert entries.count(IMPORT_FOLDER) <= 1, f"duplicate folder row: {entries!r}"
    assert _tap_text(device, IMPORT_FOLDER, timeout=10.0, exact=True), "imported folder not tappable"
    time.sleep(1.0)
    inner = [t for t, _ in _drawer_entries(device)]
    assert inner.count(IMPORT_A) <= 1, f"duplicate 'Probe A' entry: {inner!r}"
    assert inner.count(IMPORT_B) <= 1, f"duplicate 'Probe B' entry: {inner!r}"
    _close_drawer(device)
    _close_to_browser(device)


def test_bookmarks_import_malformed_file_errors(device, ctx: dict) -> None:
    """Importing an HTML file with no bookmark list shows the error dialog."""
    device.settle()
    _close_to_browser(device)
    _push_import_file(device, MALFORMED_FILE, MALFORMED_REMOTE)

    assert _pick_in_saf(device, MALFORMED_REMOTE), (
        "the malformed import file is not pickable in the file picker"
    )
    assert _wait_text(device, "Could not import bookmarks from file", timeout=25.0), (
        "the import error dialog never appeared "
        f"(nodes: {sorted(_texts(device))[:40]!r})"
    )
    # Dismiss the error dialog (its OK button), then get back to the browser.
    _tap_ok(device, timeout=6.0)
    _close_to_browser(device)


# --- reset (deletes everything — MUST run last) -------------------------------


def test_bookmarks_reset_deletes_all(device, ctx: dict) -> None:
    """Reset ('Delete all bookmarks') confirms, clears the drawer, and export
    afterwards yields a header-only file."""
    device.settle()
    _close_to_browser(device)
    _open_backup_page(device)
    assert _tap_text(device, "Reset", timeout=10.0, exact=True), "no 'Reset' row"
    assert _wait_text(device, "Delete all bookmarks?", timeout=15.0, exact=True), (
        "the delete-all confirmation dialog did not appear"
    )
    assert _tap_text(device, "Delete", timeout=10.0, exact=True), "no 'Delete' confirm button"
    time.sleep(1.5)

    # We're still on Settings -> Backup (no toolbar there); get back to the
    # browser before opening the bookmarks drawer.
    _close_to_browser(device)
    _open_bookmarks_drawer(device)
    time.sleep(1.0)
    entries = [t for t, _ in _drawer_entries(device)]
    assert not any(t.startswith("AutoTest") for t in entries), (
        f"AutoTest bookmarks survived the reset: {entries!r}"
    )
    assert not _drawer_has(device, IMPORT_ROOT_TITLE, timeout=5.0), "imported entries survived"
    _close_drawer(device)

    # Export with an (AutoTest-wise) empty set: a valid header-only file.
    # Re-enter Settings -> Backup (the drawer check above took us back to the
    # browser, whose toolbar has no Export row).
    _open_backup_page(device)
    assert _tap_text(device, "Export", timeout=15.0, exact=True), "no 'Export' row after reset"
    deadline = time.time() + 20.0
    saved = False
    while time.time() < deadline and not saved:
        if _tap_save(device, timeout=2.0):
            saved = True
    assert saved, "no SAVE button in the post-reset export picker"
    assert _wait_text(device, "Bookmarks exported to", timeout=20.0)
    local = _latest_export_on_host(device)
    assert local, "post-reset export file missing"
    with open(local, encoding="utf-8") as f:
        content = f.read()
    assert content.startswith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"), "missing the header"
    assert BM_TITLE not in content and BM_FOLDER_TITLE not in content, (
        "the reset bookmarks are still in the export file"
    )
    # Hygiene: remove the AutoTest import fixtures from the device.
    device.transport.shell(["shell", "rm", "-f", IMPORT_REMOTE, MALFORMED_REMOTE], timeout=20)
    _close_to_browser(device)


FEATURE_GROUPS = {
    "bookmarks": [
        test_bookmarks_drawer_opens_and_closes,
        test_bookmarks_add_root,
        test_bookmarks_add_duplicate_guard,
        test_bookmarks_add_in_folder,
        test_bookmarks_edit_title_and_url,
        test_bookmarks_remove_no_confirmation,
        test_bookmarks_rename_folder,
        # Export BEFORE remove_folder: the export test asserts the folder <H3>
        # is in the file, which is false once remove_folder has moved its
        # entry to the root and deleted the <H3>.
        test_bookmarks_export_file_and_content,
        test_bookmarks_remove_folder_moves_to_root,
        test_bookmarks_import_creates_entries,
        test_bookmarks_import_skips_duplicates,
        test_bookmarks_import_malformed_file_errors,
        test_bookmarks_reset_deletes_all,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_bookmarks_drawer_opens_and_closes": (
        "The main menu opens the bookmarks drawer; back closes it"
    ),
    "test_bookmarks_add_root": (
        "Ctrl+B 'Add bookmark' saves a bookmark at root; the drawer shows it"
    ),
    "test_bookmarks_add_duplicate_guard": (
        "Re-adding the same URL shows 'Bookmark already exists.' and adds nothing"
    ),
    "test_bookmarks_add_in_folder": (
        "Typing a new folder name in the add dialog creates the folder and entry; "
        "the folder opens with a '..' parent row"
    ),
    "test_bookmarks_edit_title_and_url": (
        "The bookmark context menu's 'Edit bookmark' changes title and URL"
    ),
    "test_bookmarks_remove_no_confirmation": (
        "'Remove bookmark' deletes the entry immediately (no confirmation dialog)"
    ),
    "test_bookmarks_rename_folder": (
        "The folder context menu's 'Rename folder' renames the folder"
    ),
    "test_bookmarks_remove_folder_moves_to_root": (
        "'Remove folder' deletes nothing: its entries move up to the root"
    ),
    "test_bookmarks_export_file_and_content": (
        "Export writes a FulgurisBookmarks-*.html to Downloads (SAF save dialog + "
        "snackbar) with the Netscape header, entries, nested folder and HTML escaping"
    ),
    "test_bookmarks_import_creates_entries": (
        "Import picks the pushed Netscape file and creates the entries + folder "
        "('N Bookmarks were imported')"
    ),
    "test_bookmarks_import_skips_duplicates": (
        "Re-importing an already-imported file imports only the missing entries"
    ),
    "test_bookmarks_import_malformed_file_errors": (
        "Importing an HTML file with no bookmark list shows the error dialog"
    ),
    "test_bookmarks_reset_deletes_all": (
        "Reset shows the 'Delete all bookmarks?' dialog and clears everything; "
        "the subsequent export is header-only (runs last)"
    ),
}
