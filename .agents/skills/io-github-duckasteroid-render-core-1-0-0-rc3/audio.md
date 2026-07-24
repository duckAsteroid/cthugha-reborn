# Audio Visualisation

## Architecture overview

The audio pipeline is split into three independent layers:

```
AudioDataSource (pluggable: real line or simulated)       [audio / audio.simulated packages]
  └─ AudioLine (opened via AudioDataSource.open)
       └─ AudioReader (background thread)
            └─ AudioSink.write(byte[], offset, length)
                 ├─ PboAudioSink  → GL 1-D texture → AudioWave / RadialWave  [wave package]
                 └─ FrequencyProcessor → FFTProcessor → FrequencySink         [audio.analysis package]
                      ├─ BeatDetector   → getBeatStrength(band)
                      └─ SpectrumAnalyser / RadialSpectrumAnalyser → doRender  [wave package]
```

**Package layout:**

| Package | Contents |
|---|---|
| `util.audio` | Capture: `AudioReader`, `PboAudioSink`, `RollingAudioBuffer`, `AudioDataSource`, `AudioLine`, `AudioSources`, `LineAcquirer`, `TargetLineSource`, `AudioSourceUnavailableException` |
| `util.audio.simulated` | Synthetic sources: `SimulatedDataSource`, `SimulatedSources`, `Waveform`, `OscillatingStereoPositioner` |
| `util.audio.analysis` | Signal processing: `FFTProcessor`, `FrequencyProcessor`, `FrequencySink`, `BeatDetector`, `FrequencyBand` |
| `util.wave` | Renderers: `AudioWave`, `RadialWave`, `SpectrumAnalyser`, `RadialSpectrumAnalyser` |

**Key principle:** `AudioWave` and `RadialWave` are pure renderers — they do not own threads,
manage PBOs, or know about audio capture. The experiment creates and wires up the pieces.

---

## PboAudioSink — the shared GPU audio texture

`PboAudioSink` owns a persistently-mapped PBO and a `GL_RG16_SNORM` 1-D texture.
It implements `AudioSink` so `AudioReader` writes raw PCM bytes into it directly.

### Creating and disposing

```java
// Factory — must be called on the GL thread (inside init())
PboAudioSink audioSink = PboAudioSink.create(AudioWave.AUDIO_BUFFER_SIZE, ctx);
// Disposal is registered with ctx.getResourceManager() automatically — no manual dispose() needed.
```

`stereoFrames` controls the ring-buffer size. Use the `AUDIO_BUFFER_SIZE` constant from the
renderer you plan to use (`AudioWave.AUDIO_BUFFER_SIZE` or `RadialWave.AUDIO_BUFFER_SIZE`;
both are `2048`).

### Per-frame upload

Call `upload()` **exactly once per frame**, before any renderer that uses this sink:

```java
// doRender():
audioSink.upload();          // DMA: PBO → GL_RG16_SNORM texture (one call, any number of renderers)
audioWave.doRender(ctx);
radialWave.doRender(ctx);    // both see identical data
```

`upload()` calls `glTexSubImage1D` with the PBO bound to `GL_PIXEL_UNPACK_BUFFER`, giving the
GPU direct access to the mapped write buffer with no intermediate copy.

### Implements AudioSink

```java
public interface AudioSink {
    void write(byte[] data, int offset, int length);
}
```

`PboAudioSink.write()` is safe to call from the `AudioReader` background thread. Internally it
ring-wraps into the persistently-mapped `ByteBuffer`; a `volatile int head` is advanced after
each write so the GL thread can snapshot it without locking.

---

## AudioReader — the capture thread

`AudioReader` runs a single background thread that drains an `AudioLine` and fans out
to one or more `AudioSink` implementations. It takes an `AudioDataSource` (not an already-opened
line) via `setLine` — opening, starting, stopping, and closing are its responsibility.

```java
AudioReader audioReader = new AudioReader(List.of(audioSink));   // one or more sinks
Thread audioReaderThread = new Thread(audioReader, "audio-reader");
audioReaderThread.setDaemon(true);
audioReaderThread.start();
audioReader.setLine(audioSources.list().get(selectedSource));    // opens, starts, begins capturing
```

If the source can't be opened, `setLine` logs the `AudioSourceUnavailableException` and leaves
the reader paused (as if `null` had been passed) rather than throwing.

### Switching audio source at runtime (safe from any thread)

Selection is the caller's responsibility — `AudioSources` and `LineAcquirer` hold no notion of
"current". A typical experiment keeps its own index into `audioSources.list()`:

```java
selectedSource = (selectedSource + 1) % audioSources.size();
audioReader.setLine(audioSources.list().get(selectedSource));
```

### Shutdown

```java
audioReader.setRunning(false);
audioReader.setLine(null);
audioReaderThread.join(2000);
```

---

## AudioDataSource / AudioLine — pluggable capture sources

