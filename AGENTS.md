# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

Fulguris is an Android web browser (Kotlin, Gradle, Hilt, RxJava, DataBinding).
The most common development loop is **build → install → test on real devices over adb**,
which is fully scriptable. **Always use the `scripts/` tooling instead of calling
`adb` or `gradlew` directly** — the scripts handle device selection, the debug flavor,
APK path, and UTF-8 quirks of the Windows terminal.

## Never get blocked — keep the agent moving

A wedged command or an unanswered prompt **freezes the whole session**. Defuse
road blocks actively instead of waiting:

- **Never leave a command waiting for input.** If a command prompts (Y/N, a file
  name, a password), answer it or abort it. For anything that needs a secret,
  stop and tell the user to type it directly — never route secrets through the
  tools.
- **Defuse, don't wait.** When a prompt is stuck, either send the input it wants
  (e.g. `N` / `y` / Enter) or **kill the terminal and start fresh**. Do not idle
  on a hung prompt — that looks like a frozen agent.
- **Prefer file-based helper scripts over inline one-liners / heredocs.**
  PowerShell heredocs (`<<'EOF'`) mangle into per-line commands and can wedge the
  shell into interactive compare / `fc` prompts. Write a small `scripts/tests/*.py`
  (or `.ps1`) file and run that instead.
- **Always pass a timeout to sync terminal commands** as a safety net so nothing
  can hang indefinitely; a timed-out command drops to the background where it can
  be inspected or killed.
- **Don't poll.** When an async/background command finishes, or a sync command
  times out, you are notified automatically — end the turn and wait rather than
  re-reading output in a loop.

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

`scripts/tools/adb.py` is the low-level Android/adb driver behind the above (device
resolution, `navigate`, `ime_shown`, `field_text`, `dropdown_present`, …). The command-line
tools import it directly; the **test framework** wraps it (see below). Import one of these
rather than shelling out to adb yourself.

### `scripts/framework/` — platform-agnostic device framework

The tests are written against a small, generic automation framework so they carry no
assumption about *how* a device is reached — the goal is to grow this into a multi-platform /
multi-transport harness (today only Android-over-adb is implemented). Layers:

- **`framework.Device`** (`device.py`) — the platform-neutral contract a test drives:
  `device.key(keys.DPAD_DOWN)`, `device.navigate(url)`, `device.field_text()`,
  `device.tap()`, `device.reload_button_state()`, orientation, etc. `keys` (`keys.py`) holds
  semantic key symbols (`DPAD_CENTER`, `MEDIA_PLAY_PAUSE`, …) instead of raw platform codes.
