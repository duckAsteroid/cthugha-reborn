---
type: feature
status: in-progress
tags: [tab-generators, smoothness]
updated: 2026-10-25
branch: develop
---
# Morph between translation maps: two slots and an animatable Mix

## Goal

Let the translation table morph smoothly between maps instead of jumping, both once on each map change and continuously under the user's control, without regenerating the table on every tick (ct#6 makes generator parameters non-animatable for that reason).

Today the translate pass is a single lookup per pixel: it reads `(srcX, srcY)` from the RG16UI `translateMap` and samples the previous frame there (`app/src/dist/glsl/translate/frag.glsl`, driven by render-core's `TranslateTextureRenderer`, `display/CthughaWindow.java:428`). Swapping maps is instant: `rebuildTranslateMap()` uploads a new table into the one map texture (`display/CthughaWindow.java:615`).

The structure:

- **Two tab slots, A and B**, each a resident map texture. A new table (preset load, new generator, randomise, parameter-driven regeneration) is loaded into one of them.
- **A "Mix" parameter** (0 = all A, 1 = all B), an ordinary animatable parameter that the translate shader reads as its blend factor, on the GPU each frame with no CPU regeneration. A continuous binding such as `sine(0.05)` on Mix gives a breathing back-and-forth morph, a beat trigger can flick it, and the remote UI can scrub it by hand; animating it is free, as it is a shader uniform.
- **The automatic morph** on a map change loads the new table into a slot and moves Mix toward it over the Morph settings' duration, so one-shot morphs and user-driven Mix are the same mechanism.

## Dependencies

_None._

## Related

- [6 Throttle tab regeneration driven by animation bindings](06-throttle-tab-regeneration-driven-by-animation-bindings.md)

## Decisions

- **Morph.** At blend factor t each pixel's source position is interpolated from map A's to map B's, so the flow bends continuously from one shape to the other. To handle generators that wrap around the screen edge (`TabGenerator.wrap`, `tab/TabGenerator.java:41`), the shader interpolates each pixel's displacement (source − destination) taking the shorter way round the screen, then wraps the result, so a pixel never sweeps across the middle. Dissolve (per-pixel A-or-B dithered cross-fade) is rejected: it switches regions between behaviours rather than changing the shape. (Owner, 2026-10-25.)
- **Every map change morphs, governed by one "Morph" settings group on the Tab tab.** Preset loads, new generator picks, randomise and parameter-driven regenerations all go through the same swap today (`loadTabBuffer` + `rebuildTranslateMap`, `display/CthughaWindow.java:305`); with this, each morphs from the current table to the new one using the Morph settings: a duration in seconds (with a "none" setting meaning an instant swap, as today) plus the other morph parameters (e.g. easing). The settings are ordinary parameters, so the remote UI, bindings and screen configs can change them. (Owner, 2026-10-25.)
- (implementation) Easing is one of the Morph settings: an enum of Linear and Smooth (smoothstep), default Smooth, so a morph eases in and out rather than starting and stopping abruptly.
- **One-shot and continuous morphs share one mechanism.** A map change morphs once (Mix moves to the new table's slot over the Morph duration, then stays); a continuous back-and-forth comes from animating Mix itself. The two-slot / animatable-Mix design was first drafted as ct#29 and rolled in here. (Owner, 2026-10-25.)
- **New tables load into the less visible slot automatically.** A new table always loads into the slot Mix is further from (the less visible one), so slots simply alternate and are never shown or chosen in the UI. A deliberate pair for a breathing morph is set up by loading the two presets one after the other: they land in opposite slots. (Owner, 2026-10-25.)
- **Bake the current look, then morph.** When a new table arrives with Mix strictly between 0 and 1, one GPU pass writes the current mixed displacement (the same wrap-aware interpolation the translate shader uses) into the nearer slot as a plain table, Mix is set to that slot's end, and the new table loads into the other slot and morphs in. Nothing on screen jumps, and a burst of tables (e.g. a slider drag through ct#6's coalesced regeneration) glides continuously. The cost is one pass per interruption, not per frame. Snapping Mix to the nearer end (visible jumps) and waiting for the morph to finish (lag, and never lands while a binding holds Mix mid-way) are rejected. (Owner, 2026-10-25.)
- **A continuous binding on Mix owns it.** While Mix has an active continuous binding (`AbstractValue.getAnimationBinding()`), a map change does not move Mix: the new table replaces the less visible slot once that slot is at most ~5% visible (a breathing sine reaches that every half-cycle), so the swap is invisible and the breathing carries on into the new table. If the binding holds Mix mid-way and never nears an end, the swap happens after a timeout of about the Morph duration, accepting a small jump. Setting Mix by hand is a one-off value, not ownership: the next map change morphs as normal. Suspending the binding during a morph is rejected: the binding's value on resume would not match, causing a jump. (Owner, 2026-10-25.)
- **Screen configs restore one table.** A config keeps capturing the active generator and its parameters, plus Mix and the Morph settings as ordinary parameters; on load the regenerated table fills both slots, so a breathing pair has to be set up again. Saving both slots is ct#30. (Owner, 2026-10-25.)
- (implementation) The morph and bake run in wall-clock time from the Morph duration, independent of frame rate and of ct#8's step rate; Mix is read once per simulation step. On a buffer size change (ct#1) both slots are rebuilt from the current table, with Mix kept.
- (implementation) `MorphTranslateRenderer` (`display/`) replaces render-core's `TranslateTextureRenderer` in `CthughaWindow`; its shader, inline like `IndexPaletteRenderer`'s, samples both slot map textures and the Mix uniform and reads `displayTex` with `texelFetch`. `MorphTranslateRenderer.Bake` shares the blend function and renders into an RG16UI FBO. Both derive the destination texel from `texCoords`, not `gl_FragCoord`: a shader that ignores `texCoords` lets the linker drop the `texturePosition` attribute, which render-core's `VertexBufferObject.setup()` then configures at location -1 (that hung the app at startup). render-core's renderer has a single map sampler, and the morph logic is specific to this app, so no upstream change is needed.
- (implementation) `TableMorph` (`tab/`) is the "Morph" group, mounted under `Translate Source` (the remote UI flattens the Tab tab's sole container, so a sibling group would break its layout): Duration (None, 0.25, 0.5, 1, 2, 4, 8 s; default 1 s), Easing (default Smooth) and Mix. Duration and Easing are not animatable; Mix is. The GL work is behind `TableMorph.Slots`: three RG16UI textures rotate roles as slot A, slot B and a spare that new tables are uploaded or baked into before being promoted, so a pending table for a binding-owned Mix waits in the spare.
- (implementation) A restore goes through a new `TranslationRegenerator.Request.RESTORE` (fired by `GeneratorRegistry.endRestore()` via `setOnRestored`), whose result fills both slots instead of morphing.

## Open questions

_None._

## Acceptance criteria

- [x] With Morph duration "none", every map change swaps instantly, as today (`TableMorphTest`).
- [x] With a duration set, a preset load, a new generator, randomise and a parameter-driven regeneration each morph from the current table to the new one over that duration, with the chosen easing (all go through `rebuildTranslateMap()` → `TableMorph.onNewTable`; `TableMorphTest`; checked live: "Randomise" with Duration 4 s moves Mix 1 → 0 along a smoothstep curve in 4 s).
- [x] A map that wraps around the screen edge morphs without pixels sweeping across the middle of the screen (shortest-way displacement in the shader; mid-morph screenshots show a coherent field).
- [x] A new table arriving mid-morph (or while Mix is held mid-way by hand) produces no visible jump; a slider drag on a generator parameter glides (bake-then-morph unit-tested in `TableMorphTest`).
- [x] With `sine(0.05)` on Mix, loading two presets in turn breathes between them; a further map change replaces the less visible slot without disturbing the breathing (`TableMorphTest`).
- [x] Mix and the Morph settings appear on the Tab tab, are animatable (Mix) and round-trip through screen configs (`Tab/Translate Source/Morph/*`, captured in `.current.json`).
- [ ] The translate pass with the morph adds no CPU work per frame and no frame-time regression in a fullscreen benchmark on DP-4 (unlocked session). Pending: DP-4 is at 24 Hz (2026-10-25), so a like-for-like benchmark waits for it to be back at 60 Hz. The per-frame CPU work is one uniform write.
- [x] Project docs (`CLAUDE.md` rendering pipeline and translation tables, `docs/RENDERING_PIPELINE.md`, `docs/SCREEN_CONFIGS.md`) describe the morph.
