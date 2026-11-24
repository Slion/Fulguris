# D-pad remote control (Android TV cursor)

How a standard Android TV **D-pad remote** — no analog sticks — drives the
on-screen cursor. The gamepad key map and settings are in
[cursor-gamepad.md](cursor-gamepad.md); dev notes in
[internals/cursor.md](../internals/cursor.md).

## Key map (D-pad remote)

| Input | Cursor off | Cursor on |
|---|---|---|
| **D-pad directions** | Focus navigation (normal UI) | Moves the cursor; auto-repeat accelerates it |
| **Select / center / ENTER** | Normal meaning | **Click** at the cursor (while the web content is focused); a *deliberate* hold (default 1 s) → context menu. Goes to the focused control when the web content isn't focused |
| **Play/pause — short** | Play/pause the page's `<video>` | Same (the toggle is the *long* press) |
| **Play/pause — long** | Toggle the cursor on/off | Toggle the cursor on/off (gated by the cursor-hotkey setting; the menu item works regardless) |
| **Fast-forward** | Seek the page's `<video>` +10 s | Mouse-wheel scroll **up** at the cursor (repeats while held) |
| **Rewind** | Seek the page's `<video>` −10 s | Mouse-wheel scroll **down** at the cursor |
| **BACK** | Normal meaning | Same — also re-shows a hidden tool bar |

A remote has no right stick, so with the cursor on its D-pad is the only way
to move the cursor.

## How it feels

- **Toggle:** long-press the play/pause button (or the "Cursor" menu item).
- **Click vs context menu:** press select for a click; hold it deliberately
  (~1 s, configurable) for the context menu of the element under the cursor.
  A hesitant half-second press is still a click.
- **Scroll:** the FF/REW media keys act as a mouse wheel at the cursor —
  whatever is under it scrolls (a sidebar, the page at the edge).
- **Fade:** the cursor fades out after a few seconds of no movement and
  wakes on any movement.
- **HTML5 fullscreen:** the cursor, its click and the media-key wheel keep
  working over fullscreen video.
