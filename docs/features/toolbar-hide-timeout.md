# "Hide tool bar after" (auto-hide timeout)

A per-configuration setting (Settings > General > **"Hide tool bar after"**) that
automatically hides the tool bar a configurable number of seconds after a page has
finished loading. The slider accepts 0–10 seconds; **0 disables** the feature.

- Preference key: `pref_key_hide_tool_bar_timeout` (float, stored per configuration in
  the `{package}_preferences_{portrait|landscape}.xml` file).
- Implementation: `fulguris.activity.WebBrowserActivity`
  (`scheduleHideToolBarTimeout` / `cancelHideToolBarTimeout` / `hideToolBarOnTimeout`).

## Semantics

The countdown is **armed at four deliberate points**:

1. **Load completion** — when the current tab transitions from *loading* to *loaded*
   (the `onProgressChanged` progress-100 edge).
2. **Web view input focus** — every time the web view *gains* input focus: after editing
   the address field, dismissing a menu, or switching to an already-loaded tab
   (`setTabView` requests focus on the new tab's web view, so tab switches are covered).
3. **Return to foreground** — `onResume` re-arms (the countdown is cancelled in `onStop`).
4. **Tool bar re-shown** — `showActionBar()` re-arms when it restores a hidden tool bar
   *and* the web view already holds input focus (guarded on `!isLoading`, so the
   re-show during `onPageStarted` must not arm). Without this the re-shown tool bar
   stays **stuck visible forever** in the most common flow: the timeout fires (the
   countdown is consumed by the hide), the user re-shows the bar with the back key or
   a scroll-up — neither of which moves input focus — and no focus-*gain* ever fires
   again to arm a new countdown. This is the dominant path in cursor mode: the cursor
   overlay is not focusable and the D-pad drives the cursor, so the web view holds
   input focus the whole time and BACK is the TV idiom for re-showing.

It is **cancelled** by a new page load (`onPageStarted`), HTML5 video fullscreen
(`onShowCustomView`), and app stop.

The tool bar is only hidden when the timeout fires **and**:

- the tool bar is currently visible,
- the activity has window focus (and is not in Picture-in-Picture),
- the web view has input focus (so it is never yanked out while the user is editing the
  address field),
- no menu or panel/drawer is open,
- no HTML5 fullscreen video is active.

If a guard keeps the tool bar shown when the timeout fires, the countdown is simply
consumed and starts again the next time the web view gains focus (or, since the
re-show re-arm, the next time the tool bar is re-shown while the web view has focus).

**Known edge (not fixed):** if the countdown fires while a *transient* blocker is
present — most realistically the main menu being open, which the user can easily hit
on a long YouTube watch — the countdown is consumed and *closing the menu does not
re-arm it*, because the web view never lost input focus and the tool bar is never
re-shown (it was shown the whole time). The tool bar then stays up until the next
focus-gain (e.g. a tab switch) or the next load. This is the same "consumed, not
re-armed" class of bug as the re-show case, but its trigger is the *fire* path
rather than the *show* path, and the fix (re-arm when a blocking menu closes) was
left out of this change to keep it surgical.

**Deliberately not re-armed** by other tab-state callbacks: `onTabChanged` also fires on
`onPageFinished` (which can happen several times for one load) and on `<meta
name="theme-color">` reports, and `onProgressChanged(100)` keeps arriving for subframes.
Re-arming from those would restart the countdown more often than the timeout itself on
busy pages, so it would never complete (see *Methodology* below for how this regression
was found).

## Validating test group

The behavior is covered by the **`toolbar-hide`** feature group
(`scripts/tests/toolbar_hide_tests.py`), registered in `scripts/tests/run.py`:

```powershell
python scripts/tests/run.py --device SERIAL --group toolbar-hide   # one device
python scripts/tests/run.py --all --group toolbar-hide             # every connected device
python scripts/tests/run.py --all --test toolbar                   # name filter
```

| Test | What it proves |
|---|---|
| `test_toolbar_hides_after_timeout` | With a 10 s timeout the tool bar auto-hides ~10 s after the page finishes loading (arms on load completion). |
| `test_toolbar_not_starved_on_busy_page` | A busy page that keeps firing tab-state callbacks (the local `theme_flipper.html` page changes its theme-color every 2 s, driving the same `onTabChanged` path that busy real sites like bbc.com do) does **not** starve the countdown — the tool bar still hides ~timeout s after load. This is the regression guard for the "bar never hides on bbc.com" bug. |
| `test_toolbar_not_reset_by_interaction` | A D-pad press after load does **not** restart the countdown (it stays anchored at load, ~10 s after load, not ~10 s after the press). |
| `test_toolbar_rearms_on_focus_gain` | After a first auto-hide, the tool bar is re-shown (back key), focus is moved to the search field and back onto the web view; that focus gain restarts the countdown (~10 s after the tap). |
| `test_toolbar_rehides_after_back_reshow` | After an auto-hide, back re-shows the tool bar while the web view **keeps** focus (no focus gain) and it auto-hides *again* — the countdown is re-armed by the re-show itself. Regression guard for the “tool bar stuck after back” bug. |
| `test_cursor_toolbar_rehides_after_back_reshow` | The same back-reshow cycle with cursor mode active (leanback only, cursor fade disabled so the overlay is detectable): the non-focusable cursor overlay must not prevent the re-arm. This is the exact case reported in the wild. |
| `test_toolbar_disabled_at_zero` | A timeout of 0 disables the feature (the tool bar never auto-hides). |

**How the tests observe the tool bar.** Toolbar visibility is read over adb through the
mirrored address-field text: while the tool bar is visible the field shows the page
title; once it has hidden the field is empty. The target pages
(`scripts/tests/assets/timeout_target.html`, `theme_flipper.html`) are served from the
host over an `adb reverse` tunnel (Fulguris blocks `file://`) and announce "fully
loaded" by setting `document.title`, which Fulguris mirrors into the field. Each test
rewrites the configuration preference (app stopped first so it cannot clobber the file)
and restores the default (0) afterwards, so devices are left in a known state.

## Methodology (repro-first bug fixing)

The "tool bar never hides on a busy page" regression was fixed using the
reproduce → diagnose → fix → lock-in workflow (per `AGENTS.md`, *a bug without a
failing test is not done*):

1. **Reproduce with a script** — `scripts/tests/repro_toolbar_hide.py` sets the timeout
   to 5 s, loads the deterministic busy-page stand-in, waits well past the timeout and
   reports whether the tool bar hid:

   ```powershell
   python scripts/tests/repro_toolbar_hide.py --serial R58R91GBTZK --config portrait
   ```

   The original report used `https://www.bbc.com`, but a network page is not
   deterministic. The repro was converted to the **local `theme_flipper.html` asset**,
   which changes its `<meta name="theme-color">` every 2 seconds. That is a precise
   stand-in: Fulguris injects `ThemeColor.js`, which reports every theme-color change
   through the console, and the app treats that report as a tab change
   (`WebPageChromeClient` → `WebBrowser.onTabChanged`) — the exact path busy real
   sites trigger. On the buggy build the repro failed **100 %** (tool bar still visible
   after 15 s); on bbc.com it failed most of the time.

2. **Diagnose with logs** — temporary source-labelled diagnostics in the timeout code
   (logged `ARM` / `CANCEL` / `TIMEOUT FIRED` / `HIDE` plus the reason for each,
   e.g. `progress100`, `tabChangedLoaded`, `webviewFocusGained`) were added, the APK
   rebuilt and installed, and the filtered logcat was dumped from the repro script. The
   log showed the root cause unambiguously: after the real arm at load, the countdown
   was cancelled and re-armed **exactly every 2 seconds** (matching the flipper's
   theme-color interval) — the countdown could never complete.

3. **Double-check with vision** — the repro also captures screenshots (before and after
   the wait window) under `scripts/tests/out/`, which were reviewed to confirm the
   tool-bar-visible vs tool-bar-hidden states beyond the field-text signal.

4. **Fix** — the countdown is now armed only at the four deliberate points (see
   *Semantics*): the progress-100 arm is edge-gated (only on the loading→loaded
   transition and only when no countdown is already pending), the `onTabChanged`
   re-arm was removed (tab switches are covered by the web-view focus-gain re-arm),
   and `showActionBar()` re-arms when it re-shows the tool bar while the web view
   already holds focus (the progress-100 / focus-gain edges never fire on a plain
   re-show — that is what left the tool bar stuck after a back key press).

5. **Lock in with tests** — the repro passed on both devices (phone and TV), and the
   regression was converted into the permanent `toolbar-hide` test group, which also
   covers the surrounding semantics (no reset on interaction, focus-gain re-arm, 0
   disables). The temporary diagnostics were then removed and the code re-verified.

A second bug in the same area was found later by a **real-site field test**
(`scripts/tests/toolbar_field_test.py` — BBC/Wikipedia/YouTube, plain and cursor
mode, with a 10 s timeout): the tool bar that the user **re-showed** (back key, or
scroll-up) stayed stuck visible, because the re-show goes through `showActionBar()`
and never re-arms the countdown the last auto-hide had consumed. This is the dominant
case in cursor mode (the web view keeps focus, so no focus-*gain* edge follows the
re-show). It was reproduced deterministically on both devices with
`test_toolbar_rehides_after_back_reshow` /
`test_cursor_toolbar_rehides_after_back_reshow` (both failed pre-fix, pass post-fix),
fixed by the `showActionBar()` re-arm (point 4 above), and the field test's
phase 2 asserts the re-shown bar hides again.

The field test's **phase 3** (cursor mode: drive the on-screen cursor onto a
YouTube right-rail recommendation and click it) needed a position *oracle* the
app does not otherwise expose. Two dead ends were ruled out by a controlled
probe (`scripts/tests/probe_cursor_triangulate.py`): a rapid D-pad burst over
network adb **drops key events** (travel came out a fraction of the expected),
and the page-title oracle (`cursor_target.html` mirrors the click into
`document.title`) only works on that local page — on a real site the title never
becomes coordinates, so a title-based loop steered blind. The triangulation
probe instead recorded the **same** cursor position from three independent
sources and they agreed: the app's own log
(`CursorController` → `Cursor: click at target (x, y)`, overlay coordinates),
the **screenshot** (the white arrow is a tall-narrow blob; its top-left corner
is the tip — log and vision matched to 0.5 px), and the title oracle (CSS
pixels, ≈ device pixels ÷ 2.06 — this was also the source of an earlier
"the cursor didn't move" misread, having compared the two coordinate systems).
Phase 3 therefore steers as a closed loop over the
**logcat click line** — the exact coordinate space the cursor moves in, no
uiautomator in the hot path (the RPi dump flake), valid on any page. The burst
size is proportional to the remaining distance, toward the target **zone's
center** (steering toward the nearest edge parks on the corner, which can fall
off the card — that was the last miss: a corner landing 7 px short of the
thumbnail), with a single-press nudge inside a small dead band and a stuck
detector (three identical reads in a row = the bursts are not getting through).
The loop's read clicks are real clicks, so one may navigate the rail card
mid-loop; either outcome (loop reaches the zone, or a read already navigated)
is detected by the subsequent new-page wait, and the post-click auto-hide is
asserted as before.
