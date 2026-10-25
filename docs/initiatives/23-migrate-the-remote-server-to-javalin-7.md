---
type: chore
status: draft
updated: 2026-10-25
tags: [upgrades]
---
# Migrate the remote server to Javalin 7

## Goal

Move `io.javalin:javalin` from 6.6.0 to 7.x (latest 7.2.3 as of 2026-08-12). Javalin is used in 5 files under `app/src`, centred on `RemoteServer` (routes, bearer-token auth, static SPA serving) and `RemoteEventBroadcaster` (SSE). Javalin 7 changes the config and routing APIs. Behaviour of the REST API, SSE stream and SPA serving must be unchanged, as checked by the smoke test's remote-API checks and a phone session. Plan alongside the Jackson 3 migration, since the JSON mapper Javalin 7 expects decides the order the two land in.

## Dependencies

- [3 Update all dependencies to their latest releases](03-update-all-dependencies-to-their-latest-releases.md) — start from dependencies that are already up to date within their current major

## Related

- [22 Migrate to Jackson 3](22-migrate-to-jackson-3.md)

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
