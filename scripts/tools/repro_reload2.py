"""Capture the RELOADDBG event sequence while exercising the browser.

Runs the user's likely flow (type URL -> new tab loads -> interact) and
records all RELOADDBG log lines plus the button visibility at each step.
"""
import re
import subprocess
import sys
import time

sys.path.insert(0, "scripts/tools")
import adb  # noqa: E402

SERIAL = "R58R91GBTZK"
OUT = "scripts/tools/out"
VIEW_RE = re.compile(
    r"\{[0-9a-f]+ ([VIG])[A-Z.]* [A-Z.]* \d+,\d+-\d+,\d+ #[0-9a-f]+ app:id/button_reload\}"
)


def flag() -> str:
    out = subprocess.run(
        ["adb", "-s", SERIAL, "shell", "dumpsys", "activity", "top"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    ).stdout
    m = VIEW_RE.search(out)
    return m.group(1) if m else "?"


def step(label: str) -> None:
    print(f"--- {label}  reload={flag()}", flush=True)


def main() -> None:
    package = adb.detect_package(SERIAL)
    logcat = subprocess.Popen(
        ["adb", "-s", SERIAL, "logcat", "-v", "time", "-s", "WebBrowserActivity"],
        stdout=open(f"{OUT}/reload2_logcat.txt", "wb"),
    )
    adb.restart(SERIAL, package)
    for _ in range(40):
        if flag() != "?":
            break
        time.sleep(1.0)
    step("start page (fresh launch)")

    # Type a URL -> new tab by default
    adb.key(SERIAL, adb.KEY_SEARCH, 0.7)
    adb.key(SERIAL, adb.KEY_DPAD_CENTER, 0.7)
    adb.clear_field(SERIAL)
    adb.type_text(SERIAL, "en.m.wikipedia.org/wiki/Android", 0.4)
    adb.key(SERIAL, adb.KEY_ENTER, 0.3)
    for _ in range(12):
        time.sleep(1)
        step(f"loading +{_ * 1}s")
    step("after load settled (12s)")

    # Scroll the page down (swipe up)
    adb._adb(SERIAL, ["shell", "input", "swipe", "360", "1200", "360", "400", "300"])
    time.sleep(2)
    step("after scroll down")

    # Focus the address bar, then leave it
    adb.key(SERIAL, adb.KEY_SEARCH, 0.7)
    time.sleep(1)
    step("address bar focused")
    adb.key(SERIAL, 4, 0.7)  # BACK
    time.sleep(1)
    step("address bar unfocused")

    # Navigate to a link within the page (tap a link)
    adb._adb(SERIAL, ["shell", "input", "tap", "360", "500"])
    for _ in range(10):
        time.sleep(1)
        step(f"link navigation +{_ * 1}s")
    step("after link navigation (10s)")

    # Wait a while for any delayed events
    for i in range(15):
        time.sleep(1)
        step(f"idle +{(i + 1)}s")

    logcat.terminate()
    print("DONE")


if __name__ == "__main__":
    main()
