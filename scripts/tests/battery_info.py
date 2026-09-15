"""Collect battery-health diagnostics from an Android device over adb.

Usage:
    python scripts/tests/battery_info.py                # auto-pick the Samsung phone
    python scripts/tests/battery_info.py --device SERIAL

Writes the full report to scripts/tools/out/battery-<serial>.txt and prints
the key summary lines.
"""

import argparse
import os
import re
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), os.pardir, "tools"))
import adb  # noqa: E402

OUT_DIR = os.path.join(os.path.dirname(__file__), os.pardir, "tools", "out")


def shell(serial: str, cmd: str, timeout: int = 60) -> str:
    return adb._adb(serial, ["shell", cmd], timeout=timeout)


def section(lines: list[str], title: str) -> None:
    lines.append("")
    lines.append("=" * 70)
    lines.append(f"  {title}")
    lines.append("=" * 70)


def pick_samsung() -> str | None:
    for serial in adb.list_devices():
        model = shell(serial, "getprop ro.product.model").strip()
        if "SM-" in model or "Samsung" in shell(serial, "getprop ro.product.brand").strip():
            return serial
    return None


def collect(serial: str) -> list[str]:
    L: list[str] = []

    section(L, "DEVICE IDENTITY")
    for prop in [
        "ro.product.brand", "ro.product.manufacturer", "ro.product.name",
        "ro.product.model", "ro.product.device",
        "ro.build.version.release", "ro.build.version.sdk",
        "ro.build.fingerprint", "ro.build.date",
        "battery.battery_capacity",  # mAh design capacity, present on some builds
    ]:
        L.append(f"{prop:38s} = {shell(serial, f'getprop {prop}').strip()}")

    section(L, "UPTIME / BOOT (ms)")
    for label, path in [
        ("uptime", "/proc/uptime"),
        ("boottime", "/proc/stat"),
    ]:
        text = shell(serial, f"cat {path}").strip()
        L.append(f"{label}: {text.splitlines()[0] if text else 'n/a'}")
    L.append(f"current unix time (device): {shell(serial, 'date +%s').strip()}")

    section(L, "BATTERY STATUS (dumpsys battery)")
    L.append(shell(serial, "dumpsys battery"))

    section(L, "BATTERY PROPERTIES (dumpsys batterystats --charged, key lines)")
    out = shell(serial, "dumpsys batterystats --charged", timeout=120)
    keep = re.findall(
        r".*(?:estimated battery capacity|learned battery capacity|"
        r"capacity level|capacity min|capacity max|battery capacity|"
        r"capacity:|charging|temperature|voltage|current|"
        r"power save|usage since|discharge|health|status|screen|full).*",
        out,
        re.IGNORECASE,
    )
    L.append("\n".join(keep[:200]) if keep else "(no matching lines)")

    section(L, "POWER SUPPLY SYSFS")
    L.append(shell(serial,
        "for d in /sys/class/power_supply/*; do "
        "echo \"== $d: $(cat $d/type 2>/dev/null)\"; "
        "for f in capacity level status health temp voltage_now current_now "
        "cycle_count charge_full charge_now tech_type; do "
        "v=$(cat $d/$f 2>/dev/null) && [ -n \"$v\" ] && echo \"   $f = $v\"; "
        "done; done"))

    section(L, "BATTERYSTATS HISTORY (last 40 lines)")
    L.append("\n".join(shell(serial, "dumpsys batterystats --history", timeout=120)
                          .splitlines()[-40:]))

    section(L, "THERMAL ZONES")
    L.append(shell(serial,
        "for z in /sys/class/thermal/thermal_zone*; do "
        "echo \"$z: $(cat $z/type 2>/dev/null) = $(cat $z/temp 2>/dev/null)\"; done"))

    section(L, "THERMAL (dumpsys thermalservice, current state)")
    L.append(shell(serial, "dumpsys thermalservice | grep -A6 'mThermalStatus' | head -20"))

    section(L, "BATTERY SYSPROPS")
    L.append(shell(serial, "getprop | grep -i 'battery\\|bms\\|fuel'"))

    section(L, "KERNEL LOG: BATTERY/HEAT MENTIONS (logcat -b all, last 40)")
    L.append("\n".join(
        shell(serial, "logcat -b all -d -t 4000 | grep -iE 'bms|battery|overheat|thermal'")
        .splitlines()[-40:]
    ))

    section(L, "POWER / SCREEN USAGE SUMMARY")
    L.append(shell(serial, "dumpsys power | grep -E 'mWakefulness=|Display Power:|mHolding' | head -10"))
    L.append("dumpsys usagestats last 15 min:\n" +
             shell(serial, "dumpsys usagestats | tail -25"))

    return L


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--device")
    args = parser.parse_args()

    serial = args.device or pick_samsung()
    if not serial:
        print("No Samsung device found. Use --device SERIAL.")
        return 1
    print(f"Collecting battery diagnostics from {serial} ...")

    lines = collect(serial)

    os.makedirs(OUT_DIR, exist_ok=True)
    out_path = os.path.normpath(os.path.join(OUT_DIR, f"battery-{serial}.txt"))
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"Full report: {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
