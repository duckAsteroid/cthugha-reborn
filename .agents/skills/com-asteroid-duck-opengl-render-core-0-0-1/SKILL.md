---
name: com-asteroid-duck-opengl-render-core-0-0-1
description: "Reference for the com.asteroid.duck.opengl:render-core library, including lifecycle, shaders, geometry, audio visualisation, and key input."
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

## Shaders

### Inline source (preferred for self-contained components)

```java
// language=GLSL  (IDE hint for syntax highlighting)
private static final String VERT = """
    #version 330 core
    in vec2 position;
    uniform float uTime;
    void main() {
        gl_Position = vec4(position, 0.0, 1.0);
    }
    """;

private static final String FRAG = """
    #version 330 core
    uniform vec4 uColour;
    out vec4 fragColor;
    void main() { fragColor = uColour; }
    """;

// In init():
shader = ShaderProgram.compile(
    ShaderSource.fromClass(VERT, MyClass.class),
    ShaderSource.fromClass(FRAG, MyClass.class),
    null);  // null = no geometry shader
```

### File-based source

Place files in `src/main/resources/glsl/<name>/vertex.glsl` and `frag.glsl`, then:

```java
ShaderProgram shader = ctx.getResourceManager()
    .getShader("myShader", "glsl/name/vertex.glsl", "glsl/name/frag.glsl", null);
```

### Uniforms

Cache handles once in `init()`, set each frame in `doRender()`:

```java
// init():
Uniform<Float>    uTime   = shader.uniforms().get("uTime",   Float.class);
Uniform<Integer>  uMode   = shader.uniforms().get("uMode",   Integer.class);
Uniform<Vector2f> uOffset = shader.uniforms().get("uOffset", Vector2f.class);
Uniform<Vector4f> uColour = shader.uniforms().get("uColour", Vector4f.class);

// doRender():
shader.use(ctx);
uTime.set((float) ctx.getTimer().elapsed());
uColour.set(new Vector4f(1f, 0.5f, 0f, 1f));
```

Supported Java types for `Uniform<T>`: `Float`, `Integer`, `Vector2f`, `Vector3f`, `Vector4f`,
`Matrix4f`.

**Constraint:** GLSL `uniform` names must match exactly what is passed to `shader.uniforms().get(name, …)`.

---

## Geometry (VAO / VBO)

### Vertex layout

```java
// Declare elements — the name() is used verbatim as the GLSL "in" variable name
VertexElement POSITION = new VertexElement(VertexElementType.VEC_2F, "position");
VertexElement COLOR    = new VertexElement(VertexElementType.VEC_3F, "color");
```

Available `VertexElementType` constants: `FLOAT`, `VEC_2F`, `VEC_3F`, `VEC_4F`.

### Building and filling a VBO

```java
// init():
VertexDataStructure layout = new VertexDataStructure(POSITION, COLOR);

VertexArrayObject vao = new VertexArrayObject();
vao.setDrawMode(BufferDrawMode.TRIANGLES);   // or LINE_STRIP, LINES, TRIANGLE_FAN, …
vao.init(ctx);

VertexBufferObject vbo = vao.createVbo(layout, vertexCount);
vbo.init(ctx);

// populate
vbo.setElement(0, POSITION, new Vector2f( 0f,  0.5f));
vbo.setElement(0, COLOR,    new Vector3f( 1f,  0f,   0f));
vbo.setElement(1, POSITION, new Vector2f(-0.5f,-0.5f));
// ...

vbo.update(UpdateHint.STATIC);  // STATIC = never changes; DYNAMIC = changes every frame

// Link VBO attribute locations to shader inputs (do this after shader is compiled):
vao.getVbo().setup(shader);
```

`UpdateHint` values: `STATIC` (write once), `DYNAMIC` (write many), `STREAM` (write once, use few).

### Rendering

```java
// doRender():
shader.use(ctx);
vao.bind(ctx);
vao.doRender(ctx);
```

### Disposal

```java
// dispose():
vao.dispose();   // disposes VBO and EBO too
shader.dispose();
```

---

## Resource manager

```java
ResourceManager rm = ctx.getResourceManager();

// Named textures — loaded once, cached by name
Texture tex = rm.getTexture("myTex", "textures/foo.png");
Texture tex2 = rm.getTexture("myTex", "textures/foo.png", ImageLoadingOptions.DEFAULT.withFlip());

// Named shaders loaded from classpath
ShaderProgram sh = rm.getShader("blur", "glsl/blur/vertex.glsl", "glsl/blur/frag.glsl", null);

// Texture units — allocated sequentially, avoids unit conflicts between components
TextureUnit unit = rm.nextTextureUnit();
unit.activate();
unit.bind(texture);
shader.uniforms().get("uTex", Integer.class).set(unit.index());

// Arbitrary lifecycle tracking — dispose() will be called at shutdown
rm.register(() -> { glDeleteBuffers(myPboId); });    // Resource is a @FunctionalInterface
```

---

## Utility renderers and helper classes