`AudioDataSource` is a lightweight descriptor: a name, the `AudioFormat[]` it can open in, and
`open(format, bufferSize)`, which returns an `AudioLine` — the actual open handle with
`available()` / `read()` / `start()` / `stop()` / `isRunning()` / `close()` (`AudioLine` is
`AutoCloseable`). There's no `isOpen()` on the descriptor — an `AudioLine` only exists once a
source has been successfully opened, so "read before open" isn't representable.

```java
public interface AudioDataSource {
    String getName();
    AudioFormat[] getSupportedFormats();
    AudioLine open(AudioFormat format, int bufferSize) throws AudioSourceUnavailableException;
}
```

`open()` throws `AudioSourceUnavailableException` (wrapping the underlying cause, e.g. a
`LineUnavailableException` for real hardware) rather than a `javax.sound.sampled` type directly —
callers only need to handle one exception type regardless of which kind of source they opened.

Two implementations ship in the framework:

| Implementation | Package | Backed by |
|---|---|---|
| `TargetLineSource` | `util.audio` | A real `javax.sound.sampled.TargetDataLine`, discovered via `LineAcquirer` |
| `SimulatedDataSource` | `util.audio.simulated` | A synthetic `StereoDataSource` sampled against a `Clock`, built via `SimulatedSources` |

---

## AudioSources — a pluggable list of sources

`AudioSources` is just a mutable, add/remove list of `AudioDataSource` — real, simulated, or a
mix. It tracks no selection; the caller (typically an experiment) owns an index into
`audioSources.list()`.

```java
AudioSources audioSources = new AudioSources();
audioSources.add(SimulatedSources.middleC(ctx.getClock()));   // always-available synthetic source
audioSources.add(someOtherSource);
audioSources.remove(someOtherSource);

List<AudioDataSource> all = audioSources.list();   // unmodifiable, live view
int count = audioSources.size();
```

---

## LineAcquirer — real audio device discovery

`LineAcquirer` is a static-only utility for discovering real Java Sound capture lines — it holds
no state and does not know about simulated audio. Turn a discovered `MixerLine` into an
`AudioDataSource` via `toAudioDataSource()`, then add it to an `AudioSources`. The underlying
`TargetDataLine` is not acquired until the resulting source is `open()`ed, so discovery itself is
side-effect-free.

```java
LineAcquirer.allLinesMatching(LineAcquirer.IDEAL)   // IDEAL = 48 kHz, 16-bit stereo
        .map(LineAcquirer.MixerLine::toAudioDataSource)
        .forEach(audioSources::add);
```

`MixerLine.displayName()` gives a short, UI-friendly name (just the mixer name); `toString()`
keeps the full `Mixer`/`DataLine.Info` dump for diagnostics/logs.

## SimulatedSources — synthetic audio, explicitly opted in

There is no system property or hidden flag; a client adds a simulated source exactly like a real
one, whenever it wants one available (e.g. always, so it's one `J`/`H` press away, or only under
a headless/CI condition it decides itself):

```java
audioSources.add(SimulatedSources.middleC(ctx.getClock()));   // middle-C tone panned at 1 Hz
```

`SimulatedDataSource`'s constructor also takes an explicit display name directly, for building
custom synthetic sources beyond what `SimulatedSources` provides:

```java
new SimulatedDataSource("My synth", ctx.getClock(), myStereoDataSource)
```

---

## AudioWave — scrolling horizontal waveform renderer

`AudioWave` draws a scrolling `GL_LINE_STRIP` of 1 024 vertices displaced vertically by the
audio amplitude. It is a pure `RenderedItem` — no threads, no PBO management.

```java
// Fields:
private PboAudioSink audioSink;
private AudioReader audioReader;
private Thread audioReaderThread;
private AudioWave audioWave;
private final AudioSources audioSources = new AudioSources();
private int selectedSource = 0;

// init():
audioSink = PboAudioSink.create(AudioWave.AUDIO_BUFFER_SIZE, ctx);
audioWave  = new AudioWave(audioSink);
audioWave.init(ctx);

audioSources.add(SimulatedSources.middleC(ctx.getClock()));
LineAcquirer.allLinesMatching(LineAcquirer.IDEAL)
        .map(LineAcquirer.MixerLine::toAudioDataSource)
        .forEach(audioSources::add);

audioReader = new AudioReader(List.of(audioSink));
audioReaderThread = new Thread(audioReader, "audio-reader");
audioReaderThread.setDaemon(true);
audioReaderThread.start();
audioReader.setLine(audioSources.list().get(selectedSource));

// doRender():
audioSink.upload();
audioWave.doRender(ctx);

// dispose():
audioWave.dispose();
audioReader.setRunning(false);
audioReader.setLine(null);
audioReaderThread.join(2000);
```

### Channel modes

```java
audioWave.setChannelMode(AudioWave.CHANNEL_BLEND);   // L+R average (default)
audioWave.setChannelMode(AudioWave.CHANNEL_LEFT);
audioWave.setChannelMode(AudioWave.CHANNEL_RIGHT);
audioWave.setChannelMode(AudioWave.CHANNEL_STEREO);  // two lines: L above, R below
```

