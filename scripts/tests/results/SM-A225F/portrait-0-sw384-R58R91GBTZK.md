# Test run — SM-A225F · portrait-0-sw384

- **When:** 2026-08-18T23:19:22+00:00
- **Device:** Galaxy A22 5G (Samsung SM-A225F) — Android 13 (serial `R58R91GBTZK`)
- **Config:** portrait, rotation 0°, smallest width 384dp
- **Package:** `net.slions.fulguris.full.download.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 35/35 passed in 697.4s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_launch_focus_is_webview` | After a fresh launch, initial focus lands on the web view | ✅ pass | 8.0s |
| `test_unfocused_shows_label` | Unfocused address bar shows the page label, not the URL | ✅ pass | 14.9s |
| `test_directional_focus_is_navigation_not_edit` | D-pad focus enters navigation mode without showing the keyboard | ✅ pass | 13.6s |
| `test_navigation_shows_label_not_url` | Navigation focus keeps showing the label, not the URL | ✅ pass | 18.2s |
| `test_center_enters_edit_mode` | D-pad center/enter enters edit mode and shows the keyboard | ✅ pass | 11.9s |
| `test_edit_shows_url` | Edit mode shows the URL, not the label | ✅ pass | 14.2s |
| `test_dpad_edit_selects_all` | Entering edit via D-pad selects all, so typing replaces the URL | ✅ pass | 17.0s |
| `test_type_and_validate_navigates` | Typing a URL and pressing enter navigates and returns focus to the web view | ✅ pass | 18.0s |
| `test_back_two_stage_keyboard_then_cancel` | First back hides the keyboard, second back cancels back to the label | ✅ pass | 23.7s |
| `test_back_from_navigation_returns_to_web` | Back from navigation focus returns to the web view | ✅ pass | 15.6s |
| `test_down_from_navigation_returns_to_web` | D-pad down from navigation focus leaves the field for the web view | ✅ pass | 15.4s |
| `test_suggestions_navigable_without_touch` | Suggestions popup can be navigated and opened with D-pad only | ✅ pass | 20.2s |
| `test_touch_tap_enters_edit` | Touch tap on the field goes straight to edit mode with keyboard | ✅ pass | 12.2s |
| `test_retap_after_cancel_reenters_edit` | Tapping again after a cancel re-enters edit mode | ✅ pass | 15.5s |
| `test_pill_only_when_focused` | The focus pill is only drawn while the field is focused | ✅ pass | 14.4s |
| `test_https_shows_ssl_icon` | A valid HTTPS page shows the encrypted SSL icon | ✅ pass | 13.6s |
| `test_http_shows_off_icon` | A plain HTTP page shows the encryption-off SSL icon | ✅ pass | 13.5s |
| `test_invalid_https_shows_ssl_icon` | An expired HTTPS cert shows the SSL error icon (dialog dismissed) | ✅ pass | 18.6s |
| `test_unfocused_pill_outline_visible` | The unfocused address bar still shows a subtle pill outline | ✅ pass | 11.7s |
| `test_reload_button_hidden_after_load` | Reload/stop button stays hidden on a loaded scrollable page | ✅ pass | 19.4s |
| `test_stop_button_visible_during_load` | Stop button is visible while a fresh page is loading | ✅ pass | 11.8s |
| `test_reload_button_hidden_after_reload` | Stop button reappears during a second load, then hides again | ✅ pass | 22.0s |
| `test_stop_button_click_stops_load` | Tapping the stop button aborts the page load | ✅ pass | 17.9s |
| `test_short_page_shows_reload_button` | On a short non-scrollable page the reload button stays visible | ✅ pass | 17.1s |
| `test_reload_button_tracks_tab_on_ctrl_tab` | CTRL+TAB tab switch updates the reload button to match the tab | ✅ pass | 32.9s |
| `test_reload_button_tracks_tab_via_tab_menu` | Tab switch via the tab list drawer updates the reload button | ✅ pass | 32.9s |
| `test_cursor_toggle_hotkey_shows_and_hides_overlay` | Long-press fast-forward toggles the cursor overlay on and off | ✅ pass | 27.2s |
| `test_cursor_toggle_exit_focuses_menu_button` | Exiting cursor mode moves focus to the toolbar menu button | ✅ pass | 26.9s |
| `test_cursor_movement_dpad_right_moves_right` | D-pad right moves the cursor right (click X increases) | ✅ pass | 27.8s |
| `test_cursor_movement_dpad_down_moves_down` | D-pad down moves the cursor down (click Y increases) | ✅ pass | 27.4s |
| `test_cursor_movement_edge_scrolls_page` | Pushing past the bottom edge scrolls the page | ✅ pass | 27.7s |
| `test_cursor_click_hover_fires_mouseover` | Enabling the cursor fires a mouse hover on the page | ✅ pass | 24.6s |
| `test_cursor_click_activates_under_cursor` | Select press dispatches a click the page receives at the cursor | ✅ pass | 23.3s |
| `test_cursor_menu_item_visible_on_leanback` | The Cursor main-menu item is shown on Android TV | ✅ pass | 16.8s |
| `test_cursor_menu_item_toggles_mode` | Tapping the Cursor menu item turns cursor mode on | ✅ pass | 16.7s |
