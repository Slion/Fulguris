"""Back-compat shim: the test-results recorder now lives in :mod:`autotest.results`.

The generic result persistence / regression-comparison logic moved to the
**AutoTest** submodule. This module re-exports it and points the default
:data:`RESULTS_DIR` at this repo's results folder (``scripts/tests/results``),
so ``import results as results_store`` and ``results.<fn>(...)`` keep working
with the same on-disk location as before.
"""
from __future__ import annotations

import os
import sys

# Make the AutoTest submodule importable when loaded as a plain module.
_here = os.path.dirname(os.path.abspath(__file__))            # .../scripts/tests
_at = os.path.normpath(os.path.join(os.path.dirname(_here), "..", "subs", "AutoTest"))
if _at not in sys.path:
    sys.path.insert(0, _at)

import autotest.results as _r  # noqa: E402

# Point the recorder's default directory at this repo's results folder.
_r.RESULTS_DIR = os.path.join(_here, "results")

from autotest.results import (  # noqa: E402,F401
    RESULTS_DIR,
    _sanitize,
    model_dir,
    merge_tests,
    build_record,
    load_last_run,
    render_markdown,
    save_run,
    compare,
)

__all__ = [
    "RESULTS_DIR", "model_dir", "merge_tests", "build_record",
    "load_last_run", "render_markdown", "save_run", "compare",
]