### Visual properties (applied on next frame, safe from any thread)

```java
audioWave.setLineWidth(4.0f);
audioWave.setLineColour(StandardColors.CYAN.color);
audioWave.setAmplitudeFunction(AmplitudeFunction.constant(10f));   // flat envelope
audioWave.setAmplitudeFunction(AmplitudeFunction.ellipse(10f));    // tapers to 0 at edges
audioWave.setTransform(new Matrix4f().rotateZ(0.1f).translate(0, 0.3f, 0));  // tilt + shift up
audioWave.setTransform(new Matrix4f());   // reset to identity
```

`setTransform` is safe to call from any thread. The matrix is applied in NDC space (origin = screen centre) to every vertex position on the next frame.

---

## RadialWave — circular radial waveform renderer

`RadialWave` draws a `GL_LINE_LOOP` of 1 024 vertices arranged around a circle, displaced
radially by the audio amplitude. Same pure-renderer pattern as `AudioWave`.

```java
// init():
audioSink  = PboAudioSink.create(RadialWave.AUDIO_BUFFER_SIZE, ctx);
radialWave = new RadialWave(audioSink);
radialWave.init(ctx);
// ... AudioReader setup as above ...

// doRender():
audioSink.upload();
radialWave.doRender(ctx);

// dispose():
radialWave.dispose();
// ... AudioReader shutdown as above ...
```

### Channel modes

```java
radialWave.setChannelMode(RadialWave.CHANNEL_BLEND);  // L+R average (default)
radialWave.setChannelMode(RadialWave.CHANNEL_LEFT);
radialWave.setChannelMode(RadialWave.CHANNEL_RIGHT);
```

### Visual properties (applied on next frame, safe from any thread)

```java
radialWave.setLineWidth(3.0f);
radialWave.setLineColour(StandardColors.CYAN.color);
radialWave.setRadius(0.5f);                               // base circle radius in NDC-y units
radialWave.setAmplitudeFunction(AmplitudeFunction.constant(1f));  // flat envelope (default)
radialWave.setAmplitudeFunction(AmplitudeFunction.ellipse(1f));   // tapers to 0 at the seam
radialWave.setCenter(new Vector2f(0f, 0f));               // NDC centre (default = screen centre)
radialWave.setTransform(new Matrix4f().rotateZ(angle).scale(pulse));  // spin + scale
radialWave.setTransform(new Matrix4f());   // reset to identity
```

`setTransform` is safe to call from any thread. The matrix is applied after the aspect-ratio
correction, so a rotation matrix produces a clean visual rotation of the already-round circle.

`RadialWave` automatically corrects for non-square viewports via an internal aspect-ratio
uniform — the circle stays visually round as the window is resized.

---

## Sharing a PboAudioSink between multiple renderers

Because `AudioWave` and `RadialWave` accept a `PboAudioSink` in their constructors and only
hold references to `getTextureId()` and `getHead()`, any number of renderers can share a
single sink. One `upload()` call per frame is enough — both renderers sample the same texture.

```java
// Both buffer size constants are 2048, so either works:
audioSink  = PboAudioSink.create(AudioWave.AUDIO_BUFFER_SIZE, ctx);

audioWave  = new AudioWave(audioSink);
radialWave = new RadialWave(audioSink);
audioWave.init(ctx);
radialWave.init(ctx);

// One AudioReader writes into one sink:
audioReader = new AudioReader(List.of(audioSink));

// doRender() — one upload, two renders, identical audio data:
audioSink.upload();
audioWave.doRender(ctx);
radialWave.doRender(ctx);

// dispose() — both renderers, one thread shutdown:
audioWave.dispose();
radialWave.dispose();
audioReader.setRunning(false);
audioReader.setLine(null);
audioReaderThread.join(2000);
```

You can also fan the `AudioReader` into multiple sinks simultaneously (e.g. a `PboAudioSink`
for the waveform and a `RollingAudioBuffer` for the FFT analyser):

```java
audioReader = new AudioReader(List.of(pboSink, rollingBuffer));
```

---

## AmplitudeFunction — custom amplitude envelopes

`AmplitudeFunction` is a `@FunctionalInterface` mapping `(vertexIndex, normalisedX) → amplitude`.
`normalisedX` is in `[-1, 1]`. Both `AudioWave` and `RadialWave` use the returned value as a
per-vertex scale factor multiplied directly by the normalised audio sample `[-1, 1]` in the
vertex shader. A value of `1f` gives full-scale displacement on both renderers.

```java
AmplitudeFunction flat    = AmplitudeFunction.constant(1f);
AmplitudeFunction tapered = AmplitudeFunction.ellipse(1f);

// Custom:
AmplitudeFunction custom = (index, x) -> 0.8f * (float) Math.abs(Math.cos(Math.PI * x));

audioWave.setAmplitudeFunction(custom);
radialWave.setAmplitudeFunction(custom);  // same API, same scale
```

---

## SpectrumAnalyser — FFT spectrum renderer

