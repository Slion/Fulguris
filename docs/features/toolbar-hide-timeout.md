# "Hide tool bar after" (auto-hide timeout)

**Settings → General → "Hide tool bar after"** — a per-configuration slider,
0–10 seconds. After a page has loaded, the tool bar hides automatically
after the chosen delay. **0 disables** the feature.

The tool bar only hides when it is safe to: the page is loaded, the web
content (not the address field) holds focus, and no menu, panel or
fullscreen video is active. Re-showing the bar (back key or scroll up) and
any new page load restart the countdown.

Arming/cancelling semantics, a known edge, and the test group:
[internals/toolbar-hide-timeout.md](../internals/toolbar-hide-timeout.md).
