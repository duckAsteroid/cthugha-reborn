---
type: bug
status: designed
updated: 2026-10-25
tags: [smoothness, tab-generators]
---
# Throttle tab regeneration driven by animation bindings

## Goal

Attaching a continuous animation (e.g. "sine(0.1)") to a tab-generator parameter makes `GeneratorRegistry`'s change handler request a full translation-table regeneration on every tick (`display/CthughaWindow.java:302`). `BackgroundWorkQueue.submit` drops requests while one is running (`work/BackgroundWorkQueue.java:14`), so a background core regenerates back-to-back for as long as the binding runs, every dropped tick posts a "Tab calculation in progress" notification, and each run allocates a fresh 8 MB direct `TabBuffer` (`JCthugha.java:275`). The same drop path also loses the final value of any burst of changes (a slider drag, a quick double trigger): the table on screen can lag the parameter indefinitely.

Stop continuous animation of generator parameters, make regeneration always converge on the latest values, and stop allocating a table per run.

## Decisions

- **Generator parameters are not animatable.** Every value under the selected tab generator, and the `Generator` picker itself (`tab/GeneratorRegistry.java:96`), is marked `withNoAnimate()`, so the remote UI offers no "Animate" control on them and the server rejects animation requests with 400 (`remote/RemoteServer.java:546`). A continuous per-tick script is the wrong fit for a parameter whose change regenerates the whole translation table off-thread. Edge-triggered bindings can still set them one-off.
- (implementation) **Saved continuous bindings on generator parameters stop driving them.** `isAnimatable()` is checked only at creation time today, so `ContinuousBinding` also skips a resolved target that is not animatable on each tick and reports it as the binding's error, so a config or `.current.json` saved before this change no longer drives regeneration.
- **Regeneration coalesces.** At most one translation regeneration runs at a time; a change that arrives while one is running marks it stale, and when it finishes it runs once more with the then-current values, so the latest values always land. No "Tab calculation in progress" notification is posted for a coalesced request. This covers parameter changes and new-generator picks.
- (implementation) **Two alternating TabBuffers.** Regeneration computes into a spare buffer of the current size while the other is on screen, and the two swap when the result is uploaded; a buffer is reallocated only when the buffer size changes.

## Dependencies

_None._

## Related

- [28 Blend between two translation maps over time](28-blend-between-two-translation-maps-over-time.md)

## Open questions

_None._

## Acceptance criteria

- [ ] No value under the selected tab generator, nor the `Generator` picker, offers "Animate" in the remote UI; `POST /api/v1/params/{path}/animation` on one returns 400.
- [ ] A continuous binding restored from a config onto a generator parameter does not change it, and shows an error on the binding.
- [ ] Changing a generator parameter many times while a regeneration is running leaves the on-screen table matching the final value, with at most one extra regeneration after the running one (unit-tested on the coalescing logic).
- [ ] No "Tab calculation in progress" notification is posted.
- [ ] Repeated regenerations at a fixed buffer size allocate no new `TabBuffer`.
- [ ] Project docs (`CLAUDE.md`, `docs/PARAMS.md` as relevant) describe the behaviour.
