---
type: bug
status: draft
tags: [smoothness, render-core]
updated: 2026-10-25
---
# Make beat decay and spectrum peaks frame-rate independent

## Goal

The beat detector's published strength and the spectrum waves' peak-hold run per processed frame, and `AudioPipeline.update()` runs `freqProc.process()` once per displayed frame (`display/AudioPipeline.java:130`, from `WavePhase.frameUpdate()`). So beats and peaks fade at a speed set by the refresh rate: on a 24 Hz panel 2.5× slower than tuned, on 144 Hz 2.4× faster. That leaves beat-driven bindings (`bass() > 0.7` triggers, `beat()`-driven scripts) and spectrum peaks out of step with the flames, which ct#8 made frame-rate independent.

The decay logic lives in render-core: `BeatDetector` subtracts a `final` `decayPerFrame` each update, and `FrequencyRenderer` dwells `dwellFrames` and sags `peakSagPerFrame`. Once render-core takes these per second and scales by elapsed time, bump render-core and move the app's settings to per-second:

- `[beats] decay` in `cthugha.ini` (`app/src/dist/cthugha.ini:155`, read by `BeatDetectorConfig`, `display/BeatDetectorConfig.java:99`) and the Audio tab's "Decay" slider (`display/BeatDetectorSettingsNode.java:78`, described as "per-frame … assuming ~60fps") become per second; existing per-frame values convert at 60 fps (0.156 per frame → 9.36 per second).
- Saved beat presets (`beatpresets/`) carrying the old per-frame decay convert on load the same way.

## Dependencies

- [https://github.com/duckAsteroid/opengl-jogl-template/issues/32](https://github.com/duckAsteroid/opengl-jogl-template/issues/32) — upstream render-core change to time-based ballistics; then bump render-core and convert the app's decay setting

## Related

- [8 Make the flame simulation independent of display frame rate](08-make-the-flame-simulation-independent-of-display-frame-rate.md)

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
