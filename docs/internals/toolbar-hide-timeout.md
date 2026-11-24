# "Hide tool bar after": dev notes

Feature: [features/toolbar-hide-timeout.md](../features/toolbar-hide-timeout.md).

Code is the reference: `WebBrowserActivity` —
`scheduleHideToolBarTimeout` / `cancelHideToolBarTimeout` /
`hideToolBarOnTimeout`. Pref: `pref_key_hide_tool_bar_timeout` (float,
per configuration).

## Arming & cancelling

Armed at exactly four points:

1. **Load completion** — the progress-100 *edge* (loading→loaded only;
   `onProgressChanged(100)` keeps arriving for subframes).
2. **Web view input focus gain** — covers tab switches (`setTabView`
   requests focus), address-field dismissal, menu dismissal.
3. **Return to foreground** — `onResume` (cancelled in `onStop`).
4. **Tool bar re-shown** — `showActionBar()` re-arms when it restores a
   hidden bar *and* the web view already holds focus (guarded on
   `!isLoading`). Without this the bar stays **stuck visible** in the
   dominant flow: the timeout fires (consuming the countdown), the user
   re-shows with back or scroll-up — neither moves input focus — and no
   arm edge ever follows.

Cancelled by a new load (`onPageStarted`), HTML5 fullscreen
(`onShowCustomView`) and app stop.

Hide requires: bar visible, activity window focus (not PiP), web view
input focus, no menu/panel open, no fullscreen video. A blocked fire
consumes the countdown; the next arm point restarts it.

**Known edge (not fixed):** if the timeout fires while a *transient*
blocker is present (the main menu open on a long watch), closing the menu
does not re-arm — the bar stays up until the next focus gain or load.
Re-arming on blocking-menu close was left out to keep the fix surgical.

**Deliberately not re-armed** from `onTabChanged` / repeated
`onProgressChanged(100)`: they fire many times per load on busy pages
(theme-color reports, subframes) and would starve the countdown — the
"bar never hides on bbc.com" regression.

## Test group `toolbar-hide` (`scripts/tests/toolbar_hide_tests.py`)

Covers: hide after timeout, no starvation on a busy page, no reset by
interaction, re-arm on focus gain, re-hide after back-reshow (plain and
with the cursor on), 0 disables.

Visibility is read over adb through the mirrored address-field text (title
while visible, empty once hidden); the target pages
(`timeout_target.html`, `theme_flipper.html`) are served over `adb reverse`
and announce "fully loaded" via `document.title`. Each test rewrites the
configuration preference (app stopped first) and restores 0 afterwards.

## Repro-first history

The busy-page starvation was fixed with the AGENTS.md repro-first
workflow: a deterministic local stand-in (`theme_flipper.html` flips its
`theme-color` every 2 s — the exact `onTabChanged` path busy real sites
trigger, 100 % repro vs flaky bbc.com), source-labelled logcat
diagnostics (ARM/CANCEL/FIRED + reason) that exposed the 2 s re-arm loop,
a vision double-check, then the four-point arming above locked in as the
permanent tests. A later real-site field test
(`toolbar_field_test.py`) found the back-reshow stuck-bar bug (phase 2),
reproduced by `test_toolbar_rehides_after_back_reshow` /
`test_cursor_toolbar_rehides_after_back_reshow` (both failed pre-fix) and
fixed by the `showActionBar()` re-arm.