`SpectrumAnalyser` is a pure `RenderedItem` — it receives pre-computed normalised magnitude
values via `FrequencySink.onSpectrum` and draws them as a log-frequency bar chart with a
configurable colour gradient and retro peak-hold ticks. All audio capture and FFT computation is
the caller's responsibility; wire this up as a sink on a `FrequencyProcessor`.

```java
// Fields:
private final FrequencyProcessor freqProc =
        new FrequencyProcessor(1024, 128, 48_000f, 20f, 20_000f, -80f, 0f);
private final SpectrumAnalyser analyser =
        new SpectrumAnalyser(freqProc)
                .withBarColors(new Vector4f(0, 0.8f, 0, 1), new Vector4f(0.8f, 0, 0, 1));
private AudioReader audioReader;
private Thread audioReaderThread;
private final AudioSources audioSources = new AudioSources();
private int selectedSource = 0;

// init():
freqProc.addSink(analyser);
analyser.init(ctx);

audioSources.add(SimulatedSources.middleC(ctx.getClock()));
LineAcquirer.allLinesMatching(LineAcquirer.IDEAL)
        .map(LineAcquirer.MixerLine::toAudioDataSource)
        .forEach(audioSources::add);

audioReader = new AudioReader(List.of(freqProc));
audioReaderThread = new Thread(audioReader, "spectrum-audio-reader");
audioReaderThread.setDaemon(true);
audioReaderThread.start();
audioReader.setLine(audioSources.list().get(selectedSource));

// doRender():
freqProc.process();
analyser.doRender(ctx);

// dispose():
analyser.dispose();
audioReader.setRunning(false);
audioReader.setLine(null);
audioReaderThread.join(2000);
```

### Construction

```java
new SpectrumAnalyser(freqProc)                // gap defaults to DEFAULT_GAP (0.15)
new SpectrumAnalyser(freqProc, gapFraction)   // explicit gap as fraction [0, 1) of slot width
```

The `numBins`, `fMin`, and `fMax` are derived from the `FrequencyProcessor` automatically.

### Colour gradient

```java
// Green at bottom → red at top:
analyser.withBarColors(
    new Vector4f(0.0f, 0.8f, 0.0f, 1.0f),   // colorLow  — bar bottom / low energy
    new Vector4f(0.8f, 0.0f, 0.0f, 1.0f)    // colorHigh — bar top    / high energy
);
// Solid white (palette-renderer compatible):
analyser.withBarColors(new Vector4f(1, 1, 1, 1), new Vector4f(1, 1, 1, 1));
// 50% translucent red bars, fading toward opaque at the tip:
analyser.withBarColors(new Vector4f(0.8f, 0, 0, 0.5f), new Vector4f(0.8f, 0, 0, 1.0f));
```

Default is opaque white/white (monochrome). Call before `init()`.

Colours are RGBA (`Vector4f`). `GLWindow` enables `GL_BLEND` globally with standard
`SRC_ALPHA`/`ONE_MINUS_SRC_ALPHA` blending, so an alpha component below 1 on either stop renders
that part of the bar translucent over whatever was drawn earlier in the frame. The peak-hold tick
colour (`withPeakColor`, below) is likewise RGBA.

### Peak-hold ticks

White tick marks float above each bar: they rise instantly to the bar's current peak, hold for a
configurable dwell period, then sag slowly back to zero — the classic retro graphic-EQ marker.
The bars themselves track the raw FFT magnitude at full speed; only the tick is damped.

```java
// Tune dwell and sag (call before or after init()):
analyser.withPeakDynamics(
    60,         // dwellFrames: frames to hold at peak (~1 s at 60 fps)
    1f / 180f   // peakSagPerFrame: full scale → 0 in ~3 s at 60 fps
);

// Tick line width in pixels:
analyser.withPeakLineWidth(3.0f);   // default: DEFAULT_PEAK_LINE_WIDTH = 3.0
```

| Constant | Default | Meaning |
|---|---|---|
| `DEFAULT_GAP` | 0.15 | 15% of each bar slot is empty |
| `DEFAULT_DWELL_FRAMES` | 30 | ~0.5 s at 60 fps |
| `DEFAULT_PEAK_SAG_PER_FRAME` | 1/180 | ~3 s full scale at 60 fps |
| `DEFAULT_PEAK_LINE_WIDTH` | 3.0 | slightly thicker than one pixel |

### glClear toggle

```java
analyser.setClearBeforeRender(false);   // draw on top of previously rendered content
```

Default is `true`. Set to `false` to layer the spectrum under other renderers (e.g. when
`SpectrumWave` renders beat-detection letters on top of the bars without clearing in between).

### Transform (`Transformable`)

`SpectrumAnalyser` implements `Transformable` — call `setTransform(Matrix4f)` at any time from
any thread to apply a matrix to all output vertex positions (bars, fill area, and peak indicators).
The matrix is read once per frame on the GL thread. Default: identity (no transform).

