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
MAIN_ACTIVITY = "fulguris.activity.MainActivity"

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
KEY_MEDIA_FAST_FORWARD = 90
KEY_MEDIA_PLAY_PAUSE = 85
KEY_MEDIA_REWIND = 89


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


_leanback_cache: dict[str, bool] = {}


def is_leanback(serial: str) -> bool:
    """True if the device advertises the Android TV (leanback) system feature."""
    if serial not in _leanback_cache:
        out = _adb(serial, ["shell", "pm", "list", "features"])
        _leanback_cache[serial] = "android.software.leanback" in out
    return _leanback_cache[serial]


def screen_size(serial: str) -> tuple[int, int]:
    """Physical screen size in pixels, parsed from `wm size` (falls back to a sane default)."""
    out = _adb(serial, ["shell", "wm", "size"])
    m = re.search(r"(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (1920, 1080)


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


def start_action(serial: str, package: str, action: str, wait: float = 2.0) -> None:
    """Start the main activity with a custom intent action (e.g. ``fulguris.action.OPEN_CONFIGURATION``).

    The app must already be running (see :func:`launch` / :func:`settle`); the main
    activity is ``singleTask`` so this goes through its ``onNewIntent``.
    """
    _adb(serial, ["shell", "am", "start", "-n", f"{package}/{MAIN_ACTIVITY}", "-a", action])
    time.sleep(wait)


def start_component(serial: str, component: str, action: str | None = None, wait: float = 2.0) -> None:
    """Start an activity by its fully-qualified ``component`` (``package/.Activity`` or ``package/package.Activity``).

    Unlike :func:`start_action` (which always targets the main activity) this can open a
    specific secondary activity such as the settings screen. An optional custom intent
    ``action`` is added. Waits ``wait`` seconds before returning.
    """
    cmd = ["shell", "am", "start", "-n", component]
    if action:
        cmd += ["-a", action]
    _adb(serial, cmd)
    time.sleep(wait)


# --- Device notification (optional test progress indicator) ----------------

# ``cmd notification post`` posts as the shell uid on channel ``shell_cmd``;
# re-posting the same tag updates the notification in place, so a run always
# owns exactly one notification.
NOTIFY_PKG = "com.android.shell"
NOTIFY_TAG = "fulguris-test-run"
NOTIFY_ID = 2020
NOTIFY_TITLE = "Fulguris tests"


def post_test_notification(serial: str, text: str) -> None:
    """Post/replace the single device notification that shows test progress.

    Called once per test (and for start/finish) by the runner with ``--notify``;
    the same tag is re-posted so the notification text updates in place rather
    than stacking one notification per test.
    """
    text = text.replace("'", "")
    _adb(serial, ["shell", f"cmd notification post -t '{NOTIFY_TITLE}' {NOTIFY_TAG} '{text}'"])


def dismiss_test_notification(serial: str) -> None:
    """Cancel the test-progress notification posted by :func:`post_test_notification`.

    ``cmd notification`` has no cancel subcommand, so this calls
    ``INotificationManager.cancelNotificationWithTag(pkg, opPkg, tag, id, userId)``
    directly via ``service call``. That is binder transaction 8 — the position of
    the method in the AIDL, verified identical on Android 13 and 16. Best effort:
    a notification left over from a crashed run is harmless and the next run
    dismisses it before posting its own.
    """
    _adb(serial, ["shell",
                  f"service call notification 8 s16 {NOTIFY_PKG} s16 {NOTIFY_PKG} "
                  f"s16 {NOTIFY_TAG} i32 {NOTIFY_ID} i32 0"])


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
    global TABS_OPENED
    force_stop(serial, package)
    time.sleep(0.5)
    launch(serial, package, wait)
    # A fresh launch restores the previous session, so any tabs this test
    # "opened" before the restart no longer exist; only count tabs opened
    # after it (keeps the runner's end-of-test cleanup accurate).
    TABS_OPENED = 0


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


# Whether navigate() (when called without an explicit reset=...) restarts the
# app first. The test runner sets this via reset_between_tests() based on its
# --restart flag: no restart by default (faster), restart per test on request.
RESTART_BETWEEN_TESTS = True

# Whether the runner auto-closes the tabs a test created (see TABS_OPENED).
# Closing is pure hygiene — the tab count has NO performance impact (Fulguris
# runs hundreds fine) — but tests should leave the app as they found it.
# --keep-tabs turns the auto-close off.
KEEP_TABS = False


# Number of tabs the current test has opened via navigate(). The runner resets
# this before each test and closes them again afterwards (hygiene — see
# KEEP_TABS). A typed URL opens a new tab by default (urlInNewTab), so each
# navigate() adds one.
TABS_OPENED = 0


def reset_between_tests(restart: bool) -> None:
    """Set whether navigate() without an explicit reset= restarts the app."""
    global RESTART_BETWEEN_TESTS
    RESTART_BETWEEN_TESTS = restart


def set_keep_tabs(keep: bool) -> None:
    """Set whether the runner leaves test-created tabs open (default: close them)."""
    global KEEP_TABS
    KEEP_TABS = keep


def reset_tab_counter() -> None:
    """Reset the per-test opened-tab count (called by the runner before each test)."""
    global TABS_OPENED
    TABS_OPENED = 0


def note_tab_opened() -> None:
    """Record that the current test opened a tab outside of navigate()

    (e.g. by typing a URL + ENTER or opening a suggestion, both of which open
    a new tab by default). Keeps the runner's end-of-test closing exact — it
    must never close more tabs than the test created.
    """
    global TABS_OPENED
    TABS_OPENED += 1


def navigate(serial: str, package: str, url: str, reset: bool | None = None) -> None:
    """Load the given URL, replacing whatever the edit field already holds.

    Waits for the app to be foregrounded and settled before sending any keys.
    When ``reset`` is None, the suite-wide default (see reset_between_tests)
    decides: restart the app for a clean, deterministic state, or just settle
    on the already-running app (faster, but leaves the previous tab/page state
    in place — only safe in tests that do not depend on a fresh launch).

    In no-restart mode the address field is first returned to the unfocused
    label state (back: hide keyboard / cancel edit / leave the field) so the
    previous test's field state cannot leak into the URL typing below.

    A typed URL opens a NEW tab by default (urlInNewTab), so this increments
    the runner's tab counter; the runner closes those tabs again after the test
    unless --keep-tabs is set. Closing is cheap (CTRL+W only, no uiautomator).
    """
    global TABS_OPENED
    if (RESTART_BETWEEN_TESTS if reset is None else reset):
        restart(serial, package)
    else:
        settle(serial, package)
        for _ in range(3):
            if not field_focused(serial):
                break
            key(serial, KEY_BACK, 0.8)
    enter_edit(serial)
    type_text(serial, url, 0.4)  # replaces the selected URL
    key(serial, KEY_ENTER, 3.0)
    TABS_OPENED += 1


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


def key_longpress(serial: str, keycode: int, wait: float = 0.8) -> None:
    """Send a system long-press key event (sets FLAG_LONG_PRESS).

    Used to drive the cursor-mode toggle hotkey (KEYCODE_MEDIA_FAST_FORWARD) over adb.
    The cursor controller honors FLAG_LONG_PRESS as a secondary trigger alongside its own
    hold timer, so this reliably flips cursor mode.

    Note: the key is held for the *system* long-press timeout only (~400-500 ms), which is
    inside the "hesitant click" territory — use :func:`key_hold` for a deliberate, arbitrary
    hold.
    """
    _adb(serial, ["shell", "input", "keyevent", "--longpress", str(keycode)])
    time.sleep(wait)


_api_levels: dict = {}


def _api_level(serial: str) -> int:
    """The device's API level, cached per serial (one extra shell round trip per run)."""
    if serial not in _api_levels:
        _api_levels[serial] = int(_adb(serial, ["shell", "getprop", "ro.build.version.sdk"]).strip() or 0)
    return _api_levels[serial]


def key_hold(serial: str, keycode: int, ms: int, wait: float = 0.3) -> None:
    """Press and hold a key for ``ms`` milliseconds, then release it.

    Unlike :func:`key_longpress` (which is capped at the system long-press timeout), this
    holds for an *arbitrary* duration, so a test can produce either a deliberately
    hesitant-but-short press or a deliberate long hold.

    The call blocks for the hold duration (both paths do):

    - Android 14+ (API 34): ``input keyevent --duration <ms>``.
    - Older: ``input keycombination -t <ms> <CTRL_LEFT> <key>`` — a single-key chord held
      for ``ms``; CTRL_LEFT is an inert partner (the app tracks it only for the CTRL+TAB
      shortcut, which needs a TAB key event that never happens here).
    """
    if _api_level(serial) >= 34:
        _adb(serial, ["shell", "input", "keyevent", "--duration", str(ms), str(keycode)], timeout=max(30, ms // 1000 + 5))
    else:
        _adb(serial, ["shell", "input", "keycombination", "-t", str(ms), str(KEY_CTRL_LEFT), str(keycode)], timeout=max(30, ms // 1000 + 5))
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


KEY_CTRL_W = 51  # CTRL+W closes the current tab


def close_tabs(serial: str, count: int, wait: float = 0.9) -> None:
    """Close ``count`` tabs with CTRL+W. Cheap: key chords only, no uiautomator.

    Pure session hygiene — the tab count has no performance impact — it just
    keeps tests from leaving a pile-up behind (the session persists tabs).
    """
    for _ in range(count):
        key_combination(serial, KEY_CTRL_LEFT, KEY_CTRL_W, wait=wait)


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


def logcat(serial: str, grep: str, clear: bool = False) -> str:
    """Dump the (optionally pre-cleared) device log, keeping only lines matching ``grep``."""
    if clear:
        _adb(serial, ["logcat", "-c"], timeout=15)
    out = _adb(serial, ["logcat", "-d"], timeout=60)
    return "\n".join(l for l in out.splitlines() if grep in l)


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


# User-friendly product names for known devices, keyed by ro.product.model.
# ro.product.brand gives the vendor ("samsung") but Android exposes no marketing
# name ("Galaxy A22 5G") via getprop, so known models are mapped here.
FRIENDLY_NAMES = {
    "SM-A225F": "Galaxy A22 5G",
    "Pi Compute Module 5 Rev 1.0": "Raspberry Pi 5 TV box",
}


def product_name(model: str) -> str:
    """User-friendly name for a device model, falling back to the model string."""
    return FRIENDLY_NAMES.get(model, model)


def device_label(serial: str) -> str:
    model = _adb(serial, ["shell", "getprop", "ro.product.model"]).strip()
    name = product_name(model)
    return f"{serial} ({name})" if name else serial


# --- Orientation & device configuration ------------------------------------
# Fulguris keeps separate settings per "configuration" (see fulguris.settings.Config
# and Context.configId): orientation + rotation + smallest-width-dp. Foldables get
# distinct configs because smallestScreenWidthDp changes between inner/outer screens.
# The helpers below force the device orientation and report the same triplet so a
# test run can be recorded against the exact configuration it ran in.

# adb user_rotation values (Surface rotation constants).
ROTATION_0 = 0    # natural
ROTATION_90 = 1
ROTATION_180 = 2
ROTATION_270 = 3

# Orientation names accepted by set_orientation / the runner's --orientation flag.
ORIENTATIONS = ("portrait", "landscape", "sensor")


def _int(value: str, default: int = 0) -> int:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def wm_size(serial: str) -> tuple[int, int]:
    """Physical (natural-orientation) display size in pixels, e.g. (1080, 2340)."""
    out = _adb(serial, ["shell", "wm", "size"])
    m = re.search(r"Physical size:\s*(\d+)x(\d+)", out)
    if not m:
        m = re.search(r"(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)


def wm_density(serial: str) -> int:
    """Display density in dpi (e.g. 420). Falls back to 160 (1x) if unknown."""
    out = _adb(serial, ["shell", "wm", "density"])
    m = re.search(r"Physical density:\s*(\d+)", out)
    if not m:
        m = re.search(r"(\d+)", out)
    return int(m.group(1)) if m else 160


def user_rotation(serial: str) -> int:
    """Current forced display rotation as a 0..3 Surface constant."""
    return _int(_adb(serial, ["shell", "settings", "get", "system", "user_rotation"]), 0)


def auto_rotate(serial: str) -> bool:
    """Whether accelerometer (auto) rotation is enabled."""
    return _int(_adb(serial, ["shell", "settings", "get", "system", "accelerometer_rotation"]), 0) == 1


def orientation_state(serial: str) -> tuple[int, int]:
    """Snapshot (accelerometer_rotation, user_rotation) so it can be restored later."""
    accel = _int(_adb(serial, ["shell", "settings", "get", "system", "accelerometer_rotation"]), 0)
    return accel, user_rotation(serial)


def restore_orientation(serial: str, accel: int, rotation: int) -> None:
    """Restore a state captured by orientation_state()."""
    _adb(serial, ["shell", "settings", "put", "system", "user_rotation", str(rotation)])
    _adb(serial, ["shell", "settings", "put", "system", "accelerometer_rotation", str(accel)])


def set_orientation(serial: str, orientation: str, wait: float = 1.5) -> None:
    """Force the device orientation.

    "portrait"/"landscape" disable auto-rotate and pin the display so the natural
    orientation ends up portrait/landscape respectively (works on both portrait-
    native phones and landscape-native TVs/tablets). "sensor" re-enables
    auto-rotation. Fulguris does not lock orientation, so it simply reconfigures.
    Devices that ignore user_rotation (e.g. a fixed-orientation TV) are left as-is.
    """
    if orientation == "sensor":
        _adb(serial, ["shell", "settings", "put", "system", "accelerometer_rotation", "1"])
        time.sleep(wait)
        return
    if orientation not in ("portrait", "landscape"):
        raise ValueError(f"unknown orientation '{orientation}'")
    phys_w, phys_h = wm_size(serial)
    natural_landscape = phys_w > phys_h
    want_landscape = orientation == "landscape"
    # Rotate 90° from natural when the natural orientation is the opposite one.
    rotation = ROTATION_90 if (want_landscape != natural_landscape) else ROTATION_0
    _adb(serial, ["shell", "settings", "put", "system", "accelerometer_rotation", "0"])
    _adb(serial, ["shell", "settings", "put", "system", "user_rotation", str(rotation)])
    time.sleep(wait)


def smallest_width_dp(serial: str) -> int:
    """Smallest screen width in dp (density-independent), matching Android's swNNN.

    This does not change with rotation, so it distinguishes device/screen classes
    (e.g. a foldable's inner vs outer screen) the same way Fulguris's configId does.
    """
    w, h = wm_size(serial)
    density = wm_density(serial)
    if density <= 0:
        density = 160
    return round(min(w, h) / (density / 160.0))


def device_config(serial: str) -> dict:
    """Describe the device and its current Fulguris-style configuration.

    Returns orientation/rotation/smallest_width_dp plus a ``config_id`` string of
    the form ``landscape-90-sw360`` mirroring fulguris.settings.Config ids (minus
    the ``[Config]`` file prefix), so runs can be grouped and compared per config.
    """
    phys_w, phys_h = wm_size(serial)
    natural_landscape = phys_w > phys_h
    rot = user_rotation(serial)
    rotated = rot in (ROTATION_90, ROTATION_270)
    is_landscape = natural_landscape != rotated
    orientation = "landscape" if is_landscape else "portrait"
    sw_dp = smallest_width_dp(serial)
    model = _adb(serial, ["shell", "getprop", "ro.product.model"]).strip()
    brand = _adb(serial, ["shell", "getprop", "ro.product.brand"]).strip()
    android = _adb(serial, ["shell", "getprop", "ro.build.version.release"]).strip()
    return {
        "serial": serial,
        "model": model,
        "brand": brand.title() if brand else "",
        "product_name": product_name(model),
        "android": android,
        "orientation": orientation,
        "rotation": rot * 90,
        "smallest_width_dp": sw_dp,
        "auto_rotate": auto_rotate(serial),
        "config_id": f"{orientation}-{rot * 90}-sw{sw_dp}",
    }


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

