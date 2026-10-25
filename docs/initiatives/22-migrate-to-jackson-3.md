---
type: chore
status: draft
updated: 2026-10-25
tags: [upgrades]
---
# Migrate to Jackson 3

## Goal

Move from Jackson 2.x (`com.fasterxml.jackson.core:jackson-databind`) to Jackson 3.x (`tools.jackson.core:jackson-databind`, latest 3.2.4 as of 2026-10-18). The group and Java package rename (`com.fasterxml.jackson` → `tools.jackson`) touches 21 files under `app/src`, chiefly `ParamSerializer`, `ScreenConfigStore` and the remote API. Jackson 3 also changes defaults (immutable `JsonMapper` builders, unchecked exceptions), so the JSON emitted by the remote API and stored in `configs/*.json` must be checked byte-for-byte against the current output. Plan this together with the Javalin 7 migration, since Javalin's JSON mapper integration may need a particular Jackson major (unclear until Javalin 7's docs are checked).

## Dependencies

- [3 Update all dependencies to their latest releases](03-update-all-dependencies-to-their-latest-releases.md) — start from dependencies that are already up to date within their current major

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
