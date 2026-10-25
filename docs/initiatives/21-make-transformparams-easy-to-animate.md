---
type: feature
status: designed
updated: 2026-10-25
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

## Decisions

- **In TransformParams (A).** New animation-friendly leaves are real parameters, not hidden bindings, combined in `applyTo()`: e.g. a "Zoom" multiplier on both scale axes. The binding system's clamped [0,1] normalisation stays as it is. Every leaf stays independently animatable and they compose (horizontal scale = Zoom × Scale X). The UI leads with Zoom; the X/Y scale moves into a collapsed "Stretch" group beneath it (nested containers start collapsed unless `default-open`, `remote-ui/src/components/ParamContainer.tsx:35`). (Owner, 2026-10-25.)
- **A separate "Zoom" multiplier** on top of the X/Y scale, not a "lock axes" toggle: zoom is one leaf (one binding), and Scale X/Y remain for unequal stretching under "Stretch". (Owner, 2026-10-25, with the decision above.)
- **Geometric mapping for Zoom only.** Zoom is a parameter type whose normalised value maps geometrically onto its range (e.g. 0.25×–4×: binding 0 → 0.25×, 0.5 → exactly 1×, 1 → 4×), for bindings and for its remote UI slider (a new `geometric` slider scale in `SliderControl.tsx`, so the slider and bindings agree). Binding normalisation stays linear for every other parameter, including the existing log-hinted one (`display/CthughaWindow.java:131`). (Owner, 2026-10-25.)
- **Add "Spin", in revolutions per second, −5 to +5** (negative = anticlockwise; 0 = stopped, the middle of a binding's [0,1], linear). The angle accumulates from elapsed time each frame, so speed changes never jump the picture. **"Rotation in radians" becomes "Rotation", a fixed angle in degrees, 0–360**, to which Spin's accumulated angle is added; saved radian values convert on load. (Owner, 2026-10-25.)
- **Composition order stays fixed and out of scope**: rotate (about the rotation centre) → shear → scale → translate, inside perspective when enabled (`TransformParams.java:100–137`). Spin and Zoom act in the element's own space, so a translated element spins and zooms in place; orbiting comes from a rotation centre away from the element. A different order, if an effect needs one, belongs with ct#20's animation packages. (Owner, 2026-10-25.)
- **Migrate old paths on load (A).** New names: every transform is `Transform` (waves used `transform`); new `Zoom` and `Spin`; `Scale/X|Y` → `Stretch/X|Y`; `Rotation in radians` → `Rotation` (degrees); `Rotation center point` → `Pivot`; `Translation`, `Shear` and `Perspective` unchanged. `LegacyValues` (ct#31) renames the old paths, converts radians to degrees, and rewrites binding target strings that point at old paths, so screen configs, the auto-saved session and beat presets load with the same look and their bindings keep driving the renamed leaves (a full turn either way, so a binding's [0,1] means the same angle). The conversion is in memory on each load: the auto-saved session re-saves itself in the new form within its interval; named configs keep old paths on disk until re-saved, which is optional. (Owner, 2026-10-25.)

## Open questions

_None._

## Acceptance criteria

- [ ] Every transform (wave models, quotes, video) is named `Transform` and leads with `Zoom` (0.25×–4×, geometric: binding 0.5 → exactly 1×), `Rotation` (degrees, 0–360) and `Spin` (−5 to +5 rev/s); the X/Y scale sits in a collapsed `Stretch` group; the rotation centre is `Pivot` (unit-tested on the normalised mappings and on `applyTo()`).
- [ ] One binding animates each common motion: `sine(…)` on Zoom pulses evenly around normal size, a constant Spin turns continuously, and changing Spin never makes the angle jump (unit test: accumulated angle across a speed change); Zoom composes with Stretch X/Y (horizontal scale = Zoom × X).
- [ ] The remote UI's Zoom slider uses a `geometric` scale matching the binding mapping (1× at the middle).
- [ ] Old saved values load with the same look: `Scale/X|Y`, `Rotation in radians` (converted to degrees), `Rotation center point` and wave `transform/…` paths are migrated by `LegacyValues`, and binding targets pointing at old paths are rewritten (unit-tested; live check loading an existing screen config with a rotation binding).
- [ ] The default "osc rotation" binding (`JCthugha.resetToDefaults`) targets the new path.
- [ ] Project docs (`CLAUDE.md` Transforms, `docs/PARAMS.md` / `docs/SCREEN_CONFIGS.md` where they name these leaves) describe the new controls and the migration.
