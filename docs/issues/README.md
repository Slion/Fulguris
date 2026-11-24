# docs/issues — issue files

One file **per issue** (bugs, leaks, platform floors): the root cause, the
impact, and the options considered — and why none was chosen, if that is
the current state. Named after the issue, not the feature
(e.g. `x-requested-with-old-webview.md`).

## Rules

- **Minimal, and point at the code.** The code (KDoc included) is the
  reference: name the entry point and constants, say what the code cannot
  say, and stop.
- Link the related feature doc
  ([`../features/`](../features/)) and/or dev notes
  ([`../internals/`](../internals/)) at the top.
- When an issue is fixed or accepted, either resolve it inline (one line:
  what was done / why it stays) or delete the file — do not leave dead
  "open" markers.
