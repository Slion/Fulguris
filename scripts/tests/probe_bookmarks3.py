"""Probe: full bookmark export + import flow dry-run on the Samsung.

Run: python scripts/tests/probe_bookmarks3.py
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
S = DEV.serial


def dump(label, only_edit=False, maxn=200):
    nodes = adb.nodes(S)
    print(f"=== {label} ({len(nodes)} nodes) ===")
    for x in nodes[:maxn]:
        if only_edit:
            if "Edit" in x.cls or (x.text and x.focused):
                print(f"  EDIT focus={x.focused} id={x.resource_id!r} cls={x.cls!r} {x.text[:70]!r}")
        elif x.text or x.focused:
            rid = x.resource_id.rsplit("/", 1)[-1] if "/" in x.resource_id else x.resource_id
            print(f"  f={int(x.focused)} id={rid[:32]:32s} cls={x.cls.split('.')[-1]:14s} {x.text[:58]!r}")
    return nodes


def tap_text(text, timeout=8.0, partial=False):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for x in adb.nodes(S):
            if (x.text == text if not partial else text in x.text) and x.bounds:
                adb.tap(S, (x.bounds[0] + x.bounds[2]) // 2, (x.bounds[1] + x.bounds[3]) // 2, wait=1.5)
                print(f"  tapped {text!r}")
                return True
        time.sleep(0.7)
    print(f"  NOT FOUND: {text!r}")
    return False


DEV.settle()
DEV.key(keys.BACK, 1.0)
n = adb.find_node(S, ":id/button_more")
adb.tap(S, (n.bounds[0] + n.bounds[2]) // 2, (n.bounds[1] + n.bounds[3]) // 2, wait=1.2)
assert tap_text("Settings")
time.sleep(1.5)
assert tap_text("Backup")
time.sleep(1.5)

# ---------- EXPORT ----------
print("### EXPORT ###")
assert tap_text("Export")
time.sleep(3.0)
nodes = dump("picker after Export", maxn=120)
fg = adb.foreground_package(S)
print("fg:", fg)
edit = [x for x in nodes if "Edit" in x.cls]
print("edit nodes:", [(x.resource_id.rsplit('/', 1)[-1], x.text[:60]) for x in edit])

# If a save-name dialog is not up, we are in the browser; look for a name field.
# Try: is there an EditText already? If not, maybe we need to wait for the dialog.
for _ in range(10):
    if any("Edit" in x.cls for x in adb.nodes(S)):
        break
    time.sleep(0.8)
nodes = dump("picker with edits", only_edit=True)
print("fg:", adb.foreground_package(S))

# Save: look for a confirm/save button in the dialog
saved = tap_text("Save", timeout=4.0)
print("save tapped:", saved)
time.sleep(2.5)
dump("after save", maxn=40)
print("fg:", adb.foreground_package(S))
# snackbar?
snack = [x.text for x in adb.nodes(S) if "xported" in x.text or "rror" in x.text]
print("snack-ish:", snack)

# check the file
out = DEV.transport.shell(["shell", "ls", "-t", "/sdcard/Download/"], timeout=20)
print("Downloads listing:\n", out[:600])
newest = [l.split()[-1] for l in out.splitlines() if "FulgurisBookmarks" in l]
print("FulgurisBookmarks files:", newest)
if newest:
    f = newest[0]
    out = DEV.transport.shell(["shell", "head", "-c", "1500", f"/sdcard/Download/{f}"], timeout=20)
    print("file head:\n", out)

# ---------- IMPORT ----------
print("### IMPORT ###")
# ensure we are on the Backup page (back from picker if needed)
for _ in range(4):
    if tap_text("Import", timeout=2.0):
        break
    DEV.key(keys.BACK, 1.2)
    time.sleep(0.8)
time.sleep(1.0)
dump("import picker", maxn=120)
print("fg:", adb.foreground_package(S))
# navigate to Downloads if not already
if not tap_text("autotest_import_probe.html", timeout=6.0):
    print("file not in current dir; look for Downloads shortcut")
    dump("picker2", maxn=120)
time.sleep(1.0)
print("tapping file ...")
ok = tap_text("autotest_import_probe.html", timeout=10.0)
print("picked:", ok)
time.sleep(3.0)
print("fg:", adb.foreground_package(S))
snack = [x.text for x in adb.nodes(S) if "mported" in x.text or "rror" in x.text or "import" in x.text.lower()]
print("snack-ish:", snack)

# ---------- verify in bookmarks drawer ----------
print("### DRAWER ###")
for _ in range(5):
    if "Backup" in " ".join(x.text for x in adb.nodes(S)) and tap_text("Bookmarks", timeout=1.5):
        break
    DEV.key(keys.BACK, 1.2)
    time.sleep(0.8)
    # need main menu open
    n2 = adb.find_node(S, ":id/button_more")
    if n2:
        adb.tap(S, (n2.bounds[0] + n2.bounds[2]) // 2, (n2.bounds[1] + n2.bounds[3]) // 2, wait=1.2)
time.sleep(1.5)
texts = sorted({x.text for x in adb.nodes(S) if x.text})
print("drawer texts:", texts)
for _ in range(4):
    DEV.key(keys.BACK, 1.0)
print("done")
