"""Back-compat shim: the old ``adb`` helper, now split into generic + app parts.

The *generic* Android-over-adb driver (device discovery, raw shell, key/tap/type
input, the UI hierarchy, the display, orientation) now lives in the **AutoTest**
submodule as :mod:`autotest.android.adb`. This module re-exports that generic
surface so the tools in this folder and the historical probes that do
``import adb`` keep working unchanged.

The *Fulguris-specific* helpers (which package/activities to test, the address
bar, tabs, the debug cursor teleport, the "Fulguris tests" notification, and the
Gradle build/install) are defined here, on top of the generic driver. The object-
oriented form of the same app-specific behavior is
:class:`appdevice.FulgurisDevice`; these free functions exist for the older
``adb.<fn>(serial, package, ...)`` call style.
"""
from __future__ import annotations

import glob
import os
import subprocess
import sys
import time

# Make the AutoTest submodule importable when this file is loaded as a plain
# module (probes/tools do `sys.path.insert(tools); import adb` without it).
_HERE = os.path.dirname(os.path.abspath(__file__))  # .../scripts/tools
_AUTOTEST = os.path.normpath(os.path.join(_HERE, "..", "..", "subs", "AutoTest"))
if _AUTOTEST not in sys.path:
    sys.path.insert(0, _AUTOTEST)

from autotest.android import adb as _gadb
from autotest.android.adb import *  # noqa: F401,F403  (generic surface, incl. Node)
from autotest.android.adb import _adb  # noqa: F401  (probes call adb._adb)
from autotest import keys as _keys

# --- Fulguris app constants -------------------------------------------------

DEFAULT_PACKAGE = "net.slions.fulguris.full.agent.debug"
LAUNCH_ACTIVITY = "fulguris.activity.SplashActivity"
MAIN_ACTIVITY = "fulguris.activity.MainActivity"

# Toolbar view ids (the address bar and its companions).
VIEW_SEARCH = ":id/search"
VIEW_TABS_BUTTON = ":id/tabs_button"
VIEW_TAB_ENTRY = "textTab"
VIEW_SSL_STATUS = ":id/search_ssl_status"
VIEW_RELOAD_BUTTON = "button_reload"

# Legacy key-code constants (aliases of the semantic autotest.keys symbols).
KEY_BACK = _keys.BACK
KEY_DPAD_UP = _keys.DPAD_UP
KEY_DPAD_DOWN = _keys.DPAD_DOWN
KEY_DPAD_LEFT = _keys.DPAD_LEFT
KEY_DPAD_RIGHT = _keys.DPAD_RIGHT
KEY_DPAD_CENTER = _keys.DPAD_CENTER
KEY_ENTER = _keys.ENTER
KEY_SEARCH = _keys.SEARCH
KEY_BUTTON_A = _keys.BUTTON_A
KEY_MEDIA_FAST_FORWARD = _keys.MEDIA_FAST_FORWARD
KEY_MEDIA_PLAY_PAUSE = _keys.MEDIA_PLAY_PAUSE
KEY_MEDIA_REWIND = _keys.MEDIA_REWIND

# --- Gradle build / install (Fulguris) --------------------------------------

# Gradle assemble task / APK location per deployable variant. agentDebug is the
# default (debuggable, so it supports run-as / logcat) and what the test harness
# uses; agentRelease is a minified/shrunk build for testing release behavior.
AGENT_VARIANTS = {
    "agentDebug": (":app:assembleSlionsFullAgentDebug",
                   "app/build/outputs/apk/slionsFullAgent/debug/*.apk"),
    "agentRelease": (":app:assembleSlionsFullAgentRelease",
                     "app/build/outputs/apk/slionsFullAgent/release/*.apk"),
    "downloadDebug": (":app:assembleSlionsFullDownloadDebug",
                      "app/build/outputs/apk/slionsFullDownload/debug/*.apk"),
    # Play Store AAB (Google Play release build).
    "playstoreRelease": (":app:bundleSlionsFullPlaystoreRelease",
                         "app/build/outputs/bundle/slionsFullPlaystoreRelease/*.aab"),
}
DEFAULT_BUILD_TYPE = "agentDebug"


