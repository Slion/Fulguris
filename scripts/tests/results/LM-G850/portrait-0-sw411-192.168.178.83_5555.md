# Test run — LM-G850 · portrait-0-sw411

- **When:** 2026-08-19T11:51:22+00:00
- **Device:** LM-G850 (Lge LM-G850) — Android 12 (serial `192.168.178.83:5555`)
- **Config:** portrait, rotation 0°, smallest width 411dp
- **Package:** `net.slions.fulguris.full.download.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 0/1 passed in 80.0s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_repeated_rotations_keep_page` | Forced portrait/landscape rotations keep the app on the same page without recreating the activity | ❌ fail | 78.8s |
| | _after rotation 1 to landscape the app lost the foreground_ | | |
