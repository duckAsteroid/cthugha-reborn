---
name: com-asteroid-duck-opengl-render-core-0-0-1
description: "Reference for the com.asteroid.duck.opengl:render-core library, including lifecycle, shaders, geometry, audio visualisation (waveform, spectrum analyser, beat detection), and key input."
metadata:
  sources: src/
---

# render-core — Agent Reference

`render-core` is a Java/LWJGL 3 framework for building real-time OpenGL visualisations.
It wraps the raw GL API in lifecycle-safe abstractions for shaders, geometry buffers, textures,
key input, and audio capture.

**Gradle coordinate:** `com.asteroid.duck.opengl:render-core:0.0.1`
**Java version:** 25 with `--enable-preview`

---

## Core lifecycle

Every renderable object implements `RenderedItem`:

```java
public interface RenderedItem extends Resource {
    void init(RenderContext ctx) throws IOException;  // allocate GL resources
    void doRender(RenderContext ctx);                  // called every frame
    void dispose();                                    // free GL and native resources
}
```

**Rules:**
- All three methods execute on the **GL/render thread**. Never call GL functions from any other thread.
- GL handles are `null`/`0` until `init()` is called. Do not use them before or after.
- `dispose()` must release every GL resource allocated in `init()` (shader, VAO, textures, PBOs…).

---

## RenderContext

`RenderContext` is the single argument passed to every lifecycle method. Use it instead of holding
direct references to the window.

```java
// Time
double t = ctx.getClock().elapsed();          // seconds since start (double)

// Viewport
Rectangle win = ctx.getWindow();              // pixel dimensions, origin top-left
Matrix4f proj = ctx.ortho();                  // orthographic projection for current viewport

// Resources
ResourceManager rm = ctx.getResourceManager();

// Key input
KeyRegistry keys = ctx.getKeyRegistry();

// Screen clearing
ctx.setClearScreen(true);
ctx.setBackgroundColor(new Vector4f(0f, 0f, 0f, 1f)); // RGBA

// Target frame rate (null = unlimited)
ctx.setDesiredUpdateFrequency(60.0);          // Hz
ctx.setDesiredUpdatePeriod(1.0 / 60.0);       // seconds — equivalent

// Misc
Random rng = ctx.getRandom();
ctx.addResizeListener(rect -> { /* react to window resize */ });
```

---

## Composing RenderedItems

```java
// Sequential (each item renders in order):
CompositeRenderItem composite = new CompositeRenderItem(itemA, itemB, itemC);

// Toggle visibility at runtime:
ToggledRenderItem toggled = new ToggledRenderItem(innerItem);
toggled.setVisible(false);
```

---

## Screenshot capture

Any component that holds a `RenderContext` can request a PNG screenshot. The capture is deferred
to the correct moment in the render loop (after rendering, before buffer swap) by `GLWindow`.

```java
// Capture to a timestamped file in the working directory (e.g. screenshot-1718884800000.png):
ctx.captureNextFrame();

// Capture to a specific path:
ctx.captureNextFrame(Path.of("captures/frame.png"));   // parent dirs are created automatically
```

`GLWindow` registers **no** key bindings. The `PRINT_SCREEN` → screenshot binding is wired in
`Main.registerKeys()` via `this::captureNextFrame`.

**How it works:**
- Safe to call from any thread (key callbacks, audio thread, etc.) — the path is stored in a
  `volatile` field and consumed on the render thread.
- Only the most recently requested path is captured if multiple calls arrive in the same frame.
- Pixel read (`glReadPixels`) happens on the GL thread immediately after rendering completes;
  the vertical flip and PNG write are offloaded to a virtual thread to avoid frame hitches.
- Uses `glfwGetFramebufferSize` for dimensions, so the output is correct on HiDPI displays.

---

## Video recording

`GLWindow` can record a short MP4 clip to disk using the pure-Java JCodec encoder (no external
binaries required). Duration is capped at 60 seconds.

```java
// Record 5 seconds to a timestamped file (e.g. recording-1718884800000.mp4):
ctx.startRecording(Duration.ofSeconds(5));

// Record to a specific path:
ctx.startRecording(Path.of("clips/demo.mp4"), Duration.ofSeconds(10));

// Stop early (the encoder finishes and flushes the MP4 file):
ctx.stopRecording();
```

The `SHIFT+PRINT_SCREEN` → recording binding is likewise wired in `Main.registerKeys()`.

**How it works:**
- `startRecording()` stores a request in a `volatile` field; the GL thread picks it up at the top
  of the next frame.
- Each frame calls `glReadPixels` + row-flip + `BufferedImage` on the GL thread, then offers the
  frame to a bounded `ArrayBlockingQueue` (capacity 30).
- A virtual thread drains the queue through `AWTSequenceEncoder` (JCodec) and calls `finish()`
  when recording ends.
- Duration is tracked via `RenderContext.getClock()` (the render clock), not wall-clock time, so
  paused or slowed renders are handled correctly.
- If the encode thread falls behind the render rate, frames are dropped with one WARN log.
- `dispose()` joins the encode thread (up to 30 s) to ensure the MP4 trailer is flushed.

---

## Further reference

| Topic | File |
|---|---|
| Shaders, uniforms | [shaders.md](shaders.md) |
| Geometry (VAO / VBO) | [geometry.md](geometry.md) |
| Resource manager, textures, texture units | [resources.md](resources.md) |
| Utility renderers, timer/clock library | [utilities.md](utilities.md) |
| Key input, cross-thread actions, colours | [input.md](input.md) |
| Audio capture, FFT analysis, beat detection, spectrum rendering | [audio.md](audio.md) |

---

## Core constraints

| Constraint | Detail |
|---|---|
| **GL thread only** | Every GL call, `init()`, `doRender()`, and `dispose()` must execute on the GL/render thread. Use `RenderActionQueue` to submit work from other threads. |
| **init() before everything** | GL handles are `0`/`null` until `init()` completes. Never call `doRender()` or use a `Uniform` before `init()`. |
| **dispose() cleans everything** | Call `vao.dispose()`, `shader.dispose()`, `glDeleteBuffers()`, `glDeleteTextures()` explicitly. Native memory (`MemoryUtil.memAlloc`) is not GC-collected. |
| **Audio ownership** | `AudioWave` and `RadialWave` are pure renderers — they own no threads and no PBO. The experiment creates `PboAudioSink` and `AudioReader`, passes the sink to the renderer(s), and calls `audioSink.upload()` once per frame. See [audio.md](audio.md). |
