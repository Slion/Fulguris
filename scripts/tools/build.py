#!/usr/bin/env python3
"""Build the debug APK.

    python scripts/tools/build.py
"""
from __future__ import annotations

import sys

import adb


def main() -> int:
    code = adb.gradle_build()
    if code != 0:
        print("Build FAILED.")
        return code
    apk = adb.apk_path()
    print(f"Build OK: {apk}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
