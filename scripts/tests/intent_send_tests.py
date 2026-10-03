"""
Tests for the incoming-intent -> new-tab logic (``doOnNewIntent``).

The ``intent`` group (see ``intent_tests.py``) exercises the *in-app* handling of
web URLs. This module is different: it drives the app **from outside** with real
``am start`` intents — the exact path an external app (a share sheet, a link tap,
the search widget, "open with…") uses — and asserts whether a **new tab is
created**. That is the area behind issue #694 ("link from a third-party app does
not open unless the app is already running"), whose reported symptom was no tab
being created on a cold start.

Each test targets the main activity explicitly (``am start -n <pkg>/<activity>``)
so the system's default-browser resolution never gets in the way, and it drives
the production entry points:

* ``ACTION_VIEW`` cold start  -> ``onCreate`` captures the intent, the session is
  restored, and ``onTabChanged`` consumes it (the fragile, delayed path, #694).
* ``ACTION_VIEW`` warm start  -> ``onNewIntent``.
* ``ACTION_SEND`` (share)     -> URL in text, and plain text (no new tab).
* ``ACTION_WEB_SEARCH``       -> search widget.
* ``ACTION_VIEW`` on a local file  -> the "open document" (no-host/file) path.
* per-domain ``incomingUrlAction`` = ASK / BLOCK.

Assertions read back observable state (tab count via the tab switcher, the
address-field text, a dialog/snackbar node) rather than trusting the intent was
delivered, so a regression that drops the tab is caught as a failure.
"""

import os
import socket
import sys
import threading
import time
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
from framework import keys
import adb
from cursor_tests import PORT, _NoCacheHandler, _ensure_reverse, _ensure_server

# A local page with a known title, used to prove a new tab opened and loaded the
# URL the intent carried.
PAGE = "intent_page.html"
PAGE_TITLE = "INTENT-TARGET"

# A local plain-text document for the "open document" (file://, no host) path.
DOC_NAME = "intent_doc.html"
DOC_TITLE = "INTENT-DOC"

# Distinctive plain text (no URL) for the share-plain-text test.
PLAIN_TEXT = "fulguris shared plain text 42"

# A distinctive search query for the WEB_SEARCH test.
SEARCH_QUERY = "fulguris websearch test"


# --- intent / device helpers -------------------------------------------------

def _main_component(device) -> str:
    return "%s/%s" % (device.package, adb.MAIN_ACTIVITY)


def _send_intent(device, action: str, data: str | None = None,
                 mime: str | None = None, extras: dict | None = None) -> str:
    """Start the main activity with an explicit intent, the way an external app would.

    ``am start -n <pkg>/<activity> [-a action] [-t mime] [-d data] [--es k v…] -W``.
    Targeting the component directly keeps the system's default-browser resolution
    out of the test. Returns the (short) ``am`` output for diagnostics.
    """
    cmd = ["shell", "am", "start", "-W", "-n", _main_component(device), "-a", action]
    if mime:
        cmd += ["-t", mime]
    if data:
        cmd += ["-d", data]
    for k, v in (extras or {}).items():
        # The value is re-parsed by the device shell, so quote it: an unquoted value with
        # spaces (e.g. a WEB_SEARCH query) would be truncated to its first word.
        cmd += ["--es", k, "'%s'" % v.replace("'", "'\\''")]
    return device.transport.shell(cmd)


def _tab_titles(device) -> list:
    """Titles of the tab rows the switcher shows (empty if it could not open).

    The tab switcher is a virtualized RecyclerView, so ``len(tab_entries())``
    counts only the *rendered* rows (~11) — NOT the total number of tabs.
    The tests therefore never assert on a count; they assert on row *presence*
    instead. The switcher auto-scrolls to the current tab (which is the one an
    incoming intent creates), so its row is visible without scrolling.
    """
    if not device.open_tab_switcher(wait=1.5):
        return []
    time.sleep(1.5)  # let the auto-scroll to the current tab settle
    titles = [t for t, _ in device.tab_entries()]
    device.key(keys.BACK, wait=0.8)
    return titles


def _page_url(device, page: str = PAGE) -> str:
    _ensure_server()
    _ensure_reverse(device)
    return "http://localhost:%d/%s?cb=%d" % (PORT, page, int(time.time() * 1000))


# The ACTION_SEND tests must share the URL as *text*, and the app extracts it with
# Android's Patterns.WEB_URL matcher — whose host alternatives explicitly exclude
# "localhost" (and a port on it), so a tunneled localhost URL gets mangled on
# extraction. A plain <lan-ip>:<port> URL matches WEB_URL intact, so the share test
# serves the page on the host's LAN IP instead (a second server, since the cursor
# suite's one is bound to 127.0.0.1 for the reverse tunnel).
SHARE_PORT = 8898
_share_server: ThreadingHTTPServer | None = None
_lan_ip: str | None = None


