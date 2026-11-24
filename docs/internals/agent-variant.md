# Agent flavor (build for AI-agent automated testing)

A dedicated **PUBLISHER flavor** `agent` with a distinct application id and
a split-bolt launcher icon, so an agent-driven install coexists with — and
is visibly different from — a developer's own builds.

| Variant | Build type | Application id |
|---|---|---|
| `slionsFullAgentDebug` | debug (debuggable, `run-as`) | `net.slions.fulguris.full.agent.debug` |
| `slionsFullAgentRelease` | release (minified) | `net.slions.fulguris.full.agent` |

- The `scripts/tools/` tooling builds/installs `agentDebug` by default
  (`--build-type agentRelease` for the release build); the test runner uses
  the same package.
- Like `fdroid`, it applies **no Firebase plugins** — no
  `google-services.json` entry, no Firebase SDK. Local-only, never
  published.
- APKs: `app/build/outputs/apk/slionsFullAgent/{debug,release}/*.apk`.

Reference: `app/build.gradle` (flavor definition + the `isDefault` pinning),
`app/src/agent/` (icons, `SponsorshipSettingsFragment`, no-op `Firebase.kt`),
`AGENT_VARIANTS` in `scripts/tools/adb.py`.

## Gotchas

- **Never install the Agent build over a regular one or vice versa** —
  different packages, coexist by design.
- The agent flavor must **not** be the IDE default variant:
  `app/build.gradle` sets `isDefault = true` on `slions`/`full`/`download`
  so Android Studio keeps building `slionsFullDownloadDebug` (the scripts
  name the agent variant explicitly and are unaffected).
- The `variantFilter` does not restrict the agent flavor — it combines with
  any BRAND/VERSION, though only `slionsFull` is meaningful.
