# Downloads

Downloading files from the web: a confirmation dialog, and a downloads sheet
(Menu ⋮ → Downloads) to track and manage everything.

## How a download starts

- Links with a `download` attribute, attachment responses, and in-page
  `blob:` URLs all offer a **"Download file?"** dialog before anything is
  saved (attachments and large bodies are confirmed early, before the page
  finishes fetching them).
- The saved filename honors, in order: the link's `download` attribute,
  the server's `Content-Disposition`, then the URL's last path segment.
- `data:` URI downloads are not supported.

## The downloads sheet (Menu ⋮ → Downloads)

- In-flight rows show progress, a speed sample and the byte count;
  completed and failed rows stick around until removed.
- Per row: **Clean up**, **Remove and keep**, **Remove and delete**,
  **Delete file**, **Open** — plus sheet-wide **Remove all** / **Delete all**.

Mechanics, the covered link patterns and the test groups:
[internals/downloads.md](../internals/downloads.md).