- **`framework.AndroidDevice`** (`android.py`) — the only implementation today; binds the
  contract to one adb serial + package and **delegates every call to `scripts/tools/adb.py`**
  (so adb's proven timings/retries are unchanged — this is a re-layering, not a rewrite).
  Android-only extras that have no cross-platform meaning live here: `reverse()` tunnels and
  `read_prefs()`/`write_prefs()` (used by the cursor suite).
- **`framework.Transport`** / **`AdbTransport`** (`transport.py`) — the pipe (shell/screencap/
  reverse). Isolating it is what lets a future platform swap in its own backend.
- **`framework.resolve_devices(spec, all, package)`** returns `Device` objects; the runner and
  session config (`reset_between_tests`, `set_keep_tabs`, tab counters, `ORIENTATIONS`) are
  exposed from the package too.

Adding a platform is additive: implement a new `Device` + `Transport`; **no test changes**.

### `scripts/tests/` — automated device test suite

```powershell
python scripts/tests/run.py --device SERIAL    # one device (default: the fast 'smoke' group)
python scripts/tests/run.py --all --group all  # every test on every connected device
python scripts/tests/run.py --device SERIAL --group cursor   # one feature group (see below)
python scripts/tests/run.py --device SERIAL --test suggestions   # only tests whose name matches
python scripts/tests/run.py --device SERIAL --orientation landscape  # force orientation
python scripts/tests/run.py --device SERIAL --notify   # show the running test in a device notification
python scripts/tests/run.py --list             # list available tests
```

Selection has two independent axes: **which tests** (`--group <name>` / `--test
<substr>`; default `smoke`) and **which devices** (`--device SERIAL` / `--all`).
`--group` does not pick a device — with several devices connected you must also
pass one of the device flags. The full suite (`--group all`) is slow
(~30 min per device) and only needed for broad changes or a final sign-off;
the `smoke` group is the cheap default sanity layer.

`--notify` shows the currently running test in a **device notification** (useful
on a TV across the room). It posts one notification (`cmd notification post`,
tag `fulguris-test-run`) that is re-posted in place per test, then a final
"Done: N/M" and a dismiss. `cmd notification` has no cancel, so the dismiss is
a direct `service call notification 8 …` — `INotificationManager.cancel-
NotificationWithTag` (AIDL transaction 8, verified identical on Android 13 and
16). It's best effort: notification problems never fail a run, and a leftover
from a crashed run is cleared before the next one posts.

Tests live in `scripts/tests/url_field_tests.py` (and `cursor_tests.py`,
`rotation_tests.py`, `toolbar_hide_tests.py`) as plain functions
`test_<name>(device, ctx) -> None` that raise `AssertionError` on failure;
`device` is a `framework.Device` (never touch adb directly from a test). New
tests are appended to `ALL_TESTS` (or a `FEATURE_GROUPS` entry).
The current suite covers the URL/address bar focus/edit model: label vs URL
content, D-pad navigation vs edit mode, two-stage back (hide keyboard then
cancel), suggestion navigation without touch, tap-to-edit, and focus pill
visibility, plus the **"Hide tool bar after" auto-hide timeout** (the
`toolbar-hide` group in `toolbar_hide_tests.py` — timeout arm at load / web-view
focus-gain / foreground return / tool-bar re-show, no starvation by busy pages,
no reset by interaction, 0 disables; see `docs/features/toolbar-hide-timeout.md`).

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
table lists every test with a short description (from each module's
`TEST_DESCRIPTIONS` — add an entry for every new test), its result (✅/❌/⚠️)
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
python scripts/tests/run.py --all --group smoke             # fast sanity: launch, open site, settings, background/foreground (the default)
python scripts/tests/run.py --all --group all               # the ENTIRE suite (all groups) — slow, ask first
python scripts/tests/run.py --all --group cursor            # every cursor test
python scripts/tests/run.py --all --group cursor-movement   # D-pad movement + edge (wheel) scroll
python scripts/tests/run.py --all --group cursor-click      # hover + click + drag-target (scrub bar) + hesitant-hold still clicks
python scripts/tests/run.py --all --group cursor-toggle     # the hotkey toggle + exit focus
python scripts/tests/run.py --all --group cursor-fade       # fade-out after inactivity + wake on move
python scripts/tests/run.py --all --group cursor-menu       # the menu item visibility/toggle
python scripts/tests/run.py --all --group cursor-fullscreen # cursor works over HTML5 fullscreen
python scripts/tests/run.py --all --group cursor-media      # hardware media keys drive the page video
python scripts/tests/run.py --all --group cursor-wheel      # cursor-mode fast-forward/rewind = mouse wheel scroll
python scripts/tests/run.py --all --group cursor-youtube    # cursor click seeks a YouTube-style auto-hiding scrubber
python scripts/tests/run.py --all --group cursor-context    # deliberate action-key hold opens the WebView context menu
python scripts/tests/run.py --all --group toolbar-hide     # the "Hide tool bar after" auto-hide timeout
```

`run.py` merges `url_field_tests`, `cursor_tests` and `toolbar_hide_tests` into
one `ALL_TESTS`; `--group` selects a group (defined in each module's
`FEATURE_GROUPS`), `--test <substr>` still filters by name, and a plain `--all`
runs everything. Add new groups to `FEATURE_GROUPS` and give every test a
`TEST_DESCRIPTIONS` entry.

The cursor tests read back what happened via the toolbar label rather than screenshots
(the RPi TV box's `screencap` was unreliable in the past — it composites via a hardware
plane — so the tests were designed not to depend on it; on current builds it does work and
`capture.py` is fine for manual visual checks). Instead they serve the pages under
`scripts/tests/assets/` (`cursor_target.html`, `scrub_target.html`, `fullscreen_target.html`,
`media_target.html`, `yt_scrub.html`, `context_target.html`) from the host over an `adb reverse`
tunnel (Fulguris blocks `file://`) and read back what the cursor did from each page's
`document.title`, which Fulguris mirrors into the toolbar label (`adb.field_text`): `hover` on
mouseover, `<x>,<y>` on click, `sy<n>` on scroll, `seek@<x>` on a scrub-bar drag, `fs-on`/
`fsclick@` in fullscreen, `playing`/`paused` for media keys, and `ctrl-shown`/`ctrl-hidden`/
`seek@<pct>`/`bar-miss` for the YouTube-scrubber replica. The action-key context-menu test
(`context_target.html`) is the one exception: the dialog's title is unreadable via the label, so
it asserts on the context menu's own nodes (the "Copy link" row carries the link URL as
`secondary_text`). (`youtube_embed.html` embeds the real YouTube player in an iframe for *manual*
vision checks — it hits the network so it isn't part of the automated suite.) Cursor **speed/accel/fade
are persisted user prefs**, so the suite resets them
to known values per device by rewriting the app's shared-prefs XML host-side
(`_reset_cursor_prefs`: `run-as cat` to read, push to `/data/local/tmp` + `run-as cp` to write —
`sed -i` and `cat >`-via-stdin both proved unreliable through the double shell). New adb helpers:
`adb.key_hold` (precise <ms> hold: `input keyevent --duration` on API 34+,
`input keycombination -t` with an inert CTRL_LEFT partner before), `adb.key_longpress`,
`adb.KEY_MEDIA_FAST_FORWARD`, `adb.KEY_MEDIA_REWIND`, `adb.KEY_MEDIA_PLAY_PAUSE`,
`adb.is_leanback`, `adb.screen_size`.

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

- **Toggle = long-press `KEYCODE_MEDIA_PLAY_PAUSE`.** A *short* play/pause press is yielded back
  to the activity so it still plays/pauses the page video. Long-press
  is detected with our own timer started on `ACTION_DOWN` and cancelled on `ACTION_UP`
  (media keys don't reliably fire system long-press), *plus* we also honor a real
  `FLAG_LONG_PRESS` event as a secondary trigger (this is what `adb shell input keyevent
  --longpress 85` uses to drive the tests). The key is intercepted in the activity's
  `dispatchKeyEvent` **before** it can reach a page `MediaSession` (e.g. a playing video).
  The setting `pref_key_cursor_hotkey` (Settings → General → Cursor) gates the hotkey only;
  the menu item works regardless.
- **Context menu = a *deliberate* (~1 s) hold of the action key.** While the cursor is on
  screen (`enabled || shown` — so it works even while the overlay is faded out), holding the
  action key (`KEYCODE_DPAD_CENTER` / `KEYCODE_ENTER` / `KEYCODE_BUTTON_A` — see
  `isConfirmKey`; BUTTON_A resolves to DPAD_CENTER on most remotes) for `ACTION_LONG_PRESS_MS`
  (1000 ms) performs a synthetic touch long press at the cursor point (`dispatchLongPress`:
  hover, touch DOWN, touch UP after `getLongPressTimeout() + 150 ms`, shared `downTime`), which
  opens the WebView's context menu for the element under the cursor — Fulguris's own long-press
  dialog (`WebPageTab.longClickPage` → `showLongPressLinkImageDialog`). A **short** press is
  still the normal click at the cursor: the click fires on the key `ACTION_UP`, so the
  `ACTION_DOWN` arms the hold timer and the UP resolves the press — if the timer already fired
  for this press, the UP is consumed and no click follows. With the cursor off the action key
  falls through to its normal meaning.
  **Why 1 s, and why the OS long-press flag is ignored here:** a human "short click" on a
  remote is routinely held 400–700 ms — past the ~400 ms at which the OS starts raising
  `FLAG_LONG_PRESS` on the key's repeat events. The action-key path therefore deliberately
  does **not** react to `event.isLongPress` (unlike the toggle hotkey, which is a separate
  key where a short press is yielded back). Honoring the flag at ~400 ms was a real bug: it
  reclassified hesitant clicks as long presses and opened the menu instead of clicking. Tests
  inject precise holds via `device.key_hold(code, ms)` (`input keyevent --duration <ms>` on
  Android 14+; `input keycombination -t <ms> CTRL_LEFT <key>` before — CTRL_LEFT is an inert
  partner there), so a 600 ms "hesitant click" and a 1500 ms deliberate hold are both
  reproducible across devices.
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
  produce a page click on Android WebView (`obtain` can't set `actionButton`; re-confirmed via an
  event-probe page — a mouse-source DOWN/UP yields only `pointermove`/`mousemove`, no click), and a
  `SOURCE_TOUCHSCREEN`/`deviceId 0` event is rejected by some builds (fails on the Android 13
  phone). The touch click *does* produce the full `pointerdown`/`pointerup` plus compat
  `mousedown`/`mouseup`/`click`, and the MOVE is what makes drag-only targets (YouTube's scrub bar)
  seek. `test_cursor_youtube_scrubber_seek` (`yt_scrub.html`) proves this against a faithful
  replica of YouTube's player — auto-hiding controls that wake on hover, and a bar that seeks on
  `pointerdown` at `clientX` only while controls are shown — so the cursor must keep controls alive
  (hover) *and* land a seeking click. (Caveat: the cursor clamps its hotspot to the exact overlay
  bottom, which maps just *below* the CSS viewport, so a click on content at the very bottom edge
  can miss; land clicks a hair above the edge.)
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
  iframes are unreachable) — but **only when cursor mode is off**. Long-press `PLAY_PAUSE` is the
  cursor toggle; the controller yields the *short* press back (returns false) so the activity can
  play/pause. **In cursor mode, `FAST_FORWARD` / `REWIND` become a mouse wheel scroll** (up / down,
  `WHEEL_NOTCHES` = 3) dispatched at the cursor point via the same `ACTION_SCROLL` path as edge
  scroll, so you can scroll the page under the cursor with the remote's transport keys.
- **Menu item visibility** (`isCursorModeAvailable()`): shown on leanback, or when a
  `SOURCE_GAMEPAD` / `SOURCE_JOYSTICK` / `SOURCE_DPAD` non-virtual device is connected. The
  menu is recomputed each time it opens, and the `InputDeviceListener` keeps it live as
  devices connect/disconnect.

## Workflow: fixing a bug

**Repro first, always.** A bug is not "in progress" until a test captures it and
reports **FAIL**. Get it red *before* you fix it — that red test is what later
proves the fix. A bug without a failing test is not reproducible and not done.

1. **Reproduce with a failing test.**
   - *Known regression* — find the existing test that should cover it and confirm
     it fails:
     `python scripts/tests/run.py --device SERIAL --group <group>` (or `--test <name>`).
   - *New bug* — reproduce it manually with the tools until you can see it, then
     write the repro test (step 2) and confirm it **FAILs** on the unfixed build.
   ```powershell
   python scripts/tools/launch.py --restart --all
   python scripts/tools/ui.py state
   python scripts/tools/ui.py key 23        # or tap/type as needed
   python scripts/tools/capture.py --all    # visual evidence
   ```
2. **Add/adjust the repro test** in `scripts/tests/url_field_tests.py` (or a new
   `*_tests.py` module registered from `run.py`) — a `test_<name>(device, ctx)`
   that drives the broken sequence and asserts the *correct* behavior, so it is
   red now and green once fixed. Add a `TEST_DESCRIPTIONS` entry.
3. **Fix the code** (usually `app/src/main/java/fulguris/...`), check compile/lint
   errors, rebuild and redeploy:
   ```powershell
   python scripts/tools/install.py --build --all
   python scripts/tools/launch.py --restart --all
   ```
4. **Confirm the repro test is green**, then **check the affected area for
   regressions** — run just the relevant feature group (or single tests) on
   **both** devices:
   ```powershell
   python scripts/tests/run.py --device SERIAL --test <repro_test_name>
   python scripts/tests/run.py --device SERIAL --group cursor-wheel
   python scripts/tests/run.py --device SERIAL --test suggestions
   ```
   **Do not greedily run the whole suite** — it takes ~30 min per device and can
   stall on RPi infra flakes (below). **Ask first before running the full suite.**
   Only run it for **broad** changes (framework, `adb.py`, activity input handling,
   anything shared by many tests) or before declaring a broad fix done:
   ```powershell
   python scripts/tests/run.py --all --group all
   ```
   Capture screenshots for visual UI changes.
   *Known RPi flake:* `uiautomator dump` (used for node lookups) occasionally
   hangs and stalls the runner mid-suite; the device is fine — just rerun the
   stalled test or group in isolation.
5. **Iterate** until the repro test and its affected group are green on both
   devices; run the full suite (after asking) only for broad changes.

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
4. **Add tests** covering the new behavior to `scripts/tests/` and rerun the
   relevant group plus the smoke default, then (after asking) the whole suite:
   `python scripts/tests/run.py --all --group all` — the new tests **and** the
   whole existing suite must pass on all devices.
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