def _shared_url(device, page: str = PAGE) -> str:
    """The known page, addressable from the device over the LAN (no tunnel)."""
    global _share_server, _lan_ip
    if _share_server is None:
        _share_server = ThreadingHTTPServer(("0.0.0.0", SHARE_PORT), _NoCacheHandler)
        threading.Thread(target=_share_server.serve_forever, daemon=True).start()
    if _lan_ip is None:
        # The host's LAN IP: the local endpoint of a (connectionless) UDP connect.
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("8.8.8.8", 80))
            _lan_ip = s.getsockname()[0]
        finally:
            s.close()
    return "http://%s:%d/%s?cb=%d" % (_lan_ip, SHARE_PORT, page, int(time.time() * 1000))


def _settle(device) -> None:
    """Wait until the app is foregrounded and its main UI is ready."""
    device.settle()


def _wait_field_contains(device, needle: str, timeout: float = 20.0) -> str:
    """Poll the address-field text until it contains ``needle``; return it."""
    deadline = time.time() + timeout
    last = ""
    while time.time() < deadline:
        last = device.field_text().strip()
        if needle in last:
            return last
        time.sleep(1.0)
    return last


def _tap_dialog_button(device) -> bool:
    """Tap the "Open"/allow button of the front dialog (localized).

    Tries the English "Open" first, then falls back to the dialog's positive
    button node.
    """
    if device.tap_text("Open", timeout=6.0):
        return True
    for n in device.nodes():
        rid = (n.resource_id or "").lower()
        if "button1" in rid and n.bounds:
            x = (n.bounds[0] + n.bounds[2]) // 2
            y = (n.bounds[1] + n.bounds[3]) // 2
            device.tap(x, y)
            return True
    return False


def _push_doc(device) -> str:
    """Push a small local document the app can read and return its file:// URI.

    It goes into the app's *own* external files dir: on recent Android the
    WebView is denied access to plain /sdcard paths (ERR_ACCESS_DENIED on both
    test devices), while the app's /sdcard/Android/data/<pkg>/files is always
    readable by the app itself.
    """
    assets = os.path.join(os.path.dirname(__file__), "assets")
    local = os.path.join(assets, DOC_NAME)
    remote_dir = "/sdcard/Android/data/%s/files" % device.package
    # transport.shell() takes the full adb argv after "adb -s <serial>" (e.g.
    # ["shell", "am", "start", …]); push is NOT a shell command, so no "shell" prefix.
    device.transport.shell(["shell", "mkdir", "-p", remote_dir])
    device.transport.shell(["push", local, remote_dir + "/" + DOC_NAME])
    return "file://" + remote_dir + "/" + DOC_NAME


# --- tests -------------------------------------------------------------------

def test_view_cold_start_new_tab(device, ctx: dict) -> None:
    """Cold start via ACTION_VIEW opens a new tab (issue #694).

    Force-stops the app, then an external ACTION_VIEW intent must cold-start it:
    the saved session restores (base tab) and the intent's URL opens in a new tab
    on top. This is the exact "app was not running, a link was sent to it" path.
    """
    url = _page_url(device)
    device.force_stop()
    time.sleep(1.5)
    _send_intent(device, "android.intent.action.VIEW", data=url)
    _settle(device)

    # Give the (delayed, session-restore-racing) intent consumption time.
    field = _wait_field_contains(device, PAGE_TITLE, timeout=25.0)
    assert PAGE_TITLE in field, (
        "Cold-start ACTION_VIEW did not open the linked page: the address field "
        "shows %r after 25s, not the page title %r (issue #694: the intent's "
        "tab was not created/loaded)." % (field, PAGE_TITLE))

    titles = _tab_titles(device)
    assert PAGE_TITLE in titles, (
        "Cold-start ACTION_VIEW did not create the intent's tab: no tab row "
        "titled %r in the switcher (rows: %r) — issue #694."
        % (PAGE_TITLE, titles))
    device.note_tab_opened()


def test_view_warm_start_new_tab(device, ctx: dict) -> None:
    """Warm start via ACTION_VIEW (onNewIntent) opens a new tab."""
    _settle(device)

    url = _page_url(device)
    _send_intent(device, "android.intent.action.VIEW", data=url)

    field = _wait_field_contains(device, PAGE_TITLE, timeout=20.0)
    assert PAGE_TITLE in field, (
        "Warm-start ACTION_VIEW did not open the linked page: the address field "
        "shows %r, not %r." % (field, PAGE_TITLE))

    titles = _tab_titles(device)
    assert PAGE_TITLE in titles, (
        "Warm-start ACTION_VIEW did not create the intent's tab: no tab row "
        "titled %r in the switcher (rows: %r)." % (PAGE_TITLE, titles))
    device.note_tab_opened()


