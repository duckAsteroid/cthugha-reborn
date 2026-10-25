---
type: perf
status: done
updated: 2026-10-25
docs_impact: "none: WavePhase.CachedAmplitude was never described in the project docs"
tags: [smoothness, render-core]
---
# Stop render-core wave setters allocating and rebuilding every frame

## Goal

render-core's AudioWave/RadialWave.setAmplitudeFunction() rebuilt the whole VBO on every call, and setTransform()/setLineColour() copied their argument and enqueued a capturing lambda on every call, while WavePhase calls these per frame per wave pass. render-core 1.0.0-RC7 makes them cheap: equal amplitude functions skip the rebuild (AmplitudeFunction.constant/ellipse return value-equal records) and the transform/colour setters write a preallocated last-write-wins slot. The app is on RC7 and WavePhase calls setAmplitudeFunction every frame with no cache of its own.

## Dependencies

- [https://github.com/duckAsteroid/opengl-jogl-template/issues/30](https://github.com/duckAsteroid/opengl-jogl-template/issues/30) — upstream render-core fix; then bump render-core and drop WavePhase.CachedAmplitude

## Open questions

_None._

## Acceptance criteria

- [x] render-core is on a release with no-op-on-equal wave setters (1.0.0-RC7).
- [x] `WavePhase.CachedAmplitude` is removed.
- [x] `RadialClockAnalyser` reads its per-frame matrix through `currentTransform()`, as RC7 makes `FrequencyRenderer.transform` private.
