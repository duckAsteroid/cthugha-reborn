# Utility Renderers

The core module includes reusable utility renderers that compose well with `RenderedItem` and
`CompositeRenderItem`. Use this table as a quick "what should I reach for?" reference.

| Class | Package | What it does | Common use | Notes / key methods |
|---|---|---|---|---|
| `BlurTextureRenderer` | `com.asteroid.duck.opengl.util.blur` | Single-pass separable Gaussian blur over one axis (X or Y). | Add horizontal or vertical blur to a texture already in `ResourceManager`. | Toggle with `setBlur(boolean)`; switch axis with `setXAxis(boolean)`; tune radius with `setKernelSize(int)` (`3..65`, odd only). |
| `OffscreenBlurTextureRenderer` | `com.asteroid.duck.opengl.util.blur` | Multi-pass blur pipeline using intermediate FBO textures. | Strong bloom/glow style blur where one pass is not enough. | Constructor accepts source texture name and pass count; `multiply(float)` controls intensity; internally alternates X/Y blur stages. |
| `BlurKernel` | `com.asteroid.duck.opengl.util.blur` | Builds Gaussian weights/offsets for blur shaders. | Precompute or inspect kernel sample distributions. | `new BlurKernel(size).getDiscreteSampleKernel()` yields linear-sampling friendly taps. |
| `PaletteRenderer` | `com.asteroid.duck.opengl.util.palette` | Indexed-color post-process: source texture R values (0–1) address a 2D palette texture. Total palette entries = palette width × height, up to 65 535. | Palette swapping, LUT-style recoloring, retro effects. | Source (indexed) texture must use `DataFormat.GRAY_16` for 16-bit precision. Palette is any 2D RGBA image — the shader uses `textureSize()` to decode the linear index into (col, row) automatically. Shader uniform name is `palette`; static helpers `greyScale()` and `rbgTestScale()` build `TextureData` for the palette. |
| `ColorPalette` | `com.asteroid.duck.opengl.util.palette` | Loads and registers a palette image (cropped to one row) as a 2D texture. | Bring external palette files into `ResourceManager` for use with `PaletteRenderer`. | `new ColorPalette(mgr, file)` auto-registers by filename; `size()` returns entry count (= image width). The texture is a 2D `width × 1` image. |
| `StringRenderer` | `com.asteroid.duck.opengl.util.text` | Renders dynamic text using a `FontTexture`. | HUD/debug overlays, FPS counters, experiment labels. | Call `setText(...)` before first render; `setTransform(Matrix4f)` sets position/rotation/scale via model matrix; `setTextColor(Vector4f)` is render-thread safe via internal queue. |
| `Block` | `com.asteroid.duck.opengl.util.text` | Tiny text helper for line splitting. | Multi-line string preprocessing before feeding text renderers. | `Block.lines(text)` splits on `\n`. |
| `OffscreenTextureRenderer` | `com.asteroid.duck.opengl.util` | Wraps any `RenderedItem` and renders it into a target texture via FBO. | Build post-processing chains and ping-pong pipelines. | Use with `TextureFactory.createTexture(...)`; restores viewport after offscreen pass. |
| `TranslateTextureRenderer` | `com.asteroid.duck.opengl.util` | Warps a source texture through a translation-map texture. | Distortion/displacement effects (heat haze, ripple maps, UV remap effects). | Needs source + map texture names; binds map to `map` sampler and uploads `dimensions` each frame. |
| `MapTransformRenderItem` | `com.asteroid.duck.opengl.util` | Full pipeline: source renderer → offscreen → apply translate map → offscreen → screen, with per-frame feedback. | Self-referential warp effects where the warped output feeds the next frame (e.g. spinning/swirling). | Constructor: `(RenderedItem source, String mapName, String offscreenName[, TextureOptions opts])`; creates and registers the offscreen texture internally; wraps source in an `OffscreenTextureRenderer` then chains `TranslateTextureRenderer`. |
| `MultiTextureRenderer` | `com.asteroid.duck.opengl.util` | Full-screen renderer that blends/combines multiple textures in one shader. | Compositing intermediate render targets. | Binds input textures as `tex0`, `tex1`, ... and updates a time-varying `amount` uniform. |
| `AudioWave` | `com.asteroid.duck.opengl.util.wave` | Real-time stereo audio waveform rendered as a horizontal line strip. Implements `Transformable`. | Scrolling waveform HUD overlay. | `setClearBeforeRender(false)` to skip `glClear` when compositing over another renderer; `setChannelMode(int)` (`CHANNEL_BLEND/LEFT/RIGHT/STEREO`); `setAmplitudeFunction(fn)`; `setLineWidth(float)`; `setLineColour(Vector4f)`; `setTransform(Matrix4f)` to rotate/scale/translate in NDC space. Requires `PboAudioSink`; pass `AUDIO_BUFFER_SIZE` to `PboAudioSink.create`. |
| `RadialWave` | `com.asteroid.duck.opengl.util.wave` | Real-time stereo audio waveform rendered as a polar (radial) line loop. Implements `Transformable`. | Circular visualiser, clock-face waveform. | `setClearBeforeRender(false)` to skip `glClear` when compositing; `setChannelMode(int)` (`CHANNEL_BLEND/LEFT/RIGHT`); `setRadius(float)`; `setAmplitudeFunction(AmplitudeFunction)` (same scale as `AudioWave` — `constant(1f)` = full-scale radial excursion); `setCenter(Vector2f)`; `setLineWidth(float)`; `setLineColour(Vector4f)`; `setTransform(Matrix4f)` applied after aspect-ratio correction so the circle stays round. Requires `PboAudioSink`; pass `AUDIO_BUFFER_SIZE` to `PboAudioSink.create`. |

