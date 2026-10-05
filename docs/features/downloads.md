# Downloads

Fulguris downloads, end to end: how a download starts, how the filename is
decided, and which link patterns on the web are covered by the automated tests.

## The two entry points

1. **`WebDownloadListener`** — the WebView's `setDownloadListener` callback.
   Fires for navigation-level downloads (attachment responses, `download`-
   attribute links). Fulguris's implementation is
   `LightningDownloadListener`, which shows the "Download file?" dialog and
   hands the confirmed URL to `DownloadHandler` → Android DownloadManager.
2. **Blob downloads** — `blob:` URLs (and `fetch().blob()` results). The
   DownloadManager can't resolve in-memory object URLs, so these are read out
   of the page via `BlobDownload.js` (data-URL bridge) and written to the
   Downloads folder directly. `BlobHook.js` (injected on every page load) is
   what captures the page's intent: it hooks `URL.createObjectURL`,
   `Response.prototype.blob` (early confirm dialog for attachments / large
   bodies) and anchor clicks.

## Filename resolution

The filename is decided in `LightningDownloadListener.doDownloadStart`, in
this order of precedence:

1. **The HTML5 `download` attribute** — captured by `BlobHook.js` when an
   anchor with a `download` attribute is clicked (user click *or* a
   programmatic `a.click()`, blob or ordinary URL). The hook reports it to
   `WebViewEx.BlobDownloadBridge.onFilename`, which stores it in
   `WebViewEx.blobFilenames` (URL → name). The WebView's download listener
   never receives the attribute itself, so without this hook the file would
   be saved under the URL's basename — the classic "GitHub release saved as
   `index.html`" bug. When a captured name exists, the dialog shows it and
   the confirmed download is routed through
   `DownloadHandler.onDownloadStartWithFilename` so the DownloadManager
   honors it too.
2. **`Content-Disposition`** — `filename*=` (RFC 5987) then `filename=`,
   parsed in `UrlUtils`.
3. **The URL basename** — the fallback.

MIME display: when the server sends `application/octet-stream`, the dialog
infers the type from the filename's extension (the Fulguris APK on
slions.net is the canonical case). A real MIME + wrong extension offers a
neutral "Download as <EXT>" button (extension-mismatch correction), which is
another `onDownloadStartWithFilename` caller.

## Link patterns (the `downloads-links` test group)

`scripts/tests/assets/download_links.html` exercises every common way a web
page offers a download; the tests in `scripts/tests/downloads_full_tests.py`
(`FEATURE_GROUPS["downloads-links"]`) assert the dialog **and** the file on
disk use the intended name:

| Pattern | Markup / response | Expected |
|---|---|---|
| `<a download="name.txt">`, no Content-Disposition | the common link-download (forums, release pages) | dialog + file named by the **attribute** |
| JS-created anchor, `a.download = 'name'`, `a.click()` | the GitHub-style programmatic download | same — the attribute is honored |
| `Content-Disposition: attachment; filename=…`, no attribute | server-driven naming | the header's filename |
| plain link, `octet-stream`, no CD, no attribute | bare binary URL | the "Download file?" dialog still appears (the WebView offers to download), file under the URL basename |

`data:` URI "downloads" are not supported (the image-download dialog says so
explicitly); blob: downloads are the supported in-page mechanism instead.

## The download sheet

`downloads_tests` (group `downloads`) + `downloads_full_tests` (group
`downloads-full`) cover the bottom-sheet lifecycle: empty state, the
in-flight row (percentage, `• speed` sample, `bytes / total`), completion,
mid-stream failure (`Error: 0`), cancel, and the per-row actions (Clean up /
Remove and keep / Remove and delete / Delete file / Open) plus sheet-wide
Remove all / Delete all. Known system behaviors the tests rely on:

- DownloadManager **never fails** a paused entry — a failure only appears
  after a mid-download drop; a Range-resume of the dead stream 404s, which
  is what flips it to FAILED.
- 404 responses show **no** download dialog (the WebView renders the error
  page), so a "failed row" can only be produced by a real mid-stream drop.
- Re-downloading the same URL makes the system re-name the file `name-1.ext`
  (a reliable second-file detector).

## Test notes

- Files are served host-side over the test HTTP servers + `adb reverse`
  (Fulguris blocks `file://`). The `?slow=N` throttle shrinks `SO_SNDBUF` so
  `write()` actually blocks — with the default multi-MB socket buffer the
  kernel accepts the whole file instantly and the pacing in the handler loop
  is a no-op.
- The dialog's "Download" button is matched by **exact** text (the title and
  message also contain the word) and only after the dialog has finished
  animating — a tap during the animation lands on the scrim and cancels it.
