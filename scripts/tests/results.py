"""Persist and compare device test results per device + Fulguris configuration.

Each suite run is saved under a folder named after the *device model*, so a
phone's whole history lives together:

    scripts/tests/results/<MODEL>/<config-id>.yaml
    scripts/tests/results/<MODEL>/<config-id>.md

There is one file per device model + configuration, and it is **updated in
place rather than overwritten**: a run only replaces the results of the tests
it actually ran; every other test keeps its previous status (marked
`ran: false`, so a report shows at a glance which results are fresh and which
were carried forward). That keeps the files — and their git diffs — stable:
a group run produces a diff of just the tests in that group.

The git history of each file is the time dimension (that's why results are
committed). The YAML is the machine-readable record; the Markdown is a
human-readable table of every test with a short description (from
url_field_tests.TEST_DESCRIPTIONS), its result, its duration and whether it
ran in the latest run.

History is kept per device *and* per configuration (orientation / rotation /
smallest-width-dp — see fulguris.settings.Config), which lets us track runs
over time and spot regressions for a specific screen/orientation — the part
that matters most for foldables, where each screen is a distinct configuration.

Device *identifiers* (serials — which are IP:port for network adb devices) are
kept OUT of the file names and contents: the model + configuration already
identify the recording, and a serial in a committed file is noise at best and
a privacy issue at worst.

Requires PyYAML (`pip install pyyaml`); everything else is stdlib.
"""
from __future__ import annotations

import os
import re
from datetime import datetime, timezone

import yaml

RESULTS_DIR = os.path.join(os.path.dirname(__file__), "results")

_STATUS_MARK = {"pass": "✅ pass", "fail": "❌ fail", "error": "⚠️ error"}


def _sanitize(text: str) -> str:
    """Make a model/config id safe for a file/folder name (spaces, ':' …)."""
    return re.sub(r"[^A-Za-z0-9._-]", "_", str(text)).strip() or "unknown"


def model_dir(model: str, results_dir: str = RESULTS_DIR) -> str:
    """The per-device-model results folder (created on save, not on read)."""
    return os.path.join(results_dir, _sanitize(model))


def _run_paths(record: dict, results_dir: str = RESULTS_DIR) -> tuple[str, str]:
    # No serial in the name: it is an IP:port for network devices (see module doc).
    base = os.path.join(
        model_dir(record["device"]["model"], results_dir),
        _sanitize(record["config"]["id"]),
    )
    return base + ".yaml", base + ".md"


def merge_tests(prev_record: dict | None, ran: list[dict]) -> list[dict]:
    """Merge this run's per-test results with the previously saved record.

    Tests that ran get this run's result (``ran: true``); every test that was in
    the previous record but did NOT run is carried forward unchanged (``ran:
    false``) so the saved record — and its git diff — only reflects what this
    run actually changed. Carried-forward entries keep their original
    ``duration_s``/``message``; the report shows their status as stale.
    """
    ran_tests = [dict(t) for t in ran]
    for t in ran_tests:
        t["ran"] = True
    prev_tests = (prev_record or {}).get("tests", [])
    prev_names = [t["name"] for t in prev_tests]
    result = []
    for t in prev_tests:  # previous order; rerun tests replaced in place
        if t["name"] in {r["name"] for r in ran_tests}:
            result.append(next(r for r in ran_tests if r["name"] == t["name"]))
        else:
            kept = dict(t)
            kept["ran"] = False  # not part of this run: carried forward
            result.append(kept)
    for t in ran_tests:  # tests unknown to the previous record, in run order
        if t["name"] not in prev_names:
            result.append(t)
    return result


def build_record(device: dict, package: str, options: dict,
                 ran: list[dict], duration_s: float,
                 prev: dict | None = None) -> dict:
    """Assemble the record for one run from its per-test results.

    Each entry in ``ran`` is {"name", "status", "duration_s", "message"?} where
    status is "pass", "fail" or "error". ``prev`` (the previously saved record,
    if any) contributes the tests this run did not run — see [merge_tests].
    """
    tests = merge_tests(prev, ran)
    passed = sum(1 for t in tests if t["status"] == "pass")
    return {
        "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "device": {
            "model": device["model"],
            "brand": device.get("brand", ""),
            "product_name": device.get("product_name", device["model"]),
            "android": device["android"],
        },
        "config": {
            "id": device["config_id"],
            "orientation": device["orientation"],
            "rotation": device["rotation"],
            "smallest_width_dp": device["smallest_width_dp"],
        },
        "package": package,
        "options": options,
        "summary": {
            "passed": passed,
            "failed": len(tests) - passed,
            "total": len(tests),
            "ran": len(ran),
            "duration_s": round(duration_s, 1),
        },
        "tests": tests,
    }


