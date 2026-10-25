---
type: feature
status: designed
updated: 2026-10-25
tags: [transforms]
---
# Named animation packages with their own dials

## Goal

Bundle a set of animation bindings (e.g. for translations/transforms) into a named, reusable package exposing a few high-level dials, so trippy transform-based animations can be applied in one step. From ideas.md #11.

## Dependencies

_None._

## Related

- [12 Global animatable transform applied to the whole render](12-global-animatable-transform-applied-to-the-whole-render.md)

## Decisions

- **Twelve packages, each with two or three dials plus an overall Intensity:** 2-D — Breathe (Zoom pulse: Depth, Rate), Spin (Speed, Wobble), Orbit (Pivot offset + Spin: Radius, Speed), Drift (Translation in out-of-phase sines: Distance, Rate), Jelly (Stretch X/Y in opposite phase: Amount, Rate), Beat Pulse (Zoom kicked by a beat band: Strength, Band), Tumble (Rotation pendulum: Angle, Rate); 3-D — Coin Spin (Turn Spin), Flip (Tip Spin), Swing (Turn pendulum), Fly Through (Distance saw + Zoom), Tumble 3D (Turn Spin + Tip Spin at different rates). **The Perspective group gains Turn and Tip** (degrees, rotation about the vertical and horizontal axes, inside the projection) **and Turn Spin and Tip Spin** (rev/s, accumulated like Spin); a non-zero Turn/Tip enables perspective without the Enabled toggle. A flat element is invisible edge-on at 90°, as expected. (Owner, 2026-10-25.)
- **An "Animations" list on every Transform, as modifiers in the param tree.** Each Transform gets an `Animations` container rendered as an `ADD_LIST` (the "New Wave" pattern, `display/wave/WaveSystem.java:96–146`, `params/UiHint.java:129`): existing package instances as cards, "+ Add animation" revealing a picker of the twelve packages, a Delete on each instance. Instances are auto-named "{Package} {N}", any number and any mix, including several of one kind. Each instance is an ordinary `ParamNode` whose dials (Intensity plus its own) are ordinary leaves, e.g. `Wave/Oscilloscope 1/Transform/Animations/Breathe 1/Depth`, so they work with the remote UI and API, bindings, triggers, key bindings, screen configs, the auto-saved session and `--dump-params`; the list round-trips through `DynamicChildList` like waves and bindings. Packages do not write the transform's leaves: each computes an offset per frame and `applyTo()` combines it with the base values (zoom-like effects multiply, angles and positions add), so stacked packages never fight and Intensity 0 returns exactly to the user's settings. The computed offsets are internal state, not parameters. (Owner, 2026-10-25.)
- **Built-ins only in this initiative.** The twelve packages are added, stacked and tuned per element and saved with screen configs like any other values. Saving a tuned stack as a user-named package (offered in every "+ Add animation" picker, stored like beat presets) is a follow-up initiative, to be designed once the built-ins are in use. (Owner, 2026-10-25.)
- (implementation) **Where it lives:** `params/transform/animation/`. `AnimationList` (the `Animations` node, an `ADD_LIST` implementing `DynamicChildList`) holds `AnimationPackage` instances; `AnimationType` enumerates the twelve. Each package exposes `Intensity` (0–1, default 1) and its dials as `DoubleParameter`/`EnumParameter` leaves, and contributes to a per-frame `TransformOffsets` (zoom and stretch multipliers; rotation, spin, turn, tip angle and translation, pivot and distance offsets) scaled by Intensity. `TransformParams.applyTo()` builds the offsets from the list, then composes base × offsets in its fixed order.
- (implementation) **Time:** every rate is in cycles per second and advances each package's own phase by elapsed time from `applyTo()`'s clock (the same capped step as Spin), so rate changes never jump and motion is the same at any refresh rate. Two calls in one frame advance nothing extra.
- (implementation) **Beat Pulse** reads beat strength through `ScriptHelpers` (the same beat detector binding scripts use, `binding/ScriptHelpers.java:120`), exposed as a public static lookup; its Band picks among the configured `[beats]` bands. It adds `Strength × beat` to the zoom multiplier, so the kick decays with the detector's fade time.
- (implementation) **3-D:** Turn and Tip rotate about the vertical and horizontal axes after the perspective projection and z-distance, before the 2-D components; any non-zero Turn, Tip, their spins or a 3-D package enables the projection. 3-D rotation isn't aspect-corrected (z has no screen aspect).

## Open questions

_None._

## Acceptance criteria

- [ ] Every Transform (wave models, quotes, video) has an `Animations` list: "+ Add animation" offers the twelve packages; instances are named "{Package} {N}", each with Intensity, its dials and Delete; several can be stacked, including two of one kind.
- [ ] Packages never write the transform's leaves: with any stack running, the transform's sliders keep their values, and Intensity 0 (or deleting the package) returns the element exactly to them (unit test on `applyTo()`).
- [ ] Stacking composes: zoom-like effects multiply, angles and positions add (unit test with two packages on one transform).
- [ ] Each package moves as described, driven by elapsed time and independent of refresh rate (unit tests per package at two frame rates); a rate change doesn't jump the motion.
- [ ] Perspective gains Turn, Tip, Turn Spin and Tip Spin; a non-zero one, or a 3-D package, enables the projection; Coin Spin visibly turns a wave in depth (live screenshot).
- [ ] Animation stacks save and load with screen configs and the auto-saved session (round-trip test), and their dials take bindings and triggers like any leaf.
- [ ] Project docs (`CLAUDE.md` Transforms, `docs/PARAMS.md` if it lists node types, `docs/SCREEN_CONFIGS.md` dynamic lists) describe the Animations list and packages.
