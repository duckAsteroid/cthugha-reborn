---
type: bug
status: done
updated: 2026-10-25
docs_impact: "none: PacedAudioSource was never described in the project docs"
tags: [smoothness, render-core]
---
# Fix AudioReader busy-spin upstream in render-core

## Goal

render-core's AudioReader.run() polled AudioLine.available() and called Thread.yield() when it was 0: a busy-spin that burnt a core and, on a virtual thread, resubmitted the continuation to the ForkJoin pool on every yield (profiled ~20MB/s of garbage and ~14x the render thread's CPU samples). render-core 1.0.0-RC7 parks for 0.5ms on an empty line instead. The app is on RC7, PacedAudioSource is deleted, and AudioPipeline keeps the audio-capture thread a platform thread, as render-core recommends.

## Dependencies

- [https://github.com/duckAsteroid/opengl-jogl-template/issues/29](https://github.com/duckAsteroid/opengl-jogl-template/issues/29) — upstream render-core fix; then bump render-core and delete PacedAudioSource

## Open questions

_None._

## Acceptance criteria

- [x] render-core is on a release whose AudioReader parks on an empty line (1.0.0-RC7).
- [x] `PacedAudioSource` is deleted and `AudioPipeline` hands sources to `AudioReader` unwrapped.
- [x] With the app running, the `audio-capture` thread stays at a few percent of one core (measured ~2% on RC7).
