---
type: feature
status: draft
updated: 2026-10-24
---
# Per-type notification toggles

## Goal

Let individual notification types be suppressed (e.g. just "Tab calculation in progress") in addition to the global Notifications toggle. The in-progress notice also fires repeatedly during rapid slider drags (CthughaWindow's regeneration callbacks notify whenever a job is already running), which may be the real irritant. From ideas.md #7.

## Dependencies

_None._

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
