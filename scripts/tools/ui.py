#!/usr/bin/env python3
"""Inspect or drive the address bar state without calling adb directly.

    python scripts/tools/ui.py state                 # focus / text / keyboard / popup
    python scripts/tools/ui.py key 23                 # send a key code (23 = D-pad center)
    python scripts/tools/ui.py text example.com       # type text
    python scripts/tools/ui.py tap 300 100            # tap coordinates
    python scripts/tools/ui.py focusfield             # KEYCODE_SEARCH -> focus the field
"""
from __future__ import annotations

import argparse

import adb


def print_state(serial: str) -> None:
    print(f"== {adb.device_label(serial)} ==")
    print(f"  field focused : {adb.field_focused(serial)}")
    print(f"  field text    : '{adb.field_text(serial)}'")
    print(f"  keyboard shown: {adb.ime_shown(serial)}")
    print(f"  webview focus : {adb.webview_focused(serial)}")
    print(f"  popup present : {adb.dropdown_present(serial)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["state", "key", "text", "tap", "focusfield"])
    parser.add_argument("args", nargs="*", help="Arguments for the command")
    parser.add_argument("--device", help="Target a specific adb device serial")
    parser.add_argument("--all", action="store_true", help="Act on all connected devices")
    args = parser.parse_args()

    for serial in adb.resolve_devices(args.device, args.all):
        if args.command == "state":
            print_state(serial)
        elif args.command == "key":
            adb.key(serial, int(args.args[0]))
            print_state(serial)
        elif args.command == "text":
            adb.type_text(serial, " ".join(args.args))
            print_state(serial)
        elif args.command == "tap":
            adb.tap(serial, int(args.args[0]), int(args.args[1]))
            print_state(serial)
        elif args.command == "focusfield":
            adb.key(serial, adb.KEY_SEARCH, wait=0.8)
            print_state(serial)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
