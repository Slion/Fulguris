# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

Fulguris is an Android web browser (Kotlin, Gradle, Hilt, RxJava, DataBinding).
The most common development loop is **build → install → test on real devices over adb**,
which is fully scriptable. **Always use the `scripts/` tooling instead of calling
`adb` or `gradlew` directly** — the scripts handle device selection, the debug flavor,
APK path, and UTF-8 quirks of the Windows terminal.

## Prerequisites

- Python 3 (stdlib only — no pip packages needed for `scripts/`)
- `adb` on `PATH` with at least one device connected (`adb devices`)
- Gradle JDK configured as usual for the project

Devices in this workflow are typically:
- a phone (e.g. Samsung) over USB
- an Android TV box over network adb (`192.168.x.x:5555`)

Every command below supports `--device SERIAL` to target one device, or
`--all` to act on every connected device. **Always verify on both device types
(phone and TV) before declaring something fixed** — D-pad/remote behavior on TV
differs from touch/keyboard behavior on phones.

## The scripts

### `scripts/tools/` — build & device tooling

| Command | Purpose |
|---|---|
| `python scripts/tools/build.py` | Build the debug APK (`assembleSlionsFullDownloadDebug`). Prints the APK path on success. |
| `python scripts/tools/install.py --build --all` | Build then install on **all** connected devices. This is the default "deploy" command. |
| `python scripts/tools/install.py --device SERIAL` | Install (already-built) APK on one device. |
| `python scripts/tools/launch.py --restart --all` | Force-stop and relaunch the app on device(s). Use after install to get a clean state. |
| `python scripts/tools/capture.py --all` | Screenshot each device to `scripts/tools/out/<serial>.png`. `--out path.png` for a single device. |
| `python scripts/tools/ui.py state` | Print address-bar state: field focus, field text, keyboard shown, webview focus, popup present. |
| `python scripts/tools/ui.py key 23` | Send a key code (23 = D-pad center, 4 = back, 19-22 = D-pad directions, 66 = enter, 84 = search/focus field, 96 = button A), then print state. |
| `python scripts/tools/ui.py text example.com` | Type text into the focused field. |
| `python scripts/tools/ui.py tap 300 100` | Tap screen coordinates. |
| `python scripts/tools/ui.py focusfield` | Focus the URL field (KEYCODE_SEARCH). |
| `python scripts/tools/add_md_icon.py <name> <style>` | Fetch a Material Symbols icon (style: `outline`, `rounded`, `sharp`, `fill`; default `outline`) from google/material-design-icons and write `app/src/main/res/drawable/ic_<name>` + `_outline`/`_rounded`/`_sharp`/`_fill` + `.xml` (e.g. `ic_encrypted_outline.xml`). Re-running overwrites. |

`scripts/tools/adb.py` is the shared library behind the above (device resolution,
`navigate`, `ime_shown`, `field_text`, `dropdown_present`, …). Import it when writing
new tools or tests rather than shelling out to adb yourself.

### `scripts/tests/` — automated device test suite

```powershell
python scripts/tests/run.py --all              # every test on every connected device
python scripts/tests/run.py --device SERIAL    # one device
python scripts/tests/run.py --all --test suggestions   # only tests whose name matches
python scripts/tests/run.py --device SERIAL --orientation landscape  # force orientation
python scripts/tests/run.py --list             # list available tests
```

Tests live in `scripts/tests/url_field_tests.py` as plain functions
`test_<name>(serial: str, package: str, ctx: dict) -> None` that raise
`AssertionError` on failure; new tests are appended to `ALL_TESTS`.
The current suite covers the URL/address bar focus/edit model: label vs URL
content, D-pad navigation vs edit mode, two-stage back (hide keyboard then
cancel), suggestion navigation without touch, tap-to-edit, and focus pill
visibility.

**Orientation & configuration.** `--orientation portrait|landscape|sensor`
forces the device orientation before the run (and restores it after). Fulguris
keeps separate settings per *configuration* — orientation + rotation +
smallest-width-dp (see `fulguris.settings.Config` / `Context.configId`), which
is how foldables get distinct inner/outer-screen settings. Each run detects and
records that configuration (`adb.device_config()` returns a matching
`config_id` like `landscape-90-sw384`).

**Results & regressions.** Every run is saved under a folder named after the
device **model** (see `scripts/tests/results.py`):

    scripts/tests/results/<MODEL>/<config-id>-<serial>.yaml    # machine-readable record
    scripts/tests/results/<MODEL>/<config-id>-<serial>.md      # human-readable table