```java
// Slow rotation with beat-driven scale pulse:
float angle = 0f;
// each frame:
angle += 0.005f;
float pulse = 1.0f + 0.15f * beats.getBeatStrength(0);
analyser.setTransform(new Matrix4f().rotateZ(angle).scale(pulse));

// Reset to identity:
analyser.setTransform(new Matrix4f());
```

The matrix operates in NDC space (origin = screen centre). Applies equally to both `BARS` and
`FILLED` render modes, and to the peak-hold indicators.

### Signal path

```
AudioReader thread
  └─ FrequencyProcessor.write()   (implements AudioSink)
       └─ FrequencyProcessor.process()   [render thread, once per frame]
            └─ FFTProcessor + RollingAudioBuffer  (owned by FrequencyProcessor)
                 └─ FrequencySink.onSpectrum(magnitudes)
                      └─ SpectrumAnalyser.onSpectrum()  → cached internally
                           └─ doRender():
                                ├─ bar shader  (GL_TRIANGLES, numBins × 6 vertices, colour gradient)
                                └─ peak shader (GL_LINES, numBins × 2 vertices, white ticks)
```
---

## RadialSpectrumAnalyser — polar filled spectrum renderer

`RadialSpectrumAnalyser` is a pure `RenderedItem` — it receives pre-computed normalised magnitude
values via `FrequencySink.onSpectrum` and renders them as a smooth continuous filled shape that
radiates **both outward and inward** from a base circle. All geometry is procedural (driven by
`gl_VertexID` in the vertex shader); no per-vertex VBO data is uploaded.

Key properties:
- `GL_LINEAR` texture filtering auto-interpolates between adjacent FFT bins, giving a smooth curve
  with no hard bar edges, regardless of how many ring vertices are used.
- A separate `GL_LINE_LOOP` peak-hold line traces the historical outward maximum.
- The full spectrum can be tiled and mirrored around the circle via the repeats feature.

### Wiring

```java
FrequencyProcessor freqProc = new FrequencyProcessor(4096, 128, 48_000f, 20f, 20_000f, -80f, 0f);
RadialSpectrumAnalyser radial = new RadialSpectrumAnalyser(freqProc)
        .withColors(new Vector4f(0, 0.2f, 0.6f, 1),   // inner tip — deep blue
                    new Vector4f(0, 0.7f, 0.3f, 1),   // base circle — green
                    new Vector4f(0.9f, 0.1f, 0, 1))   // outer tip  — red
        .withPeakColor(new Vector4f(1, 1, 1, 1))       // peak line  — white
        .withRepeats(2);                            // bilateral symmetry
freqProc.addSink(radial);

// init():
radial.init(ctx);

// doRender():
freqProc.process();
radial.doRender(ctx);

// dispose():
radial.dispose();
```

### Constructors

```java
// Default geometry (512 ring verts, baseRadius=0.35, outerHeight=0.55, innerDepth=0.15):
new RadialSpectrumAnalyser(freqProc)

// Custom geometry:
new RadialSpectrumAnalyser(freqProc, numRingVerts, baseRadius, outerHeight, innerDepth)
```

### Colours — 3-stop fill gradient

```java
radial.withColors(inner, base, outer);   // call before init()
```

The fill shape uses a gradient with three named stops:

| Stop | `vFillT` value | Meaning |
|---|---|---|
| `inner` | 0.0 | Inward tip — maximum inward displacement from base circle |
| `base`  | 0.5 | Gradient midpoint — roughly where the base circle sits |
| `outer` | 1.0 | Outward tip — maximum outward displacement |

```java
radial.withPeakColor(new Vector4f(1, 1, 1, 1));  // peak-hold line colour (default opaque white)
// Translucent inner tip, opaque outer tip:
radial.withColors(new Vector4f(0, 0.2f, 0.6f, 0.3f), new Vector4f(0, 0.7f, 0.3f, 1), new Vector4f(0.9f, 0.1f, 0, 1));
```

Colours are RGBA (`Vector4f`), including the peak-hold line. `GLWindow` enables `GL_BLEND`
globally with standard `SRC_ALPHA`/`ONE_MINUS_SRC_ALPHA` blending, so an alpha component below 1
on any gradient stop renders translucent.

### Repeat / symmetry

```java
radial.withRepeats(1);   // full spectrum once around (default)
radial.withRepeats(2);   // two mirrored halves: bass→treble→bass
radial.withRepeats(4);   // four quadrants, adjacent ones mirrored
int r = radial.getRepeats();
```

Odd-numbered repetition segments run the texture coordinate in reverse, so bass and treble meet
at every seam rather than jumping abruptly. Any positive integer is accepted. `withRepeats` is
safe to call after `init()` — the new value takes effect on the next frame.

### Peak-hold tuning

```java
radial.withPeakDynamics(
    30,          // dwellFrames: frames to hold at maximum (~0.5 s at 60 fps)
    1f / 180f    // peakSagPerFrame: full scale falls to 0 in ~3 s at 60 fps
);
radial.withPeakLineWidth(2.0f);   // line width in pixels (default 2.0)
```

### Default constants