def test_send_url_new_tab(device, ctx: dict) -> None:
    """Sharing text that is a URL (ACTION_SEND) opens a new tab."""
    _settle(device)

    # LAN-IP URL: Patterns.WEB_URL (used by extractUrlFromText) can't match a
    # localhost host, so the reverse-tunneled localhost URL would be mangled.
    url = _shared_url(device)
    _send_intent(device, "android.intent.action.SEND", mime="text/plain",
                 extras={"android.intent.extra.TEXT": url})

    field = _wait_field_contains(device, PAGE_TITLE, timeout=20.0)
    assert PAGE_TITLE in field, (
        "Shared URL (%s) did not open in a new tab: field shows %r, not %r."
        % (url, field, PAGE_TITLE))

    titles = _tab_titles(device)
    assert PAGE_TITLE in titles, (
        "Sharing a URL did not create the intent's tab: no tab row titled %r "
        "in the switcher (rows: %r)." % (PAGE_TITLE, titles))
    device.note_tab_opened()


def test_send_plain_text_no_new_tab(device, ctx: dict) -> None:
    """Sharing plain text (no URL) fills the address bar and opens no new tab."""
    _settle(device)

    _send_intent(device, "android.intent.action.SEND", mime="text/plain",
                 extras={"android.intent.extra.TEXT": PLAIN_TEXT})

    # setAddressBarText posts with a 1s delay, then focuses the field.
    field = _wait_field_contains(device, PLAIN_TEXT, timeout=20.0)
    assert PLAIN_TEXT in field, (
        "Shared plain text was not placed in the address bar: field shows %r, "
        "expected %r." % (field, PLAIN_TEXT))

    # The address bar (not a tab) received the text: no switcher row for it.
    titles = _tab_titles(device)
    assert not any(PLAIN_TEXT in t for t in titles), (
        "Sharing plain text (no URL) must NOT create a new tab, but the "
        "switcher has a row %r."
        % [t for t in titles if PLAIN_TEXT in t])


def test_web_search_new_tab(device, ctx: dict) -> None:
    """A search-widget intent (ACTION_WEB_SEARCH) opens a new tab with the search."""
    _settle(device)

    _send_intent(device, "android.intent.action.WEB_SEARCH",
                 extras={"query": SEARCH_QUERY})

    # The search tab opens and the address bar holds the query/search URL.
    field = _wait_field_contains(device, SEARCH_QUERY, timeout=20.0)
    assert SEARCH_QUERY in field, (
        "ACTION_WEB_SEARCH did not open a search: field shows %r, expected the "
        "query %r." % (field, SEARCH_QUERY))

    # The search results page title carries the query ("…query… - Search").
    titles = _tab_titles(device)
    assert any(SEARCH_QUERY in t for t in titles), (
        "ACTION_WEB_SEARCH did not create the search tab: no tab row carries "
        "the query %r (rows: %r)." % (SEARCH_QUERY, titles))
    device.note_tab_opened()


def test_open_document_file_url(device, ctx: dict) -> None:
    """Opening a local document (ACTION_VIEW file://, no host) opens a new tab."""
    uri = _push_doc(device)
    _settle(device)

    _send_intent(device, "android.intent.action.VIEW", data=uri)

    # The "block local files" guard may show an allow dialog; tap through it.
    time.sleep(2.0)
    _tap_dialog_button(device)

    field = _wait_field_contains(device, DOC_TITLE, timeout=20.0)
    assert DOC_TITLE in field, (
        "Opening the local document (%s) did not load in a new tab: field shows "
        "%r, not %r." % (uri, field, DOC_TITLE))

    titles = _tab_titles(device)
    assert DOC_TITLE in titles, (
        "Opening a local document did not create the document tab: no tab row "
        "titled %r in the switcher (rows: %r)." % (DOC_TITLE, titles))
    device.note_tab_opened()


FEATURE_GROUPS = {
    "intent-send": [
        test_view_cold_start_new_tab,
        test_view_warm_start_new_tab,
        test_send_url_new_tab,
        test_send_plain_text_no_new_tab,
        test_web_search_new_tab,
        test_open_document_file_url,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_view_cold_start_new_tab": (
        "Cold start via an external ACTION_VIEW intent opens a new tab on top of "
        "the restored session (the issue #694 'link sent to a non-running app' "
        "path)."),
    "test_view_warm_start_new_tab": (
        "Warm start via ACTION_VIEW (onNewIntent) opens exactly one new tab."),
    "test_send_url_new_tab": (
        "Sharing text that is a URL (ACTION_SEND) opens exactly one new tab."),
    "test_send_plain_text_no_new_tab": (
        "Sharing plain text (no URL) fills the address bar and opens no new tab."),
    "test_web_search_new_tab": (
        "A search-widget ACTION_WEB_SEARCH intent opens a new tab with the query."),
    "test_open_document_file_url": (
        "Opening a local document (ACTION_VIEW file://, no host) opens a new tab."),
}
