---
type: docs
status: done
updated: 2026-10-24
docs_impact:
  - CLAUDE.md
---
# Bring CLAUDE.md up to date with the current codebase

## Goal

CLAUDE.md has drifted: it says the build requires Java 17 (the toolchain is 25), its "Current Branch Context" section still describes the long-merged render_phase branch, and the Key Dependencies table lists versions that no longer match app/build.gradle (e.g. LWJGL). Refresh it to describe the code as it is now, and drop or rework the branch-context section so it doesn't go stale again.

## Dependencies

- [2 Bundle a pinned JDK 25 runtime with the app](02-bundle-a-pinned-jdk-25-runtime-with-the-app.md) — CLAUDE.md should describe the Java runtime and dependency versions as they end up after these land
- [3 Update all dependencies to their latest releases](03-update-all-dependencies-to-their-latest-releases.md) — CLAUDE.md should describe the Java runtime and dependency versions as they end up after these land

## Decisions

- **The Key Dependencies table lists library and purpose only, with no version numbers.** One line points to `app/build.gradle` and `remote-ui/package.json` as where versions live. The table gains the libraries it currently leaves out (e.g. janino, javacv). The one exception is Build & Run, which states "Java 25 (Corretto, bundled runtime)", because that changes only with a ticket like ct#2, not with routine version bumps.
- **The "Current Branch Context" section is deleted, with nothing in its place.** Work in flight is tracked by `in-progress` initiatives, which record their `branch`, and the existing Initiatives section already points agents to `docs/initiatives/`.

## Open questions

_None._

## Acceptance criteria

- [x] Build & Run states Java 25 (Corretto, bundled runtime), matching ct#2 as built, and describes the `CTHUGHA_JAVA_HOME` override.
- [x] The Key Dependencies table has no version numbers, covers every library the app relies on directly (including janino and javacv), and points to `app/build.gradle` / `remote-ui/package.json` for versions.
- [x] The "Current Branch Context" section is gone.
- [x] Every class, file, path, task and command named in CLAUDE.md has been checked against the code and either exists as described or has been corrected or removed. Corrections made:
  - Binding System no longer describes a "Bindings" tab, `UiHint.TARGET_PICKER`, the `paired-value-field` hint or the "Add Trigger" form. It now describes the `/animation` and `/triggers` sub-resource routes and `TriggerEditor`.
  - The phase list adds `VideoPhase`, `DebugBeatsPhase` and `WorkPhase`.
  - Wave Models covers all five types (adding `RadialClockModel`) and `RenderMode`.
  - Names that don't exist in this repo are corrected: `TranslateTextureRenderer`, `BlurTextureRenderer`, `SpectrumAnalyser` and `RadialSpectrumAnalyser` are render-core's, and `AudioSample` no longer exists.
  - `Action` moves to `params/action/`, and the tab names are corrected.
  - The doc paths move to `docs/`, the render-core skill reference no longer names a version, and the tab generator families are listed.
  - The single-test example now uses `JCthughaTest`; `TabBufferTest` has no tests, so `--tests` fails on it.
  - New sections cover session state and defaults, other packages, CLI flags, `smokeTest`, `dependencyUpdates` and the full `src/dist/` contents.
- [x] Wording is present tense, describes the code as it is, and has no history and no links to initiatives. The "now is" / "rejected alternatives" wording that remains is in the Initiatives section, where it instructs how docs are written.
