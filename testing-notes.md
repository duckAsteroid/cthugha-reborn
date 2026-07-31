# Testing Notes — Bugs

Observations from manual testing, clarified and grouped by area. Each item below is a real
bug/behavior change to make, not a feature idea (feature ideas from the original notes have moved
to `ideas.md`).

## Waves

### Scale X/Y has no visible effect
Tested on a visible, *enabled* wave — no effect observed. Code-wise, `TransformParams.scale`
(nested under "Transform > Scale > X/Y" on each wave model) does call `matrix.scale(...)` and is
passed into `setTransform()` on the underlying render-core object each frame, so it looks wired
end-to-end on paper. Needs root-causing against a real repro (which wave type, which value) —
possibly the shader isn't applying the matrix the way the CPU side assumes, or there's a
normalisation/identity-check bug suppressing non-1.0 values.

### "Disable bars" does nothing (both Spectrum and Radial Spectrum)
Confirmed broken identically on both `SpectrumModel.showBars` and `RadialSpectrumModel.showBars`.
Both only toggle alpha (1.0 vs 0.0) on the bar fragments, relying on `GL_BLEND` to make
alpha-0 fragments invisible. `GL_BLEND` is enabled/disabled locally in `QuotePhase`, `NotifPhase`,
and `WorkPhase`, but never in `WavePhase` — where these renders actually happen — so blending is
likely off (or left in a stale state from a previous phase) during the wave pass, meaning the
alpha channel is ignored and the bars' palette-index writes go through regardless of the toggle.
Fix: make "hide bars" actually skip the geometry/write (or explicitly manage blend state in
`WavePhase`) rather than relying on alpha discard in a pass that doesn't blend.

### Wave amplitude range is wrong
Oscilloscope amplitude is `DoubleParameter(0.5, 50.0, default 0.2)` — the default (0.2) is below
the parameter's own stated minimum (0.5), a plain correctness bug (constructor doesn't validate).
Target values agreed: **min 0.01, max 5.0, default 1.0**. Radial Wave's amplitude
(`0.1, 10.0, default 0.2`) uses the same pattern — check whether it should move to the same
range for consistency, since it wasn't explicitly discussed.

