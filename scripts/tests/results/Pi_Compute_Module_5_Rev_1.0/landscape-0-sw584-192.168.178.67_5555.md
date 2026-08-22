# Test run — Pi Compute Module 5 Rev 1.0 · landscape-0-sw584

- **When:** 2026-08-22T10:38:01+00:00
- **Device:** Raspberry Pi 5 TV box (Raspberry Pi Compute Module 5 Rev 1.0) — Android 16 (serial `192.168.178.67:5555`)
- **Config:** landscape, rotation 0°, smallest width 584dp
- **Package:** `net.slions.fulguris.full.download.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 5/5 passed in 31.9s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_smoke_launch` | The app launches and reaches the main browser UI in the foreground | ✅ pass | 2.3s |
| `test_smoke_open_website` | Navigating to a web site loads and the address bar shows its label | ✅ pass | 10.0s |
| `test_smoke_open_settings` | The settings activity opens via its component and renders its content | ✅ pass | 7.2s |
| `test_smoke_background_app_switch` | KEYCODE_APP_SWITCH backgrounds the app; launching brings it back to the front | ✅ pass | 5.8s |
| `test_smoke_background_home` | KEYCODE_HOME backgrounds the app; the activity intent brings it back to the front | ✅ pass | 5.7s |
