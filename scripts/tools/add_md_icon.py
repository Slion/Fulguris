#!/usr/bin/env python3
"""Fetch a Material Symbols icon and save it as an Android vector drawable.

    python scripts/tools/add_md_icon.py encrypted outline       # default style
    python scripts/tools/add_md_icon.py encrypted_off outlined
    python scripts/tools/add_md_icon.py backup rounded
    python scripts/tools/add_md_icon.py check fill
    python scripts/tools/add_md_icon.py encrypted outline --size 48

Downloads the static <name>_<size>px icon from google/material-design-icons
and writes app/src/main/res/drawable/ic_<name><suffix>.xml in the project's
vector format. Re-running overwrites the file.

Prefers the repo's ready-made Android vector XML (symbols/android/...); if
that file doesn't exist it downloads the web SVG (symbols/web/...) and
converts the path data to Android pathData.

Style -> suffix:  outline(d) -> _outline,  rounded -> _rounded,
                  sharp -> _sharp,  fill(ed) -> _fill
"""
from __future__ import annotations

import argparse
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_RAW = "https://raw.githubusercontent.com/google/material-design-icons/master"

# style -> (symbols folder, filename infix, output suffix)
STYLES = {
    "outline":  ("materialsymbolsoutlined", "", "_outline"),
    "outlined": ("materialsymbolsoutlined", "", "_outline"),
    "rounded":  ("materialsymbolsrounded",  "", "_rounded"),
    "sharp":    ("materialsymbolssharp",    "", "_sharp"),
    "fill":     ("materialsymbolsoutlined", "_fill1", "_fill"),
    "filled":   ("materialsymbolsoutlined", "_fill1", "_fill"),
}

DRAWABLE_DIR = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "drawable"

VECTOR_HEADER = (
    '<vector xmlns:android="http://schemas.android.com/apk/res/android" '
    'android:height="{size}dp" android:tint="?attr/colorControlNormal" '
    'android:viewportHeight="{vh}" android:viewportWidth="{vw}" android:width="{size}dp">\n'
)

TOKEN_RE = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?")


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "add_md_icon"})
    return urllib.request.urlopen(req).read().decode("utf-8")


def fmt(v: float) -> str:
    """Format a coordinate the way Android Studio does (no trailing zeros)."""
    r = round(v, 2)
    if r == int(r):
        return str(int(r))
    return ("%.2f" % r).rstrip("0").rstrip(".")


def convert_path(d: str, dx: float, dy: float) -> str:
    """Convert an SVG path `d` to Android pathData, shifting by (dx, dy)."""
    tokens = TOKEN_RE.findall(d)
    out: list[str] = []
    cx = cy = sx = sy = 0.0
    prev_ctrl: tuple[float, float] | None = None
    prev_kind: str | None = None

    def pt(x: float, y: float) -> str:
        return f"{fmt(x + dx)},{fmt(y + dy)}"

    def emit(c: str, v: list[float]) -> None:
        nonlocal cx, cy, sx, sy, prev_ctrl, prev_kind
        r = c.islower()
        C = c.upper()
        if C == "M":
            x, y = v
            if r:
                x, y = cx + x, cy + y
            cx, cy, sx, sy = x, y, x, y
            prev_ctrl, prev_kind = None, None
            out.append(f"M{pt(x, y)}")
        elif C == "L":
            x, y = v
            if r:
                x, y = cx + x, cy + y
            cx, cy = x, y
            prev_ctrl, prev_kind = None, "L"
            out.append(f"L{pt(x, y)}")
        elif C == "H":
            x = v[0]
            if r:
                x = cx + x
            cx = x
            prev_ctrl, prev_kind = None, "H"
            out.append(f"H{fmt(x + dx)}")
        elif C == "V":
            y = v[0]
            if r:
                y = cy + y
            cy = y
            prev_ctrl, prev_kind = None, "V"
            out.append(f"V{fmt(y + dy)}")
        elif C == "C":
            x1, y1, x2, y2, x, y = v
            if r:
                x1, y1 = cx + x1, cy + y1
                x2, y2 = cx + x2, cy + y2
                x, y = cx + x, cy + y
            cx, cy = x, y
            prev_ctrl, prev_kind = (x2, y2), "C"
            out.append(f"C{pt(x1, y1)} {pt(x2, y2)} {pt(x, y)}")
        elif C == "S":
            x1, y1, x, y = v
            if r:
                x1, y1 = cx + x1, cy + y1
                x, y = cx + x, cy + y
            if prev_kind in ("C", "S") and prev_ctrl is not None:
                x1, y1 = 2 * cx - prev_ctrl[0], 2 * cy - prev_ctrl[1]
            else:
                x1, y1 = cx, cy
            cx, cy = x, y
            prev_ctrl, prev_kind = (x1, y1), "S"
            out.append(f"S{pt(x1, y1)} {pt(x, y)}")
        elif C == "Q":
            x1, y1, x, y = v
            if r:
                x1, y1 = cx + x1, cy + y1
                x, y = cx + x, cy + y
            cx, cy = x, y
            prev_ctrl, prev_kind = (x1, y1), "Q"
            out.append(f"Q{pt(x1, y1)} {pt(x, y)}")
        elif C == "T":
            x, y = v
            if r:
                x, y = cx + x, cy + y
            if prev_kind in ("Q", "T") and prev_ctrl is not None:
                x1, y1 = 2 * cx - prev_ctrl[0], 2 * cy - prev_ctrl[1]
            else:
                x1, y1 = cx, cy
            cx, cy = x, y
            prev_ctrl, prev_kind = (x1, y1), "T"
            out.append(f"T{pt(x1, y1)} {pt(x, y)}")
        elif C == "A":
            rx, ry, rot, laf, saf, x, y = v
            if r:
                x, y = cx + x, cy + y
            cx, cy = x, y
            prev_ctrl, prev_kind = None, "A"
            out.append(f"A{fmt(rx)},{fmt(ry)} {fmt(rot)} {int(laf)} {int(saf)} {pt(x, y)}")
        else:  # Z
            cx, cy = sx, sy
            prev_ctrl, prev_kind = None, None
            out.append("Z")

    need = {
        "M": 2, "m": 2, "L": 2, "l": 2, "H": 1, "h": 1, "V": 1, "v": 1,
        "C": 6, "c": 6, "S": 4, "s": 4, "Q": 4, "q": 4, "T": 2, "t": 2,
        "A": 7, "a": 7, "Z": 0, "z": 0,
    }
    cmd: str | None = None
    i, n = 0, len(tokens)
    while i < n:
        t = tokens[i]
        if t.isalpha():
            cmd = t
            i += 1
        if cmd is None or cmd not in need:
            i += 1  # skip unexpected token
            continue
        cnt = need[cmd]
        if cnt == 0:
            emit(cmd, [])
            cmd = None
            continue
        if i + cnt > n:
            print(f"[ERROR] Malformed path data (truncated at token {i})")
            sys.exit(1)
        v = [float(tokens[i + k]) for k in range(cnt)]
        i += cnt
        emit(cmd, v)
        # implicit repeated coordinates: M/m repeat as L/l, everything else repeats itself
        repeat = "L" if cmd == "M" else "l" if cmd == "m" else cmd
        while i + cnt <= n and not tokens[i].isalpha():
            v = [float(tokens[i + k]) for k in range(cnt)]
            i += cnt
            emit(repeat, v)
        cmd = None
    return "".join(out)


