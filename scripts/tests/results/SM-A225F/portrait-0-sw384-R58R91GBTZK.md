# Test run — SM-A225F · portrait-0-sw384

- **When:** 2026-08-21T20:00:30+00:00
- **Device:** Galaxy A22 5G (Samsung SM-A225F) — Android 13 (serial `R58R91GBTZK`)
- **Config:** portrait, rotation 0°, smallest width 384dp
- **Package:** `net.slions.fulguris.full.download.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 7/7 passed in 217.7s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_toolbar_hides_after_timeout` | With a 10 s timeout the tool bar auto-hides ~10 s after the page finishes loading | ✅ pass | 30.9s |
| `test_toolbar_not_starved_on_busy_page` | A busy page that keeps firing tab-state callbacks (theme-color changes) does not starve the countdown - the tool bar still hides ~timeout s after load | ✅ pass | 27.1s |
| `test_toolbar_not_reset_by_interaction` | A D-pad press after load does not reset the countdown (it stays anchored at load) | ✅ pass | 30.8s |
| `test_toolbar_rearms_on_focus_gain` | After a first auto-hide, regaining web-view input focus restarts the countdown | ✅ pass | 49.1s |
| `test_toolbar_rehides_after_back_reshow` | After an auto-hide, back re-shows the tool bar (web view keeps focus) and it auto-hides again - the countdown is re-armed at re-show | ✅ pass | 43.0s |
| `test_cursor_toolbar_rehides_after_back_reshow` | In cursor mode (TV) the same back-reshow cycle auto-hides again - the non-focusable cursor overlay must not prevent the re-arm | ✅ pass | 0.1s |
| `test_toolbar_disabled_at_zero` | A timeout of 0 disables the feature (the tool bar never auto-hides) | ✅ pass | 30.8s |
