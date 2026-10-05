"""Probe: SAF picker UI for bookmark Export/Import on the Samsung.

Run: python scripts/tests/probe_bookmarks2.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "subs", "AutoTest"))

from appdevice import resolve_devices  # noqa: E402
import adb  # noqa: E402
from framework import keys  # noqa: E402

DEV = resolve_devices("R58R91GBTZK", False, None)[0]


def dump(label, maxn=80):
    nodes = adb.nodes(DEV.serial)
    print(f"=== {label} ({len(nodes)} nodes) ===")
    for x in nodes[:maxn]:
        if x.text or x.focused:
            print(f"  focus={x.focused} id={x.resource_id.split(':')[-1] if ':' in x.resource_id else x.resource_id!r} cls={x.cls.split('.')[-1]:12s} {x.text[:60]!r}")


def tap_text_exact(text, timeout=6.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for x in adb.nodes(DEV.serial):
            if x.text == text and x.bounds:
                adb.tap(DEV.serial, (x.bounds[0] + x.bounds[2]) // 2,
                        (x.bounds[1] + x.bounds[3]) // 2, wait=1.5)
                return True
        time.sleep(0.7)
    return False


DEV.settle()
DEV.key(keys.BACK, 1.0)
n = adb.find_node(DEV.serial, ":id/button_more")
adb.tap(DEV.serial, (n.bounds[0] + n.bounds[2]) // 2, (n.bounds[1] + n.bounds[3]) // 2, wait=1.2)
assert tap_text_exact("Settings"), "no Settings row"
time.sleep(1.5)
assert tap_text_exact("Backup"), "no Backup row"
time.sleep(1.5)

# ---- EXPORT picker ----
print("tapping Export ...")
assert tap_text_exact("Export"), "no Export row"
time.sleep(3.0)
dump("EXPORT PICKER")
print("foreground:", adb.foreground_package(DEV.serial))
# cancel out
DEV.key(keys.BACK, 1.2)
time.sleep(1.0)
DEV.key(keys.BACK, 1.2)
time.sleep(1.0)

# ---- push an import file first so it is in Downloads ----
local = os.path.join(os.path.dirname(__file__), "assets", "import_probe.html")
print("pushing import file ...")
adb.push(DEV.serial, local, "/sdcard/Download/autotest_import_probe.html")
DEV.transport.shell(["shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                     "-d", "file:///sdcard/Download/autotest_import_probe.html"], timeout=20)
time.sleep(1.0)

# ---- IMPORT picker ----
assert tap_text_exact("Import"), "no Import row"
time.sleep(3.0)
dump("IMPORT PICKER")
print("foreground:", adb.foreground_package(DEV.serial))
DEV.key(keys.BACK, 1.2)
time.sleep(1.0)
DEV.key(keys.BACK, 1.2)

# close settings
for _ in range(3):
    DEV.key(keys.BACK, 1.0)
print("done")
