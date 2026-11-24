# On-screen cursor: dev notes (Android TV)

Features: [cursor-gamepad.md](../features/cursor-gamepad.md) ·
[cursor-remote.md](../features/cursor-remote.md).

Code is the reference: the self-contained `fulguris.cursor` package
(`CursorController`, `CursorView`, `CursorSettings`), wired into
`WebBrowserActivity` (which forwards `dispatchKeyEvent` /
`dispatchGenericMotionEvent` to it first, and bridges short-press media
keys to the page video).

## Design decisions

- **Right stick drives the cursor at any time** — `onGenericMotionEvent`
  never consumes it (returns `false`), so left-stick page scroll and D-pad
  focus nav are untouched.
- **D-pad is yielded back on two-stick gamepads, even with the cursor on**
  (the stick is the cursor's driver there); stick-less remotes and adb's
  virtual keyboard drive the cursor with the D-pad.
- **The confirm key follows focus**: A / D-pad center / ENTER clicks at
  the cursor only while the web content holds input focus; otherwise it
  goes to the focused control (toolbar widget, address field, menu). In
  HTML5 fullscreen the tab is INVISIBLE (the custom view is shown), which
  strips WebView focus — so the target provider counts the fullscreen view
  as focused web content and the click keeps working.
- **Click is a synthetic touch** DOWN→MOVE→UP sharing one `downTime` (the
  2 px MOVE is what makes drag-only targets, e.g. a video scrub bar,
  seek). Synthetic *mouse-button* events never produce a page click on
  Android WebView (a mouse-source DOWN/UP yields only `pointermove`, no
  click). Hover is real `SOURCE_MOUSE` `ACTION_HOVER_MOVE`.
- **The OS `FLAG_LONG_PRESS` is ignored on the confirm key**: a human
  "short click" on a remote is routinely 400–700 ms — past the ~400 ms at
  which the OS starts flagging repeat events — so honoring it reclassified
  hesitant clicks as long presses and opened the menu instead of clicking
  (a real bug). The hold threshold is `pref_key_cursor_action_hold`,
  clamped to ≥ 0.5 s. The synthetic long press holds for the system
  long-press timeout + 150 ms; a CANCEL before the long-press dialog
  appears (~560 ms after DOWN, when it steals window focus) wedges the
  renderer's input state.
- **Toggle = long-press `KEYCODE_MEDIA_PLAY_PAUSE`** — own timer armed on
  `ACTION_DOWN` (media keys don't deliver system long-presses) with a real
  `FLAG_LONG_PRESS` event as secondary trigger (how
  `input keyevent --longpress 85` drives the tests). The short press is
  yielded back so it still plays/pauses the page video
  (`evaluateJavascript`, generic; cross-origin iframes unreachable).
- **FF/REW and LB/RB are the wheel**: discrete + auto-repeating, free of
  other mappings; `ACTION_SCROLL` (3 notches) at the cursor point so the
  engine hit-tests whatever is under it — a nested scrollable panel
  scrolls instead of the page, and at a WebView edge the wheel scrolls
  the page while the cursor stays clamped ("edge scroll").
- **Movement is physical**: cm/s and cm/s² converted to px via display
  DPI; an immediate step on each `ACTION_DOWN` (a discrete remote press
  releases instantly, so velocity alone would move ~0) plus a
  `Choreographer` frame loop for held keys.
- **Fade**: fades after `pref_key_cursor_fade_timeout` of no movement
  (0 = never), wakes on any movement; `moveBy` caches the overlay bounds
  because a faded overlay is `GONE` and measures 0.
- **Fullscreen**: the overlay is re-parented into
  `fullscreenContainerView` and the target override pointed at the custom
  view (reversed on exit), so visibility and synthetic events survive.
- **The overlay is not focusable** — the WebView keeps input focus, so the
  tool-bar auto-hide keeps arming (see
  [toolbar-hide-timeout.md](toolbar-hide-timeout.md)).

## Device detection

`hasRightStick(device)` requires **both** `AXIS_Z` and `AXIS_RZ` with a
*centered* motion range (`min < 0`) — excludes devices that report their
triggers on Z/RZ (range 0..1). Quirk relied on: an Xbox D-pad is a HAT
axis (`SOURCE_JOYSTICK` motion), and the framework's `SyntheticInputStage`
synthesizes DPAD `KeyEvent`s from it **carrying the original joystick
source and device** — which is what lets `hasRightStick(event.device)` see
the gamepad for those keys.

## Test groups (`scripts/tests/cursor_tests.py`)

```powershell
python scripts/tests/run.py --all --group cursor       # every cursor test
python scripts/tests/run.py --all --group cursor-movement  # D-pad move, edge scroll, gamepad D-pad yield
python scripts/tests/run.py --all --group cursor-click     # hover, click, drag-target, hesitant press
python scripts/tests/run.py --all --group cursor-toggle    # play/pause long-press toggle
python scripts/tests/run.py --all --group cursor-fade      # fade-out and wake
python scripts/tests/run.py --all --group cursor-wheel     # FF/REW + LB/RB wheel
python scripts/tests/run.py --all --group cursor-youtube   # auto-hiding scrubber replica
python scripts/tests/run.py --all --group cursor-context   # deliberate hold → context menu
python scripts/tests/run.py --all --group cursor-fullscreen
python scripts/tests/run.py --all --group cursor-media     # short media keys drive page video
```

The tests read back what happened from the toolbar label (the mirrored
page title) rather than screenshots — the local pages under
`scripts/tests/assets/` set `document.title` to report hover, click
coordinates, scroll position, seek and fullscreen state. The gamepad
D-pad test injects the hat via `sendevent` (so the synthesized key carries
the gamepad as source) and skips with a note when no gamepad is connected.
