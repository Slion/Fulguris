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
from collections.abc import Callable
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
    """Run an adb command, retrying a few times on timeout / transient failure.

    Network adb devices (e.g. an Android TV over Wi-Fi) can be slow enough for
    commands like `uiautomator dump` to intermittently time out, so we retry
    before giving up rather than letting one slow round trip fail a test.
    """
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    last_error: Exception | None = None
    for _ in range(3):
        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                encoding="utf-8",
                errors="replace",
                timeout=timeout,
            )
            out = result.stdout or ""
            if "offline" in out or "error: device" in out or "no devices" in out:
                # Connection dropped; retry so the device has a moment to come back.
                time.sleep(1.0)
                continue
            return out
        except subprocess.TimeoutExpired as e:
            last_error = e
            time.sleep(1.0)
    if last_error:
        raise last_error
    return ""


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


def foreground_package(serial: str) -> str | None:
    """Package of the top resumed (foreground) activity, if any."""
    out = _adb(serial, ["shell", "dumpsys", "activity", "activities"])
    m = re.search(r"(?:topResumedActivity|mResumedActivity)=.*?([\w.]+)/", out)
    return m.group(1) if m else None


def wait_until(predicate: Callable[[], bool], timeout: float = 10.0, interval: float = 0.3) -> bool:
    """Poll a predicate until it is true or the timeout elapses."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if predicate():
            return True
        time.sleep(interval)
    return predicate()


def _start_app(serial: str, package: str) -> None:
    _adb(serial, ["shell", "am", "start", "-n", f"{package}/{LAUNCH_ACTIVITY}"])
    time.sleep(2.0)


def view_present(serial: str, view_id: str) -> bool:
    """Fast check whether a view with the given resource id is in the top activity.

    Uses `dumpsys activity top` (~0.2s) instead of a full uiautomator dump
    (1-3s). Matches the `app:id/<view_id>` token in a view line.
    """
    out = _adb(serial, ["shell", "dumpsys", "activity", "top"])
    return f"app:id/{view_id}" in out


def settle(serial: str, package: str, timeout: float = 60.0) -> bool:
    """Wait until the app is in the foreground and its main UI is ready.

    Launches the app if it is not already foregrounded. Returns True once the
    address field is present in the view hierarchy, which means the main
    browser screen is up and can receive input. Slow devices (e.g. an Android
    TV over network adb) need this; a fixed sleep is not reliable.
    """

    def ready() -> bool:
        if foreground_package(serial) != package:
            _start_app(serial, package)
            return False
        try:
            return view_present(serial, "search")
        except Exception:  # noqa: BLE001 - adb hiccup, keep polling
            return False

    return wait_until(ready, timeout, interval=0.5)


def launch(serial: str, package: str, wait: float = 5.0) -> None:
    _start_app(serial, package)
    if not settle(serial, package):
        print(f"WARNING: {package} did not settle on {device_label(serial)}")


def restart(serial: str, package: str, wait: float = 5.0) -> None:
    force_stop(serial, package)
    time.sleep(0.5)
    launch(serial, package, wait)


def enter_edit(serial: str) -> None:
    """Focus the address field for navigation then enter edit mode.

    After entering edit mode the field selects all its text (the address bar's edit
    guard runs ~400ms in), so a subsequent ``type_text`` replaces the current URL.
    We deliberately do NOT empty the field first: on the TV-style address bar an empty
    field drops out of edit mode and shows the label again, which would swallow the
    typed characters. Replacing the selection avoids that whole class of flakiness.
    """
    key(serial, KEY_SEARCH, 0.7)      # focus for navigation
    key(serial, KEY_DPAD_CENTER, 0.9)  # enter edit mode; guard selects all text


def navigate(serial: str, package: str, url: str, reset: bool = True) -> None:
    """Load the given URL, replacing whatever the edit field already holds.

    Waits for the app to be foregrounded and settled before sending any keys.
    By default the app is restarted first for a clean, deterministic state (the
    convention for this test suite). Pass reset=False to skip the restart when
    the app is already running; this is faster but leaves the previous tab/page
    state in place, so only use it in tests that do not depend on a fresh launch.
    """
    if reset:
        restart(serial, package)
    else:
        settle(serial, package)
    enter_edit(serial)
    type_text(serial, url, 0.4)  # replaces the selected URL
    key(serial, KEY_ENTER, 3.0)


def clear_field(serial: str) -> None:
    """Clear the focused edit field: move to the end then delete a generous number of chars.

    Uses the `input keyevent -n <count>` repeat flag so the deletes are one adb
    call instead of one per key (161 round trips -> 2), which is much faster on
    Windows where each adb invocation is a subprocess.
    """
    _adb(serial, ["shell", "input", "keyevent", "123"])  # KEYCODE_MOVE_END
    _adb(serial, ["shell", "input", "keyevent", "-n", "160", "67"])  # KEYCODE_DEL x160
    time.sleep(0.3)


def key(serial: str, keycode: int, wait: float = 0.5) -> None:
    _adb(serial, ["shell", "input", "keyevent", str(keycode)])
    time.sleep(wait)


def tap(serial: str, x: int, y: int, wait: float = 0.7) -> None:
    _adb(serial, ["shell", "input", "tap", str(x), str(y)])
    time.sleep(wait)


# Meta / combo key codes for key_combination.
KEY_CTRL_LEFT = 113
KEY_TAB = 61


def key_combination(serial: str, *keycodes: int, wait: float = 0.6) -> None:
    """Send a chord of keys pressed together (e.g. CTRL+TAB).

    Uses `input keycombination` (Android 10+), which is the only reliable way to
    deliver a modified key like CTRL+TAB over adb; plain `input keyevent` cannot
    hold a modifier down across another key.
    """
    _adb(serial, ["shell", "input", "keycombination", *[str(k) for k in keycodes]])
    time.sleep(wait)


def ctrl_tab(serial: str, wait: float = 0.9) -> None:
    """Switch to the next/most-recent tab with CTRL+TAB, as a keyboard user would."""
    key_combination(serial, KEY_CTRL_LEFT, KEY_TAB, wait=wait)


def open_tab_switcher(serial: str, wait: float = 1.0) -> bool:
    """Open the tab list by tapping the toolbar tabs button. Returns False if absent."""
    n = find_node(serial, ":id/tabs_button")
    if not n or not n.bounds:
        return False
    x1, y1, x2, y2 = n.bounds
    tap(serial, (x1 + x2) // 2, (y1 + y2) // 2, wait)
    return True


def tab_entries(serial: str) -> list[tuple[str, tuple[int, int]]]:
    """(title, center) for each tab row in the open tab switcher (`textTab` views)."""
    result: list[tuple[str, tuple[int, int]]] = []
    for nd in nodes(serial):
        if nd.resource_id.endswith("textTab") and nd.bounds:
            x1, y1, x2, y2 = nd.bounds
            result.append((nd.text, ((x1 + x2) // 2, (y1 + y2) // 2)))
    return result




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


def ssl_icon_visible(serial: str) -> bool:
    """True if the SSL status icon in the address bar is currently visible (not GONE).

    uiautomator omits GONE views from the dump, so presence with a non-empty
    bounds box means the icon is shown.
    """
    n = find_node(serial, ":id/search_ssl_status")
    if not n or not n.bounds:
        return False
    w = n.bounds[2] - n.bounds[0]
    h = n.bounds[3] - n.bounds[1]
    return w > 0 and h > 0


def device_label(serial: str) -> str:
    model = _adb(serial, ["shell", "getprop", "ro.product.model"]).strip()
    return f"{serial} ({model})" if model else serial


def _button_view_flag(serial: str, view_id: str) -> str | None:
    """First character of the FLAGS field for a view in `dumpsys activity top`.

    V=visible, I=invisible, G=gone. Returns None if the view is not in the dump.
    Much faster than a uiautomator dump (~0.2s vs several seconds).
    """
    out = _adb(serial, ["shell", "dumpsys", "activity", "top"])
    m = re.search(
        r"\{[0-9a-f]+ ([VIG])[A-Z.]* [A-Z.]* \d+,\d+-\d+,\d+ #[0-9a-f]+ app:id/" + re.escape(view_id) + r"\}",
        out,
    )
    return m.group(1) if m else None


def reload_button_state(serial: str) -> str:
    """Current state of the toolbar reload/stop button: VISIBLE, INVISIBLE, GONE, NOTFOUND."""
    flag = _button_view_flag(serial, "button_reload")
    if flag is None:
        return "NOTFOUND"
    return {"V": "VISIBLE", "I": "INVISIBLE", "G": "GONE"}[flag]


def reload_button_visible(serial: str) -> bool:
    return reload_button_state(serial) == "VISIBLE"


def reload_button_center(serial: str) -> tuple[int, int] | None:
    """Screen center of the toolbar reload/stop button, for tapping it.

    uiautomator does not expose the button itself, but it sits immediately to the
    left of the tabs button with the same size, so we derive its position from
    tabs_button (which uiautomator does report). Only meaningful while the button
    is visible.
    """
    n = find_node(serial, ":id/tabs_button")
    if not n or not n.bounds:
        return None
    x1, y1, x2, y2 = n.bounds
    w = x2 - x1
    return x1 - w // 2, (y1 + y2) // 2