### New waves should start enabled; startup should seed only one wave
Currently `RadialWaveModel`, `SpectrumModel`, and `RadialSpectrumModel` all default to
`enabled = false` in their own constructors (only `OscilloscopeModel` defaults `true`), so both
"New Wave" and app startup inherit that. Desired behavior:
- Any wave added via "New Wave" should start **enabled**, regardless of type.
- App startup should seed **only one** wave (not the current 1 enabled + 3 disabled seeded in
  `JCthugha`'s constructor for legacy-compatibility reasons) — presumably just the Oscilloscope,
  matching the pre-existing default behavior a fresh session had before this was one.
- Note: `JCthugha.wireDefaultBindings()` currently pre-attaches a sine-rotation animation to
  *both* the seeded Oscilloscope and Radial Wave — once startup seeds only one wave, the
  Radial-Wave-specific binding wiring goes away too and should be removed/adjusted accordingly.

## Tab Generation

### Shatter zoom strength needs non-linear (log) scaling near zero
Confirmed: a symmetric log/exponential curve around 0 is wanted — fine-grained control near zero,
coarser toward the extremes, mirrored for negative (shatter) and positive (explosion) values.
`zoomStrength` is currently a plain linear `DoubleParameter(-1.0, 1.0, 0.12)`; `DoubleParameter`
has no non-linear mapping support today (`setNormalisedValue` is a straight lerp), so this needs
either a `DoubleParameter` option for a log/exp response curve, or a Shatter-specific transform
between the UI-facing normalised value and the actual `zoomStrength` passed to the generator.

### "Random tab" action — already implemented, just needs discoverability
The "New Source" button (`ActionTreeBuilder`) already does exactly what was being asked for here:
picks a random generator via `GeneratorRegistry.selectRandom()`. No functional change needed —
consider a clearer label/tooltip (e.g. "Random Source") so it reads as "pick a random effect"
rather than "add a new one".

## Remote UI (mobile SPA)

### Combo box / dropdown controls broken on mobile
Confirmed as a systemic issue, not one specific dropdown — every `Select` control (all built on
`@radix-ui/react-select` via `EnumControl.tsx`) misbehaves on mobile to varying degrees, worse
with longer option lists (generator picker, palette picker). Needs a mobile-usability pass on
the shared `EnumControl` component — likely needs a native `<select>` fallback, a full-screen
sheet/drawer pattern, or Radix mobile-specific handling instead of the current popover.

### XY touchpad — Y axis inverted
Confirmed Y-axis only: dragging to the top of the pad should map to the "up" end of the param's
range. Currently `XYPadControl.setFromClientPoint()` maps the pad's visual top edge to the
param's `minY`, which is backwards for the one param currently wired to it
(`TransformParams.rotateCenter`, an NDC-style ±1 range). Fix: flip the Y mapping in
`XYPadControl` (`ny = maxY - ty * (maxY - minY)` instead of `minY + ty * ...`), or confirm the
param's own semantics and adjust there instead if other pad-controlled params get added later.

## Quote Overlay

### Quotes aren't centered and go stale on resize
Two distinct issues, both confirmed in scope:
1. **Not horizontally centered.** `QuotePhase.updateLayout()` hardcodes `quoteX = 40.0f` (a fixed
   left margin) — it was never centered horizontally by design. Needs to actually center based on
   rendered text width and current window width.
2. **Stale after resize.** `updateLayout()` is only invoked from `syncQuoteText()`, which returns
   early when the quote text hasn't changed — so `quoteY` (and the new horizontal centering) keeps
   using whatever window size was current when the quote last changed. No resize listener is
   registered in `QuotePhase` at all (contrast `RadialSpectrumAnalyser`, which does register one).
   Needs a resize listener that re-runs `updateLayout()` independent of quote-text changes.

## Diagnostics

### ERROR/WARN not visible in console; exceptions going untracked
Unconfirmed which mechanism is the culprit — could be either or both:
- No `Thread.setDefaultUncaughtExceptionHandler` is registered for most threads (only
  `AudioPipeline`'s capture thread has a custom handler), so an uncaught exception on any other
  thread hits the JVM's default handler and bypasses logback formatting/appenders/console
  threshold entirely.
- Several code paths swallow exceptions in empty catch blocks with no logging at all — confirmed
  at least in `RemoteEventBroadcaster` (`register()`'s SSE listener and `flush()`, both
  `catch (Exception ignored) {}`), meaning a failed remote param push leaves zero trace at any
  log level.

Logback config itself (`app/src/main/resources/logback.xml`) looks correct on paper — console
appender has a WARN `ThresholdFilter`, root is `DEBUG` — so this isn't a config/level problem.
Fix should cover both: add a default uncaught-exception handler that logs via SLF4J, and replace
silent `catch (Exception ignored)` blocks with at least a `logger.warn(...)` call.

## Audio

### Occasional sharp +1/-1 spike in the audio waveform
Happens steadily during continuous playback, not tied to source/device switching — consistent
with a rare read/write race rather than a device-transition glitch. Leading theory: both
`RollingAudioBuffer.getShortAt()` (render-core) and `PboAudioSink.write()` are single-writer
(audio thread) / single-reader (GL thread) ring buffers synchronized only by a `volatile`
write-position field, with no lock around the read itself. `getShortAt()` reads a 16-bit sample
as two separate byte reads; if the audio thread wraps around and overwrites that exact byte range
between the two reads, the reader gets a torn sample that can land near `Short.MIN_VALUE`/
`MAX_VALUE` — exactly matching the reported "+1 to -1" spike shape. Needs verification (e.g. a
targeted stress test forcing frequent wraparound) before committing to a fix, then likely a
proper memory barrier or version-tagged read to reject torn samples.
