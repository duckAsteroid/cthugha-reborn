---
type: spike
status: draft
updated: 2026-10-25
tags: [smoothness]
---
# Decouple simulation from display with interpolated presentation

## Goal

Run the indexed simulation (translate, waves, blur) at its own fixed rate and resolution into alternating textures, and let the display pass present the latest completed step at the monitor's refresh rate — optionally upscaling with a smooth filter and cross-fading the last two steps for smoothness on 120Hz+ panels. A spike to measure whether this is worth the architectural change.

## Dependencies

_None._

## Measures

- **Question:** _what this spike must answer_
- **Hypothesis:** _what we expect, and why_
- **Measure:** _what is measured, and how_
- **Answer criteria:** _the threshold(s) that mean yes / no_
- **Time-box:** _e.g. 3 days_

## Findings

_Written when the spike completes: method, results against the criteria, conclusion, and whether the code should be adopted, adapted or abandoned (and where it lives)._

## Related

- [8 Make the flame simulation independent of display frame rate](08-make-the-flame-simulation-independent-of-display-frame-rate.md)
- [1 Cap the internal render resolution at high window sizes](01-cap-the-internal-render-resolution-at-high-window-sizes.md)

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
