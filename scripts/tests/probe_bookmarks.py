"""Probe: bookmark DB access via run-as + the Backup settings UI surface.

Run: python scripts/tests/probe_bookmarks.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "tools"))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "subs", "AutoTest"))

from appdevice import resolve_devices  # noqa: E402
from framework import keys  # noqa: E402

DEV = resolve_devices("R58R91GBTZK", False, None)[0]

print("package:", DEV.package)
print("transport shell works:")
print(" ", DEV.transport.shell(["echo", "hello"], timeout=15))

# 1) sqlite3 availability under run-as
print("--- sqlite3 availability ---")
for cmd in (
    ["run-as", DEV.package, "which", "sqlite3"],
    ["run-as", DEV.package, "ls", "databases"],
    ["run-as", DEV.package, "sqlite3", "databases/bookmarkManager", "select count(*) from bookmark;"],
):
    try:
        out = DEV.transport.shell(cmd, timeout=20)
        print(f"$ {' '.join(cmd)}\n{out}\n")
    except Exception as e:
        print(f"$ {' '.join(cmd)}\nEXC: {e}\n")

# 2) open the app, menu, find the Settings / Bookmarks rows
DEV.settle()
DEV.key(keys.BACK, 1.0)
print("--- open menu via button_more ---")
import adb  # noqa: E402

n = adb.find_node(DEV.serial, ":id/button_more")
print("button_more:", n)
if n and n.bounds:
    adb.tap(DEV.serial, (n.bounds[0] + n.bounds[2]) // 2, (n.bounds[1] + n.bounds[3]) // 2, wait=1.2)
texts = sorted({x.text for x in adb.nodes(DEV.serial) if x.text})
print("menu texts:", texts)

# 3) tap "Settings"
def tap_text_exact(device, text, timeout=5.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for x in adb.nodes(device.serial):
            if x.text == text and x.bounds:
                adb.tap(device.serial, (x.bounds[0] + x.bounds[2]) // 2,
                        (x.bounds[1] + x.bounds[3]) // 2, wait=1.5)
                return True
        time.sleep(0.7)
    return False

print("--- open Settings ---")
print("tapped Settings:", tap_text_exact(DEV, "Settings"))
time.sleep(2.0)
texts = sorted({x.text for x in adb.nodes(DEV.serial) if x.text})
print("settings texts:", texts)

print("--- look for Backup ---")
print("tapped Backup:", tap_text_exact(DEV, "Backup"))
time.sleep(2.0)
for x in adb.nodes(DEV.serial):
    if x.text:
        print(f"  [{x.cls.split('.')[-1]}] {x.text!r}")

# 4) close everything
for _ in range(3):
    DEV.key(keys.BACK, 1.0)
print("done")
