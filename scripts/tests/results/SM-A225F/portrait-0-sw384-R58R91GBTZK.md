# Test run — SM-A225F · portrait-0-sw384

- **When:** 2026-09-14T22:34:40+00:00
- **Device:** Galaxy A22 5G (Samsung SM-A225F) — Android 13 (serial `R58R91GBTZK`)
- **Config:** portrait, rotation 0°, smallest width 384dp
- **Package:** `net.slions.fulguris.full.agent.debug`
- **Options:** restart=False, keep_tabs=False, orientation=default, filter=all
- **Result:** 5/6 passed in 173.1s

| Test | Description | Result | Duration |
|---|---|---|---|
| `test_cursor_click_hover_fires_mouseover` | Enabling the cursor fires a mouse hover on the page | ❌ fail | 26.7s |
| | _enabling cursor should fire a mouse hover on the page, title was 'start'_ | | |
| `test_cursor_click_activates_under_cursor` | Select press dispatches a click the page receives at the cursor | ✅ pass | 22.8s |
| `test_cursor_click_drag_target_seeks` | A cursor click seeks a scrub bar via mousedown(mouse) or touch drag, like YouTube's timeline | ✅ pass | 23.3s |
| `test_cursor_click_hesitant_press_still_clicks` | A realistically held (~600 ms) select press still clicks — only a deliberate ~1 s hold opens the context menu | ✅ pass | 23.2s |
| `test_cursor_confirm_over_ui_activates_control_under_cursor` | With the cursor over a toolbar control, the confirm key (A / select) activates the control under the cursor, not the widget holding focus | ✅ pass | 33.3s |
| `test_cursor_confirm_on_over_web_ignores_stray_focus` | With the cursor ON over the page and focus stranded on a toolbar widget, the confirm key (A / select) clicks the page under the cursor instead of activating the focused widget | ✅ pass | 37.8s |
