# X-Requested-With: open issues

Feature: [features/x-requested-with.md](../features/x-requested-with.md) ·
Dev notes: [internals/x-requested-with.md](../internals/x-requested-with.md).

## Open 1: old WebViews still leak the package name (no fix available)

WebViews without `CUSTOM_REQUEST_HEADERS` (verified: **118** in WSA) have no
attach point — the custom-header machinery landed in Chromium in 2025.
Options, none chosen: accept + document; show the switch as "not supported
on this WebView" when the gate is false; or a local `URLLoaderFactory`
proxy (heavy, poor fit).

## Open 2: header cannot be removed (platform floor)

`addCustomHeader` attaches **only when the header is absent** — it can
never replace or remove the injected header, and no remove API exists.
Accepted as the platform floor: the privacy win (no package name) is the
substantive one; mere presence is a weak signal.