def repo_root() -> str:
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def gradle_build(build_type: str = DEFAULT_BUILD_TYPE) -> int:
    """Run the assemble task for the given build type. Returns the exit code."""
    gradle_task, _ = AGENT_VARIANTS[build_type]
    root = repo_root()
    gradlew = os.path.join(root, "gradlew.bat" if os.name == "nt" else "gradlew")
    print(f"Building {gradle_task} ...")
    result = subprocess.run([gradlew, gradle_task], cwd=root)
    return result.returncode


def build_output_path(build_type: str = DEFAULT_BUILD_TYPE) -> str | None:
    """Newest APK (assemble) or AAB (bundle) for the given build type."""
    _, output_glob = AGENT_VARIANTS[build_type]
    matches = glob.glob(os.path.join(repo_root(), output_glob))
    if not matches:
        return None
    return max(matches, key=os.path.getmtime)


# Backwards-compatible alias (agent variants are all APKs).
def apk_path(build_type: str = DEFAULT_BUILD_TYPE) -> str | None:
    return build_output_path(build_type)


# --- Fulguris package detection --------------------------------------------


def detect_package(serial: str) -> str:
    """The installed Fulguris package on ``serial`` (agent build preferred)."""
    return _gadb.detect_package(serial, "fulguris", (".agent.", ".debug"))


# --- Per-run session state (module-global era, for the free-function API) ---

RESTART_BETWEEN_TESTS = True
KEEP_TABS = False
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
    """Record that the current test opened a tab outside of navigate()."""
    global TABS_OPENED
    TABS_OPENED += 1


# --- Fulguris app lifecycle -------------------------------------------------


def settle(serial: str, package: str, timeout: float = 60.0) -> bool:
    """Wait until Fulguris is foregrounded and its address bar is present.

    Launches the app if it is not already foregrounded. The address field being
    present in the view hierarchy means the main browser screen is up.
    """
    def ready() -> bool:
        if _gadb.foreground_package(serial) != package:
            _gadb.start_component(serial, f"{package}/{LAUNCH_ACTIVITY}", wait=2.0)
            return False
        try:
            return _gadb.view_present(serial, VIEW_SEARCH.removeprefix(":id/"))
        except Exception:  # noqa: BLE001 - adb hiccup, keep polling
            return False

    return _gadb.wait_until(ready, timeout, interval=0.5)


def launch(serial: str, package: str, wait: float = 5.0) -> None:
    _gadb.start_component(serial, f"{package}/{LAUNCH_ACTIVITY}", wait=2.0)
    if not settle(serial, package):
        print(f"WARNING: {package} did not settle on {device_label(serial)}")


def restart(serial: str, package: str, wait: float = 5.0) -> None:
    global TABS_OPENED
    force_stop(serial, package)
    time.sleep(0.5)
    launch(serial, package, wait)
    # A fresh launch restores the previous session, so any tabs this test
    # "opened" before the restart no longer exist; only count tabs opened after it.
    TABS_OPENED = 0


def start_action(serial: str, package: str, action: str, wait: float = 2.0) -> None:
    """Start the main activity with a custom intent action (app must be running)."""
    _gadb.start_component(serial, f"{package}/{MAIN_ACTIVITY}", action, wait)


# --- Fulguris address bar ---------------------------------------------------


def enter_edit(serial: str) -> None:
    """Focus the address field for navigation then enter edit mode (selecting all)."""
    key(serial, KEY_SEARCH, 0.7)       # focus for navigation
    key(serial, KEY_DPAD_CENTER, 0.9)   # enter edit mode; guard selects all text


def navigate(serial: str, package: str, url: str, reset: bool | None = None) -> None:
    """Load the given URL, replacing whatever the edit field already holds."""
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


def field_node(serial: str) -> Node:
    return find_node(serial, VIEW_SEARCH)


def field_focused(serial: str) -> bool:
    n = field_node(serial)
    return bool(n and n.focused)


def field_text(serial: str) -> str:
    n = field_node(serial)
    return n.text if n else ""


