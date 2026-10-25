---
type: feature
status: draft
tags: [smoothness, render-core]
updated: 2026-10-25
---
# Choose the GPU and windowing platform, with native Wayland support

## Goal

Let the user pick which GPU renders and whether the window is native Wayland or X11 (Xwayland), so the steadiest present path for a machine can be chosen, and make the app work correctly as a native Wayland client. ct#32 found that on a PRIME laptop native Wayland on the integrated GPU holds a locked 60 fps at the default settings with no presentation freezes, while NVIDIA through Xwayland has the headroom for heavier settings but intermittent 100–420 ms freezes; today the start script hard-codes NVIDIA render offload and render-core forces X11.

Bring the spike's work (branch `spike/ct32-wayland`) to `develop`:
- `[display] gpu = discrete | integrated` and `platform = x11 | wayland` in `cthugha.ini`; `GlEnvironment` sets the GL vendor variables on the process with `setenv` before `glfwInit()` (via render-core's `GLFWConfigurer`) instead of the start script and the `run`/`smokeTest` tasks exporting them; `integrated` limits glvnd to Mesa's EGL vendor so NVIDIA's EGL library, which crashes the JVM at exit, is never loaded.
- Size the screen viewport and render buffer from the framebuffer in pixels, not the window in screen coordinates (a scaled display under native Wayland showed the picture in a quarter of the window).
- Under Wayland, go fullscreen on `[display] monitor` (Wayland doesn't expose window positions, so render-core's overlap-based choice is wrong).
- Set the Wayland app ID / X11 class to `cthugha-reborn`, and turn off `GLFW_AUTO_ICONIFY` so a fullscreen window stays up when focus moves.

## Dependencies

_None._

## Related

- [32 Find and remove the 100-400 ms presentation stalls](32-find-and-remove-the-100-400-ms-presentation-stalls.md)
- [https://github.com/duckAsteroid/opengl-jogl-template/issues/33](https://github.com/duckAsteroid/opengl-jogl-template/issues/33)
- [https://github.com/duckAsteroid/opengl-jogl-template/issues/34](https://github.com/duckAsteroid/opengl-jogl-template/issues/34)

## Decisions

- **Code defaults stay today's behaviour (gpu = discrete, platform = x11)**, safe on machines that haven't been measured; this machine's `cthugha.ini` (the repo's `app/src/dist/cthugha.ini`, which already carries its monitor, network interface and beat tuning) sets `gpu = integrated` and `platform = wayland`, the steadiest path measured on it. (Owner: "whatever works", 2026-10-25.)
- **Raise the two render-core behaviours the RC9 hook can't reach; keep the app's workarounds until a release fixes them.** Forced X11 needs no upstream change, since `GLFWConfigurer.beforeInit()` overrides it. Filed: [opengl-jogl-template#33](https://github.com/duckAsteroid/opengl-jogl-template/issues/33) (fullscreen and `getCurrentMonitor()` pick the monitor by window overlap, meaningless under Wayland) and [opengl-jogl-template#34](https://github.com/duckAsteroid/opengl-jogl-template/issues/34) (no accessor for the framebuffer size; `getWindow()` is in screen coordinates). The app keeps overriding `toggleFullscreen()`/`getCurrentMonitor()` under Wayland and tracking the framebuffer size itself; they are related, not blocking. (Owner, 2026-10-25.)
- (implementation) **Use render-core 1.0.0-RC10's APIs instead of the spike's overrides** (RC10 fixes opengl-jogl-template #33 and #34): size the viewport and render buffer from `RenderContext.getFramebufferSize()` rather than tracking the framebuffer size in the app, and place the window on `[display] monitor` with a `WindowPlacement`/`moveToMonitor` (which RC10 records as the window's monitor under Wayland) so `getCurrentMonitor()` and `toggleFullscreen()` land there, rather than overriding them in `CthughaWindow`.

## Open questions

- Under native Wayland the window has no title bar (libdecor's GTK plugin fails to load), so a window can't be moved or resized with the mouse. Acceptable for windowed mode, or does platform = wayland need decorations (e.g. installing a libdecor plugin, or drawing our own)?

## Acceptance criteria

- [ ] `[display] gpu` and `platform` select the renderer and windowing platform, documented in `cthugha.ini`; the log names the GPU renderer and platform at startup.
- [ ] With `gpu = discrete` (start script no longer exporting NVIDIA variables) the app renders on the NVIDIA GPU; with `integrated` it renders on the integrated GPU and NVIDIA's EGL library is not mapped into the process.
- [ ] Under `platform = wayland` on a scale-2 display the picture fills the window and fullscreen; fullscreen lands on `[display] monitor`; the dock shows the app as cthugha-reborn; a fullscreen window stays up when focus moves.
- [ ] Under `platform = x11` behaviour is unchanged (smoke test passes; fullscreen and monitor placement as before).
- [ ] A fullscreen benchmark on the built-in panel, hands off, for integrated + wayland and discrete + x11, recorded in this initiative.
- [ ] Project docs (`CLAUDE.md` build/run and rendering sections, `docs/RENDERING_PIPELINE.md` render buffer size) describe the settings.
