"""
Tests for the "launch third-party app" handling of web URLs (IntentUtils / WebPageClient).

Regression test for https://github.com/Slion/Fulguris/issues/542: on EMUI (Huawei)
devices the pre-installed Huawei Browser declares an
ACTION_VIEW intent filter for https/http with two EMPTY wildcard data authorities, which
matched every URL and made ``isSpecializedHandlerAvailable`` believe a specialized
(non-browser) app was available for all sites. Fulguris would then show the
"Launch third-party app?" dialog for every website.

The repro is the exact user flow: fresh start, type a plain site in the address bar.
On a broken build the dialog appears within ~1s (the page redirects to a /search/
URL, which is where shouldOverrideUrlLoading fires); on a fixed build the page loads
silently and no dialog shows.

A remembered per-domain choice ("Don't ask again") would silently suppress the dialog
and mask a regression, so the test deletes the domain override first — the same state
a fresh install would have.
"""

import re
import time

from cursor_tests import PORT, _ensure_reverse, _ensure_server
from framework import keys


DIALOG_TEXT = "Launch third-party app?"

# A plain site the user actually reported the bogus dialog for (issue #542).
REPRO_URL = "slions.net"
# A second real, always-reachable plain site (IANA-reserved documentation domain)
# used as a control — no app declares a data authority for it, so the dialog must
# never appear.
CONTROL_URL = "example.com"

# A URL that ONLY a specialized (non-browser) app can open: a Play Store app
# details page. The Play Store (com.android.vending) declares a data authority
# for play.google.com, so Fulguris must offer to launch it — the #542 fix
# (host-matched authorities) must not over-filter real specialized handlers.
# intent_play_redirect.html redirects to it so shouldOverrideUrlLoading fires.
PLAY_DETAILS_URL = ("https://play.google.com/store/apps/details"
                    "?id=com.google.android.apps.maps")
# reverseDomainName("play.google.com") — the domain-override file name stem.
PLAY_DOMAIN_FILE = "com.google.play"


def _clear_launch_override(device, host: str) -> None:
    """Delete the remembered per-domain launch choice ('Don't ask again').

    ``rm -f`` never fails for a missing file, so there is nothing to assert on —
    the transport returns plain stdout, not a CompletedProcess.
    """
    device.transport.shell(
        ["shell", "run-as", device.package, "rm", "-f",
         "shared_prefs/[Domain]%s.xml" % host])


def _dialog_present(device) -> bool:
    return any(DIALOG_TEXT in (n.text or "") for n in device.nodes())


def _navigate_plain_site(device, url: str) -> bool:
    """Fresh start, type the URL in the address bar, wait for the (bogus) dialog.

    Returns True if the "Launch third-party app?" dialog appeared within ~12s.
    """
    device.restart()
    device.enter_edit()
    device.type_text(url, 0.5)
    device.key(keys.ENTER, 4.0)

    for _ in range(12):
        time.sleep(1.0)
        if _dialog_present(device):
            return True
    return False


def _enable_logs_pref(device) -> None:
    """Enable ``pref_key_logs`` in the app's main shared prefs (app stopped first).

    The test deliberately LEAVES it enabled afterwards: this is the agent
    debug build, and keeping Timber logging on is convenient for future
    logcat-based debugging of the device.
    """
    path = "shared_prefs/%s_preferences.xml" % device.package
    device.force_stop()
    xml = device.read_prefs(path)
    if "pref_key_logs" in xml:
        xml = re.sub(
            r'<boolean name="pref_key_logs" value="\w+" />',
            '<boolean name="pref_key_logs" value="true" />', xml)
    else:
        xml = xml.replace(
            "</map>", '    <boolean name="pref_key_logs" value="true" />\n</map>')
    device.write_prefs(path, xml)


