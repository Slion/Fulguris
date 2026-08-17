"""Reproduce the flaky reload-test failure: spontaneous tab creation / focus steal
while the test edits the address bar right after a cold-start session restore.

Sequence:
  1. Restart app, navigate to bbc.com/news (a slow page), wait for load.
  2. Press HOME (gentle background -> onStop -> TabsManager.saveIfNeeded).
  3. Cold start (force-stop + launch) so the app restores the saved session
     asynchronously while pages reload.
  4. As soon as the field is up, SEARCH + DPAD_CENTER (enter edit), then clear.
  5. Watch the field state for 12s.

Run:  python scripts/tools/repro_flake.py [serial]
"""
import sys
import time

sys.path.insert(0, "scripts/tools")
import adb  # noqa: E402

SERIAL = sys.argv[1] if len(sys.argv) > 1 else "R58R91GBTZK"
PACKAGE = "net.slions.fulguris.full.download.debug"
KEY_HOME = 3

t0 = time.time()


def ts() -> str:
    return f"{time.time() - t0:6.2f}"


def field_state():
    n = adb.field_node(SERIAL)
    if not n:
        return "no-field"
    text = (n.text or "")[:44]
    return f"text={text!r} focused={n.focused}"


# 1. Set up a saved session containing a slow page
adb.restart(SERIAL, PACKAGE)
adb.navigate(SERIAL, PACKAGE, "https://www.bbc.com/news", reset=False)
time.sleep(8.0)
print(ts(), "setup: bbc loaded")

# 2. Gentle background so the session is saved (onStop)
adb.key(SERIAL, KEY_HOME, 1.0)
time.sleep(2.5)
print(ts(), "setup: backgrounded (session saved)")

# 3. Cold start -> async session restore
adb._adb(SERIAL, ["shell", "am", "force-stop", PACKAGE])
time.sleep(0.5)
t0 = time.time()
adb._adb(SERIAL, ["logcat", "-c"])
adb._start_app(SERIAL, PACKAGE)
print(ts(), "cold start")

deadline = time.time() + 60.0
while time.time() < deadline:
    if adb.view_present(SERIAL, "search"):
        break
    time.sleep(0.3)
print(ts(), "field present, starting edit")

# 4. Enter edit mode and clear, like the tests do
adb.key(SERIAL, adb.KEY_SEARCH, 0.7)
print(ts(), "SEARCH sent ->", field_state())
adb.key(SERIAL, adb.KEY_DPAD_CENTER, 0.7)
print(ts(), "CENTER sent ->", field_state())
adb.clear_field(SERIAL)
print(ts(), "cleared ->", field_state())

# 5. Watch
for _ in range(12):
    time.sleep(1.0)
    print(ts(), "watch ->", field_state())
print("DONE - now grab: adb -s", SERIAL, "logcat -d -v time | Select-String 'DEBUG|Notify Tab|setTabView|onPageFinished|loadUrl'")
