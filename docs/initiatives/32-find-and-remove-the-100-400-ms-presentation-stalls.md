---
type: spike
status: done
tags: [smoothness, render-core]
updated: 2026-10-25
docs_impact: "none: spike — findings recorded in the initiative"
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

- **Question:** where do the 100–420 ms freezes come from, and does a different present path remove them?
- **Hypothesis:** they come from NVIDIA PRIME presenting through Xwayland (render-core forces X11), not from our rendering; a native Wayland window avoids them.
- **Measure:** `FrameTimeStats` 10 s windows (fps, p50/p95/p99/max, janky = frames over 1.5× the median), fullscreen on eDP-1 (3840×2160@60 Hz, scale 2), 60 s runs, for each of: NVIDIA via Xwayland (today's default), NVIDIA native Wayland, Intel native Wayland. Platform chosen with `-Dcthugha.glfw.platform=wayland|x11` (a render-core RC9 `GLFWConfigurer`); Intel by launching without the PRIME offload variables.
- **Answer criteria:** after startup, no frame over 100 ms and p99 within 2 ms of the refresh interval, with the window actually visible on screen.
- **Time-box:** 1 day.

## Findings

Measured 2026-10-25 on branch `spike/ct32-wayland`, fullscreen on eDP-1 (3840×2160@60 Hz, scale 2), Ubuntu 26.04 / GNOME 50 Wayland, NVIDIA 595 driver, Dell Precision 5540.

**Both displays are wired to the integrated GPU.** `/sys/class/drm` shows eDP-1 and every DP connector (DP-4 included) on the Intel `i915` card; the NVIDIA card has no connectors. Every NVIDIA-rendered frame is copied to the Intel GPU for display.

| Present path | Visible | fps | p95 | p99 | Frames > 100 ms per 10 s (steady state) |
|---|---|---|---|---|---|
| NVIDIA (PRIME offload) via Xwayland — first runs | yes | ~60 | 17.0–17.2 ms | 19–33 ms | ~2 (100–420 ms) |
| NVIDIA (PRIME offload) via Xwayland — evening re-runs, 3 runs, ~3 min | yes | 59.6–60.0 | 18.8–19.4 ms | 19.4–20.2 ms | 0 (worst 50–68 ms) |
| NVIDIA (PRIME offload), native Wayland | **no** — dock entry only | 47–49 | 23 ms | 25 ms | 0 |
| Intel UHD 630, native Wayland | yes | 60.0 | 16.8 ms | 16.8–20.7 ms | 0 |

- **The 100–420 ms freezes are intermittent and outside the app.** They appeared in every build in the first runs and in none of the evening re-runs, including the unchanged `develop` build; render-core RC9 changed nothing in `GLWindow` presentation. The freezes are spent completing the previous frame's swap (diagnosed earlier with `glFinish` split points), i.e. on the NVIDIA → Intel → compositor path. Their trigger is not identified; `__GL_MaxFramesAllowed=1` made no measurable difference.
- **NVIDIA with native Wayland on this PRIME laptop never shows the window**: GLFW reports it fullscreen and the Quadro renders, but nothing reaches the panel. Restricting glvnd to NVIDIA's EGL vendor crashes `libEGL_nvidia` at startup; `__NV_PRIME_RENDER_OFFLOAD=1` alone already puts EGL on NVIDIA. Untested: making NVIDIA Mutter's primary GPU (udev tag `mutter-device-preferred-primary`).
- **The integrated GPU only just fits today's defaults** (hands off, native Wayland, vsync on):

  | Render buffer | Speed | fps | Typical frame | Long frames |
  |---|---|---|---|---|
  | 1920×1080 | 60 | 60.0 | 16.7 ms | none |
  | 1920×1080 | 120 | 57–58 | 21 ms | none |
  | 3840×2160 | 60 | 26–32 | 23–24 ms | ~5 per 10 s, ~800 ms |
  | 3840×2160 | 120 | 15–19 | 24–25 ms | ~5 per 10 s, ~1.5 s |

  With swap interval 0 under Wayland/Mesa frames stayed vsync-paced and gained 240–260 ms stalls, so it is no measure of headroom there.
- **NVIDIA's EGL library crashes the JVM at exit** (SIGSEGV in `libEGL_nvidia`, in a native thread) whenever glvnd loads it, even when rendering on Intel, on a signal or on a clean exit. Limiting glvnd to Mesa's EGL vendor when rendering on Intel stops it being loaded.

Native Wayland exposed app bugs, fixed on the spike branch:
- the display viewport and render buffer used the window size in screen coordinates instead of the framebuffer size in pixels, so at scale 2 the picture filled a quarter of the window (ce5640a);
- fullscreen went to the monitor render-core picks by window position, which Wayland doesn't expose, so it landed on DP-4 (24 Hz) instead of `[display] monitor` (12e7872);
- the window had no app ID, so GNOME listed it as "Unknown" (b33518a);
- GLFW minimised the fullscreen window on focus loss (`GLFW_AUTO_ICONIFY`), so it vanished until picked from the dock and the restore rebuilt the render buffer (99793dc).

And one for every Mesa driver, fixed on `develop` (292bcc7): the QR overlay shader used the reserved word `half`, which NVIDIA accepts and Mesa rejects; the compile failure ended the render loop and left the window "Not Responding".

The spike also added `[display] gpu = discrete | integrated` and `platform = x11 | wayland` (3cd4ec9): `GlEnvironment` sets the GL vendor variables on the process with `setenv` before `glfwInit()` (shown to take effect: the default renders on the Quadro with the start script no longer exporting them), and `integrated` limits glvnd to Mesa's EGL vendor (no `libEGL_nvidia` mapped, clean exit).

**Conclusion:** adapt. Native Wayland on the integrated GPU is the steadiest path at today's settings; NVIDIA through Xwayland has the headroom for heavier settings but intermittent presentation freezes. The spike's GPU/platform settings and Wayland fixes on `spike/ct32-wayland` are written to build on and are taken to `develop` by a follow-up initiative.

## Open questions

_None._

## Acceptance criteria

_What must be true when this is done._
