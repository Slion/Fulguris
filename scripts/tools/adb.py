"""Shared helpers to drive the app over adb and build / install it.

Pure standard library so it runs anywhere Python 3 is available. Both the tools in this folder
and the tests in ../tests import from here so we never call adb directly from the shell.
"""
from __future__ import annotations

import glob
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass

# Default debug package / launcher activity for the slionsFullDownload debug flavor.
DEFAULT_PACKAGE = "net.slions.fulguris.full.download.debug"
LAUNCH_ACTIVITY = "fulguris.activity.SplashActivity"

# Gradle assemble task and where its APK lands.
GRADLE_TASK = ":app:assembleSlionsFullDownloadDebug"
APK_GLOB = "app/build/outputs/apk/slionsFullDownload/debug/*.apk"

# Key codes we use.
KEY_BACK = 4
KEY_DPAD_UP = 19
KEY_DPAD_DOWN = 20
KEY_DPAD_LEFT = 21
KEY_DPAD_RIGHT = 22
KEY_DPAD_CENTER = 23
KEY_ENTER = 66
KEY_SEARCH = 84
KEY_BUTTON_A = 96


def repo_root() -> str:
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


# --- Build / install -------------------------------------------------------


def gradle_build() -> int:
    """Run the debug assemble task. Returns the process exit code."""
    root = repo_root()
    gradlew = os.path.join(root, "gradlew.bat" if os.name == "nt" else "gradlew")
    print(f"Building {GRADLE_TASK} ...")
    result = subprocess.run([gradlew, GRADLE_TASK], cwd=root)
    return result.returncode


def apk_path() -> str | None:
    matches = glob.glob(os.path.join(repo_root(), APK_GLOB))
    if not matches:
        return None
    return max(matches, key=os.path.getmtime)


def install_apk(serial: str, apk: str) -> bool:
    result = subprocess.run(
        ["adb", "-s", serial, "install", "-r", "-t", apk],
        capture_output=True,
        encoding="utf-8",
        errors="replace",
    )
    return "Success" in (result.stdout or "")


# --- adb plumbing ----------------------------------------------------------


def _adb(serial: str | None, args: list[str], timeout: int = 30) -> str:
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    result = subprocess.run(
        cmd,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )
    return result.stdout or ""


def list_devices() -> list[str]:
    out = _adb(None, ["devices"])
    devices = []
    for line in out.splitlines()[1:]:
        line = line.strip()
        if line and "\tdevice" in line:
            devices.append(line.split("\t", 1)[0])
    return devices


def resolve_devices(device: str | None, use_all: bool) -> list[str]:
    """Resolve which devices to act on, exiting with a message when ambiguous."""
    connected = list_devices()
    if not connected:
        print("No devices connected over adb.")
        sys.exit(2)
    if device:
        if device not in connected:
            print(f"Device {device} not found. Connected: {', '.join(connected)}")
            sys.exit(2)
        return [device]
    if use_all or len(connected) == 1:
        return connected
    print("Multiple devices connected; pass --device SERIAL or --all:")
    for d in connected:
        print(f"  {device_label(d)}")
    sys.exit(2)


def detect_package(serial: str) -> str:
    out = _adb(serial, ["shell", "pm", "list", "packages", "fulguris"])
    packages = [l.replace("package:", "").strip() for l in out.splitlines() if l.strip()]
    if not packages:
        return DEFAULT_PACKAGE
    if DEFAULT_PACKAGE in packages:
        return DEFAULT_PACKAGE
    debug = [p for p in packages if p.endswith(".debug")]
    return debug[0] if debug else packages[0]


def force_stop(serial: str, package: str) -> None:
    _adb(serial, ["shell", "am", "force-stop", package])


def launch(serial: str, package: str, wait: float = 5.0) -> None:
    _adb(serial, ["shell", "am", "start", "-n", f"{package}/{LAUNCH_ACTIVITY}"])
    time.sleep(wait)


def restart(serial: str, package: str, wait: float = 5.0) -> None:
    force_stop(serial, package)
    time.sleep(0.5)
    launch(serial, package, wait)


