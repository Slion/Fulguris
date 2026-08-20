# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

Fulguris is an Android web browser (Kotlin, Gradle, Hilt, RxJava, DataBinding).
The most common development loop is **build → install → test on real devices over adb**,
which is fully scriptable. **Always use the `scripts/` tooling instead of calling
`adb` or `gradlew` directly** — the scripts handle device selection, the debug flavor,
APK path, and UTF-8 quirks of the Windows terminal.

## Prerequisites

- Python 3 (stdlib only — no pip packages needed for `scripts/`)
- `adb` on `PATH` with at least one device connected (`adb devices`)
- Gradle JDK configured as usual for the project

Devices in this workflow are typically:
- a phone (e.g. Samsung) over USB
- an Android TV box over network adb (`192.168.x.x:5555`)

Every command below supports `--device SERIAL` to target one device, or
`--all` to act on every connected device. **Always verify on both device types
(phone and TV) before declaring something fixed** — D-pad/remote behavior on TV
differs from touch/keyboard behavior on phones.

## The scripts

### `scripts/tools/` — build & device tooling

| Command | Purpose |
|---|---|
| `python scripts/tools/build.py` | Build the debug APK (`assembleSlionsFullDownloadDebug`). Prints the APK path on success. |
| `python scripts/tools/install.py --build --all` | Build then install on **all** connected devices. This is the default "deploy" command. |
| `python scripts/tools/install.py --device SERIAL` | Install (already-built) APK on one device. |
| `python scripts/tools/launch.py --restart --all` | Force-stop and relaunch the app on device(s). Use after install to get a clean state. |
| `python scripts/tools/capture.py --all` | Screenshot each device to `scripts/tools/out/<serial>.png`. `--out path.png` for a single device. |
| `python scripts/tools/ui.py state` | Print address-bar state: field focus, field text, keyboard shown, webview focus, popup present. |
| `python scripts/tools/ui.py key 23` | Send a key code (23 = D-pad center, 4 = back, 19-22 = D-pad directions, 66 = enter, 84 = search/focus field, 96 = button A), then print state. |
| `python scripts/tools/ui.py text example.com` | Type text into the focused field. |
| `python scripts/tools/ui.py tap 300 100` | Tap screen coordinates. |
| `python scripts/tools/ui.py focusfield` | Focus the URL field (KEYCODE_SEARCH). |
| `python scripts/tools/add_md_icon.py <name> <style>` | Fetch a Material Symbols icon (style: `outline`, `rounded`, `sharp`, `fill`; default `outline`) from google/material-design-icons and write `app/src/main/res/drawable/ic_<name>` + `_outline`/`_rounded`/`_sharp`/`_fill` + `.xml` (e.g. `ic_encrypted_outline.xml`). Re-running overwrites. |

`scripts/tools/adb.py` is the shared library behind the above (device resolution,
`navigate`, `ime_shown`, `field_text`, `dropdown_present`, …). Import it when writing
new tools or tests rather than shelling out to adb yourself.

### `scripts/tests/` — automated device test suite

```powershell
python scripts/tests/run.py --all              # every test on every connected device
python scripts/tests/run.py --device SERIAL    # one device
python scripts/tests/run.py --all --test suggestions   # only tests whose name matches
python scripts/tests/run.py --device SERIAL --orientation landscape  # force orientation
python scripts/tests/run.py --list             # list available tests
```

Tests live in `scripts/tests/url_field_tests.py` as plain functions
`test_<name>(serial: str, package: str, ctx: dict) -> None` that raise
`AssertionError` on failure; new tests are appended to `ALL_TESTS`.
The current suite covers the URL/address bar focus/edit model: label vs URL
content, D-pad navigation vs edit mode, two-stage back (hide keyboard then
cancel), suggestion navigation without touch, tap-to-edit, and focus pill
visibility.

**Orientation & configuration.** `--orientation portrait|landscape|sensor`
forces the device orientation before the run (and restores it after). Fulguris
keeps separate settings per *configuration* — orientation + rotation +
smallest-width-dp (see `fulguris.settings.Config` / `Context.configId`), which
is how foldables get distinct inner/outer-screen settings. Each run detects and
records that configuration (`adb.device_config()` returns a matching
`config_id` like `landscape-90-sw384`).

**Results & regressions.** Every run is saved under a folder named after the
device **model** (see `scripts/tests/results.py`):

    scripts/tests/results/<MODEL>/<config-id>-<serial>.yaml    # machine-readable record
    scripts/tests/results/<MODEL>/<config-id>-<serial>.md      # human-readable table

