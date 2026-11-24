# X-Requested-With: implementation notes and open issues

Feature: [features/x-requested-with.md](../features/x-requested-with.md).
Code is the reference: `WebPageTab.initializePreferences()`
(`setProfileHeader` helper, `HEADER_*` constants).

Implementation: androidx.webkit **profile-level custom headers** (webkit
1.16+) preempt Chromium's own XRW injection — the package name is only added
if the header is absent, and a profile custom header attaches when absent.
The empty XRW value therefore wins; DNT and Save-Data are set the same way,
so the legacy `requestHeaders` map (navigations only, and the mechanism that
broke back/forward history, #414/#743) stays empty on supported WebViews.
`setRequestedWithHeaderOriginAllowList` was the "native" fix but is
`@Deprecated` / disabled since the XRW origin trial ended. minSdk: webkit
1.16+ needs 24, handled via `tools:overrideLibrary` (see
`AndroidManifest.xml`).

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
