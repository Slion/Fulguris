#!/usr/bin/env python3
"""Build a deployable APK (default: the slionsFullAgentDebug variant).

"agent" is a PUBLISHER-dimension flavor with a robot launcher icon, dedicated to
automated testing; see docs/features/agent-variant.md.

    python scripts/tools/build.py                    # agentDebug (default)
    python scripts/tools/build.py --build-type agentRelease
"""
from __future__ import annotations

import argparse

import adb


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--build-type", choices=sorted(adb.AGENT_VARIANTS),
                        default=adb.DEFAULT_BUILD_TYPE,
                        help="Which variant to build (default: agentDebug)")
    args = parser.parse_args()

    code = adb.gradle_build(args.build_type)
    if code != 0:
        print("Build FAILED.")
        return code
    apk = adb.apk_path(args.build_type)
    print(f"Build OK: {apk}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