The core module includes reusable utility renderers that compose well with `RenderedItem` and
`CompositeRenderItem`. Use this table as a quick "what should I reach for?" reference.

| Class | Package | What it does | Common use | Notes / key methods |
|---|---|---|---|---|
| `BlurTextureRenderer` | `com.asteroid.duck.opengl.util.blur` | Single-pass separable Gaussian blur over one axis (X or Y). | Add horizontal or vertical blur to a texture already in `ResourceManager`. | Toggle with `setBlur(boolean)`; switch axis with `setXAxis(boolean)`; tune radius with `setKernelSize(int)` (`3..65`, odd only). |
| `OffscreenBlurTextureRenderer` | `com.asteroid.duck.opengl.util.blur` | Multi-pass blur pipeline using intermediate FBO textures. | Strong bloom/glow style blur where one pass is not enough. | Constructor accepts source texture name and pass count; `multiply(float)` controls intensity; internally alternates X/Y blur stages. |
| `BlurKernel` | `com.asteroid.duck.opengl.util.blur` | Builds Gaussian weights/offsets for blur shaders. | Precompute or inspect kernel sample distributions. | `new BlurKernel(size).getDiscreteSampleKernel()` yields linear-sampling friendly taps. |
| `PaletteRenderer` | `com.asteroid.duck.opengl.util.palette` | Indexed-color post-process: source texture values become indices into a 1D palette texture. | Palette swapping, LUT-style recoloring, retro effects. | Requires source texture + palette texture; shader uniform name is `palette`; includes helpers like `greyScale()` and `rbgTestScale()`. |
| `ColorPalette` | `com.asteroid.duck.opengl.util.palette` | Loads and registers a palette texture as a single-line image. | Bring external palette files into `ResourceManager`. | `new ColorPalette(mgr, file)` auto-registers by filename; `size()` returns entry count. |
| `StringRenderer` | `com.asteroid.duck.opengl.util.text` | Renders dynamic text using a `FontTexture`. | HUD/debug overlays, FPS counters, experiment labels. | Call `setText(...)` before first render; `setPosition(Point)` updates only model matrix; `setTextColor(Vector4f)` is render-thread safe via internal queue. |
| `Block` | `com.asteroid.duck.opengl.util.text` | Tiny text helper for line splitting. | Multi-line string preprocessing before feeding text renderers. | `Block.lines(text)` splits on `\n`. |
| `OffscreenTextureRenderer` | `com.asteroid.duck.opengl.util` | Wraps any `RenderedItem` and renders it into a target texture via FBO. | Build post-processing chains and ping-pong pipelines. | Use with `TextureFactory.createTexture(...)`; restores viewport after offscreen pass. |
| `TranslateTextureRenderer` | `com.asteroid.duck.opengl.util` | Warps a source texture through a translation-map texture. | Distortion/displacement effects (heat haze, ripple maps, UV remap effects). | Needs source + map texture names; binds map to `map` sampler and uploads `dimensions` each frame. |
| `MultiTextureRenderer` | `com.asteroid.duck.opengl.util` | Full-screen renderer that blends/combines multiple textures in one shader. | Compositing intermediate render targets. | Binds input textures as `tex0`, `tex1`, ... and updates a time-varying `amount` uniform. |

### Minimal text overlay example (`StringRenderer`)

```java
FontTexture font = new FontTextureFactory(new Font("Monospaced", Font.PLAIN, 18), true)
    .createFontTexture();
StringRenderer text = new StringRenderer(font);
text.init(ctx);
text.setText("Hello, render-core");
text.setPosition(new Point(24, 48));
text.setTextColor(StandardColors.CYAN.color);

// in doRender:
text.doRender(ctx);
```

---

## Key input

```java
// init():
ctx.getKeyRegistry().registerKeyAction(
    KeyCombination.simple('A'),
    () -> { /* runs on GL thread when A is pressed */ },
    "Human-readable description for help display");

// With modifiers:
ctx.getKeyRegistry().registerKeyAction(
    KeyCombination.simpleWithMods('A', "SHIFT"),
    () -> { /* Shift+A */ },
    "Description");
```

---

## Cross-thread render actions (RenderActionQueue)

Use this when a non-GL thread (e.g. key callback, audio thread) needs to mutate GL state.
Singleton types keep only the most recent enqueued action, discarding stale intermediate values.

```java
// Fields:
private static final String ACTION_COLOUR = "colour";  // singleton — only latest matters
private static final String ACTION_FLASH  = "flash";   // non-singleton — every event matters
private final RenderActionQueue renderActions =
    new RenderActionQueue(ACTION_COLOUR);               // pass singleton names to constructor

// From any thread:
renderActions.enqueue(ACTION_COLOUR, ctx -> uColour.set(newColour));
renderActions.enqueue(ACTION_FLASH,  ctx -> triggerFlash());

// In doRender() — must be called every frame:
renderActions.processAll(ctx);
```

---

## Colors

`StandardColors` is a pre-built enum of common RGBA colours:

