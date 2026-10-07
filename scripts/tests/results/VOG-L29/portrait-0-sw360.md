# Test run — VOG-L29 · portrait-0-sw360

- **When:** 2026-10-07T06:30:11+00:00
- **Device:** VOG-L29 (Huawei VOG-L29) — Android 10
- **Config:** portrait, rotation 0°, smallest width 360dp
- **Package:** `net.slions.fulguris.full.agent.debug`
- **Options:** restart=True, keep_tabs=False, orientation=default, filter=import
- **Result:** 86/110 passed in 486.6s (3 ran, 107 carried forward)

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_downloads_sheet_survives_active_download` | Opening the downloads sheet while a download is in progress must not crash the app (regression: NoSuchMethodError in ContentObserver, issue #802) | ✅ pass ⏸ | 31.5s |
| `test_no_launch_dialog_for_plain_site` | Typing a plain https site shows no 'Launch third-party app?' dialog (EMUI wildcard-authority regression, #542). | ❌ fail ⏸ | 112.5s |
| | _Page did not load: address field is ''_ | | |
| `test_launch_dialog_for_specialized_handler` | A Play Store details URL still shows the launch dialog listing the Play Store (the #542 host-matching fix must not over-filter real specialized handlers). | ✅ pass ⏸ | 19.6s |
| `test_unfocused_shows_label` | Unfocused address bar shows the page label, not the URL | ✅ pass ⏸ | 10.1s |
| `test_navigation_shows_label_not_url` | Navigation focus keeps showing the label, not the URL | ✅ pass ⏸ | 24.3s |
| `test_toolbar_label_refreshes_while_field_focused` | A live title change updates the label even while the field is navigation-focused (#694) | ❌ fail ⏸ | 35.2s |
| | _navigation focus should show the current label, got 'LBL-END'_ | | |
| `test_view_cold_start_new_tab` | Cold start via an external ACTION_VIEW intent opens a new tab on top of the restored session (the issue #694 'link sent to a non-running app' path). | ✅ pass ⏸ | 14.8s |
| `test_view_warm_start_new_tab` | Warm start via ACTION_VIEW (onNewIntent) opens exactly one new tab. | ✅ pass ⏸ | 9.7s |
| `test_send_url_new_tab` | Sharing text that is a URL (ACTION_SEND) opens exactly one new tab. | ✅ pass ⏸ | 10.9s |
| `test_send_plain_text_no_new_tab` | Sharing plain text (no URL) fills the address bar and opens no new tab. | ✅ pass ⏸ | 10.8s |
| `test_web_search_new_tab` | A search-widget ACTION_WEB_SEARCH intent opens a new tab with the query. | ✅ pass ⏸ | 13.3s |
| `test_open_document_file_url` | Opening a local document (ACTION_VIEW file://, no host) opens a new tab. | ✅ pass ⏸ | 15.0s |
| `test_empty_tab_back_key_backgrounds_app` | With zero tabs open, the back key backgrounds the app instead of crashing or reopening a tab | ✅ pass ⏸ | 8.8s |
| `test_smoke_launch` | The app launches and reaches the main browser UI in the foreground | ✅ pass ⏸ | 2.4s |
| `test_smoke_open_website` | Navigating to a web site loads and the address bar shows its label | ✅ pass ⏸ | 10.2s |
| `test_smoke_open_settings` | The settings activity opens via its component and renders its content | ✅ pass ⏸ | 6.8s |
| `test_smoke_background_app_switch` | KEYCODE_APP_SWITCH backgrounds the app; launching brings it back to the front | ✅ pass ⏸ | 6.1s |
| `test_smoke_background_home` | KEYCODE_HOME backgrounds the app; the activity intent brings it back to the front | ✅ pass ⏸ | 6.0s |
| `test_empty_tab_shows_logo` | Closing the last tab keeps the app alive on the empty state (large logo, no 'New tab' button); typing a URL in the field opens a fresh tab | ✅ pass ⏸ | 32.5s |
| `test_empty_tab_menu_hides_tab_items` | With zero tabs the 'Web page' switcher stays reachable but the tab menu hides its tab-specific items; they reappear once a tab is open | ✅ pass ⏸ | 49.0s |
| `test_empty_tab_tabs_button_creates_tab` | With zero tabs, tapping the tabs button creates a tab (the way back in) instead of opening an empty tab list | ✅ pass ⏸ | 25.1s |
| `test_launch_focus_is_webview` | After a fresh launch, initial focus lands on the web view | ✅ pass ⏸ | 4.8s |
| `test_directional_focus_is_navigation_not_edit` | D-pad focus enters navigation mode without showing the keyboard | ✅ pass ⏸ | 19.5s |
| `test_center_enters_edit_mode` | D-pad center/enter enters edit mode and shows the keyboard | ✅ pass ⏸ | 24.2s |
| `test_edit_shows_url` | Edit mode shows the URL, not the label | ✅ pass ⏸ | 17.7s |
| `test_dpad_edit_selects_all` | Entering edit via D-pad selects all, so typing replaces the URL | ✅ pass ⏸ | 17.6s |
| `test_type_and_validate_navigates` | Typing a URL and pressing enter navigates and returns focus to the web view | ✅ pass ⏸ | 29.3s |
| `test_back_two_stage_keyboard_then_cancel` | First back hides the keyboard, second back cancels back to the label | ❌ fail ⏸ | 18.5s |
| | _precondition: keyboard shown while editing_ | | |
| `test_back_from_navigation_returns_to_web` | Back from navigation focus returns to the web view | ✅ pass ⏸ | 22.3s |
| `test_down_from_navigation_returns_to_web` | D-pad down from navigation focus leaves the field for the web view | ✅ pass ⏸ | 13.9s |
| `test_suggestions_navigable_without_touch` | Suggestions popup can be navigated and opened with D-pad only | ❌ fail ⏸ | 33.9s |
| | _opening a suggestion should navigate and focus the web view_ | | |
| `test_touch_tap_enters_edit` | Touch tap on the field goes straight to edit mode with keyboard | ✅ pass ⏸ | 21.5s |
| `test_retap_after_cancel_reenters_edit` | Tapping again after a cancel re-enters edit mode | ✅ pass ⏸ | 27.7s |
| `test_pill_only_when_focused` | The focus pill is only drawn while the field is focused | ✅ pass ⏸ | 20.4s |
| `test_https_shows_ssl_icon` | A valid HTTPS page shows the encrypted SSL icon | ✅ pass ⏸ | 15.8s |
| `test_http_shows_off_icon` | A plain HTTP page shows the encryption-off SSL icon | ✅ pass ⏸ | 12.2s |
| `test_invalid_https_shows_ssl_icon` | An expired HTTPS cert shows the SSL error icon (dialog dismissed) | ✅ pass ⏸ | 18.8s |
| `test_unfocused_pill_outline_visible` | The unfocused address bar still shows a subtle pill outline | ✅ pass ⏸ | 11.4s |
| `test_reload_button_hidden_after_load` | Reload/stop button stays hidden on a loaded scrollable page | ✅ pass ⏸ | 17.4s |
| `test_stop_button_visible_during_load` | Stop button is visible while a fresh page is loading | ✅ pass ⏸ | 8.3s |
| `test_reload_button_hidden_after_reload` | Stop button reappears during a second load, then hides again | ✅ pass ⏸ | 17.6s |
| `test_stop_button_click_stops_load` | Tapping the stop button aborts the page load | ✅ pass ⏸ | 16.4s |
| `test_short_page_shows_reload_button` | On a short non-scrollable page the reload button stays visible | ❌ fail ⏸ | 36.9s |
| | _short page should keep the reload button visible, got GONE_ | | |
| `test_reload_button_tracks_tab_on_ctrl_tab` | CTRL+TAB tab switch updates the reload button to match the tab | ❌ fail ⏸ | 45.2s |
| | _tab B (short) should show the button_ | | |
| `test_reload_button_tracks_tab_via_tab_menu` | Tab switch via the tab list drawer updates the reload button | ❌ fail ⏸ | 45.2s |
| | _short tab should show the button_ | | |
| `test_cursor_toggle_hotkey_shows_and_hides_overlay` | Long-press play/pause toggles the cursor overlay on and off | ✅ pass ⏸ | 23.0s |
| `test_cursor_toggle_exit_focuses_menu_button` | Turning the cursor off moves focus to the toolbar menu button | ✅ pass ⏸ | 20.5s |
| `test_cursor_survives_options_sheet_dismiss` | Opening then dismissing the options bottom sheet does not leave the cursor suspended (resumes on sheet close) | ✅ pass ⏸ | 34.8s |
| `test_cursor_movement_dpad_right_moves_right` | D-pad right moves the cursor right (click X increases) | ✅ pass ⏸ | 25.1s |
| `test_cursor_movement_dpad_down_moves_down` | D-pad down moves the cursor down (click Y increases) | ✅ pass ⏸ | 23.1s |
| `test_cursor_movement_edge_scrolls_page` | Pushing past the bottom edge scrolls the page | ✅ pass ⏸ | 73.4s |
| `test_cursor_movement_gamepad_dpad_yields_to_focus_nav` | With the cursor on, a two-stick gamepad's D-pad is yielded to focus navigation (the right stick drives the cursor) while the stick-less D-pad still moves it | ✅ pass ⏸ | 1.4s |
| `test_cursor_fade_hides_then_wakes` | The cursor fades out after the inactivity timeout and wakes on movement | ❌ fail ⏸ | 22.2s |
| | _cursor should fade out after the inactivity timeout_ | | |
| `test_cursor_click_hover_fires_mouseover` | Enabling the cursor fires a mouse hover on the page | ❌ fail ⏸ | 21.0s |
| | _enabling cursor should fire a mouse hover on the page, title was 'start'_ | | |
| `test_cursor_click_activates_under_cursor` | Select press dispatches a click the page receives at the cursor | ✅ pass ⏸ | 19.4s |
| `test_cursor_click_drag_target_seeks` | A cursor click seeks a scrub bar via mousedown(mouse) or touch drag, like YouTube's timeline | ✅ pass ⏸ | 18.8s |
| `test_cursor_click_hesitant_press_still_clicks` | A realistically held (~600 ms) select press still clicks — only a deliberate ~1 s hold opens the context menu | ❌ fail ⏸ | 16.8s |
| | _a 600 ms held select press is still a click and must land on the page, title was 'hover'_ | | |
| `test_cursor_confirm_over_ui_activates_control_under_cursor` | With the cursor over a toolbar control, the confirm key (A / select) activates the control under the cursor, not the widget holding focus | ✅ pass ⏸ | 27.2s |
| `test_cursor_confirm_on_over_web_ignores_stray_focus` | With the cursor ON over the page and focus stranded on a toolbar widget, the confirm key (A / select) clicks the page under the cursor instead of activating the focused widget | ❌ fail ⏸ | 25.1s |
| | _enabling the cursor must not move focus; focus should still be on button_more, was ''_ | | |
| `test_cursor_menu_item_visible_on_leanback` | The Cursor main-menu item is shown on Android TV | ✅ pass ⏸ | 12.4s |
| `test_cursor_menu_item_toggles_mode` | Tapping the Cursor menu item turns the cursor on | ✅ pass ⏸ | 12.2s |
| `test_cursor_fullscreen_click_reaches_custom_view` | In HTML5 fullscreen the cursor is visible and its click reaches the fullscreen view | ✅ pass ⏸ | 29.1s |
| `test_cursor_media_play_pause` | The media play/pause key pauses and resumes the page video | ❌ fail ⏸ | 42.7s |
| | _the test video should autoplay, title was ''_ | | |
| `test_cursor_wheel_ff_rewind_scrolls` | With the cursor on, fast-forward/rewind and the gamepad shoulder buttons (LB/RB) wheel-scroll the page up/down at the cursor | ✅ pass ⏸ | 29.5s |
| `test_cursor_youtube_scrubber_seek` | A cursor click seeks a YouTube-style auto-hiding scrubber (hover keeps controls alive, click seeks) | ❌ fail ⏸ | 19.1s |
| | _cursor hover should wake the auto-hiding player controls, title was 'ctrl-hidden'_ | | |
| `test_cursor_youtube_scrubber_seek_after_idle` | Click seeks even after controls auto-hid (dispatchHover+delay re-shows them before BUTTON_PRESS lands) (leanback only) | ✅ pass ⏸ | 0.0s |
| `test_cursor_context_menu_action_long_press` | Long-press the action key (select / DPAD center) opens the WebView context menu for the element under the cursor | ❌ fail ⏸ | 18.7s |
| | _action-key long press should open the link context menu (a node should carry the link URL), node texts were ['start', 'Context menu link target']_ | | |
| `test_cursor_context_menu_repeated_long_press_touch_stays_clean` | Repeated long presses (same page) each deliver a fresh touch — the synthetic long press must not leave the WebView's touch state stuck | ❌ fail ⏸ | 24.0s |
| | _each of the 3 long presses should deliver a fresh touch (pointerdown) to the page, but only 0 did — the WebView's touch state is stuck (UP-after-cancel); log='Web browser - Wikipedia'_ | | |
| `test_repeated_rotations_keep_page` | Forced portrait/landscape rotations keep the app on the same page without recreating the activity | ✅ pass ⏸ | 26.7s |
| `test_configuration_bottom_sheet_opens` | The OPEN_CONFIGURATION intent opens the configuration bottom sheet (regression: ClassCastException in setDefaultIfNeeded) | ✅ pass ⏸ | 10.3s |
| `test_toolbar_hides_after_timeout` | With a 10 s timeout the tool bar auto-hides ~10 s after the page finishes loading | ❌ fail ⏸ | 23.6s |
| | _tool bar hid 6.46 s after load (expected ~10 s)_ | | |
| `test_toolbar_not_starved_on_busy_page` | A busy page that keeps firing tab-state callbacks (theme-color changes) does not starve the countdown - the tool bar still hides ~timeout s after load | ✅ pass ⏸ | 20.7s |
| `test_toolbar_not_reset_by_interaction` | A D-pad press after load does not reset the countdown (it stays anchored at load) | ❌ fail ⏸ | 23.6s |
| | _tool bar hid 1.54 s after the press (6.48 s after load); expected the countdown to stay anchored at load (~10 s after load)_ | | |
| `test_toolbar_rearms_on_focus_gain` | After a first auto-hide, regaining web-view input focus restarts the countdown | ❌ fail ⏸ | 34.9s |
| | _tool bar hid 4.42 s after the re-arm (expected ~10 s)_ | | |
| `test_toolbar_rehides_after_back_reshow` | After an auto-hide, back re-shows the tool bar (web view keeps focus) and it auto-hides again - the countdown is re-armed at re-show | ✅ pass ⏸ | 32.1s |
| `test_cursor_toolbar_rehides_after_back_reshow` | With the cursor on (TV) the same back-reshow cycle auto-hides again - the non-focusable cursor overlay must not prevent the re-arm | ✅ pass ⏸ | 0.0s |
| `test_toolbar_disabled_at_zero` | A timeout of 0 disables the feature (the tool bar never auto-hides) | ✅ pass ⏸ | 24.5s |
| `test_back_two_tabs_keeps_foreground` | Back with a tab to close closes the top tab, not finish the app (regression: predictive back bypassing onBackPressed) | ❌ fail ⏸ | 34.5s |
| | _page A did not load (field='Example Domain')_ | | |
| `test_back_last_tab_stays_foreground` | Back on the last tab closes it (start page), not finish the app | ✅ pass ⏸ | 18.8s |
| `test_back_editing_two_stage_exit` | Back while editing the URL field: first press hides the keyboard, second press cancels back to the label | ✅ pass ⏸ | 34.2s |
| `test_settings_back_from_nested_no_crash` | Back from a nested settings screen (Appearance > Portrait) pops the nested screen back to the parent and keeps back usable until it exits, without crashing or getting stuck | ✅ pass ⏸ | 34.7s |
| `test_download_link_attr_filename` | <a download='name.txt'> link, no Content-Disposition: dialog + row + file use the download-attribute filename, not the URL basename | ✅ pass ⏸ | 71.3s |
| `test_download_link_js_anchor_filename` | JS-created <a download> anchor (a.click()), no Content-Disposition: the attribute filename is honored | ✅ pass ⏸ | 67.9s |
| `test_download_link_content_disposition_filename` | Content-Disposition: attachment link, no download attribute: the server's filename is honored (baseline) | ✅ pass ⏸ | 71.8s |
| `test_downloads_sheet_empty_state` | A fresh downloads sheet shows the empty state; only 'Open folder' is enabled | ✅ pass ⏸ | 59.5s |
| `test_downloads_dialog_and_completion` | File-URL download: dialog copy + known size, in-flight row (percentage + 'bytes / total' + speed), completed row (size), file on disk | ❌ fail ⏸ | 47.4s |
| | _row should show 'bytes / 15 MB' while running, got '23% • 200 kB/s\n3,59 MB / 15,00 MB'_ | | |
| `test_downloads_in_progress_row_states` | While a download runs, the row shows 'NN%' and the '• speed' sample; cancelling removes the row and the partial file | ❌ fail ⏸ | 130.8s |
| | _completed row should report its size ('15 MB'), got summary '15,00 MB\n7 Oct 2026, 02:24'_ | | |
| `test_downloads_failure_mid_download` | A stream that dies mid-download ends in an 'Error: 0' row / 'Download failed' snackbar (a 404 can't be used: no dialog is shown for error responses) | ✅ pass ⏸ | 90.2s |
| `test_downloads_clean_up_failed` | 'Clean up' removes the failed/orphaned entries only | ✅ pass ⏸ | 45.1s |
| `test_downloads_cancel_running` | 'Cancel download' on a running download removes the row and the partial file | ✅ pass ⏸ | 85.5s |
| `test_downloads_delete_file_orphans_entry` | 'Delete file' deletes the file, keeps the entry, and orphans it ('File not found') | ❌ fail ⏸ | 61.2s |
| | _no 'Delete file' option_ | | |
| `test_downloads_remove_and_keep` | 'Remove and keep' deletes the entry but keeps the file on disk | ❌ fail ⏸ | 118.6s |
| | _no 'Remove and keep' option_ | | |
| `test_downloads_remove_and_delete` | 'Remove and delete' deletes both the entry and the file | ✅ pass ⏸ | 129.1s |
| `test_downloads_remove_all_keeps_files` | 'Remove all' clears every entry but keeps the files on disk | ❌ fail ⏸ | 74.6s |
| | _no confirm dialog_ | | |
| `test_downloads_delete_all` | 'Delete all' clears every entry and deletes the files | ✅ pass ⏸ | 95.9s |
| `test_downloads_same_url_twice_creates_second_file` | Downloading the same URL twice creates two entries; the system re-names the file '-1' | ✅ pass ⏸ | 112.5s |
| `test_bookmarks_drawer_opens_and_closes` | The main menu opens the bookmarks drawer; back closes it | ✅ pass ⏸ | 38.3s |
| `test_bookmarks_add_root` | Ctrl+B 'Add bookmark' saves a bookmark at root; the drawer shows it | ✅ pass ⏸ | 141.0s |
| `test_bookmarks_add_duplicate_guard` | Re-adding the same URL shows 'Bookmark already exists.' and adds nothing | ✅ pass ⏸ | 148.7s |
| `test_bookmarks_add_in_folder` | Typing a new folder name in the add dialog creates the folder and entry; the folder opens with a '..' parent row | ✅ pass ⏸ | 165.1s |
| `test_bookmarks_edit_title_and_url` | The bookmark context menu's 'Edit bookmark' changes title and URL | ✅ pass ⏸ | 173.2s |
| `test_bookmarks_remove_no_confirmation` | 'Remove bookmark' deletes the entry immediately (no confirmation dialog) | ✅ pass ⏸ | 131.7s |
| `test_bookmarks_rename_folder` | The folder context menu's 'Rename folder' renames the folder | ✅ pass ⏸ | 73.6s |
| `test_bookmarks_export_file_and_content` | Export writes a FulgurisBookmarks-*.html to Downloads (SAF save dialog + snackbar) with the Netscape header, entries, nested folder and HTML escaping | ✅ pass ⏸ | 156.0s |
| `test_bookmarks_remove_folder_moves_to_root` | 'Remove folder' deletes nothing: its entries move up to the root | ✅ pass ⏸ | 65.8s |
| `test_bookmarks_import_creates_entries` | Import picks the pushed Netscape file and creates the entries + folder ('N Bookmarks were imported') | ✅ pass | 247.2s |
| `test_bookmarks_import_skips_duplicates` | Re-importing an already-imported file imports only the missing entries | ✅ pass | 144.1s |
| `test_bookmarks_import_malformed_file_errors` | Importing an HTML file with no bookmark list shows the error dialog | ✅ pass | 95.3s |
| `test_bookmarks_reset_deletes_all` | Reset shows the 'Delete all bookmarks?' dialog and clears everything; the subsequent export is header-only (runs last) | ✅ pass ⏸ | 196.8s |
| `test_in_tab_back_not_confused_with_field_edit` | Back with in-page history and an unfocused address field still steps the page history (the two-stage edit exit does not swallow it) | ✅ pass ⏸ | 22.5s |
