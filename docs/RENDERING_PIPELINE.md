# Rendering Pipeline

## Overview

Cthugha Reborn renders a palettized (256-colour indexed) pixel buffer in real time and
reacts to live audio. Every frame is a fixed sequence of GPU passes separated by a
two-phase component model that keeps the `CthughaWindow` orchestrator small and ignorant
of what each visual effect does.

---

## Texture vocabulary

All rendering revolves around three named R8 textures (one byte = one palette index per
pixel) and a RGBA palette LUT:

| Name | Format | Role |
|---|---|---|
| `pingTex` | R8 | Translate source; display source; blur output |
| `pongTex` | R8 | Translate output; indexed-render target; blur input |
| `flameTex` | R8 (GRAY) | Intermediate between the two blur passes |
| `palette` | 256×1 RGBA | 256-entry colour LUT; uploaded when dirty |

The translate map is a separate RG16UI texture (`translateMap`): each texel holds the
absolute (x, y) pixel coordinates to *read from* in `pingTex`, so it encodes an
arbitrary per-pixel displacement field.

---

## Frame sequence

```
CPU tick ──────────────────────────────────── JCthugha.doRenderCPU()
Palette upload (if dirty) ─────────────────── glTexSubImage2D palette
                                                   │
┌──────── Indexed pass (pongFBO bound) ────────────┤
│  Translate      pingTex ──[tab map]──► pongTex   │
│  phase.indexedRender() × N            (wave/flash/quote-in-buffer write here)
└──────────────────────────────────────────────────┘
                                                   │
┌──────── Blur / flame pass ───────────────────────┤
│  xBlur   pongTex ──► flameTex   (horizontal Gaussian + multiplier=1.0)
│  yBlur   flameTex ──► pingTex   (vertical Gaussian  + multiplier=fade)
└──────────────────────────────────────────────────┘
                                                   │
┌──────── Screen pass (default FBO) ───────────────┤
│  PaletteRenderer  pingTex ──[palette LUT]──► RGBA window
│  phase.screenRender() × N            (overlays: quote, notifs, QR)
└──────────────────────────────────────────────────┘
```

---

## Indexed pass

`CthughaWindow` binds `pongFBO` and hands control to each phase in order.

### Translate step

`TranslateTextureRenderer` (render-core) runs a fragment shader that reads the RG16UI
translation map: for every pixel in `pongTex` it looks up the coordinate stored in
`translateMap` and samples that pixel from `pingTex`. This is the *displacement* or
*warping* effect — the image never stops moving even when no audio is playing.

### Phase indexedRender()

Each `RenderPhase` that participates in the indexed pass renders *directly into the
currently bound R8 FBO* (`pongTex`). Because the framebuffer is R8, the red channel
of every fragment output is interpreted as a raw palette index (normalised to [0, 1]):

```
palette index N  →  red channel value  N / 255.0
```

Wave renderers use index 200 (≈ 0.784) as the default wave colour. Flash effects use
the luma value of the source image as the index, so bright pixels map to high palette
indices and dark pixels to low ones. Only fragments actually *emitted* by the geometry
(LINE\_STRIP, filled arcs) overwrite pixels; everything else is unchanged.

---

## Blur / flame pass

Two `BlurTextureRenderer` passes implement a separable Gaussian blur with a *fade
multiplier*:

1. **xBlur** — horizontal pass, `pongTex` → `flameTex`, multiplier = 1.0
2. **yBlur** — vertical pass, `flameTex` → `pingTex`, multiplier = `blurFade` (default 0.99)

The fade multiplier slightly darkens the output each frame, so colours drift toward
palette index 0 over time. Combined with the translate warp this creates the classic
"flame spread" effect: bright wave pixels bloom outward, shift, and gradually fade.

Blur can be toggled (`blurEnabled`) and the Gaussian kernel size is adjustable
(`blurKernelSize`). The kernel size must be odd and is bounded by
`BlurTextureRenderer.MIN_KERNEL_SIZE` / `MAX_KERNEL_SIZE`.

After both blur passes, `pingTex` is the authoritative indexed frame ready for display.

---

## Screen pass

