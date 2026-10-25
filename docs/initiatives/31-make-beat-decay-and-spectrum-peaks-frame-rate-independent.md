---
type: bug
status: done
tags: [smoothness, render-core]
updated: 2026-10-25
docs_impact:
  - docs/SCREEN_CONFIGS.md
---
# Make beat decay and spectrum peaks frame-rate independent

## Goal

The beat detector's published strength fades per processed frame, and `AudioPipeline.update()` runs `freqProc.process()` once per displayed frame (`display/AudioPipeline.java:130`, from `WavePhase.frameUpdate()`). So beats fade at a speed set by the refresh rate: on a 24 Hz panel 2.5× slower than tuned, on 144 Hz 2.4× faster. That leaves beat-driven bindings (`bass() > 0.7` triggers, `beat()`-driven scripts) out of step with the flames, which ct#8 made frame-rate independent.

render-core 1.0.0-RC9 (on `develop` since 9e0f1af) times both against the wall clock: `BeatDetector` takes a `Duration decayTime` (full scale to zero), and `SpectrumAnalyser`'s peak ticks use time-based defaults, which the app already uses (it never calls `withPeakDynamics`), so spectrum peaks need no app change. The app still builds `BeatDetector` with the deprecated per-frame constructor (`display/AudioPipeline.java:73`, `:136`), which RC9 converts at 60 fps, so behaviour is unchanged but the setting is still per-frame:

- `[BeatDetector] decay` in `cthugha.ini` (`app/src/dist/cthugha.ini:160`, read by `BeatDetectorConfig`, `display/BeatDetectorConfig.java:99`) and the Audio tab's "Decay" slider (`display/BeatDetectorSettingsNode.java:78`, described as "per-frame … assuming ~60fps", range 0–0.5 per frame).
- Saved beat presets (`beatpresets/`) capture the Beat Detector subtree's leaf values, so they carry the per-frame value too.

Move the setting to time and pass it to the `Duration` constructor; existing per-frame values convert at 60 fps.

## Verification

- Unit tests: `BeatDetectorConfigTest` (old `decay` key converted at 60 fps, `decay_time` wins, conversion clamped to 0.02–5 s, and a beat fading by the same amount over 0.25 s of updates at 24 Hz and 60 Hz) and `LegacyValuesTest` (screen-config and preset paths).
- Live, 2026-10-25: the auto-saved session's `Audio/Beat Detector/Decay` = 0.156 per frame was applied as `Decay Time` = 0.107 s on restore and saved back under the new name.

## Dependencies

_None._

## Related

- [8 Make the flame simulation independent of display frame rate](08-make-the-flame-simulation-independent-of-display-frame-rate.md)
- [https://github.com/duckAsteroid/opengl-jogl-template/issues/32](https://github.com/duckAsteroid/opengl-jogl-template/issues/32)

## Decisions

- **Fade time in seconds**: the setting is how long a full-strength beat takes to fall to zero (render-core's `decayTime`). Today's 0.156 per frame becomes 0.107 s. (Owner, 2026-10-25.)
- (implementation) **Renamed, so old values can't be misread.** The Audio tab parameter becomes "Decay Time" (0.02–5 s, default 1 s, render-core's default) and the `cthugha.ini` key `[BeatDetector] decay_time`. A per-frame value can't be confused with a time, because it lives under the old name.
- (implementation) **Old per-frame values convert at 60 fps** (`seconds = 1 / (perFrame × 60)`, clamped to the range; 0 or less → the maximum): `BeatDetectorConfig.loadTuning` reads the old `decay` key when `decay_time` is absent, and `ScreenConfigParams.apply` rewrites a saved `…Beat Detector/Decay` value (screen configs, the auto-saved session) or a preset's `Decay` value to `Decay Time` before applying, so every load path converts.
- (implementation) `AudioPipeline` builds `BeatDetector` with the `Duration decayTime` constructor and render-core's `DEFAULT_NOISE_FLOOR` (what the per-frame constructor used).

## Open questions

_None._

## Acceptance criteria

- [x] `BeatDetector` is built with the time-based constructor; no deprecated per-frame decay call remains.
- [x] The decay setting in `cthugha.ini`, the Audio tab slider and its description are in time units; an existing per-frame `decay` value in `cthugha.ini` and in saved beat presets is converted at 60 fps, so a setup tuned at 60 Hz behaves the same (unit-tested on the conversion).
- [x] A beat fades in the same wall-clock time at 24 Hz and 60 Hz (unit test driving `BeatDetector.update(magnitudes, dt)` at both rates, or a live check on DP-4 vs eDP-1).
- [x] `cthugha.ini` comments describe the setting in time units (and `docs/SCREEN_CONFIGS.md` describes the conversion of saved values).
