---
type: bug
status: draft
updated: 2026-10-24
---
# Throttle tab regeneration driven by animation bindings

## Goal

Attaching a continuous animation (e.g. "sine(0.1)") to any tab-generator parameter makes GeneratorRegistry's change handler request a full translation-buffer regeneration on every tick, so a background thread regenerates the whole buffer back-to-back for as long as the binding runs, allocating a fresh direct TabBuffer each time (PERFORMANCE_REVIEW.md findings #1 and #6). Either mark generator params withNoAnimate(), debounce regeneration, or both — and reuse/double-buffer the TabBuffer instead of reallocating it per regeneration.

## Dependencies

_None._

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
