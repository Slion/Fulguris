# X-Requested-With suppression ("Remove identifying headers")

**Settings → Privacy → Remove identifying headers** (default **on**).

The WebView adds `X-Requested-With: <package name>` to every request it
makes (page loads, XHR, subresources), revealing the exact app to every
server the user visits. This switch stops the package name from being sent:
the header goes out with an **empty** value instead.

## Verified

| WebView | Result |
|---|---|
| 156 (Samsung A22) | header present, **empty** — package gone (page loads and XHR) |
| 118 (WSA) | package name still sent — see [known gap](../issues/x-requested-with-old-webview.md) |

## Limitation

The header **cannot be removed entirely** — only its value can be replaced.
On supported WebViews the fingerprint floor is "header present, empty
value"; desktop browsers send no such header at all.

Implementation details and open issues:
[internals/x-requested-with.md](../internals/x-requested-with.md).