---

## Timer / Clock library (`com.asteroid.duck.opengl.util.timer`)

### Core types

| Class / Interface | Role |
|---|---|
| `Clock` | Interface: `double elapsed()` — seconds since the clock started. |
| `ClockImpl` | Full mutable clock: pause, step, reset, configurable time source. Backed by a `TimeSource`. |
| `StaticClock` | Immutable frozen snapshot record `(double elapsed, double now)`. Returned by `ClockImpl.snapshot()`. |
| `Timer` | Immutable record `(Clock clock, double startTime, double durationSeconds)` — tracks a duration against a clock. |
| `TimeSource` | Named `Callable<Double>` factory for standard time sources. |

### Getting a clock

Inside any `RenderedItem`, always obtain the clock from `RenderContext`:

```java
Clock clock = ctx.getClock();
double t = clock.elapsed();   // seconds since the application started
```

`ClockImpl` and `TimeSource` are used by `GLWindow` to create and drive the clock — experiments
and utility renderers should never construct their own `ClockImpl`.

### Tracking a duration with `Timer`

```java
// Create a timer that fires after 5 seconds:
Timer countdown = ctx.getClock().track(Duration.ofSeconds(5));

// In doRender():
if (countdown.hasElapsed()) { /* time is up */ }
double pct      = countdown.progress();   // 0.0 → 1.0
double elapsed  = countdown.elapsed();    // seconds since the timer was created
double remaining = countdown.remaining(); // seconds until the timer fires
```

`Timer` is a record — it captures `startTime` at construction and never mutates. Create a new
`Timer` each time you need to restart the countdown.

### Clock control (ClockImpl)

```java
clock.setPaused(true);    // freeze elapsed time
clock.togglePaused();     // flip pause state
clock.reset();            // restart from zero
clock.step(0.016);        // advance by a fixed delta (useful in tests)
```

### Snapshots

```java
StaticClock snap = clock.snapshot();
// snap.elapsed() and snap.now() are frozen at the capture instant
```

### Time-driven function helpers (`timer.function` subpackage)

| Class | Output | Key setters |
|---|---|---|
| `WaveFunction` | `sin(2π × freq × (t + phase))` in [–1, 1] | `setFrequency(double)`, `setPhase(double)` |
| `LinearFunction` | Sawtooth ramp [0, maxY] at given frequency | `setFrequency(double)`, `setMaxY(double)` |
| `AccumulatorFunction` | Monotonically growing value (delta × speed) | `setSpeed(double)`, `setMinSpeed`, `setMaxSpeed` |

```java
WaveFunction wave = new WaveFunction(ctx.getClock(), 0.5);   // 0.5 Hz sine
double v = wave.value();                                      // −1 … 1

LinearFunction ramp = new LinearFunction(ctx.getClock());
ramp.setFrequency(1.0);
ramp.setMaxY(360.0);      // 0→360° sawtooth once per second

AccumulatorFunction spin = new AccumulatorFunction(ctx.getClock());
spin.setSpeed(90.0);      // degrees per second
double angle = spin.value();
```

---

## Text overlay with `StringRenderer`

Text vertices sit at the origin in string space. Position, rotation, and scale are all applied via
`setTransform(Matrix4f)`, which only updates the `model` uniform — the vertex buffer is never
rebuilt by a transform change.

```java
FontTexture font = new FontTextureFactory(new Font("Monospaced", Font.PLAIN, 18), true)
    .createFontTexture();
StringRenderer text = new StringRenderer(font);
text.init(ctx);
text.setText("Hello, render-core");
text.setTextColor(StandardColors.CYAN.color);

// Position only — translate to (24, 48) in screen pixels
text.setTransform(new Matrix4f().translate(24, 48, 0));

// Position + rotate 15° clockwise around the text origin
text.setTransform(new Matrix4f().translate(24, 48, 0).rotateZ((float) Math.toRadians(15)));

// Position + scale up 2× + rotate
text.setTransform(new Matrix4f().translate(24, 48, 0).rotateZ(angle).scale(2, 2, 1));

// in doRender:
text.doRender(ctx);
```

When tracking transform state across key-press or animation events, keep the components (`tx`,
`ty`, `rotation`, `scale`) as separate fields and rebuild the matrix on each change rather than
reading it back from the renderer:

```java
private float tx, ty, rotation, scale = 1f;

private void applyTransform() {
    renderer.setTransform(
        new Matrix4f().translate(tx, ty, 0).rotateZ(rotation).scale(scale, scale, 1)
    );
}
```