def navigate(serial: str, package: str, url: str) -> None:
    """Restart then load the given URL, clearing whatever the edit field already holds."""
    restart(serial, package)
    key(serial, KEY_SEARCH, 0.7)      # focus for navigation
    key(serial, KEY_DPAD_CENTER, 0.7)  # enter edit mode
    clear_field(serial)
    type_text(serial, url, 0.4)
    key(serial, KEY_ENTER, 3.0)


def clear_field(serial: str) -> None:
    """Clear the focused edit field: move to the end then delete a generous number of chars."""
    _adb(serial, ["shell", "input", "keyevent", "123"])  # KEYCODE_MOVE_END
    _adb(serial, ["shell", "input", "keyevent"] + ["67"] * 160)  # KEYCODE_DEL x160
    time.sleep(0.3)


def key(serial: str, keycode: int, wait: float = 0.5) -> None:
    _adb(serial, ["shell", "input", "keyevent", str(keycode)])
    time.sleep(wait)


def tap(serial: str, x: int, y: int, wait: float = 0.7) -> None:
    _adb(serial, ["shell", "input", "tap", str(x), str(y)])
    time.sleep(wait)


def type_text(serial: str, text: str, wait: float = 0.5) -> None:
    _adb(serial, ["shell", "input", "text", text.replace(" ", "%s")])
    time.sleep(wait)


def dump_ui(serial: str) -> str:
    _adb(serial, ["shell", "uiautomator", "dump", "/sdcard/w.xml"], timeout=30)
    return _adb(serial, ["shell", "cat", "/sdcard/w.xml"])


@dataclass
class Node:
    resource_id: str
    cls: str
    text: str
    focused: bool
    bounds: tuple[int, int, int, int] | None


def _parse_bounds(value: str) -> tuple[int, int, int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", value or "")
    if not m:
        return None
    return tuple(int(g) for g in m.groups())  # type: ignore[return-value]


def nodes(serial: str) -> list[Node]:
    xml = dump_ui(serial)
    result: list[Node] = []
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return result
    for el in root.iter("node"):
        result.append(
            Node(
                resource_id=el.get("resource-id", ""),
                cls=el.get("class", ""),
                text=el.get("text", ""),
                focused=el.get("focused", "false") == "true",
                bounds=_parse_bounds(el.get("bounds", "")),
            )
        )
    return result


def find_node(serial: str, id_suffix: str) -> Node | None:
    for n in nodes(serial):
        if n.resource_id.endswith(id_suffix):
            return n
    return None


def field_node(serial: str) -> Node | None:
    return find_node(serial, ":id/search")


def field_focused(serial: str) -> bool:
    n = field_node(serial)
    return bool(n and n.focused)


def field_text(serial: str) -> str:
    n = field_node(serial)
    return n.text if n else ""


def webview_focused(serial: str) -> bool:
    for n in nodes(serial):
        if n.cls == "android.webkit.WebView" and n.focused:
            return True
    return False


def field_center(serial: str) -> tuple[int, int] | None:
    n = field_node(serial)
    if not n or not n.bounds:
        return None
    x1, y1, x2, y2 = n.bounds
    return (x1 + x2) // 2, (y1 + y2) // 2


def ime_shown(serial: str) -> bool:
    out = _adb(serial, ["shell", "dumpsys", "input_method"])
    m = re.search(r"mInputShown=(\w+)", out)
    return bool(m and m.group(1) == "true")


def dropdown_present(serial: str) -> bool:
    """True if a suggestion list popup window is present.

    We look at the window list rather than the view hierarchy because uiautomator does not
    reliably capture the dropdown popup window while the keyboard is up.
    """
    out = _adb(serial, ["shell", "dumpsys", "window", "windows"])
    return "PopupWindow" in out


def screenshot(serial: str, path: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as f:
        cmd = ["adb", "-s", serial, "exec-out", "screencap", "-p"]
        f.write(subprocess.run(cmd, capture_output=True).stdout)


def device_label(serial: str) -> str:
    model = _adb(serial, ["shell", "getprop", "ro.product.model"]).strip()
    return f"{serial} ({model})" if model else serial
