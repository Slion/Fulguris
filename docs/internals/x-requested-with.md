# X-Requested-With: dev notes

Feature: [features/x-requested-with.md](../features/x-requested-with.md).
Issue: [issues/x-requested-with-old-webview.md](../issues/x-requested-with-old-webview.md).

Code is the reference: `WebPageTab.initializePreferences()`
(`setProfileHeader` helper, `HEADER_*` constants).

Implementation: androidx.webkit **profile-level custom headers** (webkit
1.16+) preempt Chromium's own XRW injection — the package name is only
added if the header is absent, and a profile custom header attaches when
absent. The empty XRW value therefore wins; DNT and Save-Data are set the
same way, so the legacy `requestHeaders` map (navigations only, and the
mechanism that broke back/forward history, #414/#743) stays empty on
supported WebViews. `setRequestedWithHeaderOriginAllowList` was the
"native" fix but is `@Deprecated` / disabled since the XRW origin trial
ended. minSdk: webkit 1.16+ needs 24, handled via `tools:overrideLibrary`
(see `AndroidManifest.xml`).
