# Test run — Pi Compute Module 5 Rev 1.0 · landscape-0-sw584

- **When:** 2026-08-21T20:07:11+00:00
- **Device:** Raspberry Pi 5 TV box (Raspberry Pi Compute Module 5 Rev 1.0) — Android 16 (serial `192.168.178.67:5555`)
- **Config:** landscape, rotation 0°, smallest width 584dp
- **Package:** `net.slions.fulguris.full.download.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 16/16 passed in 378.8s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_cursor_toggle_hotkey_shows_and_hides_overlay` | Long-press play/pause toggles the cursor overlay on and off | ✅ pass | 21.3s |
| `test_cursor_toggle_exit_focuses_menu_button` | Exiting cursor mode moves focus to the toolbar menu button | ✅ pass | 20.8s |
| `test_cursor_movement_dpad_right_moves_right` | D-pad right moves the cursor right (click X increases) | ✅ pass | 22.1s |
| `test_cursor_movement_dpad_down_moves_down` | D-pad down moves the cursor down (click Y increases) | ✅ pass | 21.2s |
| `test_cursor_movement_edge_scrolls_page` | Pushing past the bottom edge scrolls the page | ✅ pass | 30.6s |
| `test_cursor_fade_hides_then_wakes` | The cursor fades out after the inactivity timeout and wakes on movement | ✅ pass | 25.9s |
| `test_cursor_click_hover_fires_mouseover` | Enabling the cursor fires a mouse hover on the page | ✅ pass | 17.8s |
| `test_cursor_click_activates_under_cursor` | Select press dispatches a click the page receives at the cursor | ✅ pass | 17.3s |
| `test_cursor_click_drag_target_seeks` | A cursor click seeks a scrub bar via mousedown(mouse) or touch drag, like YouTube's timeline | ✅ pass | 17.2s |
| `test_cursor_menu_item_visible_on_leanback` | The Cursor main-menu item is shown on Android TV | ✅ pass | 17.4s |
| `test_cursor_menu_item_toggles_mode` | Tapping the Cursor menu item turns cursor mode on | ✅ pass | 23.8s |
| `test_cursor_fullscreen_click_reaches_custom_view` | In HTML5 fullscreen the cursor is visible and its click reaches the fullscreen view | ✅ pass | 24.9s |
| `test_cursor_media_play_pause` | The media play/pause key pauses and resumes the page video | ✅ pass | 21.6s |
| `test_cursor_wheel_ff_rewind_scrolls` | In cursor mode fast-forward/rewind wheel-scroll the page up/down at the cursor | ✅ pass | 21.9s |
| `test_cursor_youtube_scrubber_seek` | A cursor click seeks a YouTube-style auto-hiding scrubber (hover keeps controls alive, click seeks) | ✅ pass | 26.1s |
| `test_cursor_youtube_scrubber_seek_after_idle` | Click seeks even after controls auto-hid (dispatchHover+delay re-shows them before BUTTON_PRESS lands) (leanback only) | ✅ pass | 33.4s |
