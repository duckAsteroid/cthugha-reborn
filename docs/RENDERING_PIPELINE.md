# Rendering Pipeline

## Overview

Cthugha Reborn renders a palettized (colour-indexed) pixel buffer in real time and
reacts to live audio. Every frame is a fixed sequence of GPU passes, organised by a
component model that keeps the `CthughaWindow` orchestrator small and ignorant
of what each visual effect does.

---

## Texture vocabulary

All rendering revolves around two R16 indexed textures, one GRAY blur intermediate,
and a RGBA palette LUT:

| Name | Format | Role |
|---|---|---|
| `displayTex` | R16 (GL_R16, uint16) | Blur output; translate source (next step's feedback input) |
| `renderTex` | R16 (GL_R16, uint16) | Translate output; indexed-render target; display source; blur input |
| `flameTex` | GRAY (render-core DataFormat.GRAY) | Intermediate between the two blur passes |
| `palette` | 256×N RGBA | Colour LUT, one texel per entry, row-major; uploaded when dirty |

`displayTex` and `renderTex` store palette indices as 16-bit unsigned integers normalised
to `[0, 1]` when sampled. `IndexPaletteRenderer` resolves a pixel's palette entry as
`round(sampledValue * (entries − 1))`, so `0.0` is the first entry and `1.0` the last,
whatever the palette size. Wave renderers and flash effects write `1.0` (the maximum
normalised value) to target the last palette entry.

A translation map is an RG16UI texture: each texel holds the absolute (x, y) pixel
coordinates to *read from* in `displayTex`, so it encodes an arbitrary per-pixel
displacement field. There are two live maps, slots A and B, which the translate pass
blends (see [Morphing between tables](#morphing-between-tables)), plus a spare.

### Render buffer size

The indexed textures, the blur intermediate and the map slots all share one size, the
*render buffer* (`JCthugha.bufferWidth/Height`), which the palette pass scales to fill the
output. `RenderSize` (`display/`) derives it from the output (the window, or the monitor
when fullscreen) and `[display]` in `cthugha.ini`:

- By default the buffer has the output's shape, scaled down evenly to fit within
  `render_max` (`WxH`, default 1920×1080; `0` = no cap); an output that already fits is
  used 1:1. A 3840×2160 fullscreen output gives 1920×1080; a 2000×1000 window 1920×960.
- `render_width` / `render_height` set the size explicitly and override the cap, in pixels
  (`"1280"`, `"1280px"`) or as a percentage of the current output (`"50%"`); an unset one
  is 100% of the output.

The buffer follows the output. A framebuffer resize (dragging the window edge, or toggling
fullscreen) is debounced for 250 ms; then, if the size it calls for changed,
`CthughaWindow.followOutputSize()` gives the existing textures new storage at that size
(so FBOs and renderers keep their references), `JCthugha.resizeBuffer` swaps in an
identity table at the new size (filling both morph slots), and the current generator's
table is regenerated and morphs in. The flame trail restarts at that moment.

---

## Frame sequence

The flame simulation (indexed pass + blur) runs in *steps* at a fixed rate in wall-clock
time, the Render tab's "Speed" (`JCthugha.simSpeed`, steps per second, 10–120, default
60), so motion speed doesn't depend on the display's refresh rate. A translate step moves
each pixel a whole map displacement, so steps can't be fractional: `StepClock` accumulates
elapsed time × Speed and each frame runs the whole steps due, 0 or more. Its accumulator
starts half a step in, so when Speed matches the refresh rate vsync jitter still gives
exactly one step per frame; after a stall at most `ceil(speed / refresh) + 1` steps run and
the rest of the time is dropped. A frame that runs no step re-presents `renderTex` as it is.
In-buffer (`BUFFER`/`BOTH`) waves, flashes and quotes therefore update at the step rate,
while the screen pass, including `OVERLAY` elements, runs every frame.

```
CPU tick ──────────────────────────────────── JCthugha.doRenderCPU()
Palette upload (if dirty) ─────────────────── glTexSubImage2D palette
phase.frameUpdate() × N ───────────────────── per-frame work (audio upload, FFT, wave sync)
                                                   │
╔═══════ Simulation step × 0..k (StepClock) ═══════╗
┌──────── Indexed pass (renderFBO bound) ──────────┤
│  Translate      displayTex ──[tab map]──► renderTex │
│  phase.indexedRender() × N            (wave/flash/quote-in-buffer write here)
└──────────────────────────────────────────────────┘
                                                   │
┌──────── Blur / flame pass ───────────────────────┤
│  xBlur   renderTex ──► flameTex   (horizontal Gaussian + multiplier=1.0)
│  yBlur   flameTex ──► displayTex  (vertical Gaussian  + multiplier=fade)
└──────────────────────────────────────────────────┘  (feeds next step's translate)
╚══════════════════════════════════════════════════╝
                                                   │
┌──────── Screen pass (default FBO) ───────────────┤
│  IndexPaletteRenderer  renderTex ──[palette LUT]──► RGBA window
│  phase.screenRender() × N            (overlays: quote, notifs, QR)
└──────────────────────────────────────────────────┘
```

---

## Indexed pass

`CthughaWindow` binds `renderFBO` and hands control to each phase in order.

### Translate step

`MorphTranslateRenderer` (`display/`) runs a fragment shader that reads the two RG16UI
map slots: for every pixel in `renderTex` it looks up the source coordinate each slot
stores, blends them by the morph's Mix, and samples that pixel from `displayTex` with
`texelFetch`. This is the *displacement* or *warping* effect — the image never stops
moving even when no audio is playing. At Mix 0 or 1 it reads exactly one slot's
coordinate.

### Phase indexedRender()

Each `RenderPhase` that participates in the indexed pass renders *directly into the
currently bound R16 FBO* (`renderTex`). Because the framebuffer is R16, the red channel
of every fragment output is stored as a normalised 16-bit value interpreted as a palette
index by `IndexPaletteRenderer`:

```
palette index N  →  red channel value  N / (entries − 1)
pixelIndex       =  round(sampledValue * (entries − 1))
```

Wave renderers default to `red = 1.0` (the last palette entry). Flash effects use the
luma value of the source image mapped to `[0, 1]`, so bright pixels reach high palette
indices. Only fragments actually *emitted* by the geometry (LINE\_STRIP, filled arcs)
overwrite pixels; everything else is unchanged.

### Why round-to-nearest

render-core's own `PaletteRenderer` decodes with `floor(pos * totalEntries)`, which puts
every `N / size` value exactly on the lower edge of its bucket. That had two failure
modes, which is why the app uses its own `IndexPaletteRenderer` instead:

- **`1.0` wrapped to entry 0.** `1.0 * 256 = 256` decodes to column `mod(256, 256) = 0`
  — the first entry, not the last.
- **R16 quantisation rounded down for the upper half.** Storing `N / 256` as unorm16
  gives `round(65535·N / 256) = 256·N − round(N / 256)`, one step *below* `N / 256` for
  `N ≥ 128`, so `floor` landed on entry `N − 1`.

Rounding to the nearest of `entries` evenly spaced steps puts every encoded value in the
middle of its bucket, well clear of R16 quantisation error.

---

## Blur / flame pass

Two `BlurTextureRenderer` passes implement a separable Gaussian blur with a *fade
multiplier*. The blur output is **not** what's displayed this frame — it's the input to
the *next* frame's translate. So freshly drawn waves/flashes appear on screen at full
value, and only start spreading and fading on the next trip round the feedback loop:

1. **xBlur** — horizontal pass, `renderTex` → `flameTex`, multiplier = 1.0
2. **yBlur** — vertical pass, `flameTex` → `displayTex`, multiplier = `blurFade` (default 0.99)

The fade multiplier slightly darkens the output each step (it applies even when blur is
disabled), so colours drift toward palette index 0 over time. The fade is geometric: a
pixel is at `fadeⁿ` after n steps, so at the default 0.99 each halving takes ~69 steps
and the top of the palette flashes by much faster than the bottom. Combined with the
translate warp this creates the classic "flame spread" effect: bright wave pixels bloom outward, shift, and gradually fade.

Blur can be toggled (`blurEnabled`) and the Gaussian kernel size is adjustable
(`blurKernelSize`). The kernel size must be odd and is bounded by
`BlurTextureRenderer.MIN_KERNEL_SIZE` / `MAX_KERNEL_SIZE`.

After both blur passes, `displayTex` holds the blurred, faded image that the next step
translates from. Because translate and blur don't commute, displaying before the blur
means translate's nearest-neighbour artefacts (duplicated pixels on zooming/stretching
tables) are visible for one frame before the blur smooths them.

---

## Screen pass

`CthughaWindow` unbinds all FBOs and calls `IndexPaletteRenderer` to convert `renderTex`
(R16) to RGBA using the palette LUT texture. Each R16 normalised value is rounded to an
entry index (see [Why round-to-nearest](#why-round-to-nearest)) and fetched from the LUT
with `texelFetch` at `(idx % width, idx / width)`, where `width` comes from
`textureSize()` so it always reflects the current LUT.

Each phase then gets `screenRender()` to draw RGBA overlays on top of the palette
output. Every phase manages its own blend state (enable/disable `GL_BLEND`).

---

## RenderPhase interface

```java
public interface RenderPhase {
    default void init(RenderContext ctx) throws IOException {}
    default void indexedRender(RenderContext ctx) throws IOException {}
    default void screenRender(RenderContext ctx) throws IOException {}
    default void dispose() {}
    default void registerActions(ParamNode generalGroup,
                                  RenderActionQueue renderActions) {}
}
```

All methods are default no-ops so a phase only overrides what it participates in.
`JCthugha.createPhases()` builds the base ordered list (`WavePhase`, `FlashPhase`,
`VideoPhase`, `QuotePhase`, a `NotifPhase`, `DebugBeatsPhase`); `CthughaWindow` then
appends `WorkPhase` unconditionally and `QrPhase` only when the remote server is
enabled. Two non-phase helpers also live in `display/phase/`: `PhaseConfig` (shared
INI-parsing statics used by several phases) and `SolidQuad` (a shared full-screen quad
helper).

### Phase implementations

Runtime order: `WavePhase → FlashPhase → VideoPhase → QuotePhase → NotifPhase →
DebugBeatsPhase → WorkPhase → [QrPhase if remote enabled]`.

| Phase | Indexed | Screen | Actions registered |
|---|---|---|---|
| `WavePhase` | Renders wave geometry into renderTex (`BUFFER`/`BOTH` instances) | Renders wave geometry as an RGB overlay (`OVERLAY`/`BOTH` instances) | Cycle Audio |
| `FlashPhase` | Bakes flash texture into renderTex | — | Flash Image, Flash White |
| `VideoPhase` | — | Alpha-blends the looping video overlay onto the screen FBO | (video selection lives on the "Videos" tab, not as a general action) |
| `QuotePhase` | Bakes quote text (when `mode` is `BUFFER`/`BOTH`) | Renders quote overlay (when `mode` is `OVERLAY`/`BOTH`) | Show Quote, Toggle Quote Mode |
| `NotifPhase` | — | Renders 3-second notification toast | — |
| `DebugBeatsPhase` | — | Renders a beat-detector debug HUD (gated on the "Audio" tab's Debug Beats toggle) | — |
| `WorkPhase` | — | Renders a spinner + status label while background work is active | — |
| `QrPhase` | — | Renders QR code overlay | — |

---

## WavePhase in detail

`WavePhase` owns the `AudioPipeline` plus a live `WaveEntry` per wave instance
currently in `cthugha.waveSystem` (a `WaveSystem` `ParamNode` holding a dynamic,
ordered list — see "Wave Models" in `CLAUDE.md`). Each instance is one of five model
types: `OscilloscopeModel`, `RadialWaveModel`, `SpectrumModel`, `RadialSpectrumModel`,
`RadialClockModel`.

On each `indexedRender()` call it:

1. Calls `audioPipeline.update()` to push the latest audio samples to GPU PBOs
2. Calls `resyncWaves()` — reconciles the live `entries` map (a `LinkedHashMap`,
   append-only, so render order tracks the wave system's insertion order) against
   `cthugha.waveSystem.instances()`, disposing renderers for removed instances and
   building renderers for new ones
3. Renders every entry whose `mode` is not `OVERLAY` (i.e. `BUFFER` or `BOTH`)

`screenRender()` then renders every entry whose `mode` is not `BUFFER` (i.e. `OVERLAY`
or `BOTH`) as an alpha-blended RGB overlay on the screen FBO — the same GL draw call,
issued a second time against the window framebuffer instead of `renderTex`, coloured by
the instance's RGB `ColorParam` instead of its palette index. `RenderMode` (`BUFFER` /
`OVERLAY` / `BOTH`, shared with `QuotePhase`) lets a wave draw into the indexed buffer
(subject to blur/translate/fade), as a crisp always-on-top overlay, or both.

Each `WaveEntry.render(ctx, overlayPass)` picks its colour based on which pass called
it:

```java
wave.setLineColour(overlayPass
        ? model.color.toVector4f(1f)                                  // screen overlay: RGB
        : new Vector4f((float) model.index.value, 0f, 0f, 1f));        // indexed buffer: palette index
```

`model.index` defaults to `1.0f` (the maximum normalised value — last palette entry).
For the three spectrum-family renderer types (`Spectrum`, `RadialSpectrum`,
`RadialClock`), each wave instance keeps **two independently-live GL analyser
objects** — one built with index (buffer) colours, one with RGB (overlay) colours —
since render-core's analysers only accept colours at construction time; a colour
change marks the stale one dirty and it's lazily disposed and rebuilt on the GL
thread.

---

## FlashPhase in detail

`FlashPhase` uses `TextureBakeRenderer` to write a full-screen texture into the
currently bound R16 FBO in one draw call:

- **White flash**: a 1×1 RGBA texture with R=0xFF, A=0xFF. The normalised R value (1.0)
  maps to the last palette entry. Uploaded once at init.
- **Image flash**: the selected PNG file from `RandomImageSource` converted to RGBA where
  `R = luma(pixel) / 255`, so bright parts of the image map to high palette indices.
  Uploaded on demand on the GL thread.

`TextureBakeRenderer`'s fragment shader outputs `vec4(s.r, 0, 0, s.a)` — it routes
the source red channel directly to the R16 output and uses the alpha channel for
transparency so partial-alpha pixels blend with the underlying content.

---

## QuotePhase in detail

Quote rendering is controlled by a `mode` (`RenderMode`) parameter — the same
`BUFFER` / `OVERLAY` / `BOTH` enum `WavePhase` uses for its wave instances:

**Overlay mode** (default, `mode = OVERLAY`):  
`screenRender()` alpha-blends `StringRenderer` output over the palette-converted frame.
The text colour is white RGBA. Skipped when `mode == BUFFER`.

**Buffer-bake mode** (`mode = BUFFER`, or both passes run when `mode = BOTH`):  
`indexedRender()` uses a GL framebuffer save/restore trick to avoid needing a reference
to `renderFBO` (skipped when `mode == OVERLAY`):

```java
// save whatever FBO is currently bound (renderFBO, bound by CthughaWindow)
glGetIntegerv(GL_FRAMEBUFFER_BINDING, savedFbo);

// render RGBA text into a private offscreen RGBA FBO
textFBO.bind();
glClear(GL_COLOR_BUFFER_BIT);
quoteRenderer.doRender(ctx);
textFBO.unbind();

// restore renderFBO and bake the RGBA text → palette indices
glBindFramebuffer(GL_FRAMEBUFFER, savedFboId);
textBaker.doRender(ctx);   // TextureBakeRenderer: r → R16 palette index
```

This keeps the `RenderPhase` interface clean: no phase ever holds a reference to
`renderFBO` directly.

---

## VideoPhase in detail

`VideoPhase` plays a single video file on loop as a full-screen (stretch-to-fill),
alpha-blended **screen** overlay — it has no `indexedRender()` at all, since decoded
video is real RGBA colour, not a palette index, so it belongs in the RGBA screen pass
rather than the R16 indexed buffer. This is playback of local video files, not
recording or export — there is no encoder anywhere in the codebase.

**Library**: `video/VideoLibrary` loads a manifest (`videos/manifest.json`, modelled
by `video/VideoManifest` + `video/VideoEntry`) describing each video's file, title,
source/license, tags, duration, and named chapters (start/end sub-ranges plus an
optional default chapter). `VideoLibrary.generateThumbnail()` produces a cached `.png`
sidecar per video via a throwaway `FFmpegFrameGrabber`. `video/VideosLibraryNode` is
the "Videos" tab: a thumbnail-grid picker (`EnumParameter<String>`) plus a "Random"
action, calling `videoPhase.loadVideo(entry)` on selection.

**Decoding**: built on JavaCV/FFmpeg bindings (`org.bytedeco:javacv`, pinned to
`linux-x86_64` native jars only — see `app/build.gradle`), configured to decode
directly to `AV_PIX_FMT_RGBA` so no colour-space conversion happens on the Java side.
Each active video gets one virtual decode thread (`decodeLoop()`), paced against each
decoded frame's PTS (scaled by the `speed` parameter) rather than running flat-out, so
playback matches the file's real rate and can be slowed/sped/paused live. Frames pass
to the GL thread through `VideoPhase.UploadRing`: a pixel-unpack buffer of three frame
slots, created with `glBufferStorage` and mapped persistent + coherent, so the decode
thread copies each decoded frame straight into GPU-visible memory. `FrameSlots` tracks
each slot as free, being written, pending or in flight without locks: the decoder takes
a free slot (dropping the frame when none is free), fills it and publishes it, which
recycles any pending frame the GL thread never took, so only the newest frame is shown.
Each slot holds a whole frame at javacv's padded row stride (rows padded to at most a
multiple of 64 pixels), sized from the video's width and height.

**Upload/render**: `screenRender()` first releases in-flight slots whose fences have
signalled (zero-timeout `glClientWaitSync`), then, if a frame is pending, binds the
buffer and issues `glTexSubImage2D` from that slot's offset into `videoTex` (with
`GL_UNPACK_ROW_LENGTH` set to the frame's stride) and fences it. The copy runs on the
GPU, so no pixel data passes through the GL thread. `videoTex` is allocated in `init()`
and reallocated only across a resolution change on `loadVideo`. It then draws a full-screen quad with a
GLSL shader supporting colour mode (colour / greyscale / black-and-white), invert, a
vignette, the same `TransformParams` zoom/rotation/spin/stretch/translation/shear/perspective
convention used by `QuotePhase`/wave models, and a `BlendMode` (`NORMAL`, `ADD`,
`MULTIPLY`, `SCREEN`, `DARKEN`, `LIGHTEN`) mapped to paired `glBlendFunc`/
`glBlendEquation` state (`DARKEN`/`LIGHTEN` use the `GL_MIN`/`GL_MAX` equations, which
ignore blend-func factors, so their alpha-fade is baked into the shader's output colour
instead).

**Switching videos**: `loadVideo(VideoEntry)` is callable from any thread — it blocks
to tear down the old decode thread/grabber off the GL thread, then marshals only the
texture reallocation and a fresh `UploadRing` onto the GL thread via `RenderActionQueue`
(the old ring is freed there once its decode thread has stopped), so switching videos
never blocks the render loop on decoding.

> **Native-image caveat**: `docs/NATIVE_IMAGE.md`'s "known gaps" list explicitly calls
> out video overlay playback as not yet exercised under the GraalVM native-image build
> (alongside bindings, tab generators, and screen configs) — it may need extra
> reachability metadata for FFmpeg/JavaCV's native bindings.

---

## Translation tables

The displacement field is held CPU-side in a `TabBuffer` (a direct `ByteBuffer` viewed
as a `ShortBuffer` of RG16UI pairs); the newest table is `JCthugha.getTabBuffer()`, and
the GPU holds it in one of the morph's map slots. When the table changes
`CthughaWindow.rebuildTranslateMap()` hands it to `TableMorph`, which uploads it with
`glTexSubImage2D` into a pre-allocated texture and morphs to it.

Regeneration runs off the GL thread, on a virtual thread with no on-screen work
indicator, through `TranslationRegenerator` (`tab/`), which coalesces requests: at most one regeneration runs at a time, and any changes made while it
runs (a slider drag on a generator parameter, a burst of triggers) cost a single follow-up
run with the then-current values, so the table always ends up matching the parameters. A
generator pick or "Randomise" requests a randomising run, which a later plain regenerate
request does not downgrade. Each run computes into a spare `TabBuffer`
(`JCthugha.computeTranslation`), hands it to the GL thread to adopt and upload
(`JCthugha.adoptTranslation`), and waits for that before the next run, so the buffer it
replaced becomes the next run's spare and no buffer is allocated per run at a fixed size.
A table loaded from elsewhere (a saved preset, `JCthugha.loadTabBuffer`) bumps an epoch,
so a run computed against the previous table is discarded instead of overwriting it.
The generator's name is shown as a notification only after a generator pick or
"Randomise", not after a regeneration from a parameter change.

### Morphing between tables

A new table never replaces the live one instantly: `TableMorph` (`tab/`, the Tab tab's
"Morph" group under `Translate Source`) morphs to it. There are two map slots, A and B,
and a **Mix** parameter (0 = all A, 1 = all B) that the translate shader reads each step.
For each destination pixel the shader takes each slot's *displacement* (source −
destination) the shorter way round the toroidal buffer, blends the two by Mix, adds it
back and wraps the result, so the flow bends from one shape to the other and a pixel whose
source wraps across an edge in one map never sweeps across the middle of the screen.

Every map change — a preset load, a generator pick, "Randomise", or a regeneration after a
parameter change — goes through `TableMorph.onNewTable`:

- The new table is uploaded into the **less visible** slot (the one Mix is further from),
  and Mix moves to it over **Duration** (None, 0.25–8 s; default 1 s) with **Easing**
  (Smooth = smoothstep, or Linear), in wall-clock time. With Duration None, Mix jumps.
- If Mix is strictly between 0 and 1 when a table arrives (mid-morph, or set there by
  hand), the current mixed look is first **baked** into the nearer slot: a second shader
  (`MorphTranslateRenderer.Bake`) writes the blended source coordinates into an RG16UI
  framebuffer as a plain table, Mix is set to that slot's end, and the morph starts from
  there. Nothing on screen jumps, so a burst of tables (a slider drag on a generator
  parameter) glides continuously.
- While a continuous binding drives Mix (`isControlled()`), the binding owns it: a new table
  doesn't move Mix, but waits in the spare texture until the less visible slot is at most
  5% visible (or the Duration, or 1 s with None, has passed) and then replaces it, so a
  breathing morph (e.g. two presets loaded one after the other plus `sine(0.05)` on Mix)
  carries on into the new table.

The three map textures rotate roles: slot A, slot B, and a spare that new tables are
uploaded or baked into before being promoted into a slot, whose old texture becomes the
spare. A restored screen config (or the saved session at startup) fills both slots with its
regenerated table at once, rather than morphing to it.

Generator parameters (every value under each `TabGenerator`, and the `Generator` picker)
are marked `withNoAnimate()`: a per-tick continuous binding would regenerate the whole
table continuously. Edge-triggered bindings can still set them.

`TabGenerator` implementations live in `tab/generators/`, grouped into themed
subpackages, and are discovered/registered by `GeneratorRegistry` via `@AutoService`:

| Subpackage | Generators |
|---|---|
| `tab/generators/` (top level) | `NoDistortion` |
| `tab/generators/fractal/` | `Mandelbrot` |
| `tab/generators/lens/` | `CircleInversion`, `FisheyeLens`, `GravityWells`, `LogPolarZoom`, `Shatter`, `Space`, `SpherePour` |
| `tab/generators/rotation/` | `BigHalfWheel`, `ConcentricRings`, `DownSpiral`, `Hurricane`, `Kaleidoscope`, `Pinwheel`, `SpiralGalaxyPlughole`, `Spiral`, `Twist` |
| `tab/generators/wave/` | `LinearSlider`, `PlasmaFlow`, `Ripple`, `SineGridWarp`, `Smoke`, `WaveInterference` |

`TabStore` saves and loads presets to/from the `tabs/` directory.

---

## Palette system

`PaletteMap` holds packed RGB integers, one per entry. Every shipped `.MAP` file has 256
entries (the original DOS Cthugha format), but `MapFileReader` accepts any count ≥ 1 and
the remote palette editor can save palettes of other sizes. The R16 index textures can
address up to 65,535 entries.

`JCthugha` owns the active map and exposes `loadPalette(PaletteMap)` which sets
`paletteDirty = true` in the window. On the next frame `CthughaWindow` uploads the new
colours to `paletteTex` and clears the flag. The LUT is always 256 texels wide and
`ceil(size / 256)` rows tall (so 256×1 for a standard palette); it's updated in place
with `glTexSubImage2D`, or reallocated with `glTexImage2D` when the row count changes.

The LUT's padding texels (past the last entry, in the final row) are zero-filled and never
addressed. After each upload `CthughaWindow` passes the new entry count to
`IndexPaletteRenderer.setEntries()`, which pushes it to the shader's `entries` uniform
every frame.

`PaletteLibraryNode` in the parameter tree lists all `.MAP` files in the `maps/`
directory and exposes palette switching through the remote UI.

---

## Buffer lifetimes and ownership

```
CthughaWindow
  ├── displayTex / renderTex             (R16, owned by window)
  ├── flameTex                           (GRAY, owned by window — blur intermediate)
  ├── mapTex[3] + mapFBO[3]              (RG16UI map slots A/B + spare, owned by window)
  ├── paletteTex                         (256×N RGBA, owned by window)
  ├── displayFBO / renderFBO / flameFBO  (owned by window)
  ├── MorphTranslateRenderer (+ Bake) (owned by window)
  ├── BlurTextureRenderer ×2        (owned by window)
  ├── IndexPaletteRenderer          (owned by window)
  └── List<RenderPhase>
        ├── WavePhase               (owns AudioPipeline + a WaveEntry per live wave
        │                            instance in cthugha.waveSystem; spectrum-family
        │                            entries keep 2 GL analysers each — index + RGB)
        ├── FlashPhase              (owns TextureBakeRenderer + flash textures)
        ├── VideoPhase              (owns videoTex + a virtual FFmpeg decode thread)
        ├── QuotePhase              (owns 2 StringRenderers + text FBO + TextureBakeRenderer)
        ├── NotifPhase              (owns 1 StringRenderer)
        ├── DebugBeatsPhase         (owns 1 StringRenderer)
        ├── WorkPhase               (owns 2 StringRenderers; added by CthughaWindow,
        │                            not JCthugha.createPhases())
        └── QrPhase (optional)     (owns QrOverlay)
```

All GL resources are disposed in reverse order: phases first (via the phases loop in
`CthughaWindow.dispose()`), then the GL backbone renderers, then the FBOs and textures.
