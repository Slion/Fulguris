# Test run — Pi Compute Module 5 Rev 1.0 · landscape-0-sw584

- **When:** 2026-08-19T03:21:19+00:00
- **Device:** Raspberry Pi 5 TV box (Raspberry Pi Compute Module 5 Rev 1.0) — Android 16 (serial `192.168.178.67:5555`)
- **Config:** landscape, rotation 0°, smallest width 584dp
- **Package:** `net.slions.fulguris.full.download.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 36/39 passed in 1140.9s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_launch_focus_is_webview` | After a fresh launch, initial focus lands on the web view | ✅ pass | 15.5s |
| `test_unfocused_shows_label` | Unfocused address bar shows the page label, not the URL | ✅ pass | 18.0s |
| `test_directional_focus_is_navigation_not_edit` | D-pad focus enters navigation mode without showing the keyboard | ✅ pass | 17.3s |
| `test_navigation_shows_label_not_url` | Navigation focus keeps showing the label, not the URL | ✅ pass | 22.5s |
| `test_center_enters_edit_mode` | D-pad center/enter enters edit mode and shows the keyboard | ✅ pass | 15.7s |
| `test_edit_shows_url` | Edit mode shows the URL, not the label | ✅ pass | 18.3s |
| `test_dpad_edit_selects_all` | Entering edit via D-pad selects all, so typing replaces the URL | ✅ pass | 23.1s |
| `test_type_and_validate_navigates` | Typing a URL and pressing enter navigates and returns focus to the web view | ✅ pass | 24.0s |
| `test_back_two_stage_keyboard_then_cancel` | First back hides the keyboard, second back cancels back to the label | ✅ pass | 29.7s |
| `test_back_from_navigation_returns_to_web` | Back from navigation focus returns to the web view | ✅ pass | 21.6s |
| `test_down_from_navigation_returns_to_web` | D-pad down from navigation focus leaves the field for the web view | ✅ pass | 21.1s |
| `test_suggestions_navigable_without_touch` | Suggestions popup can be navigated and opened with D-pad only | ✅ pass | 29.6s |
| `test_touch_tap_enters_edit` | Touch tap on the field goes straight to edit mode with keyboard | ✅ pass | 19.7s |
| `test_retap_after_cancel_reenters_edit` | Tapping again after a cancel re-enters edit mode | ✅ pass | 22.6s |
| `test_pill_only_when_focused` | The focus pill is only drawn while the field is focused | ✅ pass | 20.5s |
| `test_https_shows_ssl_icon` | A valid HTTPS page shows the encrypted SSL icon | ✅ pass | 19.5s |
| `test_http_shows_off_icon` | A plain HTTP page shows the encryption-off SSL icon | ✅ pass | 18.3s |
| `test_invalid_https_shows_ssl_icon` | An expired HTTPS cert shows the SSL error icon (dialog dismissed) | ✅ pass | 24.0s |
| `test_unfocused_pill_outline_visible` | The unfocused address bar still shows a subtle pill outline | ✅ pass | 17.5s |
| `test_reload_button_hidden_after_load` | Reload/stop button stays hidden on a loaded scrollable page | ✅ pass | 92.5s |
| `test_stop_button_visible_during_load` | Stop button is visible while a fresh page is loading | ❌ fail | 29.9s |
| | _stop button was never visible during page load_ | | |
| `test_reload_button_hidden_after_reload` | Stop button reappears during a second load, then hides again | ❌ fail | 46.8s |
| | _stop button not shown during reload/second load_ | | |
| `test_stop_button_click_stops_load` | Tapping the stop button aborts the page load | ❌ fail | 27.0s |
| | _precondition: stop button visible while loading_ | | |
| `test_short_page_shows_reload_button` | On a short non-scrollable page the reload button stays visible | ✅ pass | 18.9s |
| `test_reload_button_tracks_tab_on_ctrl_tab` | CTRL+TAB tab switch updates the reload button to match the tab | ✅ pass | 60.3s |
| `test_reload_button_tracks_tab_via_tab_menu` | Tab switch via the tab list drawer updates the reload button | ✅ pass | 55.2s |
| `test_cursor_toggle_hotkey_shows_and_hides_overlay` | Long-press fast-forward toggles the cursor overlay on and off | ✅ pass | 27.1s |
| `test_cursor_toggle_exit_focuses_menu_button` | Exiting cursor mode moves focus to the toolbar menu button | ✅ pass | 26.6s |
| `test_cursor_movement_dpad_right_moves_right` | D-pad right moves the cursor right (click X increases) | ✅ pass | 28.5s |
| `test_cursor_movement_dpad_down_moves_down` | D-pad down moves the cursor down (click Y increases) | ✅ pass | 27.2s |
| `test_cursor_movement_edge_scrolls_page` | Pushing past the bottom edge scrolls the page | ✅ pass | 36.2s |
| `test_cursor_fade_hides_then_wakes` | The cursor fades out after the inactivity timeout and wakes on movement | ✅ pass | 31.7s |
| `test_cursor_click_hover_fires_mouseover` | Enabling the cursor fires a mouse hover on the page | ✅ pass | 24.7s |
| `test_cursor_click_activates_under_cursor` | Select press dispatches a click the page receives at the cursor | ✅ pass | 23.4s |
| `test_cursor_click_drag_target_seeks` | A cursor click seeks a drag-only scrub bar (down/move/up), like YouTube's timeline | ✅ pass | 26.3s |
| `test_cursor_menu_item_visible_on_leanback` | The Cursor main-menu item is shown on Android TV | ✅ pass | 24.1s |
| `test_cursor_menu_item_toggles_mode` | Tapping the Cursor menu item turns cursor mode on | ✅ pass | 29.5s |
| `test_cursor_fullscreen_click_reaches_custom_view` | In HTML5 fullscreen the cursor is visible and its click reaches the fullscreen view | ✅ pass | 30.1s |
| `test_cursor_media_play_pause` | The media play/pause key pauses and resumes the page video | ✅ pass | 33.2s |
