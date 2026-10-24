---
type: perf
status: draft
updated: 2026-10-24
---
# Cap the internal render resolution at high window sizes

## Goal

Remove the remaining 150–450ms frame stalls seen fullscreen at 4K. The internal render buffer defaults to the window size (CthughaWindow#init, `render_width`/`render_height` in cthugha.ini), so a 3840×2160 fullscreen window runs translate, two blur passes and palette conversion over 8.3 Mpx, and on PRIME render-offload laptops every swap then copies the frame to the display GPU. Benchmarked 2026-10-24 (fullscreen DP-4, FrameTimeStats): at `render_width/height = 50%` mid-run stalls over 100ms disappeared and worst frames fell from 260–440ms to 33–86ms. Lowering resolution coarsens the flame spread (closer to original 320×200 Cthugha), so the default is a look decision as much as a performance one.

## Dependencies

_None._

## Open questions

- What should the default be: a fixed cap on the longest side (e.g. 1920px), a percentage of the window, or keep full window resolution and only document the setting?

## Acceptance criteria

_What must be true when this is done._