| Constant | Default | Meaning |
|---|---|---|
| `DEFAULT_RING_VERTS` | 512 | Vertices tessellating the circle |
| `DEFAULT_BASE_RADIUS` | 0.35 | Base circle radius in NDC units |
| `DEFAULT_OUTER_HEIGHT` | 0.55 | Max outward extension at full magnitude |
| `DEFAULT_INNER_DEPTH` | 0.15 | Max inward contraction at full magnitude |
| `DEFAULT_DWELL_FRAMES` | 30 | ~0.5 s peak hold at 60 fps |
| `DEFAULT_PEAK_SAG_PER_FRAME` | 1/180 | ~3 s full-scale sag at 60 fps |
| `DEFAULT_PEAK_LINE_WIDTH` | 2.0 | pixels |

### glClear toggle

```java
radial.setClearBeforeRender(false);   // composite over previously rendered content
```

Default is `true`.

### Transform (`Transformable`)

`RadialSpectrumAnalyser` implements `Transformable` — call `setTransform(Matrix4f)` at any time
from any thread to apply a matrix to all output vertex positions (fill shape and peak line).
The matrix is read once per frame on the GL thread. Default: identity (no transform).

```java
// Slow rotation with beat-driven scale pulse:
float angle = 0f;
// each frame:
angle += 0.005f;
float pulse = 1.0f + 0.15f * beats.getBeatStrength(0);
radial.setTransform(new Matrix4f().rotateZ(angle).scale(pulse));

// Reset to identity:
radial.setTransform(new Matrix4f());
```

The matrix is applied in NDC space (origin = screen centre), after the aspect-ratio correction
already embedded in the vertex positions — a rotation matrix therefore produces a clean visual
rotation of the already-round shape.

### Signal path

```
AudioReader thread
  └─ FrequencyProcessor.write()   (implements AudioSink)
       └─ FrequencyProcessor.process()   [render thread, once per frame]
            └─ FFTProcessor + RollingAudioBuffer (owned internally)
                 └─ FrequencySink.onSpectrum(magnitudes)
                      └─ RadialSpectrumAnalyser.onSpectrum()  → cached internally
                           └─ doRender():
                                ├─ fill shader  (GL_TRIANGLE_STRIP, numRingVerts*2+2 vertices, 3-stop gradient)
                                └─ peak shader  (GL_LINE_LOOP, numRingVerts vertices, solid colour)
```

---

## BeatDetector — per-band onset detection

`BeatDetector` analyses the `float[] magnitudes` array produced by `FFTProcessor` each frame.
It splits the spectrum into named frequency bands, tracks a rolling energy average per band, and
publishes a `[0, 1]` beat strength that rises instantly when energy spikes above the rolling
average and then decays at a configurable rate. No extra FFT is run — it reuses work already done.

### Frequency bands

`FrequencyBand` is an immutable record `(String name, float fMin, float fMax)`.
Three presets cover the standard drum-machine layout:

| Preset constant | Name string | Hz range | Typical source |
|---|---|---|---|
| `FrequencyBand.BASS` | `"bass"` | 20–250 Hz | Kick drum, sub-bass |
| `FrequencyBand.SNARE` | `"snare"` | 250–2 000 Hz | Snare, clap, attack transients |
| `FrequencyBand.HI_HAT` | `"hihat"` | 2 000–20 000 Hz | Hi-hat, cymbals, shimmer |

`FrequencyBand.defaults()` returns `List.of(BASS, SNARE, HI_HAT)`.

Custom bands are just records — any name, any Hz range:

```java
new FrequencyBand("sub",   20f,   80f)
new FrequencyBand("kick",  80f,  200f)
new FrequencyBand("vocal", 300f, 3_000f)
```

### Signal chain

The preferred way to wire beat detection is through `FrequencyProcessor`, which runs the FFT once
and fans the result to any number of `FrequencySink` consumers (both `BeatDetector` and
`SpectrumAnalyser` implement `FrequencySink`):

```
AudioReader thread
  └─ FrequencyProcessor.write()   (implements AudioSink)
       └─ FrequencyProcessor.process()   [render thread, once per frame]
            └─ FFTProcessor + RollingAudioBuffer  (owned internally)
                 └─ FrequencySink.onSpectrum(magnitudes)
                      ├─ BeatDetector     → getBeatStrength("bass") etc.
                      └─ SpectrumAnalyser → doRender() draws the bars
```

**Critical constraint:** `BeatDetector` must be constructed with the same `numBins`, `fMin`, and
`fMax` as the `FrequencyProcessor` it will receive magnitudes from. The convenience constructor
`new BeatDetector(processor)` extracts these automatically — prefer it over the raw form.

### Construction

