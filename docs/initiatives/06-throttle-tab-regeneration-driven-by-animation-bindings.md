---
type: bug
status: done
updated: 2026-10-25
tags: [smoothness, tab-generators]
docs_impact:
  - CLAUDE.md
  - docs/RENDERING_PIPELINE.md
---
# Throttle tab regeneration driven by animation bindings

## Goal

Attaching a continuous animation (e.g. "sine(0.1)") to a tab-generator parameter makes `GeneratorRegistry`'s change handler request a full translation-table regeneration on every tick (`display/CthughaWindow.java:302`). `BackgroundWorkQueue.submit` drops requests while one is running (`work/BackgroundWorkQueue.java:14`), so a background core regenerates back-to-back for as long as the binding runs, every dropped tick posts a "Tab calculation in progress" notification, and each run allocates a fresh 8 MB direct `TabBuffer` (`JCthugha.java:275`). The same drop path also loses the final value of any burst of changes (a slider drag, a quick double trigger): the table on screen can lag the parameter indefinitely.

Stop continuous animation of generator parameters, make regeneration always converge on the latest values, and stop allocating a table per run.

## Decisions

- **Generator parameters are not animatable.** `GeneratorRegistry` marks every value under every tab generator `withNoAnimate()` at construction (the `Generator` picker already was), so the remote UI offers no "Animate" control on them and the server rejects animation create and edit requests with 400. A continuous per-tick script is the wrong fit for a parameter whose change regenerates the whole translation table off-thread. Edge-triggered bindings can still set them one-off; ct#28's morph is the way to animate tab changes.
- (implementation) **Saved continuous bindings on generator parameters stop driving them.** `ContinuousBinding` skips a resolved target that is not animatable on each tick, leaves it under manual control, and reports `NOT_ANIMATABLE_ERROR` as its error, so a config or `.current.json` saved before this change no longer drives regeneration. Deleting an animation (`DELETE /api/v1/params/{path}/animation`) no longer requires an animatable target, so such a binding can be removed from the remote UI.
- **Regeneration coalesces.** `TranslationRegenerator` runs at most one regeneration at a time; requests made while one runs merge into a single pending run, which runs once more with the then-current values, so the latest values always land. A randomising request (generator pick, "Randomise") is not downgraded by a later plain regenerate. The "Tab calculation in progress" notification is gone. This covers parameter changes, generator picks and "Randomise", which now also runs off the GL thread.
- **No on-screen indicator for tab regeneration.** Runs start on a plain virtual thread rather than through `BackgroundWorkQueue`, so `WorkPhase` shows no "Tab calculating…" spinner or label, and the generator's name is notified only after a generator pick or "Randomise", not after every parameter-driven regeneration. Regenerations are frequent and brief, so the messages were noise. (Owner, 2026-10-25.)
- (implementation) **Two alternating TabBuffers.** A run computes into `JCthugha`'s spare buffer (`computeTranslation`), the GL thread adopts and uploads it (`adoptTranslation`), and the run waits for that before the next one, so the replaced buffer becomes the next spare; a buffer is allocated only when the size changes. A table loaded from elsewhere (a preset, `loadTabBuffer`) bumps an epoch, and a run computed against the older table is discarded instead of overwriting it.

## Dependencies

_None._

## Related

- [28 Blend between two translation maps over time](28-blend-between-two-translation-maps-over-time.md)

## Open questions

_None._

## Acceptance criteria

- [x] No value under the selected tab generator, nor the `Generator` picker, offers "Animate" in the remote UI; `POST /api/v1/params/{path}/animation` on one returns 400 (checked live on `Sine Grid Warp/Freq X`).
- [x] A continuous binding restored from a config onto a generator parameter does not change it, and shows an error on the binding, and can be deleted.
- [x] Changing a generator parameter many times while a regeneration is running leaves the on-screen table matching the final value, with at most one extra regeneration after the running one (unit-tested on the coalescing logic).
- [x] No "Tab calculation in progress" notification, "Tab calculating…" work indicator, or per-tweak generator-name notification is shown.
- [x] Repeated regenerations at a fixed buffer size allocate no new `TabBuffer`.
- [x] Project docs (`CLAUDE.md`, `docs/RENDERING_PIPELINE.md`) describe the behaviour.
