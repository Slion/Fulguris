# Test run — Pi Compute Module 5 Rev 1.0 · landscape-0-sw584

- **When:** 2026-09-14T22:36:17+00:00
- **Device:** Raspberry Pi 5 TV box (Raspberry Pi Compute Module 5 Rev 1.0) — Android 16 (serial `192.168.178.67:5555`)
- **Config:** landscape, rotation 0°, smallest width 584dp
- **Package:** `net.slions.fulguris.full.agent.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 1/2 passed in 46.3s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_cursor_context_menu_action_long_press` | Long-press the action key (select / DPAD center) opens the WebView context menu for the element under the cursor | ✅ pass | 21.6s |
| `test_cursor_context_menu_repeated_long_press_touch_stays_clean` | Repeated long presses (same page) each deliver a fresh touch — the synthetic long press must not leave the WebView's touch state stuck | ❌ fail | 22.8s |
| | _each of the 3 long presses should deliver a fresh touch (pointerdown) to the page, but only 2 did — the WebView's touch state is stuck (UP-after-cancel); log='pd0 ts4 pc541 ctx541 tc570 pd3180 ts3181 pc3683 ctx3683 tc3704 ctx6828'_ | | |
