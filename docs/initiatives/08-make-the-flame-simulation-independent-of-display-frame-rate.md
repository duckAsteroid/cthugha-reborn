---
type: feature
status: draft
updated: 2026-10-25
tags: [smoothness]
---
# Make the flame simulation independent of display frame rate

## Goal

The feedback loop advances one translate + blur/fade step per displayed frame, so the effect runs at different speeds on 60Hz vs 144Hz panels and any slow frame visibly stalls the flames. Step the simulation at a fixed rate (e.g. 60 steps/s with 0–2 catch-up steps per displayed frame), or scale the fade per step by the frame's time delta, so motion speed is constant in wall-clock time.

## Dependencies

_None._

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
