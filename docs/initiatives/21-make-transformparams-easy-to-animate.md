---
type: feature
status: draft
updated: 2026-10-24
tags: [transforms]
---
# Make TransformParams easy to animate

## Goal

Make the per-element affine transform (TransformParams, used by every wave model, QuotePhase and VideoPhase) easy to animate with bindings, so common motions — zoom, spin, drift, wobble — take one binding with a simple script rather than several hand-tuned ones. Today it fights animation in five ways:

1. **Split axes.** Scale, shear, translation and rotation centre are separate X/Y leaves (XYParam), so a uniform zoom needs two bindings with identical scripts and nothing keeps the axes locked together.
2. **Identity sits off-centre in the normalised range.** Bindings push a clamped [0,1] value through setNormalisedValue, mapped linearly onto each leaf's min..max. Scale is 0.05–10, so identity (1.0) sits at ~9.5% of the range: a `sine()` around "no zoom" swings mostly towards big zooms unless remapped with `range()`.
3. **Rotation is an angle, not a spin.** "Rotation in radians" is clamped to [0, 2π]; continuous spin needs a hand-written `saw(...)` binding, there is no speed control, and radians are an unfriendly unit.
4. **Fixed composition order.** applyTo() always applies rotate → shear → scale → translate.
5. **Inconsistent naming.** `transform` (wave models) vs `Transform` (QuotePhase, VideoPhase); verbose leaf names like "Rotation in radians" and "Rotation center point" alongside terse "Translation" — awkward to type into binding target paths and scripts.

## Dependencies

_None._

## Related

- [12 Global animatable transform applied to the whole render](12-global-animatable-transform-applied-to-the-whole-render.md)
- [20 Named animation packages with their own dials](20-named-animation-packages-with-their-own-dials.md)

## Open questions

- Where should the fix live: in TransformParams itself (new animation-friendly controls such as a uniform zoom and a spin speed), in the binding system (let continuous scripts output values in the parameter's own units instead of a clamped [0,1] that setNormalisedValue maps linearly — DoubleParameter.java:70), or both?
- How should uniform zoom work: a separate "Zoom" multiplier applied on top of the existing X/Y scale, or a "lock axes" toggle on XYParam that makes Y follow X?
- Should binding normalisation honour a parameter's UiHint.SCALE_LOG (today it only affects the remote UI slider; setNormalisedValue is always linear), so a log-scaled Scale puts identity 1.0 near the middle of a binding's [0,1] range?
- For spin: add a "Spin speed" control (in revolutions per second) that accumulates the angle each frame, alongside the static rotation angle — and should the rotation angle switch from radians to degrees or turns?
- Should the composition order (rotate → shear → scale → translate) become selectable, or stay fixed and out of scope for this ticket?
- Renaming leaves (e.g. "transform" → "Transform", "Rotation in radians" → "Rotation") changes the parameter paths that saved screen configs, the persisted current state and binding targets refer to. Migrate old paths on load, or accept that existing configs/bindings lose those values?

## Acceptance criteria

- A uniform zoom, a continuous spin and an oscillation around identity (e.g. a gentle scale "breathe" around 1.0) can each be set up with a single binding and a one-term script, with no `range()` remapping needed.
- The same controls work on every TransformParams user: all wave models, the quote overlay and the video overlay.
- Existing saved screen configs and bindings still load as decided in the open questions; nothing breaks silently.
- docs/PARAMS.md (and SCREEN_CONFIGS.md if paths change) describe the controls as they now are.