```java
// From a FrequencyProcessor — geometry extracted automatically (recommended):
BeatDetector beats = new BeatDetector(freqProc);

// With custom bands from a FrequencyProcessor:
BeatDetector beats = new BeatDetector(List.of(FrequencyBand.BASS, FrequencyBand.HI_HAT), freqProc);

// Raw form — must match the FrequencyProcessor's geometry exactly:
BeatDetector beats = new BeatDetector(numBins, fMin, fMax);

// Full constructor — all parameters explicit:
BeatDetector beats = new BeatDetector(
    FrequencyBand.defaults(),   // or any List<FrequencyBand>
    numBins,                    // must match FrequencyProcessor
    fMin,                       // must match FrequencyProcessor
    fMax,                       // must match FrequencyProcessor
    43,                         // historyLength: frames in rolling average (~0.7 s at 60 fps)
    1.3f,                       // threshold: ratio above average before any trigger (30%)
    2.0f,                       // sensitivity: 50% above average → strength 1.0
    1f / 60f                    // decayPerFrame: full scale decays to zero in one second at 60 fps
);
```

### Wiring into an experiment with FrequencyProcessor

`FrequencyProcessor` implements `AudioSink`, so it replaces `RollingAudioBuffer` in the
`AudioReader`'s sink list. It internally owns the buffer and FFT; call `process()` once per frame
to drive all registered sinks.

```java
// ── Fields ─────────────────────────────────────────────────────────────────────
private FrequencyProcessor freqProc;
private BeatDetector       beatDetector;
private AudioReader        audioReader;
private Thread             audioReaderThread;
private final AudioSources audioSources = new AudioSources();
private int                selectedSource = 0;

// ── init() ─────────────────────────────────────────────────────────────────────
freqProc     = new FrequencyProcessor(1024, 128, 48_000f, 20f, 20_000f, -80f, 0f);
beatDetector = new BeatDetector(freqProc);          // geometry derived from freqProc
freqProc.addSink(beatDetector);

audioSources.add(SimulatedSources.middleC(ctx.getClock()));
LineAcquirer.allLinesMatching(LineAcquirer.IDEAL)
        .map(LineAcquirer.MixerLine::toAudioDataSource)
        .forEach(audioSources::add);

audioReader = new AudioReader(List.of(freqProc));   // freqProc replaces RollingAudioBuffer
audioReaderThread = new Thread(audioReader, "beat-audio-reader");
audioReaderThread.setDaemon(true);
audioReaderThread.start();
audioReader.setLine(audioSources.list().get(selectedSource));

// ── doRender() — one call runs the FFT and notifies all sinks ──────────────────
freqProc.process();

float kickStrength  = beatDetector.getBeatStrength("bass");
float snareStrength = beatDetector.getBeatStrength("snare");
float hihatStrength = beatDetector.getBeatStrength("hihat");
// → pass as shader uniforms (see below)

// ── dispose() ─────────────────────────────────────────────────────────────────
audioReader.setRunning(false);
audioReader.setLine(null);
audioReaderThread.join(2000);
```

### Querying beat strengths

```java
// By name (throws IllegalArgumentException if the name was not registered):
float kick  = beats.getBeatStrength("bass");

// By index (declaration order):
float kick  = beats.getBeatStrength(0);
float snare = beats.getBeatStrength(1);
float hihat = beats.getBeatStrength(2);

// All bands as a snapshot array (allocates; use named/indexed form in the render loop):
float[] all = beats.getBeatStrengths();   // length == getBandCount()

// Introspect the registered bands:
List<FrequencyBand> bands = beats.getBands();
int count = beats.getBandCount();
```

### Using beat strengths as shader uniforms

Beat strength values are plain floats in `[0, 1]` — pass them as uniforms after calling
`update()` each frame:

```java
// After beatDetector.update(magnitudes):
myShader.use(ctx);
myShader.uniforms().get("uKick",  Float.class).set(beatDetector.getBeatStrength("bass"));
myShader.uniforms().get("uSnare", Float.class).set(beatDetector.getBeatStrength("snare"));
myShader.uniforms().get("uHiHat", Float.class).set(beatDetector.getBeatStrength("hihat"));
```

In GLSL, use them to pulse colours, scale geometry, or mix effects:

```glsl
uniform float uKick;    // [0, 1] — bass beat strength, decayed
uniform float uSnare;
uniform float uHiHat;

// Pulse brightness on kick:
vec3 color = baseColor * (1.0 + uKick * 2.0);

// Flash white on snare:
color = mix(color, vec3(1.0), uSnare);
```

### Combining SpectrumAnalyser and BeatDetector on one FFT

Both `SpectrumAnalyser` and `BeatDetector` implement `FrequencySink`. Pass a `FrequencyProcessor`
to the `SpectrumAnalyser(FrequencyProcessor)` constructor and register both as sinks — one call to
`freqProc.process()` per frame drives the entire pipeline:

