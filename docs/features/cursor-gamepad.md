# Gamepad control (Android TV cursor)

How a gamepad — particularly a *two-stick* gamepad like an Xbox controller —
drives the on-screen cursor, clicking, and scrolling on Android TV.
The D-pad-remote walkthrough (toggle, click vs hold, fade) is in
[cursor-remote.md](cursor-remote.md).

## Key map

| Input | Cursor off | Cursor on |
|---|---|---|
| **Right stick** | Moves the cursor (no toggle needed) | Moves the cursor |
| **Left stick** | Native WebView page scroll | Same (untouched) |
| **D-pad — two-stick gamepad** | Focus navigation | Focus navigation (yielded back — the stick is the cursor's driver) |
| **D-pad — stick-less remote / virtual keyboard** | Focus navigation | Moves the cursor; select clicks |
| **LB / Fast-forward** | — | Mouse-wheel scroll **up** at the cursor (repeats while held) |
| **RB / Rewind** | — | Mouse-wheel scroll **down** at the cursor |
| **A / D-pad center / ENTER** | Normal meaning | Click at the cursor; a *deliberate* hold (default 1 s) is a long press → context menu. Goes to the focused control when the web content isn't focused |
| **Play/pause — short** | Play/pause the page's `<video>` | Same |
| **Play/pause — long** | Toggle the cursor on/off | Toggle the cursor on/off |
| B, X, Y, Start, Back, Guide, LT/RT, stick clicks | Normal meaning | Same |

FF ≡ LB (up) and REW ≡ RB (down): a standard gamepad has no media keys, so
the shoulder buttons are *the* wheel for gamepads.

## Settings

| Setting | What it does | Default |
|---|---|---|
| Cursor hotkey | Allow the play/pause long-press to toggle the cursor (the menu item works regardless) | on |
| Cursor speed | 0–100 % | 30 |
| Cursor acceleration | While a direction is held, 0–100 % | 30 |
| Cursor fade timeout | Seconds of no movement before the cursor fades; 0 = never | 3 |
| Action-key hold | Seconds of deliberate hold for the context menu (0.5–1 s) | 1.0 |

Speed and acceleration are **physical** (cm/s and cm/s² scaled by display
DPI), so a given setting feels comparable on any screen.

Mechanics, design decisions and the test groups:
[internals/cursor.md](../internals/cursor.md).
