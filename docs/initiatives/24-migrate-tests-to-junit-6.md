---
type: chore
status: draft
updated: 2026-10-25
tags: [upgrades]
---
# Migrate tests to JUnit 6

## Goal

Move `org.junit.jupiter:junit-jupiter` from the 5.x line to 6.x (latest 6.1.3 as of 2026-08-08), together with the matching `junit-platform-launcher` and a Mockito release that supports it. JUnit Jupiter is imported in 25 files under `app/src`. Both `test` and the tag-filtered `smokeTest` task (`useJUnitPlatform { includeTags 'smoke' }`) must still select and run the same tests as before.

## Dependencies

- [3 Update all dependencies to their latest releases](03-update-all-dependencies-to-their-latest-releases.md) — start from dependencies that are already up to date within their current major

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