There is one file pair per configuration + device serial, overwritten on each
run — the **git history of each file is the time dimension**. The Markdown
table lists every test with a short description (from `TEST_DESCRIPTIONS` in
`url_field_tests.py` — add an entry for every new test), its result (✅/❌/⚠️)
and duration. History is thus tracked **per device model and per
configuration**. The runner compares each run against the previous one for the
same model+config+serial and prints `REGRESSIONS`/`fixed` lines (pass↔fail
transitions). The `results/` files are **committed** — that's the point, so
runs can be compared across time, devices and configurations — so stage the
new/changed `.yaml`/`.md` files after each run. Pass `--no-save` to skip
recording. Uses PyYAML (`pip install pyyaml`).

### Feature test groups (`--group`) and the cursor suite

`scripts/tests/cursor_tests.py` holds the Android TV **cursor mode** tests (see the
cursor component section below). They are organized into named **feature groups** so a
subset relevant to one feature can be run on its own, rather than the whole suite:

```powershell
python scripts/tests/run.py --all --group cursor            # every cursor test
python scripts/tests/run.py --all --group cursor-movement   # D-pad movement + edge (wheel) scroll
python scripts/tests/run.py --all --group cursor-click      # hover + click + drag-target (scrub bar)
python scripts/tests/run.py --all --group cursor-toggle     # the hotkey toggle + exit focus
python scripts/tests/run.py --all --group cursor-fade       # fade-out after inactivity + wake on move
python scripts/tests/run.py --all --group cursor-menu       # the menu item visibility/toggle
python scripts/tests/run.py --all --group cursor-fullscreen # cursor works over HTML5 fullscreen
python scripts/tests/run.py --all --group cursor-media      # hardware media keys drive the page video
```

`run.py` merges `url_field_tests` and `cursor_tests` into one `ALL_TESTS`; `--group`
selects a group (defined in `cursor_tests.FEATURE_GROUPS`), `--test <substr>` still
filters by name, and a plain `--all` runs everything. Add new groups to
`FEATURE_GROUPS` and give every test a `TEST_DESCRIPTIONS` entry.

The cursor tests can't rely on screenshots (the RPi TV box's `screencap` returns black —
it composites via a hardware plane; the phone's works). Instead they serve the pages under
`scripts/tests/assets/` (`cursor_target.html`, `scrub_target.html`, `fullscreen_target.html`,
`media_target.html`) from the host over an `adb reverse` tunnel (Fulguris blocks `file://`)
and read back what the cursor did from each page's `document.title`, which Fulguris mirrors
into the toolbar label (`adb.field_text`): `hover` on mouseover, `<x>,<y>` on click, `sy<n>`
on scroll, `seek@<x>` on a scrub-bar drag, `fs-on`/`fsclick@` in fullscreen, `playing`/`paused`
for media keys. Cursor **speed/accel/fade are persisted user prefs**, so the suite resets them
to known values per device by rewriting the app's shared-prefs XML host-side
(`_reset_cursor_prefs`: `run-as cat` to read, push to `/data/local/tmp` + `run-as cp` to write —
`sed -i` and `cat >`-via-stdin both proved unreliable through the double shell). New adb helpers:
`adb.key_longpress`, `adb.KEY_MEDIA_FAST_FORWARD`, `adb.KEY_MEDIA_PLAY_PAUSE`, `adb.is_leanback`,
`adb.screen_size`.

## Android TV cursor mode component

An on-screen mouse cursor for the WebView, driven by D-pad / joystick, lives in its own
self-contained package **`app/src/main/java/fulguris/cursor/`** (kept out of the huge
`WebBrowserActivity` so it can be pulled into a library later):

- **`CursorController`** — owns cursor position/velocity, the `Choreographer` movement loop,
  the long-press toggle timer, and synthetic event dispatch. Public surface the activity
  forwards to: `dispatchKeyEvent`, `onGenericMotionEvent`, `toggle`/`enable`/`disable`,
  `enabled`, `release`. Coupled only to a `CursorView`, a `() -> View?` target provider
  (the current WebView), a `CursorSettings` interface and an `onModeChanged` callback — no
  dependency on the activity's class hierarchy.
- **`CursorView`** — transparent overlay (in `activity_main.xml` inside `web_view_frame`)
  that draws the cursor. Graphic is `ic_arrow_selector_tool_fill` (generated by
  `add_md_icon.py`), rendered as a white fill over a dark outline so it stays visible on
  any page.
- **`CursorSettings`** — tiny interface (`hotkeyEnabled`, `speed`, `acceleration`,
  `fadeTimeoutMs`); the activity backs it with the matching `UserPreferences.cursor*`.

