# Test run — VOG-L29 · portrait-0-sw360

- **When:** 2026-10-02T13:17:16+00:00
- **Device:** VOG-L29 (Huawei VOG-L29) — Android 10
- **Config:** portrait, rotation 0°, smallest width 360dp
- **Package:** `net.slions.fulguris.full.agent.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=launch_dialog
- **Result:** 3/3 passed in 115.5s (2 ran, 1 carried forward)

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_downloads_sheet_survives_active_download` | Opening the downloads sheet while a download is in progress must not crash the app (regression: NoSuchMethodError in ContentObserver, issue #802) | ✅ pass ⏸ | 34.2s |
| `test_no_launch_dialog_for_plain_site` | Typing a plain https site shows no 'Launch third-party app?' dialog (EMUI wildcard-authority regression, #542). | ✅ pass | 94.6s |
| `test_launch_dialog_for_specialized_handler` | A Play Store details URL still shows the launch dialog listing the Play Store (the #542 host-matching fix must not over-filter real specialized handlers). | ✅ pass | 19.7s |
