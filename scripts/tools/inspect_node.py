#!/usr/bin/env python3
"""Print the :id/search node (and any node with 'search' in its id) from a uiautomator dump.

    python scripts/tools/inspect_node.py scripts/tools/out/edit_trace_<serial>_2_after_center_0.xml
"""
from __future__ import annotations
import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else ""
if not path:
    print("usage: inspect_node.py <dump.xml>")
    raise SystemExit(1)

s = open(path, encoding="utf-8", errors="replace").read()
# Split the single-line xml into node tags for readability.
for m in re.findall(r"<node\b[^>]*>", s):
    if ":id/search" in m or "EditText" in m:
        # pretty-print attributes
        attrs = re.findall(r'(\w[\w-]*)="([^"]*)"', m)
        print("---- node ----")
        for k, v in attrs:
            print(f"  {k} = {v}")
