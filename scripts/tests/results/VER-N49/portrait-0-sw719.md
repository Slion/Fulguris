# Test run — VER-N49 · portrait-0-sw719

- **When:** 2026-09-30T22:03:51+00:00
- **Device:** VER-N49 (Honor VER-N49) — Android 16
- **Config:** portrait, rotation 0°, smallest width 719dp
- **Package:** `net.slions.fulguris.full.agent.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 5/5 passed in 33.3s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_smoke_launch` | The app launches and reaches the main browser UI in the foreground | ✅ pass | 2.5s |
| `test_smoke_open_website` | Navigating to a web site loads and the address bar shows its label | ✅ pass | 10.6s |
| `test_smoke_open_settings` | The settings activity opens via its component and renders its content | ✅ pass | 7.5s |
| `test_smoke_background_app_switch` | KEYCODE_APP_SWITCH backgrounds the app; launching brings it back to the front | ✅ pass | 5.9s |
| `test_smoke_background_home` | KEYCODE_HOME backgrounds the app; the activity intent brings it back to the front | ✅ pass | 5.9s |