def field_center(serial: str) -> tuple[int, int] | None:
    n = field_node(serial)
    if not n or not n.bounds:
        return None
    x1, y1, x2, y2 = n.bounds
    return (x1 + x2) // 2, (y1 + y2) // 2


def ssl_icon_visible(serial: str) -> bool:
    """True if the SSL status icon in the address bar is currently visible."""
    n = find_node(serial, VIEW_SSL_STATUS)
    if not n or not n.bounds:
        return False
    w = n.bounds[2] - n.bounds[0]
    h = n.bounds[3] - n.bounds[1]
    return w > 0 and h > 0


def reload_button_state(serial: str) -> str:
    """Current state of the toolbar reload/stop button: VISIBLE/INVISIBLE/GONE/NOTFOUND."""
    flag = _gadb.view_flag(serial, VIEW_RELOAD_BUTTON.removeprefix(":id/"))
    if flag is None:
        return "NOTFOUND"
    return {"V": "VISIBLE", "I": "INVISIBLE", "G": "GONE"}[flag]


def reload_button_visible(serial: str) -> bool:
    return reload_button_state(serial) == "VISIBLE"


def reload_button_center(serial: str) -> tuple[int, int] | None:
    """Screen center of the toolbar reload/stop button, for tapping it."""
    n = find_node(serial, VIEW_TABS_BUTTON)
    if not n or not n.bounds:
        return None
    x1, y1, x2, y2 = n.bounds
    w = x2 - x1
    return x1 - w // 2, (y1 + y2) // 2


# --- Fulguris tabs ----------------------------------------------------------


def open_tab_switcher(serial: str, wait: float = 1.0) -> bool:
    """Open the tab list by tapping the toolbar tabs button. Returns False if absent."""
    n = find_node(serial, VIEW_TABS_BUTTON)
    if not n or not n.bounds:
        return False
    x1, y1, x2, y2 = n.bounds
    tap(serial, (x1 + x2) // 2, (y1 + y2) // 2, wait)
    return True


def tab_entries(serial: str) -> list[tuple[str, tuple[int, int]]]:
    """(title, center) for each tab row in the open tab switcher (`textTab` views)."""
    result: list[tuple[str, tuple[int, int]]] = []
    for nd in nodes(serial):
        if nd.resource_id.endswith(VIEW_TAB_ENTRY) and nd.bounds:
            x1, y1, x2, y2 = nd.bounds
            result.append((nd.text, ((x1 + x2) // 2, (y1 + y2) // 2)))
    return result


def close_tabs(serial: str, count: int, wait: float = 0.9) -> None:
    """Close ``count`` tabs with CTRL+W. Cheap: key chords only, no uiautomator."""
    for _ in range(count):
        key_combination(serial, KEY_CTRL_LEFT, KEY_CTRL_W, wait=wait)


# --- Fulguris debug hooks ---------------------------------------------------


def cursor_teleport(serial: str, package: str, x: float, y: float, wait: float = 0.6) -> None:
    """Teleport the on-screen cursor to screen point ``(x, y)`` (debug builds only)."""
    _gadb.broadcast(serial, package, f"{package}.action.cursor_test_teleport",
                    {"x": x, "y": y}, wait)


# --- Fulguris progress notification -----------------------------------------

NOTIFY_PKG = "com.android.shell"
NOTIFY_TAG = "fulguris-test-run"
NOTIFY_ID = 2020
NOTIFY_TITLE = "Fulguris tests"


def post_test_notification(serial: str, text: str) -> None:
    """Post/replace the single device notification that shows test progress."""
    _gadb.post_notification(serial, text, pkg=NOTIFY_PKG, tag=NOTIFY_TAG,
                            nid=NOTIFY_ID, title=NOTIFY_TITLE)


def dismiss_test_notification(serial: str) -> None:
    """Cancel the test-progress notification posted by :func:`post_test_notification`."""
    _gadb.dismiss_notification(serial, pkg=NOTIFY_PKG, tag=NOTIFY_TAG, nid=NOTIFY_ID)
