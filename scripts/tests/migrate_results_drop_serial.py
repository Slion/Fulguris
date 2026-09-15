"""One-off migration: drop device serials from existing result files.

- Renames <config-id>-<serial>.{yaml,md} to <config-id>.{yaml,md} (git mv, so the
  file history survives).
- Removes the `serial` key from the YAML record and the "(serial ...)" note from
  the Markdown header.

Safe to re-run: files already in the new format are skipped.
"""
from __future__ import annotations

import os
import re
import subprocess

RESULTS = os.path.join(os.path.dirname(__file__), "results")


def run_git(*args: str) -> None:
    subprocess.run(["git", "mv", *args], check=True, cwd=os.path.dirname(RESULTS))


def main() -> None:
    for model in sorted(os.listdir(RESULTS)):
        mdir = os.path.join(RESULTS, model)
        if not os.path.isdir(mdir):
            continue
        for fname in sorted(os.listdir(mdir)):
            # A result file is <config-id>[-<serial>].<ext> where the config id is
            # exactly <orientation>-<rotation>-sw<dp>; anything after it is a serial.
            m = re.match(
                r"^(?P<config>(?:portrait|landscape|sensor)-\d+-sw\d+)"
                r"(?:-(?P<serial>.+?))?\.(?P<ext>yaml|md)$",
                fname,
            )
            if not m:
                continue
            cfg, ext = m["config"], m["ext"]
            src = os.path.join(mdir, fname)
            dst = os.path.join(mdir, f"{cfg}.{ext}")
            if os.path.abspath(src) != os.path.abspath(dst) and not os.path.exists(dst):
                run_git(src, dst)
                print(f"renamed: {fname} -> {cfg}.{ext}")
            with open(dst, encoding="utf-8") as fh:
                text = fh.read()
            if ext == "yaml":
                stripped = re.sub(r"^\s*serial: .*\n", "", text, count=1, flags=re.M)
            else:
                stripped = re.sub(r" \(serial `[^`]*`\)", "", text, count=1)
            if stripped == text:
                continue
            with open(dst, "w", encoding="utf-8") as fh:
                fh.write(stripped)
            print(f"  stripped serial from {cfg}.{ext}")


if __name__ == "__main__":
    main()
