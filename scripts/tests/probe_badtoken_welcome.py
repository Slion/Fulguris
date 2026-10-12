"""Probe: BadTokenException when the app is closed before tab init finishes.

Repro of `WindowManager$BadTokenException` in
WebBrowserActivity.welcomeToFulguris, called from the
`tabsManager.doOnceAfterInitialization` callback registered in onCreate:
TabsManager is a @Singleton whose setupTabs() coroutine (delay + tab
initialization) is NOT tied to the activity lifecycle. On a fresh install
(versionCode pref == 0) the first-run welcome dialog is shown once init
completes; if the user presses BACK (a browser with an empty tab finishes
the activity) in the meantime, the callback fires on a destroyed activity
and `dialog.show()` throws BadTokenException.

Each attempt: uninstall -> install -> pre-stage pref_key_accept_terms=true
(so no IntroActivity sits on top) -> launch -> wait until
WebBrowserActivity is resumed -> immediate BACK (finishes the browser) ->
check the crash buffer.

    python scripts/tests/probe_badtoken_welcome.py [ATTEMPTS]
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))

from framework import resolve_devices  # noqa: E402
import adb  # noqa: E402

SERIAL = "R58R91GBTZK"  # A22
PKG = adb.DEFAULT_PACKAGE
CRASH_SIG = "BadTokenException"


def _adb(*args: str) -> str:
    r = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, text=True)
    return (r.stdout or "") + (r.stderr or "")


def _uninstall() -> None:
    print("  uninstall ...", _adb("uninstall", PKG).strip() or "(already gone)")


def _install() -> None:
    apk = adb.apk_path()
    assert apk and os.path.exists(apk), f"APK missing: {apk}"
    out = _adb("install", "-r", apk)
    print("  install ...", out.strip())
    assert "Success" in out, out


def _prestage_prefs() -> None:
    # acceptTerms=true so the IntroActivity does not sit on top (one BACK
    # then finishes the browser directly). Done while the app is not
    # running so it is not clobbered. The attributes must be in the
    # non-namespaced form the app itself writes: with `android:name` the
    # key is not read back as pref_key_accept_terms.
    name = f"{PKG}_preferences.xml"
    xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"
           "<map>"
           '<boolean name="pref_key_accept_terms" value="true" />'
           "</map>")
    remote = f"/data/data/{PKG}/shared_prefs/{name}"
    # shared_prefs/ does not exist until the app has run once, so create it.
    # The XML is base64'd to survive the double shell (adb shell + run-as
    # sh -c) without any quoting hazards.
    import base64
    b64 = base64.b64encode(xml.encode("utf-8")).decode("ascii")
    prefs_dir = f"/data/data/{PKG}/shared_prefs"
    script = f"run-as {PKG} sh -c 'mkdir -p {prefs_dir} && echo {b64} | base64 -d > {remote}'"
    p = subprocess.run(
        ["adb", "-s", SERIAL, "shell", script],
        capture_output=True, text=True)
    out = (p.stdout or p.stderr).strip()
    print(f"  prefs: {out or 'ok'}")
    assert not out, f"pre-staging prefs failed: {out}"


def _resumed_activity() -> str | None:
    out = _adb("shell", "dumpsys", "activity", "activities")
    for line in out.splitlines():
        if "ResumedActivity" in line and "LastResumedActivity" not in line:
            return line
    return None


def _crash_log() -> str:
    return _adb("logcat", "-b", "crash", "-d", "-t", "300")


def attempt(n: int) -> bool:
    """One install/launch/back attempt. Returns True if the crash was caught."""
    print(f"[attempt {n}]")
    _uninstall()
    _install()
    _prestage_prefs()

    _adb("logcat", "-b", "crash", "-c")
    # Cold launch, then wait until the BROWSER activity (past the splash) is
    # resumed — that is the moment a user could press BACK to leave. The
    # concrete browser activity is MainActivity (a WebBrowserActivity).
    t0 = time.time()
    _adb("shell", "am", "start", "-W", "-n", f"{PKG}/fulguris.activity.SplashActivity")
    resumed = None
    while time.time() - t0 < 60:
        resumed = _resumed_activity()
        if resumed and "MainActivity" in resumed:
            break
        time.sleep(0.1)
    print(f"  browser resumed after {time.time() - t0:.2f}s")
    assert resumed and "MainActivity" in resumed, f"browser never resumed: {resumed}"

    # The moment of truth: leave the browser while tab init is still pending.
    # BACK presses may first re-show the toolbar or delete the only (empty)
    # tab -> empty state; once currentTab == null the next BACK finishes the
    # activity. Three quick presses cover all cases.
    for _ in range(3):
        _adb("shell", "input", "keyevent", "4")
        time.sleep(0.35)
    time.sleep(4.0)

    pid = adb.pidof(SERIAL, PKG)
    log = _crash_log()
    crashed = CRASH_SIG in log
    resumed_after = _resumed_activity()
    left = "MainActivity" not in (resumed_after or "")
    print(f"  left browser: {left}  pid after back: {pid!r}  crash in log: {crashed}")
    if not left and not crashed:
        print(f"  !! could not finish the browser ({resumed_after}); attempt inconclusive")
    if crashed:
        m = re.search(r"FATAL EXCEPTION: main.*?(?=\n\n|\Z)", log, re.S)
        print("---- crash excerpt ----")
        print((m.group(0) if m else log)[:2500])
    return crashed


def main() -> None:
    attempts = int(sys.argv[1]) if len(sys.argv) > 1 else 5
    resolve_devices(SERIAL, use_all=False)  # sanity: device present
    for i in range(1, attempts + 1):
        if attempt(i):
            print(f"\nREPRODUCED on attempt {i}")
            return
    print(f"\nnot reproduced in {attempts} attempts")


if __name__ == "__main__":
    main()
