---
type: feature
status: draft
tags: [tab-generators, smoothness]
updated: 2026-10-25
---
# Blend between two translation maps over time

## Goal

Let the app animate from translation map A to map B over a chosen period, so a tab can morph smoothly instead of jumping, and generator changes can be animated without regenerating the table on every tick (ct#6 makes generator parameters non-animatable for that reason).

Today the translate pass is a single lookup per pixel: it reads `(srcX, srcY)` from the RG16UI `translateMap` and samples the previous frame there (`app/src/dist/glsl/translate/frag.glsl`, driven by render-core's `TranslateTextureRenderer`, `display/CthughaWindow.java:428`). Swapping maps is instant: `rebuildTranslateMap()` uploads a new table into the one map texture (`display/CthughaWindow.java:615`).

A blend keeps both tables resident as two map textures and combines them in the translate shader with a blend factor `t` that runs 0→1 over the period, on the GPU each frame with no CPU regeneration. When `t` reaches 1, B becomes the current map and A's texture is free for the next blend. It is driven by an action (e.g. "blend to preset X over 4 s"), so edge-triggered bindings and the remote UI can start one.

## Dependencies

_None._

## Related

- [6 Throttle tab regeneration driven by animation bindings](06-throttle-tab-regeneration-driven-by-animation-bindings.md)

## Open questions

- How should two maps combine at a given t: morph (interpolate each pixel's source coordinate from A's to B's, so the flow visibly bends from one shape to the other) or dissolve (each pixel uses A or B, with the share using B growing with t, like a dithered cross-fade)? Morph has a wrap-around snag: a pixel whose source wraps across the edge in one map (x=1 in A, x=1999 in B) would sweep through the middle of the screen.
- What can A and B be: only saved tab presets (`tabs/`, `TabStore`), or also "the current table → a newly generated one", so picking a new generator or randomising blends in rather than jumps?
- Is a blend one-shot (A→B, then B stays) or can it also loop or ping-pong (A→B→A… continuously, as a slow breathing morph)?
- What happens when a new blend is requested while one is running: snap the in-flight one to its target and start from there, start from the current mid-blend look (needs a third map or baking the mix), or ignore the request?
- (implementation) Where the blend shader lives: an app-side translate renderer (custom shader with two map samplers and `t`) or an upstream option on render-core's `TranslateTextureRenderer`.
- (implementation) Easing curve for t (linear vs smoothstep) and whether it is a parameter.

## Acceptance criteria

_What must be true when this is done._
