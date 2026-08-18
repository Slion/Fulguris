"""Persist and compare device test results per device + Fulguris configuration.

Each suite run is saved under a folder named after the *device model*, so a
phone's whole history lives together:

    scripts/tests/results/<MODEL>/<config-id>-<serial>.yaml
    scripts/tests/results/<MODEL>/<config-id>-<serial>.md

There is one file per configuration + serial, overwritten on each run — the
git history of each file is the time dimension (that's why results are
committed). The YAML is the machine-readable record; the Markdown is a
human-readable table of every test with a short description (from
url_field_tests.TEST_DESCRIPTIONS), its result and its duration.

History is kept per device *and* per configuration (orientation / rotation /
smallest-width-dp — see fulguris.settings.Config), which lets us track runs
over time and spot regressions for a specific screen/orientation — the part
that matters most for foldables, where each screen is a distinct configuration.

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
    base = os.path.join(
        model_dir(record["device"]["model"], results_dir),
        f"{_sanitize(record['config']['id'])}-{_sanitize(record['device']['serial'])}",
    )
    return base + ".yaml", base + ".md"


def build_record(device: dict, package: str, options: dict,
                 tests: list[dict], duration_s: float) -> dict:
    """Assemble the record for one run from its per-test results.

    Each entry in ``tests`` is {"name", "status", "duration_s", "message"?} where
    status is "pass", "fail" or "error".
    """
    passed = sum(1 for t in tests if t["status"] == "pass")
    return {
        "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "device": {
            "serial": device["serial"],
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
            "duration_s": round(duration_s, 1),
        },
        "tests": tests,
    }


def load_last_run(model: str, config_id: str, serial: str,
                  results_dir: str = RESULTS_DIR) -> dict | None:
    """Return the previously-saved run for this model + config + serial, if any."""
    d = model_dir(model, results_dir)
    path = os.path.join(d, f"{_sanitize(config_id)}-{_sanitize(serial)}.yaml")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def render_markdown(record: dict, descriptions: dict) -> str:
    """Render a human-readable Markdown report for one run."""
    device, config, options = record["device"], record["config"], record["options"]
    summary = record["summary"]
    lines = [
        f"# Test run — {device['model']} · {config['id']}",
        "",
        f"- **When:** {record['timestamp']}",
        f"- **Device:** {device.get('product_name') or device['model']} "
        f"({device.get('brand', '')} {device['model']}) — Android {device['android']} "
        f"(serial `{device['serial']}`)",
        f"- **Config:** {config['orientation']}, rotation {config['rotation']}°, "
        f"smallest width {config['smallest_width_dp']}dp",
        f"- **Package:** `{record['package']}`",
        f"- **Options:** restart={options['restart']}, keep_tabs={options['keep_tabs']}, "
        f"orientation={options['orientation'] or 'default'}, "
        f"filter={options['test_filter'] or 'all'}",
        f"- **Result:** {summary['passed']}/{summary['total']} passed in {summary['duration_s']}s",
        "",
        "| Test | Description | Result | Duration |",
        "|---|---|---|---|",
    ]
    for t in record["tests"]:
        desc = descriptions.get(t["name"], "")
        if not desc:
            print(f"  [warn] no TEST_DESCRIPTIONS entry for {t['name']}")
        status = _STATUS_MARK.get(t["status"], t["status"])
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
    tests that are newly added or no longer present.
    """
    curr_status = {t["name"]: t["status"] for t in curr["tests"]}
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
    removed = sorted(set(prev_status) - set(curr_status))
    return {
        "regressions": sorted(regressions),
        "fixes": sorted(fixes),
        "new": new,
        "removed": removed,
    }
