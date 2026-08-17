"""Repro/verify the reload/stop button behavior after a page load.

Fast-samples button_reload visibility (via `dumpsys activity top`, ~0.2s per
sample) around page load completion, and records the WebView
onProgressChanged events from logcat so we can see if progress ever re-dips
below 100 after load finished.

Usage:  python scripts/tools/repro_reload.py [URL]
"""
import re
import subprocess
import sys
import time

sys.path.insert(0, "scripts/tools")
import adb  # noqa: E402

SERIAL = "R58R91GBTZK"
URL = sys.argv[1] if len(sys.argv) > 1 else "en.m.wikipedia.org/wiki/Android"
OUT = "scripts/tools/out"

# dumpsys prints one line per view:  {hexid FLAGS ........ BOUNDS #res app:id/name}
# The first char of FLAGS encodes visibility: V=VISIBLE, I=INVISIBLE, G=GONE.
VIEW_RE = re.compile(
    r"\{[0-9a-f]+ ([VIG])[A-Z.]* [A-Z.]* \d+,\d+-\d+,\d+ #[0-9a-f]+ app:id/button_reload\}"
)


def reload_flag() -> str:
    """Return the visibility flag (V/I/G) of button_reload, or '?' if absent."""
    out = subprocess.run(
        ["adb", "-s", SERIAL, "shell", "dumpsys", "activity", "top"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    ).stdout
    m = VIEW_RE.search(out)
    return m.group(1) if m else "?"


def main() -> None:
    package = adb.detect_package(SERIAL)
    adb.restart(SERIAL, package)
    adb.settle(SERIAL, package)

    # Start capturing logcat (WebView onProgressChanged events are logged at verbose).
    logcat = subprocess.Popen(
        ["adb", "-s", SERIAL, "logcat", "-v", "time", "-s",
         "fulguris.view.WebPageChromeClient", "fulguris.view.WebPageClient",
         "fulguris.activity.WebBrowserActivity"],
        stdout=open(f"{OUT}/reload_logcat.txt", "wb"),
    )

    # Wait until the browser toolbar is actually in the view tree (settle uses
    # foreground detection which can be flaky right after a restart).
    for _ in range(40):
        if reload_flag() != "?":
            break
        time.sleep(1.0)
    else:
        print("ERROR: browser toolbar never appeared in dumpsys activity top")
        logcat.terminate()
        return
    print(f"toolbar ready, initial reload flag={reload_flag()!r}")

    adb.key(SERIAL, adb.KEY_SEARCH, 0.7)
    adb.key(SERIAL, adb.KEY_DPAD_CENTER, 0.7)
    adb.clear_field(SERIAL)
    adb.type_text(SERIAL, URL, 0.4)

    print(f"loading {URL}; sampling button_reload visibility (V=visible I=invisible G=gone)...")
    t0 = time.time()
    last = None
    adb.key(SERIAL, adb.KEY_ENTER, 0.3)
    for i in range(120):  # ~45s
        flag = reload_flag()
        mark = ""
        if flag != last:
            mark = "  <== CHANGE"
            adb.screenshot(SERIAL, f"{OUT}/reload_sample_{int(time.time() - t0)}s_{flag}.png")
            last = flag
        print(f"t={time.time() - t0:5.1f}s reload={flag}{mark}", flush=True)
        time.sleep(0.25)

    logcat.terminate()
    print("DONE - logcat in", f"{OUT}/reload_logcat.txt")


if __name__ == "__main__":
    main()
