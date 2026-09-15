"""Print the pass/fail history of selected tests from committed result files.

Usage: python scripts/tests/probe_result_history.py <results-file-relative-path> [test-substr ...]
"""
import subprocess
import sys


def main() -> None:
    path = sys.argv[1]
    needles = sys.argv[2:] or ["fade_hides_then_wakes", "repeated_long_press"]
    out = subprocess.run(
        ["git", "log", "--all", "--oneline", "--", path],
        capture_output=True, text=True, check=True,
    ).stdout.split()
    hexc = set("0123456789abcdef")
    hashes = [t for t in out if 7 <= len(t) <= 40 and set(t) <= hexc]
    for h in hashes:
        res = subprocess.run(
            ["git", "show", f"{h}:{path}"], capture_output=True, text=True, errors="replace"
        )
        if res.returncode != 0 or res.stdout is None:
            print(f"{h} : (no file)")
            continue
        for needle in needles:
            for line in res.stdout.splitlines():
                if needle in line:
                    verdict = "PASS" if "pass" in line else "FAIL"
                    print(f"{h} {needle}: {verdict}")
                    break
            else:
                print(f"{h} {needle}: (absent)")


if __name__ == "__main__":
    main()
