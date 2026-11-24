# Downloads: dev notes

Feature: [features/downloads.md](../features/downloads.md).

## Entry points & filename

- **`LightningDownloadListener`** (the WebView `setDownloadListener`) —
  navigation-level downloads; shows the "Download file?" dialog, hands the
  confirmed URL to `DownloadHandler` → Android DownloadManager.
- **`BlobHook.js`** (injected on every page load) — hooks
  `URL.createObjectURL`, `Response.prototype.blob` (early confirm dialog
  for attachments / large bodies) and anchor clicks (user or programmatic
  `a.click()`). It reports captured `download`-attribute names via
  `WebViewEx.BlobDownloadBridge.onFilename` → `WebViewEx.blobFilenames`
  (URL → name) — the WebView's download listener never receives the
  attribute itself, so without the hook the file is saved under the URL's
  basename (the "GitHub release saved as `index.html`" bug).
- **`BlobDownload.js`** — reads `blob:` URLs out via a data-URL bridge and
  writes them directly; the DownloadManager can't resolve in-memory object
  URLs.
- Filename precedence in `LightningDownloadListener.doDownloadStart`:
  captured `download` attribute (routed through
  `DownloadHandler.onDownloadStartWithFilename`) → `Content-Disposition`
  (`filename*=` RFC 5987, then `filename=`, parsed in `UrlUtils`) → URL
  basename.
- MIME: `application/octet-stream` infers the type from the extension; a
  real MIME with a wrong extension offers "Download as \<EXT\>" (another
  `onDownloadStartWithFilename` caller).

## Link patterns (test group `downloads-links`)

`scripts/tests/assets/download_links.html` exercises every common
download-offer; the tests assert the dialog **and** the file on disk use
the intended name.

| Pattern | Markup / response | Expected |
|---|---|---|
| `<a download="name.txt">`, no Content-Disposition | the common link-download | named by the **attribute** |
| JS anchor, `a.download = 'name'`, `a.click()` | GitHub-style programmatic | same — attribute honored |
| `Content-Disposition: attachment; filename=…`, no attribute | server-driven | the header's filename |
| plain link, `octet-stream`, no CD, no attribute | bare binary URL | dialog appears; URL basename |

## Download sheet (groups `downloads`, `downloads-full`)

Known system behaviors the tests rely on:

- DownloadManager **never fails** a paused entry — a failure only appears
  after a mid-download drop (the Range-resume of the dead stream 404s,
  flipping it to FAILED).
- 404 responses show **no** dialog (the WebView renders the error page),
  so a failed row can only come from a real mid-stream drop.
- Re-downloading the same URL re-names the file `name-1.ext` (reliable
  second-file detector).

## Test notes

- Files are served host-side over the test HTTP servers + `adb reverse`
  (Fulguris blocks `file://`). `?slow=N` shrinks `SO_SNDBUF` so `write()`
  blocks — with the default multi-MB buffer the kernel accepts the whole
  file instantly and pacing is a no-op.
- The dialog's "Download" button is matched by **exact** text and only
  after the dialog animation finishes — a tap mid-animation lands on the
  scrim and cancels.
