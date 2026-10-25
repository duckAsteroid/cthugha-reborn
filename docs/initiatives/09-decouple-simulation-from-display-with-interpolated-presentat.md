---
type: spike
status: designed
updated: 2026-10-25
tags: [smoothness]
---
# Cross-fade between simulation steps for smooth presentation

## Goal

Once ct#8 steps the simulation at a fixed rate, a displayed frame often runs no step and re-presents the last one: at Speed 20 on a 60 Hz panel each step is shown for three frames, and at Speed 60 on a 144 Hz panel steps land unevenly. Find out whether cross-fading the last two completed steps by how far the display frame sits between them makes that motion visibly smoother, and what it costs.

Blending must happen after palette conversion: mixing two palette indices gives an unrelated third colour. The spike keeps the two most recent completed steps' `renderTex` (one extra R16 texture), palette-converts both to RGBA and blends them by the frame's phase between steps, in the display pass. Upscaling filters are out of scope: the screen pass keeps today's nearest-neighbour look.

## Decisions

- **Scope: cross-fade only.** Smooth upscaling of the buffer to the output is a separate look decision, not part of this spike. (Owner, 2026-10-25.)
- The spike builds on ct#1's buffer sizing and ct#8's fixed-rate stepping and "Speed" parameter, so both block it.

## Dependencies

- [1 Size the internal render buffer from the display at a chosen resolution](01-cap-the-internal-render-resolution-at-high-window-sizes.md) — the spike measures cost at the buffer size ct#1 sets
- [8 Make the flame simulation independent of display frame rate](08-make-the-flame-simulation-independent-of-display-frame-rate.md) — cross-fading needs fixed-rate steps to fade between

## Measures

- **Question:** Does cross-fading the last two simulation steps make slow or off-refresh motion visibly smoother, at a GPU cost low enough to leave on by default?
- **Hypothesis:** Yes for slow Speeds, where each step is held for several frames and the flames visibly judder; the cost is one extra palette lookup per output pixel plus a blend, well under 1 ms a frame on the Quadro T2000. Fast flames may look smeared rather than smoother.
- **Measure:** A toggle for the cross-fade. GPU time of the display pass with and without it, via `GL_TIME_ELAPSED` queries, at 1920×1080 buffer / 3840×2160 fullscreen on DP-4 (unlocked session). `FrameTimeStats` p99 and janky count over 60 s each way. The owner compares both by eye, A/B, at Speed 20, 40 and 60 with the same tab and palette.
- **Answer criteria:** Adopt if the owner judges Speed 20 and 40 visibly smoother with the cross-fade, the extra display-pass GPU time is under 1 ms a frame, and p99 and janky are no worse. Adopt as an option (off by default) if it is smoother only at some Speeds, or smears fast motion. Abandon if it is not visibly smoother.
- **Time-box:** 1 day.

## Findings

_Written when the spike completes: method, results against the criteria, conclusion, and whether the code should be adopted, adapted or abandoned (and where it lives)._

## Open questions

_None._

## Acceptance criteria

- [ ] `## Findings` records the measurements against the answer criteria, the owner's A/B verdict, and whether the code is adopted, adapted or abandoned, and on which branch it lives.
