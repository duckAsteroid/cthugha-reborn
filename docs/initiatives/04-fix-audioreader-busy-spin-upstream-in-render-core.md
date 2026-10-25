---
type: bug
status: draft
updated: 2026-10-25
tags: [smoothness, render-core]
---
# Fix AudioReader busy-spin upstream in render-core

## Goal

render-core's AudioReader.run() polls AudioLine.available() and calls Thread.yield() when it is 0 — a busy-spin that burns a core, and on a virtual thread resubmits the continuation to the ForkJoin pool on every yield (profiled ~20MB/s of garbage and ~14x the render thread's CPU samples). This app works around it with PacedAudioSource (parks 0.5ms on an empty line) and a platform audio-capture thread in AudioPipeline. Fix it in render-core (block/park instead of yield), bump the render-core dependency, then delete PacedAudioSource.

## Dependencies

- [https://github.com/duckAsteroid/opengl-jogl-template/issues/29](https://github.com/duckAsteroid/opengl-jogl-template/issues/29) — upstream render-core fix; then bump render-core and delete PacedAudioSource

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
