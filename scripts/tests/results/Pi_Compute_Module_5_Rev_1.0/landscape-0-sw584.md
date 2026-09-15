# Test run — Pi Compute Module 5 Rev 1.0 · landscape-0-sw584

- **When:** 2026-09-15T08:00:17+00:00
- **Device:** Raspberry Pi 5 TV box (Raspberry Pi Compute Module 5 Rev 1.0) — Android 16
- **Config:** landscape, rotation 0°, smallest width 584dp
- **Package:** `net.slions.fulguris.full.agent.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 58/63 passed in 27.8s (1 ran, 62 carried forward)

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_cursor_context_menu_action_long_press` | Long-press the action key (select / DPAD center) opens the WebView context menu for the element under the cursor | ✅ pass ⏸ | 21.3s |
| `test_cursor_context_menu_repeated_long_press_touch_stays_clean` | Repeated long presses (same page) each deliver a fresh touch — the synthetic long press must not leave the WebView's touch state stuck | ❌ fail ⏸ | 24.1s |
| | _each of the 3 long presses should deliver a fresh touch (pointerdown) to the page, but only 2 did — the WebView's touch state is stuck (UP-after-cancel); log='pd0 ts2 pc506 ctx506 tc590 pd3145 ts3146 pc3659 ctx3659 tc3670 ctx6852'_ | | |
| `test_launch_focus_is_webview` | After a fresh launch, initial focus lands on the web view | ✅ pass ⏸ | 5.0s |
| `test_unfocused_shows_label` | Unfocused address bar shows the page label, not the URL | ✅ pass ⏸ | 9.9s |
| `test_directional_focus_is_navigation_not_edit` | D-pad focus enters navigation mode without showing the keyboard | ✅ pass ⏸ | 10.7s |
| `test_navigation_shows_label_not_url` | Navigation focus keeps showing the label, not the URL | ✅ pass ⏸ | 15.1s |
| `test_center_enters_edit_mode` | D-pad center/enter enters edit mode and shows the keyboard | ✅ pass ⏸ | 9.5s |
| `test_edit_shows_url` | Edit mode shows the URL, not the label | ✅ pass ⏸ | 11.5s |
| `test_dpad_edit_selects_all` | Entering edit via D-pad selects all, so typing replaces the URL | ✅ pass ⏸ | 15.3s |
| `test_type_and_validate_navigates` | Typing a URL and pressing enter navigates and returns focus to the web view | ✅ pass ⏸ | 20.7s |
| `test_back_two_stage_keyboard_then_cancel` | First back hides the keyboard, second back cancels back to the label | ✅ pass ⏸ | 25.6s |
| `test_back_from_navigation_returns_to_web` | Back from navigation focus returns to the web view | ✅ pass ⏸ | 18.7s |
| `test_down_from_navigation_returns_to_web` | D-pad down from navigation focus leaves the field for the web view | ✅ pass ⏸ | 18.7s |
| `test_suggestions_navigable_without_touch` | Suggestions popup can be navigated and opened with D-pad only | ✅ pass ⏸ | 22.9s |
| `test_touch_tap_enters_edit` | Touch tap on the field goes straight to edit mode with keyboard | ✅ pass ⏸ | 16.1s |
| `test_retap_after_cancel_reenters_edit` | Tapping again after a cancel re-enters edit mode | ✅ pass ⏸ | 18.5s |
| `test_pill_only_when_focused` | The focus pill is only drawn while the field is focused | ✅ pass ⏸ | 17.0s |
| `test_https_shows_ssl_icon` | A valid HTTPS page shows the encrypted SSL icon | ✅ pass ⏸ | 16.9s |
| `test_http_shows_off_icon` | A plain HTTP page shows the encryption-off SSL icon | ✅ pass ⏸ | 16.9s |
| `test_invalid_https_shows_ssl_icon` | An expired HTTPS cert shows the SSL error icon (dialog dismissed) | ✅ pass ⏸ | 21.6s |
| `test_unfocused_pill_outline_visible` | The unfocused address bar still shows a subtle pill outline | ✅ pass ⏸ | 15.2s |
| `test_reload_button_hidden_after_load` | Reload/stop button stays hidden on a loaded scrollable page | ✅ pass ⏸ | 84.9s |
| `test_stop_button_visible_during_load` | Stop button is visible while a fresh page is loading | ❌ fail ⏸ | 25.3s |
| | _stop button was never visible during page load_ | | |
| `test_reload_button_hidden_after_reload` | Stop button reappears during a second load, then hides again | ❌ fail ⏸ | 41.4s |
| | _stop button not shown during reload/second load_ | | |
| `test_stop_button_click_stops_load` | Tapping the stop button aborts the page load | ❌ fail ⏸ | 26.2s |
| | _precondition: stop button visible while loading_ | | |
| `test_short_page_shows_reload_button` | On a short non-scrollable page the reload button stays visible | ✅ pass ⏸ | 19.6s |
| `test_reload_button_tracks_tab_on_ctrl_tab` | CTRL+TAB tab switch updates the reload button to match the tab | ✅ pass ⏸ | 62.3s |
| `test_reload_button_tracks_tab_via_tab_menu` | Tab switch via the tab list drawer updates the reload button | ✅ pass ⏸ | 54.1s |
| `test_smoke_launch` | The app launches and reaches the main browser UI in the foreground | ✅ pass ⏸ | 7.3s |
| `test_smoke_open_website` | Navigating to a web site loads and the address bar shows its label | ✅ pass ⏸ | 15.2s |
| `test_smoke_open_settings` | The settings activity opens via its component and renders its content | ✅ pass ⏸ | 12.1s |
| `test_smoke_background_app_switch` | KEYCODE_APP_SWITCH backgrounds the app; launching brings it back to the front | ✅ pass ⏸ | 15.8s |
| `test_smoke_background_home` | KEYCODE_HOME backgrounds the app; the activity intent brings it back to the front | ✅ pass ⏸ | 15.7s |
| `test_cursor_toggle_hotkey_shows_and_hides_overlay` | Long-press play/pause toggles the cursor overlay on and off | ✅ pass ⏸ | 26.8s |
| `test_cursor_toggle_exit_focuses_menu_button` | Turning the cursor off moves focus to the toolbar menu button | ✅ pass ⏸ | 19.6s |
| `test_cursor_survives_options_sheet_dismiss` | Opening then dismissing the options bottom sheet does not leave the cursor suspended (resumes on sheet close) | ✅ pass ⏸ | 34.7s |
| `test_cursor_movement_dpad_right_moves_right` | D-pad right moves the cursor right (click X increases) | ✅ pass ⏸ | 22.6s |
| `test_cursor_movement_dpad_down_moves_down` | D-pad down moves the cursor down (click Y increases) | ✅ pass ⏸ | 20.9s |
| `test_cursor_movement_edge_scrolls_page` | Pushing past the bottom edge scrolls the page | ✅ pass ⏸ | 30.7s |
| `test_cursor_movement_gamepad_dpad_yields_to_focus_nav` | With the cursor on, a two-stick gamepad's D-pad is yielded to focus navigation (the right stick drives the cursor) while the stick-less D-pad still moves it | ✅ pass ⏸ | 1.1s |
| `test_cursor_fade_hides_then_wakes` | The cursor fades out after the inactivity timeout and wakes on movement | ✅ pass | 26.8s |
| `test_cursor_click_hover_fires_mouseover` | Enabling the cursor fires a mouse hover on the page | ✅ pass ⏸ | 19.7s |
| `test_cursor_click_activates_under_cursor` | Select press dispatches a click the page receives at the cursor | ✅ pass ⏸ | 16.1s |
| `test_cursor_click_drag_target_seeks` | A cursor click seeks a scrub bar via mousedown(mouse) or touch drag, like YouTube's timeline | ✅ pass ⏸ | 16.9s |
| `test_cursor_click_hesitant_press_still_clicks` | A realistically held (~600 ms) select press still clicks — only a deliberate ~1 s hold opens the context menu | ✅ pass ⏸ | 17.2s |
| `test_cursor_confirm_over_ui_activates_control_under_cursor` | With the cursor over a toolbar control, the confirm key (A / select) activates the control under the cursor, not the widget holding focus | ✅ pass ⏸ | 26.1s |
| `test_cursor_confirm_on_over_web_ignores_stray_focus` | With the cursor ON over the page and focus stranded on a toolbar widget, the confirm key (A / select) clicks the page under the cursor instead of activating the focused widget | ✅ pass ⏸ | 31.5s |
| `test_cursor_menu_item_visible_on_leanback` | The Cursor main-menu item is shown on Android TV | ✅ pass ⏸ | 23.7s |
| `test_cursor_menu_item_toggles_mode` | Tapping the Cursor menu item turns the cursor on | ✅ pass ⏸ | 23.4s |
| `test_cursor_fullscreen_click_reaches_custom_view` | In HTML5 fullscreen the cursor is visible and its click reaches the fullscreen view | ✅ pass ⏸ | 24.8s |
| `test_cursor_media_play_pause` | The media play/pause key pauses and resumes the page video | ✅ pass ⏸ | 27.4s |
| `test_cursor_wheel_ff_rewind_scrolls` | With the cursor on, fast-forward/rewind and the gamepad shoulder buttons (LB/RB) wheel-scroll the page up/down at the cursor | ✅ pass ⏸ | 28.7s |
| `test_cursor_youtube_scrubber_seek` | A cursor click seeks a YouTube-style auto-hiding scrubber (hover keeps controls alive, click seeks) | ✅ pass ⏸ | 29.4s |
| `test_cursor_youtube_scrubber_seek_after_idle` | Click seeks even after controls auto-hid (dispatchHover+delay re-shows them before BUTTON_PRESS lands) (leanback only) | ✅ pass ⏸ | 29.5s |
| `test_repeated_rotations_keep_page` | Forced portrait/landscape rotations keep the app on the same page without recreating the activity | ✅ pass ⏸ | 31.8s |
| `test_configuration_bottom_sheet_opens` | The OPEN_CONFIGURATION intent opens the configuration bottom sheet (regression: ClassCastException in setDefaultIfNeeded) | ✅ pass ⏸ | 10.2s |
| `test_toolbar_hides_after_timeout` | With a 10 s timeout the tool bar auto-hides ~10 s after the page finishes loading | ✅ pass ⏸ | 30.9s |
| `test_toolbar_not_starved_on_busy_page` | A busy page that keeps firing tab-state callbacks (theme-color changes) does not starve the countdown - the tool bar still hides ~timeout s after load | ✅ pass ⏸ | 28.4s |
| `test_toolbar_not_reset_by_interaction` | A D-pad press after load does not reset the countdown (it stays anchored at load) | ✅ pass ⏸ | 24.0s |
| `test_toolbar_rearms_on_focus_gain` | After a first auto-hide, regaining web-view input focus restarts the countdown | ❌ fail ⏸ | 44.2s |
| | _tool bar hid 12.31 s after the re-arm (expected ~10 s)_ | | |
| `test_toolbar_rehides_after_back_reshow` | After an auto-hide, back re-shows the tool bar (web view keeps focus) and it auto-hides again - the countdown is re-armed at re-show | ✅ pass ⏸ | 39.9s |
| `test_cursor_toolbar_rehides_after_back_reshow` | With the cursor on (TV) the same back-reshow cycle auto-hides again - the non-focusable cursor overlay must not prevent the re-arm | ✅ pass ⏸ | 41.6s |
| `test_toolbar_disabled_at_zero` | A timeout of 0 disables the feature (the tool bar never auto-hides) | ✅ pass ⏸ | 24.0s |