`CthughaWindow` unbinds all FBOs and calls `PaletteRenderer` to convert `pingTex`
(R8) to RGBA using the 256-entry palette LUT texture. Each R8 value is used as a
1D texture coordinate into the palette, outputting the corresponding RGB colour.

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
    default void registerActions(ContainerNode generalGroup,
                                  RenderActionQueue renderActions) {}
}
```

All methods are default no-ops so a phase only overrides what it participates in.
`JCthugha.createPhases()` builds the default ordered list; `CthughaWindow` appends
`QrPhase` when the remote server is enabled.

### Phase implementations

| Phase | Indexed | Screen | Actions registered |
|---|---|---|---|
| `WavePhase` | Renders wave geometry into pongTex | — | Cycle Audio |
| `FlashPhase` | Bakes flash texture into pongTex | — | Flash Image, Flash White |
| `QuotePhase` | Bakes quote text (if `quoteInBuffer`) | Renders quote overlay | Show Quote, Toggle Quote Mode |
| `NotifPhase` | — | Renders 3-second notification toast | — |
| `QrPhase` | — | Renders QR code overlay | — |

---

## WavePhase in detail

`WavePhase` owns the `AudioPipeline` and all four GL wave renderers. On each
`indexedRender()` call it:

1. Calls `audioPipeline.update()` to push the latest audio samples to GPU PBOs
2. For each *enabled* wave model, sets its transform and amplitude and calls `doRender()`

Wave renderers are configured with R-channel-only colours so their geometry outputs
valid palette indices:

| Renderer | Colour config |
|---|---|
| `AudioWave` (oscilloscope) | `setLineColour(new Vector4f(WAVE_IDX, 0, 0, 1))` |
| `RadialWave` | `setLineColour(new Vector4f(WAVE_IDX, 0, 0, 1))` |
| `SpectrumAnalyser` | `.withBarColors(SPECTRUM_COLOUR, SPECTRUM_COLOUR)` |
| `RadialSpectrumAnalyser` | `.withColors(SPECTRUM_COLOUR, SPECTRUM_COLOUR, SPECTRUM_COLOUR)` |

`WAVE_IDX = 200f / 255f` (palette index 200). The `withBarColors` / `withColors` calls
must happen **before** `init()` on the spectrum analysers.

---

## FlashPhase in detail

`FlashPhase` uses `TextureBakeRenderer` to write a full-screen texture into the
currently bound R8 FBO in one draw call:

- **White flash**: a 1×1 RGBA texture with R=0xFF (index 255). Uploaded once at init.
- **Image flash**: the next PCX file from `RandomImageSource` converted to RGBA where
  `R = luma(pixel)`, so bright parts of the image map to high palette indices. Uploaded
  on demand on the GL thread.

`TextureBakeRenderer`'s fragment shader outputs `vec4(s.r, 0, 0, s.a)` — it routes
the source red channel directly to the R8 output and uses the alpha channel for
transparency so partial-alpha pixels blend with the underlying content.

---

## QuotePhase in detail

Quote rendering has two modes controlled by `quoteInBuffer`:

**Overlay mode** (default, `quoteInBuffer = false`):  
`screenRender()` alpha-blends `StringRenderer` output over the palette-converted frame.
The text colour is white RGBA.

**Buffer-bake mode** (`quoteInBuffer = true`):  
`indexedRender()` uses a GL framebuffer save/restore trick to avoid needing a reference
to `pongFBO`:

```java
// save whatever FBO is currently bound (pongFBO, bound by CthughaWindow)
glGetIntegerv(GL_FRAMEBUFFER_BINDING, savedFbo);

// render RGBA text into a private offscreen RGBA FBO
textFBO.bind();
glClear(GL_COLOR_BUFFER_BIT);
quoteRenderer.doRender(ctx);
textFBO.unbind();

// restore pongFBO and bake the RGBA text → palette indices
glBindFramebuffer(GL_FRAMEBUFFER, savedFboId);
textBaker.doRender(ctx);   // TextureBakeRenderer: r → R8 palette index
```

This keeps the `RenderPhase` interface clean: no phase ever holds a reference to
`pongFBO` directly.

---

## Translation tables

The displacement field is held CPU-side in a `TabBuffer` (a direct `ByteBuffer` viewed
as a `ShortBuffer` of RG16UI pairs) and mirrored GPU-side in `translateMap`. When the
table is rebuilt `CthughaWindow.rebuildTranslateMap()` calls `glTexSubImage2D` to
upload the new data without re-allocating the texture.

`TabGenerator` implementations live in `tab/generators/` and produce the RG16UI data:

| Generator | Effect |
|---|---|
| `Spiral` | Rotating inward spiral |
| `Hurricane` | Turbulent circular flow |
| `Smoke` | Upward drifting turbulence |
| `Tunnel` | Zooming tunnel perspective |
| … | (others registered via `GeneratorRegistry`) |

`TabStore` saves and loads presets to/from the `tabs/` directory.

---

## Palette system

`PaletteMap` holds 256 packed RGB integers. `JCthugha` owns the active map and exposes
`loadPalette(PaletteMap)` which sets `paletteDirty = true` in the window. On the next
frame `CthughaWindow` uploads the new colours to the 256×1 RGBA `paletteTex` via
`glTexSubImage2D` and clears the flag.

`PaletteLibraryNode` in the parameter tree lists all `.MAP` files in the `maps/`
directory and exposes palette switching through the remote UI.

---

## Buffer lifetimes and ownership

```
CthughaWindow
  ├── pingTex / pongTex / flameTex  (R8, owned by window)
  ├── translateMapTex               (RG16UI, owned by window)
  ├── paletteTex                    (256×1 RGBA, owned by window)
  ├── pingFBO / pongFBO / flameFBO  (owned by window)
  ├── TranslateTextureRenderer      (owned by window)
  ├── BlurTextureRenderer ×2        (owned by window)
  ├── PaletteRenderer               (owned by window)
  └── List<RenderPhase>
        ├── WavePhase               (owns AudioPipeline + 4 wave renderers)
        ├── FlashPhase              (owns TextureBakeRenderer + flash textures)
        ├── QuotePhase              (owns 2 StringRenderers + text FBO + TextureBakeRenderer)
        ├── NotifPhase              (owns 1 StringRenderer)
        └── QrPhase (optional)     (owns QrOverlay)
```

All GL resources are disposed in reverse order: phases first (via the phases loop in
`CthughaWindow.dispose()`), then the GL backbone renderers, then the FBOs and textures.
