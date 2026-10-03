# Test run — VOG-L29 · portrait-0-sw360

- **When:** 2026-10-03T15:27:36+00:00
- **Device:** VOG-L29 (Huawei VOG-L29) — Android 10
- **Config:** portrait, rotation 0°, smallest width 360dp
- **Package:** `net.slions.fulguris.full.agent.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 21/21 passed in 110.9s (4 ran, 17 carried forward)

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_downloads_sheet_survives_active_download` | Opening the downloads sheet while a download is in progress must not crash the app (regression: NoSuchMethodError in ContentObserver, issue #802) | ✅ pass ⏸ | 34.2s |
| `test_no_launch_dialog_for_plain_site` | Typing a plain https site shows no 'Launch third-party app?' dialog (EMUI wildcard-authority regression, #542). | ✅ pass ⏸ | 94.6s |
| `test_launch_dialog_for_specialized_handler` | A Play Store details URL still shows the launch dialog listing the Play Store (the #542 host-matching fix must not over-filter real specialized handlers). | ✅ pass ⏸ | 19.7s |
| `test_unfocused_shows_label` | Unfocused address bar shows the page label, not the URL | ✅ pass ⏸ | 14.6s |
| `test_navigation_shows_label_not_url` | Navigation focus keeps showing the label, not the URL | ✅ pass ⏸ | 21.1s |
| `test_toolbar_label_refreshes_while_field_focused` | A live title change updates the label even while the field is navigation-focused (#694) | ✅ pass ⏸ | 27.4s |
| `test_view_cold_start_new_tab` | Cold start via an external ACTION_VIEW intent opens a new tab on top of the restored session (the issue #694 'link sent to a non-running app' path). | ✅ pass ⏸ | 15.3s |
| `test_view_warm_start_new_tab` | Warm start via ACTION_VIEW (onNewIntent) opens exactly one new tab. | ✅ pass ⏸ | 10.8s |
| `test_send_url_new_tab` | Sharing text that is a URL (ACTION_SEND) opens exactly one new tab. | ✅ pass ⏸ | 10.9s |
| `test_send_plain_text_no_new_tab` | Sharing plain text (no URL) fills the address bar and opens no new tab. | ✅ pass ⏸ | 10.9s |
| `test_web_search_new_tab` | A search-widget ACTION_WEB_SEARCH intent opens a new tab with the query. | ✅ pass ⏸ | 13.3s |
| `test_open_document_file_url` | Opening a local document (ACTION_VIEW file://, no host) opens a new tab. | ✅ pass ⏸ | 14.9s |
| `test_empty_tab_back_key_backgrounds_app` | With zero tabs open, the back key backgrounds the app instead of crashing or reopening a tab | ✅ pass | 8.8s |
| `test_smoke_launch` | The app launches and reaches the main browser UI in the foreground | ✅ pass ⏸ | 2.3s |
| `test_smoke_open_website` | Navigating to a web site loads and the address bar shows its label | ✅ pass ⏸ | 10.7s |
| `test_smoke_open_settings` | The settings activity opens via its component and renders its content | ✅ pass ⏸ | 6.8s |
| `test_smoke_background_app_switch` | KEYCODE_APP_SWITCH backgrounds the app; launching brings it back to the front | ✅ pass ⏸ | 6.0s |
| `test_smoke_background_home` | KEYCODE_HOME backgrounds the app; the activity intent brings it back to the front | ✅ pass ⏸ | 6.0s |
| `test_empty_tab_shows_logo` | Closing the last tab keeps the app alive on the empty state (large logo, no 'New tab' button); typing a URL in the field opens a fresh tab | ✅ pass | 25.5s |
| `test_empty_tab_menu_hides_tab_items` | With zero tabs the 'Web page' switcher stays reachable but the tab menu hides its tab-specific items; they reappear once a tab is open | ✅ pass | 49.0s |
| `test_empty_tab_tabs_button_creates_tab` | With zero tabs, tapping the tabs button creates a tab (the way back in) instead of opening an empty tab list | ✅ pass | 25.2s |
