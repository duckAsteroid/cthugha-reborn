---
type: perf
status: draft
updated: 2026-10-25
tags: [smoothness, render-core]
---
# Stop render-core wave setters allocating and rebuilding every frame

## Goal

In render-core, AudioWave/RadialWave.setAmplitudeFunction() marks the VBO dirty unconditionally (rebuilding every vertex, boxing a Float each), and setTransform()/setLineColour() copy their argument and enqueue a capturing lambda on every call. WavePhase calls these per frame per wave pass. This app now skips setAmplitudeFunction when amplitude/ellipse are unchanged (WavePhase.CachedAmplitude); fix the setters upstream (no-op on equal values, write uniforms without per-call copies/lambdas) and drop the workaround.

## Dependencies

_None._

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
