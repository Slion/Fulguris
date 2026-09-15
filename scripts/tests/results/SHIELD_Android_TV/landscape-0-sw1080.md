# Test run — SHIELD Android TV · landscape-0-sw1080

- **When:** 2026-08-24T00:51:06+00:00
- **Device:** SHIELD Android TV (Nvidia SHIELD Android TV) — Android 11
- **Config:** landscape, rotation 0°, smallest width 1080dp
- **Package:** `net.slions.fulguris.full.download.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=scrubber_seek_after_idle
- **Result:** 1/1 passed in 85.8s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_cursor_youtube_scrubber_seek_after_idle` | Click seeks even after controls auto-hid (dispatchHover+delay re-shows them before BUTTON_PRESS lands) (leanback only) | ✅ pass | 84.2s |
