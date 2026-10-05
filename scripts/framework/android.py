"""Back-compat shim: ``from framework.android import AndroidDevice``.

The old ``framework.android.AndroidDevice`` was the Fulguris device (it knew the
app's address bar, tabs, …). It is now :class:`appdevice.FulgurisDevice`. The
parent :mod:`framework` package sets up the import paths and re-exports it, so
this submodule just re-exports the same object for the ``framework.android``
import path.
"""
from __future__ import annotations

from . import AndroidDevice  # noqa: F401  (the Fulguris device)

__all__ = ["AndroidDevice"]