There is one file pair per configuration + device serial, overwritten on each
run — the **git history of each file is the time dimension**. The Markdown
table lists every test with a short description (from `TEST_DESCRIPTIONS` in
`url_field_tests.py` — add an entry for every new test), its result (✅/❌/⚠️)
and duration. History is thus tracked **per device model and per
configuration**. The runner compares each run against the previous one for the
same model+config+serial and prints `REGRESSIONS`/`fixed` lines (pass↔fail
transitions). The `results/` files are **committed** — that's the point, so
runs can be compared across time, devices and configurations — so stage the
new/changed `.yaml`/`.md` files after each run. Pass `--no-save` to skip
recording. Uses PyYAML (`pip install pyyaml`).

## Workflow: fixing a bug

1. **Reproduce** — get the app to the broken state on a device:
   ```powershell
   python scripts/tools/launch.py --restart --all
   python scripts/tools/ui.py state
   python scripts/tools/ui.py key 23        # or tap/type as needed
   python scripts/tools/capture.py --all    # visual evidence
   ```
   For a known regression, first find the failing test:
   `python scripts/tests/run.py --all` (or `--test <name>`).
2. **Add/adjust a test that reproduces it** in `scripts/tests/url_field_tests.py`
   (or a new `*_tests.py` module registered from `run.py`). A bug without a
   failing test is not done.
3. **Fix the code** (usually `app/src/main/java/fulguris/...`), check for
   compile/lint errors, then:
   ```powershell
   python scripts/tools/install.py --build --all
   python scripts/tools/launch.py --restart --all
   ```
4. **Verify** — rerun the full suite, not just the fixed test:
   ```powershell
   python scripts/tests/run.py --all
   ```
   Both devices must pass. Capture screenshots for visual UI changes.
5. **Iterate** until green. Do not declare a fix without a full-suite pass.

## Workflow: adding a new feature

1. **Implement** in the usual places:
   - UI code: `app/src/main/java/fulguris/` (activities, `view/` widgets)
   - Layouts/resources: `app/src/main/res/`
   - Watch out for TV vs phone: anything involving D-pad, remote control,
     focus, or the on-screen keyboard must work in **both** touch mode and
     directional (leanback) mode.
2. **Build & deploy**:
   ```powershell
   python scripts/tools/install.py --build --all
   python scripts/tools/launch.py --restart --all
   ```
3. **Explore manually with the tools** (`ui.py state/key/text/tap`, `capture.py`)
   on every device type to confirm the feature works and existing behavior is
   intact (especially the URL field focus/edit states and the WebView).
4. **Add tests** covering the new behavior to `scripts/tests/` and rerun
   `python scripts/tests/run.py --all` — the new tests **and** the whole
   existing suite must pass on all devices.
5. Update this file's test list if a new test module is introduced.

## Conventions

- **Never run `adb` or `gradlew` directly** — use `scripts/tools/*`. If a tool
  is missing a capability, add it to `scripts/tools/adb.py` / a new
  `scripts/tools/<name>.py` and use that.
- **Never force a clean rebuild** (no `--clean`, no deleting `build/`) unless
  the user explicitly asks; incremental builds are the norm.
- **Never commit without explicit user approval.** Staging is fine; committing is not.
- Windows PowerShell: quote paths that contain spaces; adb output must be
  decoded as UTF-8 (handled by `scripts/tools/adb.py` — don't bypass it).
- TV gotchas baked into the tooling: the leanback IME is fullscreen (field text
  during edit may read the IME hint), and `dumpsys window windows` is the
  reliable way to detect the autocomplete popup.
- Keep tests deterministic: reset state at the start of each test (settle +
  navigate, clear the field) rather than relying on the previous test's end
  state. The runner does NOT restart the app between tests by default (much
  faster on the TV); pass `--restart` to `run.py` for a fresh launch per test.
  Tests that genuinely need a fresh launch (initial focus, clean tab state) call
  `adb.restart(...)` / `_reset(..., restart=True)` themselves — fine even in
  no-restart mode.
  The session persists tabs across runs, and the runner closes the tabs each
  test created after the test (hygiene — tab count has NO performance impact,
  the browser runs hundreds fine). Pass `--keep-tabs` to `run.py` to leave them.
- Localization: see `L10N.md` and `.github/copilot-instructions.md` — string
  work uses `subs/l10n/android/strings.py`, not hand-edited XML.
