---
type: feature
status: superseded
tags: [tab-generators]
updated: 2026-10-25
superseded_by: 28
status_note: "Rolled into ct#28: the two tab slots and the animatable Mix are now ct#28's morph mechanism."
---
# Breathing morph: two tab slots with an animatable mix

> [!NOTE]
> **Superseded** by [#28 Blend between two translation maps over time](28-blend-between-two-translation-maps-over-time.md) (2026-10-25): Rolled into ct#28: the two tab slots and the animatable Mix are now ct#28's morph mechanism.

## Goal

Let the translation table swing continuously between two maps (A→B→A…), a slow "breathing" morph, rather than only morphing once on a map change.

ct#28 keeps two map textures resident and morphs between them with a blend factor t that runs 0→1 once per map change. This idea exposes that structure directly:

- **Two tab slots, A and B.** Loading a preset, picking a generator or randomising chooses which slot the new table goes into (e.g. a slot selector on the Tab tab, or "load into A" / "load into B" actions).
- **A "Mix" parameter** (0 = all A, 1 = all B), an ordinary animatable parameter, so a continuous binding such as `sine(0.05)` gives a breathing morph, a beat trigger can flick it, and the remote UI can scrub it by hand. Animating it is free: it is a shader uniform, with no regeneration (unlike the generator parameters ct#6 makes non-animatable).

Compared with a dedicated ping-pong mode (which two maps, period, on/off), this reuses the binding system for the motion and gives manual control too. It may also reframe ct#28's one-shot morph as "load into the other slot, then animate Mix toward it over the Morph duration".

## Dependencies

_None._

## Open questions

- How do a ct#28 morph and a user-driven Mix interact? When a map change arrives while a binding is driving Mix, does the morph take over Mix for its duration (and the binding resume after), does the new table go into the slot Mix is currently furthest from without moving Mix, or does setting Mix by hand/binding switch the automatic morph off?
- How does the user choose which slot a new table loads into: an explicit "target slot" selector on the Tab tab, separate "into A" / "into B" actions on presets and generators, or automatic (always the slot not currently dominant)?

## Acceptance criteria

_What must be true when this is done._
