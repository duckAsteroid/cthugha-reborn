# Blur-pass image drift

## Symptom

Over time, the whole rendered image slowly drifts — visibly up and to the right —
regardless of which tab generator (translation-table warp) is active, including
`NoDistortion` (see below), which applies zero pixel displacement. The drift is gentle,
only noticeable after tens of seconds to a few minutes, but it is a real positional walk
of on-screen content, not a rendering glitch or palette effect.

## Why `NoDistortion` doesn't fix it

`NoDistortion` (`app/src/main/java/io/github/duckasteroid/cthugha/tab/generators/NoDistortion.java`)
was added to rule out the tab-generator warp as the cause: it maps every destination
pixel straight to itself (`map_x = dstX`, `map_y = dstY`), so `TranslateTextureRenderer`
becomes a pixel-perfect passthrough copy of `displayTex` into `renderTex`. The drift is
still present with this generator selected, which proves the translate step is *not*
the source — the bug is elsewhere in the per-frame feedback loop described in
[`docs/RENDERING_PIPELINE.md`](docs/RENDERING_PIPELINE.md).

## Root cause: linear-sampling Gaussian blur in a feedback loop

The remaining candidate — and the one that fits both the direction and the gradual,
accumulating nature of the drift — is the blur pass:

```
xBlur   renderTex ──► flameTex   (horizontal Gaussian + multiplier=1.0)
yBlur   flameTex ──► displayTex  (vertical Gaussian  + multiplier=fade)
```

`BlurTextureRenderer` and `BlurKernel` (render-core, `com.asteroid.duck.opengl.util.blur`)
implement the ["Efficient Gaussian Blur with Linear Sampling"](https://www.rastergrid.com/blog/2010/09/efficient-gaussian-blur-with-linear-sampling/)
technique: instead of one texture fetch per Gaussian tap, adjacent tap pairs are
collapsed into a single fetch at a computed sub-texel offset, relying on the GPU's
bilinear filter to blend the two texels in hardware. The offset math
(`BlurKernel.getDiscreteSampleKernel()`) is symmetric — the shader always samples
`texCoords + delta` and `texCoords - delta` with the same weight — so on paper there is
no directional bias.

In practice, GPU texture units quantize the bilinear blend factor to a fixed-point
fraction (commonly 8 bits). That quantization does not round symmetrically around the
sample's true position, so it introduces a small, *consistent* bias toward one
neighbouring texel. On a single frame this is invisible. But `displayTex` — the blur
output — is also the translate step's read source *next* frame
(`displayTex → renderTex → flameTex → displayTex → …`), so the same tiny per-frame bias
is reapplied every frame and accumulates into a visible walk. A consistent bias in both
the X-axis pass and the Y-axis pass reads as diagonal motion; up-and-right is consistent
with OpenGL's texture-V convention (V increases upward).

## Why this isn't a tab-generator or app-level bug

- No pan/offset/drift logic exists anywhere in this repo's rendering code
  (`JCthugha`, `CthughaWindow`) — confirmed by inspection.
- The effect persists independent of which `TabGenerator` is selected, including the
  zero-displacement `NoDistortion` generator.
- The blur implementation lives entirely in the `render-core` library dependency
  (`com.asteroid.duck.opengl:render-core:0.0.1`), not in this repo.

## Possible mitigations (not yet implemented)

- **Naive per-tap Gaussian blur**: drop the linear-sampling optimisation in
  `BlurTextureRenderer` and fetch every tap individually (double the texture reads per
  pass, no hardware interpolation quantization error). This would require a change in
  `render-core`, not this repo.
- **Accept it as a characteristic of the visualizer**: buffer "walk" from feedback loops
  is a long-standing trait of Cthugha-style visualizers; it may be tolerable or even
  aesthetically in-keeping given how gentle/slow it is.

No fix has been applied yet — this document exists to record the investigation so it
doesn't need to be re-derived later.