```java
// ── Fields ─────────────────────────────────────────────────────────────────────
private FrequencyProcessor freqProc;
private SpectrumAnalyser   analyser;
private BeatDetector       beats;
private AudioReader        audioReader;
private Thread             audioReaderThread;
private final AudioSources audioSources = new AudioSources();
private int                selectedSource = 0;

// ── init() ─────────────────────────────────────────────────────────────────────
freqProc = new FrequencyProcessor(1024, 128, 48_000f, 20f, 20_000f, -80f, 0f);
analyser = new SpectrumAnalyser(freqProc);    // pure renderer — no internal AudioReader
beats    = new BeatDetector(freqProc);        // geometry matched automatically
freqProc.addSink(analyser);
freqProc.addSink(beats);

audioSources.add(SimulatedSources.middleC(ctx.getClock()));
LineAcquirer.allLinesMatching(LineAcquirer.IDEAL)
        .map(LineAcquirer.MixerLine::toAudioDataSource)
        .forEach(audioSources::add);
analyser.init(ctx);                           // GL setup only; no audio thread started

audioReader = new AudioReader(List.of(freqProc));
audioReaderThread = new Thread(audioReader, "freq-audio-reader");
audioReaderThread.setDaemon(true);
audioReaderThread.start();
audioReader.setLine(audioSources.list().get(selectedSource));

// ── doRender() — one FFT, two consumers ───────────────────────────────────────
freqProc.process();               // runs FFT; calls analyser.onSpectrum() and beats.onSpectrum()
analyser.doRender(ctx);           // uploads texture, draws bars — uses magnitudes set above
float kick = beats.getBeatStrength("bass");

// ── dispose() ─────────────────────────────────────────────────────────────────
analyser.dispose();               // GL cleanup only — no thread to join
audioReader.setRunning(false);
audioReader.setLine(null);
audioReaderThread.join(2000);
```

### Tuning reference

| Parameter | Default | Effect |
|---|---|---|
| `historyLength` | 43 | Longer = slower response to new tempo; shorter = more false positives in sustained passages |
| `threshold` | 1.3 | Higher = fewer triggers; 1.0 triggers on any above-average energy |
| `sensitivity` | 2.0 | Higher = reaches full strength at a lower peak; lower = more dynamic range |
| `decayPerFrame` | 1/60 | `1/60` decays full scale in 1 s at 60 fps; larger values decay faster |

### Custom band example

```java
List<FrequencyBand> percussionBands = List.of(
    new FrequencyBand("sub",    20f,   80f),    // sub-kick thump
    new FrequencyBand("kick",   80f,  200f),    // kick body
    new FrequencyBand("snare",  200f, 600f),    // snare crack
    new FrequencyBand("hihat", 600f, 8_000f),   // closed hi-hat
    new FrequencyBand("air",  8_000f, 20_000f)  // cymbal shimmer / air
);
BeatDetector beats = new BeatDetector(
    percussionBands, NUM_BINS, F_MIN, F_MAX, 43, 1.3f, 2.0f, 1f / 60f
);
```

### Startup behaviour

For approximately the first `historyLength + ceil(1 / decayPerFrame)` frames after construction,
the rolling average is building up from zero — any non-silent audio will produce a brief burst of
high beat strengths on all bands. At the default settings this clears in about 1 second. This is
harmless for continuous visualisers; if you need to suppress it (e.g. for onset logging), ignore
values until `update()` has been called at least `historyLength * 3` times.

---

## Constraints

| Constraint | Detail |
|---|---|
| **GL thread for PboAudioSink** | `PboAudioSink.create()` and `upload()` must be called on the GL thread. `write()` is safe from the audio thread. |
| **upload() once per frame** | Call `audioSink.upload()` exactly once per frame before any renderer that shares that sink. Calling it multiple times per frame wastes a DMA transfer; skipping it leaves the texture stale. |
| **AUDIO_BUFFER_SIZE compatibility** | `AudioWave.AUDIO_BUFFER_SIZE` and `RadialWave.AUDIO_BUFFER_SIZE` are both `2048`. Pass either constant to `PboAudioSink.create()` when sharing between them. |
| **AudioReader thread lifecycle** | Start before calling `setLine()`; stop with `setRunning(false)` + `setLine(null)` + `join(2000)` in `dispose()`. |
| **AudioDataSource has no `isOpen()`** | An `AudioLine` only exists once `open()` has succeeded — `setLine()` handles this for you; don't try to read/start/stop an `AudioDataSource` directly. |
| **AudioSources tracks no selection** | It's just add/remove/list. Cycling ("next input") is the caller's own index into `audioSources.list()`. |
| **FrequencyProcessor.process() before sinks** | Call `freqProc.process()` once per frame before any `FrequencySink` (BeatDetector, SpectrumAnalyser) that reads the result in the same frame. |
| **addSink() is your responsibility** | Constructing a `BeatDetector` or `SpectrumAnalyser` with a `FrequencyProcessor` does NOT auto-register it. Call `freqProc.addSink(sink)` explicitly. |
| **BeatDetector geometry must match FrequencyProcessor** | Use `new BeatDetector(freqProc)` to derive geometry automatically. If using the raw constructor, pass the same `numBins`, `fMin`, `fMax` as the `FrequencyProcessor`. |
| **BeatDetector is GL-thread-safe** | `BeatDetector.onSpectrum()` / `update()` and all getters are pure CPU arithmetic — call only on the render thread. |
