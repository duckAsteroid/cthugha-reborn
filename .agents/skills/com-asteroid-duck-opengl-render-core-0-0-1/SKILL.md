---
name: com-asteroid-duck-opengl-render-core-0-0-1
description: "Reference for the com.asteroid.duck.opengl:render-core library, including lifecycle, shaders, geometry, audio visualisation, and key input."
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
double t = ctx.getTimer().elapsed();          // seconds since start (double)

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

The typical wiring is a key binding registered in `init()`:

```java
ctx.getKeyRegistry().registerKeyAction(
    KeyCombination.simple('P'),
    ctx::captureNextFrame,
    "Save screenshot");
```

**How it works:**
- Safe to call from any thread (key callbacks, audio thread, etc.) — the path is stored in a
  `volatile` field and consumed on the render thread.
- Only the most recently requested path is captured if multiple calls arrive in the same frame.
- Pixel read (`glReadPixels`) happens on the GL thread immediately after rendering completes;
  the vertical flip and PNG write are offloaded to a virtual thread to avoid frame hitches.
- Uses `glfwGetFramebufferSize` for dimensions, so the output is correct on HiDPI displays.

---

## Further reference

| Topic | File |
|---|---|
| Shaders, uniforms | [shaders.md](shaders.md) |
| Geometry (VAO / VBO) | [geometry.md](geometry.md) |
| Resource manager, textures, texture units | [resources.md](resources.md) |
| Utility renderers (blur, palette, text, warp) | [utilities.md](utilities.md) |
| Key input, cross-thread actions, colours | [input.md](input.md) |
| Audio visualisation (waveform, spectrum) | [audio.md](audio.md) |

---

## Core constraints

| Constraint | Detail |
|---|---|
| **GL thread only** | Every GL call, `init()`, `doRender()`, and `dispose()` must execute on the GL/render thread. Use `RenderActionQueue` to submit work from other threads. |
| **init() before everything** | GL handles are `0`/`null` until `init()` completes. Never call `doRender()` or use a `Uniform` before `init()`. |
| **dispose() cleans everything** | Call `vao.dispose()`, `shader.dispose()`, `glDeleteBuffers()`, `glDeleteTextures()` explicitly. Native memory (`MemoryUtil.memAlloc`) is not GC-collected. |
| **Audio ownership** | `AudioWave` and `RadialWave` are pure renderers — they own no threads and no PBO. The experiment creates `PboAudioSink` and `AudioReader`, passes the sink to the renderer(s), and calls `audioSink.upload()` once per frame. See [audio.md](audio.md). |
