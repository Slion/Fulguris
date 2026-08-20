"""Platform-neutral key symbols used by tests and tools.

Tests refer to keys by these semantic names (``keys.DPAD_CENTER``) rather than
raw platform codes, so a test reads the same on any platform. The integer values
happen to be Android ``KeyEvent`` codes today — the only platform we drive — and
a future platform's ``Device`` implementation is responsible for mapping these
symbols onto its own input system.
"""
from __future__ import annotations

BACK = 4
DPAD_UP = 19
DPAD_DOWN = 20
DPAD_LEFT = 21
DPAD_RIGHT = 22
DPAD_CENTER = 23
ENTER = 66
SEARCH = 84
BUTTON_A = 96
MEDIA_FAST_FORWARD = 90
MEDIA_PLAY_PAUSE = 85
MEDIA_REWIND = 89
