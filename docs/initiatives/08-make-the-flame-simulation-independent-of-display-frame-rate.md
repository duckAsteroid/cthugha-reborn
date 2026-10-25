---
type: feature
status: done
updated: 2026-10-25
tags: [smoothness]
docs_impact:
  - CLAUDE.md
  - docs/RENDERING_PIPELINE.md
---
# Make the flame simulation independent of display frame rate

## Goal

Each displayed frame runs exactly one simulation step (`display/CthughaWindow.java:546–556`): translate, the phases' `indexedRender` (waves, flashes and quotes baked into the buffer), then the x/y blur with the fade applied in `yBlur` (`:446`). So the flames run 2.4x faster on a 144 Hz panel than on a 60 Hz one, and any slow frame visibly stalls them. Step the simulation at a fixed rate in wall-clock time, and expose that rate as a live control.

## Decisions

- **Fixed-rate steps, not a time-scaled fade.** The translate pass moves every pixel by a whole map displacement per step, so a fractional step isn't possible; the only way to make motion speed independent of the display is to run a whole number of steps per displayed frame from a time accumulator: 0 or 1 at 144 Hz, exactly 1 at 60 Hz (today's behaviour), more when the rate exceeds the refresh rate.
- **A live "Speed" parameter on the Render tab**, in steps per second, default 60 (today's look on a 60 Hz panel), range about 10–120. It is an ordinary animatable parameter captured by screen configs, so scripts can drive slow-motion and speed-up moves; changing it only changes how many steps run per frame, so animating it costs nothing.
- In-buffer (`BUFFER`/`BOTH`) waves, flashes and quotes are drawn on each step, so they update at the step rate; overlay (`OVERLAY`) elements still draw on every displayed frame. A displayed frame that runs no step leaves the indexed buffer as it is and re-presents it.
- (implementation) **Bounded catch-up.** A frame runs at most `ceil(speed / refresh) + 1` steps; time beyond that is dropped from the accumulator, so a stall doesn't turn into a burst of slow catch-up frames.
- (implementation) When several steps run in one frame, the phases' `indexedRender` runs on each, with the same audio data. Per-frame work that isn't drawing moves to a new `RenderPhase.frameUpdate()`, called once per displayed frame before the steps: `WavePhase` uploads audio, runs the FFT/beat detection and resyncs its wave list there, so overlay waves stay live on frames that run no step and the FFT doesn't run twice on frames that run two.
- (implementation) `StepClock` (`display/`) holds the accumulator. It starts half a step in, so with Speed equal to the refresh rate vsync jitter of up to half a frame still gives exactly one step per frame. The refresh rate for the catch-up bound comes from `getCurrentMonitor().refreshRate()`, re-read once a second.

## Dependencies

_None._

## Related

- [9 Decouple simulation from display with interpolated presentation](09-decouple-simulation-from-display-with-interpolated-presentat.md)

## Open questions

_None._

## Acceptance criteria

- [x] At a given Speed, flame motion takes the same wall-clock time on a 60 Hz and a faster panel (or with `target_fps` forcing a different frame rate), checked by counting steps per second in a test of the accumulator (`StepClockTest`: 600 ± 2 steps in 10 s at Speed 60 on 30, 60 and 144 Hz).
- [x] Speed 60 on a 60 Hz panel looks the same as today: exactly one step per frame, including with ±30% frame jitter (`StepClockTest`).
- [x] "Speed" appears on the Render tab, can be animated, and round-trips through screen configs (checked live over the remote API; captured as `Render/Speed` in `.current.json`).
- [x] After a stall, at most the bounded number of steps runs in the next frame (`StepClockTest`).
- [x] Project docs (`CLAUDE.md` rendering pipeline, `docs/RENDERING_PIPELINE.md`) describe the stepping.
