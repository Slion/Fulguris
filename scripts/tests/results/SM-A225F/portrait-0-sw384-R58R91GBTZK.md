# Test run — SM-A225F · portrait-0-sw384

- **When:** 2026-08-22T11:07:22+00:00
- **Device:** Galaxy A22 5G (Samsung SM-A225F) — Android 13 (serial `R58R91GBTZK`)
- **Config:** portrait, rotation 0°, smallest width 384dp
- **Package:** `net.slions.fulguris.full.download.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 5/5 passed in 39.4s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_smoke_launch` | The app launches and reaches the main browser UI in the foreground | ✅ pass | 2.5s |
| `test_smoke_open_website` | Navigating to a web site loads and the address bar shows its label | ✅ pass | 15.6s |
| `test_smoke_open_settings` | The settings activity opens via its component and renders its content | ✅ pass | 7.7s |
| `test_smoke_background_app_switch` | KEYCODE_APP_SWITCH backgrounds the app; launching brings it back to the front | ✅ pass | 6.0s |
| `test_smoke_background_home` | KEYCODE_HOME backgrounds the app; the activity intent brings it back to the front | ✅ pass | 6.0s |
