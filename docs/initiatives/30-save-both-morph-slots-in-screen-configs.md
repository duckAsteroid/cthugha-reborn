---
type: feature
status: draft
tags: [tab-generators]
updated: 2026-10-25
---
# Save both morph slots in screen configs

## Goal

ct#28 gives the translation table two slots, A and B, with an animatable Mix between them. A screen config captures only the active tab generator and its parameters (`ScreenConfigParams`, `docs/SCREEN_CONFIGS.md`), and the table is regenerated from them on load, so a saved breathing setup (e.g. Spiral in A, Ripple in B, `sine(0.05)` on Mix) comes back with the same table in both slots and nothing visibly moves.

Record each slot's source in the config (its generator and parameter values, or the preset it was loaded from) and regenerate both on load, so a breathing morph round-trips. Today only one generator's parameters live in the parameter tree (`GeneratorRegistry` mounts the selected generator), so the second slot's source needs a home outside the live tree, e.g. through the `DynamicChildList`-style snapshot extension configs already use for bindings and waves.

## Dependencies

- [28 Morph between translation maps: two slots and an animatable Mix](28-blend-between-two-translation-maps-over-time.md) — the two morph slots exist only once ct#28 lands

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