def test_no_launch_dialog_for_plain_site(device, ctx: dict) -> None:
    """Typing a plain https site must NOT show the launch-third-party-app dialog.

    Regression test for the EMUI/Huawei Browser wildcard-authority false positive
    (issue #542). On a broken build the dialog shows within a second for the repro
    site; on a fixed build the page just loads silently.
    """
    _clear_launch_override(device, "slions.net")
    _clear_launch_override(device, "example.com")

    appeared = _navigate_plain_site(device, REPRO_URL)
    assert not appeared, (
        "The 'Launch third-party app?' dialog appeared for a plain site "
        "(%s) — a web handler with an unrelated/wildcard data authority is "
        "being treated as a specialized app (issue #542)." % REPRO_URL)
    assert device.field_text() not in ("", REPRO_URL, "https://%s/" % REPRO_URL), (
        "Page did not load: address field is %r" % device.field_text())

    # Control: a second plain site also must not trigger the dialog.
    appeared = _navigate_plain_site(device, CONTROL_URL)
    assert not appeared, (
        "The 'Launch third-party app?' dialog appeared for %s "
        "(issue #542 regression)." % CONTROL_URL)


def test_launch_dialog_for_specialized_handler(device, ctx: dict) -> None:
    """A URL only a specialized app can open MUST show the launch dialog.

    Positive counterpart of ``test_no_launch_dialog_for_plain_site``: it proves
    the #542 host-matching fix does not over-filter. Navigating to a Play Store
    app details page (via a local redirect page, so shouldOverrideUrlLoading
    fires) must show the "Launch third-party app?" dialog and list the Play
    Store — exercising both call sites of the shared predicate:
    ``isSpecializedHandlerAvailable`` (whether the dialog shows at all) and the
    ``specializedApps`` filter in ``WebPageClient.launchAppIfNeeded`` (which
    apps the dialog lists).

    Requires the Play Store (com.android.vending) to be installed.
    """
    if "com.android.vending" not in device.transport.shell(
            ["shell", "pm", "list", "packages", "com.android.vending"]):
        raise AssertionError(
            "Play Store (com.android.vending) not installed — this test "
            "requires it to have a genuine specialized handler for the URL.")

    _ensure_server()
    _ensure_reverse(device)
    _clear_launch_override(device, PLAY_DOMAIN_FILE)
    _enable_logs_pref(device)

    try:
        device.logcat("", clear=True)
        url = "http://localhost:%d/intent_play_redirect.html?cb=%d" % (
            PORT, int(time.time() * 1000))
        device.navigate(url, reset=True)

        for _ in range(15):
            time.sleep(1.0)
            if _dialog_present(device):
                break
        assert _dialog_present(device), (
            "The 'Launch third-party app?' dialog did not appear for %s — a "
            "genuine specialized handler (the Play Store) is being filtered "
            "out (issue #542 over-filtering regression)." % PLAY_DETAILS_URL)

        # Call site 1: shouldOverrideUrlLoading fired for the redirect and took
        # the ASK branch (isSpecializedHandlerAvailable returned true).
        logs = device.logcat("WebPageClient")
        assert "shouldOverrideUrlLoading - %s" % PLAY_DETAILS_URL in logs, (
            "The redirect to the Play Store details URL did not go through "
            "shouldOverrideUrlLoading.\nlogcat: %s" % logs)
        assert "Launch app - ASK" in logs, (
            "launchAppIfNeeded did not take the ASK branch for %s.\nlogcat: %s"
            % (PLAY_DETAILS_URL, logs))

        # Call site 2: the specializedApps filter kept the Play Store — the
        # dialog lists it ("Single app: Play Store" layout).
        texts = [n.text or "" for n in device.nodes()]
        assert any("Play Store" in t for t in texts) or \
                "Single app: Play Store" in logs or \
                "Multiple apps available" in logs, (
            "The dialog does not list the Play Store as a specialized app for "
            "%s.\ndialog texts: %s\nlogcat: %s"
            % (PLAY_DETAILS_URL, texts, logs))
    finally:
        # Dismiss the dialog (never "Launch" — that would leave the app) and
        # let the runner close the opened tab.
        device.key(keys.BACK, 1.0)


FEATURE_GROUPS = {
    "intent": [
        test_no_launch_dialog_for_plain_site,
        test_launch_dialog_for_specialized_handler,
    ],
}

ALL_TESTS = [t for group in FEATURE_GROUPS.values() for t in group]

TEST_DESCRIPTIONS = {
    "test_no_launch_dialog_for_plain_site": (
        "Typing a plain https site shows no 'Launch third-party app?' dialog "
        "(EMUI wildcard-authority regression, #542)."),
    "test_launch_dialog_for_specialized_handler": (
        "A Play Store details URL still shows the launch dialog listing the "
        "Play Store (the #542 host-matching fix must not over-filter real "
        "specialized handlers)."),
}
