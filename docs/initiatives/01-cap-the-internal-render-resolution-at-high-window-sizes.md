---
type: perf
status: in-progress
updated: 2026-10-25
tags: [smoothness]
branch: develop
---
# Size the internal render buffer from the display at a chosen resolution

## Goal

Remove the remaining 150–450ms frame stalls seen when running fullscreen, and make the internal render resolution a deliberate choice rather than an accident of the configured window size.

Today the indexed render buffer (translate, two blur passes, palette conversion input) is sized once in `CthughaWindow#init` (`CthughaWindow.java:279`) from `render_width`/`render_height`, defaulting to the window size *at init*. Fullscreen is applied afterwards (`CthughaWindow.java:484`), so the buffer is always the configured windowed size (2000×1000 in the shipped cthugha.ini, ~2 Mpx) and the final palette pass stretches it to the real output (3840×2160 on the dev machine). Consequences:

- **Aspect distortion:** a 2:1 buffer is shown on a 16:9 display, so fullscreen stretches the image vertically. Changing window size or toggling fullscreen never changes the buffer.
- **Cost unrelated to output:** the per-pixel simulation cost is set by the windowed size in cthugha.ini, not by the display.

Benchmarked 2026-10-24 (fullscreen on DP-4, FrameTimeStats): `render_width/height = 50%` (1000×500) removed mid-run stalls over 100ms; worst frames fell from 260–440ms to 33–86ms. The final pass is nearest-neighbour (`renderTex` uses `Filter.NEAREST`, `CthughaWindow.java:401`, and `IndexPaletteRenderer` must sample whole palette indices), so a lower buffer resolution shows as visibly blockier pixels — closer to original 320×200 Cthugha, but a change to the look.

## Decisions

- Yes. The buffer follows the actual output (window, or monitor when fullscreen): toggling fullscreen or resizing rebuilds the R16 textures, FBOs and translation map. A brief stale frame and a restarted flame trail at the moment of switching are accepted. (Owner, 2026-10-25.)
- **Default size: the output's shape, capped at 1920×1080.** With no size keys set, the buffer has the output's aspect ratio and is scaled down evenly to fit within the cap; an output that already fits is used 1:1. At 3840×2160 fullscreen that is 1920×1080 (~2 Mpx, today's cost); a 2000×1000 window gives 1920×960.
- **The cap is a built-in default, overridable by a new `[display] render_max = WxH` key** in `cthugha.ini`; `render_max = 0` means no cap (full output resolution).
- **`render_width`/`render_height` override the cap.** Pixel values (`"1280"`, `"1280px"`) fix the buffer size whatever the output; percentage values (`"50%"`) are taken of the *current* output and re-evaluated whenever it changes, instead of the window size at init.
- (implementation) **Resizes are debounced** (~250 ms after the last framebuffer-size event, from render-core's `ResizeListener`), so dragging a window edge rebuilds once.
- (implementation) **On a size change** `displayTex`, `renderTex`, `flameTex` and the three morph map textures (ct#28) get new storage at the new size in place (`Texture.generate` re-specifies the same GL texture), so their FBOs and the renderers keep their references; `JCthugha.resizeBuffer` sets `bufferWidth/Height` and swaps in an identity table at the new size, which fills both morph slots; then a regeneration is requested through ct#6's coalesced `TranslationRegenerator`, and the real table morphs in from the identity one. A result computed at the old size is discarded by the table epoch, and `uploadSpare` ignores any table whose size doesn't match the textures.
- (implementation) `RenderSize` (`display/`) holds the sizing rules and `parseDimension`, shared with the initial window sizing. `cthugha.ini` documents `render_max`.

## Dependencies

_None._

## Related

- [6 Throttle tab regeneration driven by animation bindings](06-throttle-tab-regeneration-driven-by-animation-bindings.md)

## Open questions

_None._

## Acceptance criteria

- [x] With no size keys, the buffer is the output's shape fitted within 1920×1080: 1920×1080 at 3840×2160 fullscreen, and 1:1 for a window that already fits (`RenderSizeTest`; live: 2000×1000 window → 1920×960, fullscreen 3840×2160 → 1920×1080).
- [x] Toggling fullscreen or resizing the window rebuilds the buffer at the new size; the image is not stretched out of its aspect ratio (checked live on DP-4: radial waves stay round fullscreen and windowed).
- [x] `render_max = WxH` changes the cap, `render_max = 0` removes it, pixel `render_width/height` fix the size, and percentage values follow the current output (unit-tested on the size computation).
- [x] Waves, quotes, flashes and tab presets render correctly after a size change (screenshots after each toggle; presets load at `resolution()`, the current buffer size).
- [ ] A fullscreen benchmark on DP-4 (unlocked session) shows no frames over 100 ms in steady state, recorded in this initiative. Pending: DP-4 is at 24 Hz (2026-10-25); benchmark with it back at 60 Hz.
- [x] `cthugha.ini`'s comments and project docs (`CLAUDE.md`, `docs/RENDERING_PIPELINE.md`) describe the sizing.