```java
// As Vector4f:
Vector4f red  = StandardColors.RED.color;
Vector4f half = StandardColors.BLUE.withAlpha(0.5f);

// All values (useful for random colour selection):
StandardColors[] all = StandardColors.values();
Vector4f random = all[rng.nextInt(all.length)].color;
```

---

## Audio visualisation

### AudioWave — pre-built waveform renderer

`AudioWave` is a `RenderedItem` that captures real-time stereo PCM audio and draws it as a
scrolling line strip. Drop it into any experiment.

```java
// Fields:
private final AudioWave audioWave = new AudioWave();
private final LineAcquirer lineAcquirer = new LineAcquirer();

// init():
audioWave.init(ctx);
lineAcquirer.init(ctx, LineAcquirer.IDEAL);   // IDEAL = 48 kHz, 16-bit stereo
audioWave.setLine(lineAcquirer.getSelectedSource());

// doRender():
audioWave.doRender(ctx);

// dispose():
audioWave.dispose();
```

#### Switching audio source at runtime (safe from any thread)

```java
lineAcquirer.next();                                    // advance to next device
audioWave.setLine(lineAcquirer.getSelectedSource());
```

#### Channel modes

```java
audioWave.setChannelMode(AudioWave.CHANNEL_BLEND);   // L+R average (default)
audioWave.setChannelMode(AudioWave.CHANNEL_LEFT);
audioWave.setChannelMode(AudioWave.CHANNEL_RIGHT);
audioWave.setChannelMode(AudioWave.CHANNEL_STEREO);  // two lines: L above, R below
```

#### Visual properties (applied on next frame, safe from any thread)

```java
audioWave.setLineWidth(4.0f);                          // GL line width in pixels
audioWave.setLineColour(StandardColors.CYAN.color);
audioWave.setAmplitudeFunction(AmplitudeFunction.constant(10f));   // flat envelope
audioWave.setAmplitudeFunction(AmplitudeFunction.ellipse(10f));    // tapers to 0 at edges
```

### AmplitudeFunction — custom amplitude envelopes

`AmplitudeFunction` is a `@FunctionalInterface` mapping `(vertexIndex, normalisedX) → amplitude`.
`normalisedX` is in `[-1, 1]`. The returned value is stored in each vertex's Y component and
multiplied by the normalised audio sample `[-1, 1]` in the vertex shader.

```java
// Built-in factories:
AmplitudeFunction flat    = AmplitudeFunction.constant(10f);
AmplitudeFunction tapered = AmplitudeFunction.ellipse(10f);

// Custom:
AmplitudeFunction custom = (index, x) -> 8f * (float) Math.abs(Math.cos(Math.PI * x));

audioWave.setAmplitudeFunction(custom);
```

### LineAcquirer — audio source discovery

```java
LineAcquirer la = new LineAcquirer();
la.init(ctx, LineAcquirer.IDEAL);

AudioDataSource current  = la.getSelectedSource();
AudioDataSource next     = la.next();      // returns old source, advances index
AudioDataSource previous = la.previous();  // wraps around

// Check simulate.audio system property:
// -Dsimulate.audio=true  → a SimulatedDataSource is prepended to the source list
// -Dsimulate.audio=false → only real hardware lines (default)
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

## Key constraints and gotchas

| Constraint | Detail |
|---|---|
| **GL thread only** | Every GL call, `init()`, `doRender()`, and `dispose()` must execute on the GL/render thread. Use `RenderActionQueue` to submit work from other threads. |
| **init() before everything** | GL handles are `0`/`null` until `init()` completes. Never call `doRender()` or use a `Uniform` before `init()`. |
| **dispose() cleans everything** | Call `vao.dispose()`, `shader.dispose()`, `glDeleteBuffers()`, `glDeleteTextures()` explicitly. Native memory (`MemoryUtil.memAlloc`) is not GC-collected. |
| **Vertex name contract** | `VertexElement.name()` must match the GLSL `in` variable name exactly. A mismatch silently produces a black screen. |
| **Uniform caching** | Obtain `Uniform<T>` handles once in `init()` after `shader.use(ctx)`. Getting them every frame is harmless but wasteful. |
| **UpdateHint.STATIC** | Calling `vbo.update(UpdateHint.STATIC)` after initial population. If vertex data changes at runtime, use `UpdateHint.DYNAMIC` and call `update()` again after each write. |
| **PBO persistent mapping** | A persistently-mapped PBO (`GL_MAP_PERSISTENT_BIT`) must stay mapped for the life of the object. Do not wrap it in `VertexBufferObject`; manage it with raw GL calls and register cleanup via `rm.register(...)`. |
| **ExclusivityGroup** | VAOs, EBOs, shader programs, and standard textures are bound through `ExclusivityGroup` wrappers. Always use the wrapper (`vao.bind(ctx)`, `shader.use(ctx)`) rather than raw `glBind*` calls to avoid silent state corruption. |
| **AudioWave thread** | `AudioWave` starts a background `"audio-reader"` daemon thread in `init()`. `dispose()` stops it and joins with a 2 s timeout before releasing GPU resources. |