def load_last_run(model: str, config_id: str,
                  results_dir: str = RESULTS_DIR) -> dict | None:
    """Return the previously-saved record for this model + configuration, if any."""
    d = model_dir(model, results_dir)
    path = os.path.join(d, f"{_sanitize(config_id)}.yaml")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def render_markdown(record: dict, descriptions: dict) -> str:
    """Render a human-readable Markdown report for one run."""
    device, config, options = record["device"], record["config"], record["options"]
    summary = record["summary"]
    stale = summary["total"] - summary["ran"]
    result = f"{summary['passed']}/{summary['total']} passed in {summary['duration_s']}s"
    if stale:
        result += f" ({summary['ran']} ran, {stale} carried forward)"
    lines = [
        f"# Test run — {device['model']} · {config['id']}",
        "",
        f"- **When:** {record['timestamp']}",
        f"- **Device:** {device.get('product_name') or device['model']} "
        f"({device.get('brand', '')} {device['model']}) — Android {device['android']}",
        f"- **Config:** {config['orientation']}, rotation {config['rotation']}°, "
        f"smallest width {config['smallest_width_dp']}dp",
        f"- **Package:** `{record['package']}`",
        f"- **Options:** restart={options['restart']}, keep_tabs={options['keep_tabs']}, "
        f"orientation={options['orientation'] or 'default'}, "
        f"filter={options['test_filter'] or 'all'}",
        f"- **Result:** {result}",
        "",
        "| Test | Description | Result | Duration |",
        "|---|---|---|---|",
    ]
    for t in record["tests"]:
        desc = descriptions.get(t["name"], "")
        if not desc:
            print(f"  [warn] no TEST_DESCRIPTIONS entry for {t['name']}")
        status = _STATUS_MARK.get(t["status"], t["status"])
        if t.get("ran") is False:
            status += " ⏸"
        lines.append(f"| `{t['name']}` | {desc} | {status} | {t['duration_s']}s |")
        if t["status"] != "pass" and t.get("message"):
            lines.append(f"| | _{t['message']}_ | | |")
    lines.append("")
    return "\n".join(lines)


def save_run(record: dict, descriptions: dict, results_dir: str = RESULTS_DIR) -> tuple[str, str]:
    """Write (overwrite) the YAML + Markdown for a run; return (yaml_path, md_path)."""
    yaml_path, md_path = _run_paths(record, results_dir)
    os.makedirs(os.path.dirname(yaml_path), exist_ok=True)

    header = f"# Fulguris UI test run — {record['device']['model']} · {record['config']['id']}\n"
    with open(yaml_path, "w", encoding="utf-8") as fh:
        fh.write(header + yaml.safe_dump(record, sort_keys=False, allow_unicode=True, width=1000))

    with open(md_path, "w", encoding="utf-8") as fh:
        fh.write(render_markdown(record, descriptions))
    return yaml_path, md_path


def compare(prev: dict | None, curr: dict) -> dict:
    """Diff two runs' per-test statuses.

    Returns regressions (pass -> fail/error), fixes (fail/error -> pass), plus
    tests that are newly added or no longer present. Only tests that actually
    ran in the current record are compared — carried-forward results (``ran:
    false``) cannot regress or fix anything.
    """
    curr_status = {t["name"]: t["status"] for t in curr["tests"] if t.get("ran", True)}
    if not prev:
        return {"regressions": [], "fixes": [], "new": sorted(curr_status), "removed": []}
    prev_status = {t["name"]: t["status"] for t in prev["tests"]}

    def failed(s: str) -> bool:
        return s in ("fail", "error")

    regressions, fixes = [], []
    for name, now in curr_status.items():
        was = prev_status.get(name)
        if was is None:
            continue
        if not failed(was) and failed(now):
            regressions.append(name)
        elif failed(was) and not failed(now):
            fixes.append(name)
    new = sorted(set(curr_status) - set(prev_status))
    return {
        "regressions": sorted(regressions),
        "fixes": sorted(fixes),
        "new": new,
        "removed": [],
    }