`WebBrowserActivity` integration is intentionally thin: it creates the controller
(`createCursorController`, whose `targetProvider` returns `iCursorTargetOverride ?: currentTabView`),
forwards `dispatchKeyEvent` (first thing) and `dispatchGenericMotionEvent`, bridges short-press
media keys to the page video (`handleMediaKey`/`controlPageMedia`), re-parents the overlay and sets
the target override on `onShowCustomView`/`onHideCustomView` (fullscreen), exposes
`isCursorModeAvailable()` / `isCursorModeEnabled()` / `toggleCursorMode()`, wires the menu item and
`executeAction(R.id.action_toggle_cursor)`, and registers an `InputManager.InputDeviceListener` in
`onStart`/`onStop`.

Key facts a future agent needs:

- **Toggle = long-press `KEYCODE_MEDIA_FAST_FORWARD`.** A short press is unused. Long-press
  is detected with our own timer started on `ACTION_DOWN` and cancelled on `ACTION_UP`
  (media keys don't reliably fire system long-press), *plus* we also honor a real
  `FLAG_LONG_PRESS` event as a secondary trigger (this is what `adb shell input keyevent
  --longpress 90` uses to drive the tests). The key is intercepted in the activity's
  `dispatchKeyEvent` **before** it can reach a page `MediaSession` (e.g. a playing video).
  The setting `pref_key_cursor_hotkey` (Settings → General → Cursor) gates the hotkey only;
  the menu item works regardless.
- **Two ways to drive it.** *Cursor mode* (`enabled`, toggled by the hotkey/menu) lets the
  **D-pad** move the cursor and the select button click. Independently, on a two-stick gamepad the
  **right stick** (`AXIS_Z`/`AXIS_RZ`, validated as a *centered* axis via motion-range `min < 0` so
  triggers don't count) moves the cursor **at any time without toggling** — `onGenericMotionEvent`
  returns **false** (non-consuming) so the left stick's scroll and D-pad focus nav are untouched
  (Z/RZ aren't used by either). The select/`BUTTON_A` click fires whenever the cursor is `shown`.
- **Movement is physical.** Speed/acceleration are in cm/s and cm/s² converted to px via the
  display DPI (`pxPerCm`, using `DisplayMetrics.xdpi/ydpi`, clamped, `densityDpi` fallback), so a
  setting feels the same on any screen. An immediate step is applied on each key `ACTION_DOWN`
  (a discrete remote press releases instantly, so a velocity-only loop would move ~0); the
  `Choreographer` loop adds accelerating continuous movement while a key/stick is held.
- **Fade.** The cursor fades out after `CursorSettings.fadeTimeoutMs` of no movement (0 = never) and
  wakes on any movement. `moveBy` caches the overlay bounds (`boundsX/Y`) because a faded-out
  overlay is `GONE` and measures 0, and it calls `wakeCursor()` so a discrete D-pad step (which
  bypasses the frame loop) still fades the cursor back in. `enable()` sets the overlay `VISIBLE`
  **before** the `post` that centers it, otherwise it measures 0 and centers at (0,0).
- **Hover vs click.** Hover is real `SOURCE_MOUSE` `ACTION_HOVER_MOVE` via
  `dispatchGenericMotionEvent` (so `:hover` / `mouseover` fire). The **click is a touch
  DOWN→MOVE→UP** (`MotionEvent.obtain(downTime, eventTime, action, x, y, 0)` — tool FINGER, source
  unspecified — sharing one `downTime`, with a 2px MOVE). Synthetic *mouse button* events don't
  produce a page click on Android WebView (`obtain` can't set `actionButton`), and a
  `SOURCE_TOUCHSCREEN`/`deviceId 0` event is rejected by some builds (fails on the Android 13
  phone). The MOVE is what makes drag-only targets (YouTube's scrub bar) seek on a click.
- **Edge scroll = mouse wheel.** At a WebView edge, `moveBy` dispatches a synthetic
  `ACTION_SCROLL` (`AXIS_VSCROLL`/`AXIS_HSCROLL`, `SOURCE_MOUSE`) at the cursor point via
  `dispatchGenericMotionEvent`, so the engine hit-tests whatever's under the cursor and a nested
  scrollable panel scrolls instead of the whole page; the cursor stays clamped.
- **Fullscreen (`onShowCustomView`).** HTML5 fullscreen adds a custom view on top of the decor
  view, outside the WebView hierarchy. The activity re-parents the cursor overlay into
  `fullscreenContainerView` (above the custom view) and points `iCursorTargetOverride` at it, so
  the cursor stays visible and its synthetic events reach the fullscreen view; reversed on
  `onHideCustomView`.
- **Media keys.** The activity's `handleMediaKey` bridges short-press `PLAY_PAUSE` / `REWIND` /
  `FAST_FORWARD` to the page's active `<video>` via `evaluateJavascript` (generic; cross-origin
  iframes are unreachable). Long-press `FAST_FORWARD` stays the cursor toggle; the controller
  yields the *short* press back (returns false) so the activity can seek.
- **Menu item visibility** (`isCursorModeAvailable()`): shown on leanback, or when a
  `SOURCE_GAMEPAD` / `SOURCE_JOYSTICK` / `SOURCE_DPAD` non-virtual device is connected. The
  menu is recomputed each time it opens, and the `InputDeviceListener` keeps it live as
  devices connect/disconnect.

## Workflow: fixing a bug

1. **Reproduce** — get the app to the broken state on a device:
   ```powershell
   python scripts/tools/launch.py --restart --all
   python scripts/tools/ui.py state
   python scripts/tools/ui.py key 23        # or tap/type as needed
   python scripts/tools/capture.py --all    # visual evidence
   ```
   For a known regression, first find the failing test:
   `python scripts/tests/run.py --all` (or `--test <name>`).
2. **Add/adjust a test that reproduces it** in `scripts/tests/url_field_tests.py`
   (or a new `*_tests.py` module registered from `run.py`). A bug without a
   failing test is not done.
3. **Fix the code** (usually `app/src/main/java/fulguris/...`), check for
   compile/lint errors, then:
   ```powershell
   python scripts/tools/install.py --build --all
   python scripts/tools/launch.py --restart --all
   ```
4. **Verify** — rerun the full suite, not just the fixed test:
   ```powershell
   python scripts/tests/run.py --all
   ```
   Both devices must pass. Capture screenshots for visual UI changes.
5. **Iterate** until green. Do not declare a fix without a full-suite pass.

## Workflow: adding a new feature

1. **Implement** in the usual places:
   - UI code: `app/src/main/java/fulguris/` (activities, `view/` widgets)
   - Layouts/resources: `app/src/main/res/`
   - Watch out for TV vs phone: anything involving D-pad, remote control,
     focus, or the on-screen keyboard must work in **both** touch mode and
     directional (leanback) mode.
2. **Build & deploy**:
   ```powershell
   python scripts/tools/install.py --build --all
   python scripts/tools/launch.py --restart --all
   ```
3. **Explore manually with the tools** (`ui.py state/key/text/tap`, `capture.py`)
   on every device type to confirm the feature works and existing behavior is
   intact (especially the URL field focus/edit states and the WebView).
4. **Add tests** covering the new behavior to `scripts/tests/` and rerun
   `python scripts/tests/run.py --all` — the new tests **and** the whole
   existing suite must pass on all devices.
5. Update this file's test list if a new test module is introduced.

## Conventions

- **Never run `adb` or `gradlew` directly** — use `scripts/tools/*`. If a tool
  is missing a capability, add it to `scripts/tools/adb.py` / a new
  `scripts/tools/<name>.py` and use that.
- **Never force a clean rebuild** (no `--clean`, no deleting `build/`) unless
  the user explicitly asks; incremental builds are the norm.
- **Never commit without explicit user approval.** Staging is fine; committing is not.
- Windows PowerShell: quote paths that contain spaces; adb output must be
  decoded as UTF-8 (handled by `scripts/tools/adb.py` — don't bypass it).
- TV gotchas baked into the tooling: the leanback IME is fullscreen (field text
  during edit may read the IME hint), and `dumpsys window windows` is the
  reliable way to detect the autocomplete popup.
- Keep tests deterministic: reset state at the start of each test (settle +
  navigate, clear the field) rather than relying on the previous test's end
  state. The runner does NOT restart the app between tests by default (much
  faster on the TV); pass `--restart` to `run.py` for a fresh launch per test.
  Tests that genuinely need a fresh launch (initial focus, clean tab state) call
  `adb.restart(...)` / `_reset(..., restart=True)` themselves — fine even in
  no-restart mode.
  The session persists tabs across runs, and the runner closes the tabs each
  test created after the test (hygiene — tab count has NO performance impact,
  the browser runs hundreds fine). Pass `--keep-tabs` to `run.py` to leave them.
- Localization: see `L10N.md` and `.github/copilot-instructions.md` — string
  work uses `subs/l10n/android/strings.py`, not hand-edited XML.
