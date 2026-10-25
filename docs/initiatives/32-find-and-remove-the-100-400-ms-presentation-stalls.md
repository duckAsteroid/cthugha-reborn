---
type: spike
status: draft
tags: [smoothness, render-core]
updated: 2026-10-25
---
# Find and remove the 100-400 ms presentation stalls

## Goal

The app visibly freezes for a moment a few times every 10 s (the owner: "stuttering quite frequently - seems to freeze for a very brief moment"). Benchmarked 2026-10-25 fullscreen on eDP-1 (3840×2160@60 Hz, Quadro T2000 via PRIME render offload, GNOME Wayland): about 2–10 frames per 10 s take 100–420 ms, often a strikingly constant ~172 ms, in every build tested (before and after ct#1/ct#28) and at every render-buffer size tried.

Diagnostics (temporary `glFinish()` split points in `CthughaWindow.render()`, not committed) located the stall: the frame's own GPU work (per-frame updates, simulation steps, screen pass) finishes in 1–8 ms, the GPU sits at P0 at ~35–40% utilisation, and no other process uses the GPU except gnome-shell. The 100–420 ms is spent completing the *previous frame's buffer swap*: a `glFinish()` issued at the start of a frame with no GL work of our own since the last finish takes that long. So the stall is in presentation, not rendering.

render-core forces GLFW onto X11 on Linux (`GLWindow` constructor: `glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11)`, "to avoid libdecor issues on Wayland"), so on a Wayland session every frame goes NVIDIA → Xwayland → Mutter → PRIME → panel. NVIDIA under Xwayland is a known source of occasional long present stalls; the leading hypothesis is that native Wayland (or a different present path) removes them.

## Dependencies

_None._

## Related

- [1 Size the internal render buffer from the display at a chosen resolution](01-cap-the-internal-render-resolution-at-high-window-sizes.md)
- [9 Cross-fade between simulation steps for smooth presentation](09-decouple-simulation-from-display-with-interpolated-presentat.md)

## Measures

- **Question:** _what this spike must answer_
- **Hypothesis:** _what we expect, and why_
- **Measure:** _what is measured, and how_
- **Answer criteria:** _the threshold(s) that mean yes / no_
- **Time-box:** _e.g. 3 days_

## Findings

_Written when the spike completes: method, results against the criteria, conclusion, and whether the code should be adopted, adapted or abandoned (and where it lives)._

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
