# docs/internals — dev notes

One developer-facing file **per feature**, named after it (mirroring
[`../features/`](../features/): `cursor.md`, `downloads.md`, …). These
documents cover the non-obvious mechanics: *why* it works the way it does,
edge cases, platform quirks, and the test groups that lock the behavior in.

## Rules

- **Minimal, and point at the code.** The code (KDoc included) is the
  reference: name the entry point and constants, say the *why* the code
  cannot say, and stop. Do not restate what the code shows.
- Link back to the feature doc in
  [`../features/`](../features/) at the top.
- Small known edges and accepted behavior may be noted inline as part of
  the notes. Anything that is really an *issue* (a bug, a leak, a platform
  floor with options on the table) gets its own file in
  [`../issues/`](../issues/) — never an issue-specific file here.