def build_vector(size: int, vw: int, vh: int, path_datas: list[str]) -> str:
    lines = [VECTOR_HEADER.format(size=size, vw=vw, vh=vh).rstrip("\n"), "      "]
    for pd in path_datas:
        lines.append(f'    <path android:fillColor="@android:color/white" android:pathData="{pd}"/>')
    lines += ["    ", "</vector>"]
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("name", help="Material icon name, e.g. encrypted or encrypted_off")
    parser.add_argument("style", nargs="?", default="outline", choices=sorted(STYLES),
                        help="Icon style (default: outline)")
    parser.add_argument("--size", type=int, default=24, choices=[20, 24, 40, 48],
                        help="Icon size in px (default: 24)")
    args = parser.parse_args()

    folder, infix, suffix = STYLES[args.style]
    stem = f"{args.name}{infix}_{args.size}px"

    # 1) Prefer the repo's ready-made Android vector XML (Google's own conversion)
    xml_url = f"{REPO_RAW}/symbols/android/{args.name}/{folder}/{stem}.xml"
    path_datas: list[str] | None = None
    vw = vh = 0
    try:
        xml = fetch(xml_url)
        vw = int(re.search(r'android:viewportWidth="(\d+)"', xml).group(1))
        vh = int(re.search(r'android:viewportHeight="(\d+)"', xml).group(1))
        path_datas = re.findall(r'android:pathData="([^"]+)"', xml)
        if not path_datas:
            raise ValueError("no pathData in Android XML")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            path_datas = None
        else:
            print(f"[ERROR] HTTP {e.code}: {xml_url}")
            return 1
    except (urllib.error.URLError, ValueError, AttributeError) as e:
        print(f"[WARN] Could not parse Android XML ({e}); falling back to SVG")

    # 2) Fall back to converting the web SVG
    if path_datas is None:
        svg_url = f"{REPO_RAW}/symbols/web/{args.name}/{folder}/{stem}.svg"
        try:
            svg = fetch(svg_url)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                print(f"[ERROR] Not found: {svg_url}")
                print("        Check the icon name/style; browse https://fonts.google.com/icons")
            else:
                print(f"[ERROR] HTTP {e.code}: {svg_url}")
            return 1
        except urllib.error.URLError as e:
            print(f"[ERROR] Network error: {e.reason}")
            return 1
        m = re.search(r'<svg\b[^>]*\bviewBox="([^"]+)"', svg)
        if not m:
            print(f"[ERROR] No viewBox found in SVG: {svg_url}")
            return 1
        vx, vy, vw, vh = (float(x) for x in m.group(1).split())
        paths = re.findall(r'<path\b[^>]*?\bd=["\']([^"\']+)["\']', svg)
        if not paths:
            print(f"[ERROR] No <path> elements in SVG: {svg_url}")
            return 1
        path_datas = [convert_path(d, -vx, -vy) for d in paths]
        print("[WARN] Converted from web SVG (no Android XML in repo)")

    out = DRAWABLE_DIR / f"ic_{args.name}{suffix}.xml"
    existed = out.exists()
    out.write_text(build_vector(args.size, int(vw), int(vh), path_datas), encoding="utf-8", newline="\n")
    print(f"[{'UPDATED' if existed else 'OK'}] {out}  ({args.name}, {args.style}, {args.size}px)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
