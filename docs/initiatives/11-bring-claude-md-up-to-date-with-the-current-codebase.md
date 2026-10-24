---
type: docs
status: designed
updated: 2026-10-24
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

- [ ] Build & Run states Java 25 (Corretto, bundled runtime), matching ct#2 as built, and describes the `CTHUGHA_JAVA_HOME` override.
- [ ] The Key Dependencies table has no version numbers, covers every library the app relies on directly (including janino and javacv), and points to `app/build.gradle` / `remote-ui/package.json` for versions.
- [ ] The "Current Branch Context" section is gone.
- [ ] Every class, file, path, task and command named in CLAUDE.md has been checked against the code and either exists as described or has been corrected or removed (e.g. the `smokeTest` task, `src/dist/` contents, the phase list, package locations).
- [ ] Wording is present tense, describes the code as it is, and has no history and no links to initiatives.
