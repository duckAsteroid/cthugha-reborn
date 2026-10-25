---
type: perf
status: draft
updated: 2026-10-25
tags: [smoothness]
---
# Size the internal render buffer from the display at a chosen resolution

## Goal

Remove the remaining 150–450ms frame stalls seen when running fullscreen, and make the internal render resolution a deliberate choice rather than an accident of the configured window size.

Today the indexed render buffer (translate, two blur passes, palette conversion input) is sized once in `CthughaWindow#init` (`CthughaWindow.java:279`) from `render_width`/`render_height`, defaulting to the window size *at init*. Fullscreen is applied afterwards (`CthughaWindow.java:484`), so the buffer is always the configured windowed size (2000×1000 in the shipped cthugha.ini, ~2 Mpx) and the final palette pass stretches it to the real output (3840×2160 on the dev machine). Consequences:

- **Aspect distortion:** a 2:1 buffer is shown on a 16:9 display, so fullscreen stretches the image vertically. Changing window size or toggling fullscreen never changes the buffer.
- **Cost unrelated to output:** the per-pixel simulation cost is set by the windowed size in cthugha.ini, not by the display.

Benchmarked 2026-10-24 (fullscreen on DP-4, FrameTimeStats): `render_width/height = 50%` (1000×500) removed mid-run stalls over 100ms; worst frames fell from 260–440ms to 33–86ms. The final pass is nearest-neighbour (`renderTex` uses `Filter.NEAREST`, `CthughaWindow.java:401`, and `IndexPaletteRenderer` must sample whole palette indices), so a lower buffer resolution shows as visibly blockier pixels — closer to original 320×200 Cthugha, but a change to the look.

## Dependencies

_None._


## Decisions

- Yes. The buffer follows the actual output (window, or monitor when fullscreen): toggling fullscreen or resizing rebuilds the R16 textures, FBOs and translation map. A brief stale frame and a restarted flame trail at the moment of switching are accepted. (Owner, 2026-10-25.)

## Open questions

- What should the default be: a fixed cap on the longest side (e.g. 1920px), a percentage of the window, or keep full window resolution and only document the setting?

## Acceptance criteria

_What must be true when this is done._
