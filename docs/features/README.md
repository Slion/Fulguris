# docs/features — user-facing feature notes

One file per user-facing feature, named after the feature
(e.g. `x-requested-with.md`). These documents describe **what a user gets**,
not how it is built.

## What goes here

- One short paragraph: what the feature is and where the user finds it
  (Settings path, menu item, hardware input).
- Optionally: the user-visible key map or a short behavior table, known
  **limitations the user should know about**, and a pointer to the dev
  doc in [`../internals/`](../internals/) / known issues in
  [`../issues/`](../issues/) when they exist.

## What does NOT go here

- **Implementation details** — classes, methods, APIs, resource names,
  build-variant mechanics. That belongs in a dev doc in
  [`../internals/`](../internals/) and, above all, in **KDoc on the code itself** —
  the code is the reference; the docs point at it.
- **Test groups and verification methodology** — those live in the dev doc
  (the `AGENTS.md` test workflow is the general reference).

Keep a feature doc to roughly 10–30 lines. If you find yourself explaining
*how*, you are writing the dev doc.

## Adding a feature doc

1. Create `docs/features/<feature-name>.md` (kebab-case).
2. Link it from `AGENTS.md` where the workflow references the feature.
3. If the feature has non-obvious mechanics or open problems, add a dev doc
   to `../internals/` and link it from the bottom of the feature doc.
