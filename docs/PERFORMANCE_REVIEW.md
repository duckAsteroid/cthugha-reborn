# Performance, GC & Render-Pipeline Review

Date: 2026-08-23
Scope: full read of `display/`, `display/phase/`, `display/wave/`, `params/`, `params/values/`,
`params/transform/`, `binding/`, `remote/`, `screenconfig/`, `tab/`, `tab/generators/**`, `map/`,
plus JVM/build config. Reviewed with a **performance-wins bias**: the product's primary job is
smooth, low-latency real-time rendering, so every finding is judged by its effect on frame time,
GC pressure, and GPU pipeline efficiency — not on style.

Each finding states **Criticality** (Critical/High/Medium/Low — how much it can hurt frame
smoothness or memory behavior) and **Impact** (how often the code path actually runs). A "Low"
criticality item that runs every frame is still worth fixing eventually; a "High" item gated
behind a rare user action is still worth knowing about.

No blocking/critical *correctness* bugs were found. One **High** finding (translation-buffer
animation binding) is a real, reachable footgun rather than a default-path problem. Most of the
codebase's dirty-flag, buffer-reuse, and thread-isolation choices are already correct — see
[Confirmed Good Decisions](#confirmed-good-decisions) — so the fixes below are refinements, not a
rescue job.

---

## Summary table

| # | Finding | Criticality | Runs |
|---|---|---|---|
| 1 | Animation binding on a tab-generator param causes unthrottled continuous full-buffer regeneration | **High** | opt-in, but unbounded once triggered |
| 2 | `TransformParams`/`XYParam.is()` allocates a Stream pipeline + boxes on the render hot path | **High** | every frame, per active wave/quote/video transform |
| 3 | `QuotePhase` allocates a direct `IntBuffer` + `glGetIntegerv` every frame in bake mode | **High** | every frame while a quote is shown (Buffer/Both mode) |
| 4 | `WaveSystem.instances()`/`WavePhase.resyncWaves()` do full reconciliation every frame, unconditionally | **Medium** | every frame, always |
| 5 | Per-wave-entry `Matrix4f`/`Vector4f` allocated fresh on every `render()` | **Medium** | every frame, per active wave instance/pass |
| 6 | `TabBuffer` is reallocated (not reused) on every translation-map regeneration | **Medium** (High if combined with #1) | per regeneration |
| 7 | `ContinuousBinding` tree-walks via `String.split` + Stream-based `getChild` every tick | **Medium** | every tick, per active continuous binding |
| 8 | `FlashPhase.toIndexedRGBA` uses per-pixel `getRGB` instead of bulk read | **Medium** | per flash trigger (can be beat-bound) |
| 9 | No explicit JVM heap/GC configuration for a real-time render app | **Medium** (architectural) | always |
| 10 | `AmplitudeFunction.constant/ellipse` allocates a capturing lambda every frame | **Low** | every frame, per Oscilloscope/RadialWave instance |
| 11 | Spectrum/RadialSpectrum/RadialClock keep two live GL analysers permanently | **Low** | constant, per instance (documented tradeoff) |
| 12 | `VideoPhase`/`WorkPhase`/`SolidQuad` allocate `Matrix4f` per call | **Low** | opt-in/occasional features |
| 13 | `DebugBeatsPhase` calls `String.format` per band per frame | **Low** | debug HUD only |
| 14 | `Node.getChild(String)` uses Stream/Optional for a plain linear scan | **Low** | underlies #7, multiplicative |
| 15 | `BooleanParameter` missing primitive `setNormalisedValue` override | **Low** | rare (animating a boolean) |
| 16 | Non-`volatile` primitive fields on `DoubleParameter`/`IntegerParameter`/`BooleanParameter` | **Low** (correctness-adjacent) | every read/write, cross-thread |
| 17 | `ScreenConfigParams` re-splits/re-walks the tree per leaf on load | **Low** | user-triggered save/load only, confirmed out of hot path |

---

## High

### 1. Animation binding on a tab-generator parameter causes unthrottled, continuous full-buffer regeneration
**Files:** `tab/GeneratorRegistry.java:76-78,102,257-262`, `binding/ContinuousBinding.java:69-90`,
`params/values/DoubleParameter.java:70-73`, `display/CthughaWindow.java:297-320`,
`work/BackgroundWorkQueue.java:39-59`

Traced end-to-end: `JCthugha.doRenderCPU()` calls `bindings.tick()` every render frame →
`ContinuousBinding.tick()` unconditionally calls `setNormalisedValue(...)` every tick with no
change-detection → `DoubleParameter.setNormalisedValue()` always calls `fireChangeListeners()` →
`GeneratorRegistry.watchParamChanges()` has attached a change handler to *every* leaf value under
the active generator (only `generatorSelector` is exempted via `withNoAnimate()`,
`GeneratorRegistry.java:102`) → the handler unconditionally requests regeneration → `CthughaWindow`
submits a background job that recomputes the full translation buffer. `BackgroundWorkQueue`
dedupes by key so only one job is in flight, but the very next tick resubmits, so the effect is
continuous back-to-back full-buffer regeneration for as long as the binding is active.

The remote UI's generic "Animate" control can be attached to *any* leaf, including individual
generator parameters (Spiral's Delta A, Mandelbrot's Zoom, etc.) — nothing prevents a user from
doing this today.

**Cost scenario:** attaching `"sine(0.1)"` to a generator parameter pegs a background thread doing
continuous parallel full-frame regeneration (trig + RNG per pixel), plus a fresh ~8MB direct-buffer
allocation per cycle at 1080p (see #6), competing with GL/audio threads indefinitely.

**Fix direction:** mark individual generator parameters `withNoAnimate()` (matching the existing
`generatorSelector` precedent), or give `onRegenerateNeeded` a real debounce/coalesce interval
distinct from "one job in flight," or have `ContinuousBinding` skip pushes into any leaf whose
ancestor is a `TabGenerator`.

---

### 2. `TransformParams.applyTo()`/`XYParam.is()` allocates a Stream pipeline and boxes on the render hot path
**Files:** `params/transform/XYParam.java:73-78`, `params/transform/TransformParams.java:100-138`

```java
// XYParam.java:73-78
public boolean is(DoublePredicate test) {
    return Stream.of(x, y)
      .map(DoubleParameter::getValue)
      .mapToDouble(Number::doubleValue)
      .allMatch(test);
}
```

`applyTo()` runs once per enabled wave/quote/video model **every rendered frame** (call sites:
`WavePhase.java:250,287,391,498,609`, `QuotePhase.java:276`, `VideoPhase.java:560`), and each call
does up to three `is()` checks (translate/scale/shear identity tests). Each `is()` call allocates a
`Stream.of` pipeline plus boxes both `DoubleParameter.getValue()` results. With ~4 active
wave/overlay transforms this is ~12 Stream-pipeline allocations + ~8 boxed `Double`s per frame —
roughly 720+ small allocations/sec at 60fps for what should be four primitive comparisons.

**Fix direction:** replace with direct primitive comparisons (no `Stream`, no `DoublePredicate`, no
boxing) — the logic is "is x and y each within epsilon of a target value," which needs no
functional-pipeline machinery.

---

### 3. `QuotePhase` allocates a direct `IntBuffer` and issues `glGetIntegerv` every frame while baking a quote into the buffer
**File:** `display/phase/QuotePhase.java:142-145`

```java
IntBuffer savedFbo = BufferUtils.createIntBuffer(1);
glGetIntegerv(GL_FRAMEBUFFER_BINDING, savedFbo);
int savedFboId = savedFbo.get(0);
```

Only reached when Quote Mode is `BUFFER`/`BOTH` (default is `OVERLAY`, so this is opt-in) and a
quote is currently showing — but while active it runs every frame for the quote's `duration`
(default 10s). `BufferUtils.createIntBuffer(1)` is a **direct** (off-heap) allocation — heavier
than a normal object allocation (native `malloc` + `Cleaner`/phantom-reference bookkeeping) — just
to read back a value (`renderFBO`'s id) that `CthughaWindow` already knows statically, since it
always binds `renderFBO` before calling `indexedRender`. `glGetIntegerv` is also a synchronous
state query that can force a partial pipeline sync on some drivers — normally avoided in tight
render loops.

**Fix direction:** reuse a single `IntBuffer` field allocated once in `init()`, or better, avoid the
query entirely — have `CthughaWindow` pass the known `renderFBO` id through, or have `QuotePhase`
call `renderFBO.bind()`/`unbind()` directly instead of round-tripping through `glGetIntegerv`.

---

## Medium

### 4. `WaveSystem.instances()`/`WavePhase.resyncWaves()` run full reconciliation every frame with no dirty-flag skip
**Files:** `display/wave/WaveSystem.java:166-169`, `display/phase/WavePhase.java:141-192`
*(independently confirmed by both the render-pipeline and the audio/wave review passes)*

```java
// WaveSystem.java
public List<ParamNode> instances() {
    return List.copyOf(waves);
}
```

Called once every frame from `WavePhase.indexedRender()`, this unconditionally defensive-copies
the `CopyOnWriteArrayList` backing array — regardless of whether any wave was added/removed since
the last frame. `resyncWaves()` then does `entries.keySet().removeIf(model ->
!current.contains(model) ...)`, an `O(entries × current)` scan via `List.contains`, every frame.
`WaveSystem` already exposes `setOnTreeChanged`, fired exactly on add/remove, so a boolean dirty
flag would let this collapse to a single check on the overwhelming majority of unchanged frames.

**Cost scenario:** at 60fps this is ~216,000 list copies + reconciliation passes per hour of
runtime for no functional benefit on unchanged frames. Absolute per-call cost is small at typical
instance counts (1-10), but it's allocation + `O(n²)`-shaped work done for free every frame when it
could be `O(1)` almost all the time, and cost scales with however many waves a user stacks via "New
Wave" (unbounded).

**Fix direction:** cache the last-seen instance-list reference (or a generation counter bumped by
`setOnTreeChanged`) and skip the copy/scan when the wave list hasn't changed.

---

### 5. Per-wave-entry `Matrix4f`/`Vector4f` allocated fresh on every `render()` call
**File:** `display/phase/WavePhase.java` — `OscilloscopeEntry.render` (243-255),
`RadialWaveEntry.render` (280-292), `SpectrumEntry.render` (391), `RadialSpectrumEntry.render`
(498), `RadialClockEntry.render` (609), plus `positionBase()` (129-138) and
`ColorParam.toVector4f()` (`params/ColorParam.java:34-36`)
*(independently confirmed by both the render-pipeline and the audio/wave review passes)*

```java
wave.setTransform(model.transform.applyTo(new Matrix4f(), PhaseConfig.aspect(ctx)));
wave.setLineColour(overlayPass
        ? model.color.toVector4f(1f)
        : new Vector4f((float) model.index.value, 0f, 0f, 1f));
```

Every entry type allocates a fresh `Matrix4f` + `Vector4f` per call, and render-core's
`setTransform()` copies the matrix again internally (`this.transform = new Matrix4f(matrix)`), so
each call site triggers two allocations. `WaveEntry.render()` runs once from `indexedRender` and
once from `screenRender` per entry (mode permitting), so an enabled wave costs up to 2 `Matrix4f` +
2 `Vector4f` allocations per frame — live from first launch since a default session already has one
Oscilloscope enabled — scaling linearly with wave-instance count.

**Fix direction:** give each `WaveEntry` a reusable `Matrix4f`/`Vector4f` field, `.identity()` +
mutate in place instead of `new` at the call site — the library-side defensive copy in
`setTransform` already gives thread-safety, so call-site reuse is safe.

---

### 6. `TabBuffer` is reallocated, not reused, on every regeneration
**File:** `tab/TabBuffer.java:20-25`, call sites `JCthugha.java:268-279`
*(compounds directly with finding #1)*

`computeNewTranslation()`/`computeRegeneratedTranslation()` each construct
`new TabBuffer(new Dimension(...))`, and `TabBuffer`'s constructor calls
`ByteBuffer.allocateDirect(width * height * 4)` — a fresh off-heap allocation every regeneration
(~8.3MB at 1920×1080) instead of refilling one persistent buffer in place. `TabBuffer.fill()`
itself is allocation-free and correctly reuses the `shortBuffer` view — the problem is the whole
*object's* lifecycle, not the fill logic. Direct-buffer cleanup is Cleaner/GC-driven and
nondeterministic, so repeated multi-MB native allocations cause native memory churn independent of
heap GC — and become continuous if finding #1's animation-binding path is exercised.

**Fix direction:** double-buffer two pre-sized `TabBuffer`s and swap, reallocating only on
resolution change; or thread the computed `TabMapping` back into the render thread's existing
buffer directly.

---

### 7. `ContinuousBinding` tree-walks via `String.split` + Stream-based lookup every tick
**Files:** `binding/Binding.java:79-84`, `binding/ContinuousBinding.java:69-90,114-127`,
`params/Node.java:104-137`

```java
// Binding.java
protected Optional<Node> resolveTarget() {
    return r.getChild(path.split("/"));
}
```

Every enabled `ContinuousBinding` calls `resolveTarget()` unconditionally every tick, and
additionally `isTargetActive()` walks from the target back to the root calling `getChild("enabled")`
at every ancestor level. Both paths go through `String.split` (array alloc) → `ArrayDeque` +
`Arrays.asList` wrapper (2 more allocs) → recursive `getChild(String)`, each level allocating a
Stream pipeline + `Optional` + capturing lambda (see finding #14). For a target at depth D, that's
roughly 4 objects/level × 2D per binding per tick — with N=10 continuous bindings at D≈4, ~320
small allocations/tick, ~19k/sec at 60fps.

This *is* the deliberate, documented design (lazy path resolution auto-handles deleted/not-yet-
created targets with no explicit cleanup) — the tradeoff itself is sound, the implementation cost
of getting it is higher than necessary. **For contrast:** `EdgeTriggeredBinding.tick()` only calls
`resolveTarget()` after its rising-edge + cooldown gate passes (`EdgeTriggeredBinding.java:60-90`),
so it does *not* pay this cost every tick — a cheaper pattern `ContinuousBinding` could borrow from.

**Fix direction:** cache the last-resolved `Node` per binding and cheaply verify it's still attached
(parent-chain identity, or a tree-generation counter bumped on add/removeChild) before falling back
to a full re-walk; or maintain a `Map<String,Node>` path index updated incrementally on
`addChild`/`removeChild`.

---

### 8. `FlashPhase.toIndexedRGBA` uses per-pixel `BufferedImage.getRGB(x,y)` instead of bulk row access
**File:** `display/phase/FlashPhase.java:126-145`

```java
for (int y = h - 1; y >= 0; y--) {
    for (int x = 0; x < w; x++) {
        int rgb = img.getRGB(x, y);
        ...
```

Only runs when a flash is triggered — but flashes can be wired to an `EDGE_TRIGGERED` binding
(e.g. `"bass() > 0.7"` with a short cooldown), making this a realistic, potentially frequent hot
path for the life of a session. Single-pixel `getRGB` goes through `Raster`/`ColorModel`
indirection per call — notably slower than the bulk overload.

**Fix direction:** use `img.getRGB(0, 0, w, h, pixels, 0, w)` to pull the whole raster in one call,
then iterate the resulting `int[]`.

---

### 9. No explicit JVM heap size or GC algorithm configured for a real-time render app
**Files:** `app/build.gradle` (`run{}` block — only `--enable-native-access=ALL-UNNAMED` and a few
`-D` properties; `applicationDefaultJvmArgs` commented out), `startScripts` override, root
`build.gradle`/`settings.gradle`, `src/dist/`

No `-Xmx`/`-Xms`/`-XX:+UseG1GC`/`-XX:+UseZGC`/`-XX:MaxGCPauseMillis` anywhere in the app's own run
configuration (the only `-Xmx`/`-Xms` in the repo configure the Gradle daemon via `gradlew.bat`, not
the app). Given the off-heap direct-buffer churn in findings #3 and #6 and steady per-frame GL/CPU
work, an explicit low-pause GC configuration is standard practice for this class of application.

This is flagged as a **gap**, not a proven defect — no profiling data was collected as part of this
review.

**Fix direction:** set an explicit `-Xmx`/`-Xms` and a low-pause collector (G1 with a
`MaxGCPauseMillis` target, or ZGC) in `applicationDefaultJvmArgs`/the dist launch scripts, then
verify with GC logging under real usage before tuning further.

---

## Low

### 10. `AmplitudeFunction.constant`/`.ellipse` allocates a capturing lambda every frame
**File:** `display/phase/WavePhase.java:248-249,285-286`

```java
wave.setAmplitudeFunction(
        model.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
```

render-core's `AmplitudeFunction.constant(float)`/`.ellipse(float)` return capturing lambdas
(non-cacheable by the JVM, unlike non-capturing lambdas), so each call allocates a new instance.
One extra small allocation per Oscilloscope/RadialWave instance per frame per active pass — minor
alone, compounds with finding #5 at the same call sites.

**Fix direction:** cache the built `AmplitudeFunction` in the entry, rebuild only when
`amplitude`/`ellipse` actually change.

---

### 11. Spectrum/RadialSpectrum/RadialClock entries keep two permanently-live GL analysers
**File:** `display/phase/WavePhase.java:300-509`

Each of these three entry types builds *two* full GL analyser objects (buffer + overlay) and
registers both as FFT sinks unconditionally, regardless of the model's current render mode — a
deliberate, documented tradeoff (class-level javadoc, lines 54-67) to avoid double-speed
peak-hold ballistics in `BOTH` mode. Confirmed this does **not** cause redundant FFT work — the FFT
runs once per frame and fans the same array out via `System.arraycopy` per sink. The real cost is
standing GPU resources (1D texture + shader + uniforms) for an analyser that may never render if
mode is `BUFFER`-only.

**Fix direction:** lazily build the unused analyser only when `mode` first requires it, mirroring
the existing dirty-flag/reinit pattern already used for colour changes.

---

### 12. `VideoPhase`/`WorkPhase`/`SolidQuad` allocate a `Matrix4f` per call
**Files:** `display/phase/VideoPhase.java:560`, `display/phase/WorkPhase.java:97-100`,
`display/phase/SolidQuad.java:90`

Each allocates fresh every call, but all are gated behind opt-in/occasional features (video overlay,
background generator work, the DebugBeats HUD), so aggregate GC pressure is low under default
settings.

**Fix direction:** same reusable-field pattern as finding #5, applied opportunistically.

---

### 13. `DebugBeatsPhase` calls `String.format("%.2f", ...)` per band per frame
**File:** `display/phase/DebugBeatsPhase.java:157`

Re-parses the format string and boxes the `float` argument on every call, per band, every frame —
but only while the debug HUD toggle is on.

**Fix direction:** hand-roll two-decimal formatting, or update the cached string on a coarser
interval since the HUD is a tuning aid, not precision-critical.

---

### 14. `Node.getChild(String)` uses Stream/Optional machinery for a plain linear scan
**File:** `params/Node.java:104-106`

```java
default Optional<Node> getChild(String name) {
  return getChildren().filter(child -> child.getName().equals(name)).findFirst();
}
```

Children are a plain `ArrayList`, so a name lookup is already O(children) — an accepted tradeoff at
typical child counts. But implementing it via `Stream.filter().findFirst()` instead of a manual
loop adds Stream-pipeline overhead on top of the scan for no benefit (no laziness/parallelism is
exploited). This is the single chokepoint underneath finding #7 and every other `getChild` caller
(`RemoteServer.java:531`, `KeyBindingConfig.java:69`, `ScreenConfigParams.java:224,244`), so fixing
it here has multiplicative benefit without touching lazy-resolution semantics.

**Fix direction:** replace with a manual for-loop over the backing list.

---

### 15. `BooleanParameter` is missing a primitive `setNormalisedValue` override
**Files:** `params/AbstractValue.java:175-177` (default), `params/values/DoubleParameter.java:70-73`
and `IntegerParameter.java:70-73` (primitive overrides), `params/values/BooleanParameter.java` (no
override)

If ever driven by a `ContinuousBinding`, `BooleanParameter` falls through to `AbstractValue`'s
default, which computes a primitive `double` then autoboxes it (`Double.valueOf` has no cache,
unlike small `Integer`s). Low impact — animating a boolean is an unusual use case — but a one-line
override would close the gap for consistency with `DoubleParameter`/`IntegerParameter`.

---

### 16. Non-`volatile` primitive fields on `DoubleParameter`/`IntegerParameter`/`BooleanParameter`
**Files:** `params/values/DoubleParameter.java:20`, `IntegerParameter.java:20`,
`BooleanParameter.java:18` (plain fields) vs. `params/values/StringParameter.java:8` and
`binding/ScriptParameter.java:28` (`volatile`)

These fields are written from Javalin HTTP threads (`ParamValues.applyText` → `setValue`) and from
the render thread (`ContinuousBinding.tick()` → `setNormalisedValue`), then read directly by the
render thread in hot paths (`TransformParams.applyTo()`, `WavePhase`). No `volatile`/synchronization
means no formal happens-before guarantee the render thread observes an HTTP-thread write promptly —
not a contention/blocking issue (no locks involved), but the one thread-safety gap in an otherwise
consistently-`volatile` codebase (`StringValue`/`ScriptParameter`/`ConditionParameter` all correctly
use `volatile`).

**Fix direction:** mark the primitive `value` fields `volatile` (near-zero cost for single-field
read/write on modern hardware, no lock involved).

---

### 17. `ScreenConfigParams` re-splits and re-walks the tree per leaf on config load
**File:** `screenconfig/ScreenConfigParams.java:224,244`

Each captured leaf path does its own `path.split("/")` + tree walk rather than one combined
descent, because an earlier entry can restructure the tree before a later one applies (documented,
`ScreenConfigParams.java:165-167`) — a deliberate, correctness-motivated tradeoff. Confirmed **not**
on any hot path: `CurrentStatePersister.tick()` runs on its own 5s-interval scheduler thread, not
the render thread. No action needed; included for completeness.

---

## GC & Memory Management — focused notes

The codebase's off-heap/allocation profile is dominated by a small number of chokepoints rather
than being pervasively leaky:

- **Direct-buffer churn**: findings #3 (`QuotePhase` per-frame `IntBuffer`) and #6 (`TabBuffer`
  reallocated per regeneration) are the two places native/off-heap memory is allocated repeatedly
  instead of reused. Both are straightforward to fix by hoisting the allocation out of the hot path.
- **Boxing hotspots**: findings #2 and #7 (Stream-based identity checks and tree-path resolution)
  are the main sources of avoidable small-object churn on the render/tick threads; both stem from
  reaching for `java.util.stream` where a hand-written loop or primitive comparison would do.
- **No GC tuning** (#9): worth addressing given the above, since a low-pause collector configured
  with real headroom will absorb the remaining allocation rate far better than JVM GC defaults tuned
  for throughput over latency.
- **Nothing catastrophic**: no evidence of per-frame allocation inside FFT/audio processing (shared,
  buffer-reused — see below), no O(n²) blowups beyond the already-small wave-reconciliation scan,
  and no unbounded collection growth was found anywhere in scope.

---

## Confirmed Good Decisions

For balance — these are deliberate, correct performance choices found during the review and worth
protecting from regression:

- **Shared FFT, no redundant computation.** `FrequencyProcessor.process()` runs the windowed FFT
  exactly once per frame and fans the single `magnitudes`/`rawMagnitudes` array out to every
  registered sink via `System.arraycopy`. Despite `WavePhase` potentially having many
  Spectrum/RadialSpectrum/RadialClock/BeatDetector sinks, none trigger their own FFT pass.
- **`AudioPipeline.update()` is allocation-free** in this repo's own code; `FrequencyProcessor`
  reuses pre-allocated `sampleBuffer`/`magnitudes` arrays across frames rather than allocating per
  call.
- **No per-frame audio device polling** — `LineAcquirer.allLinesMatching()` runs once at init and
  once at node construction; device selection never runs from the per-frame update path.
- **Script compilation is properly cached.** `ScriptParameter`/`ConditionParameter` only
  Janino-compile when script *text* changes, never per tick; `AnimScript.apply()`/
  `ConditionScript.apply()` are single field writes with zero allocation per evaluation.
- **`EdgeTriggeredBinding` avoids the tree-walk on every tick** — only resolves its target after the
  rising-edge + cooldown gate passes, unlike `ContinuousBinding` (finding #7).
- **SSE/remote architecture keeps I/O and JSON serialization off the render thread.**
  `RemoteEventBroadcaster.register()`'s change listener does only a `ConcurrentHashMap.put` on the
  render thread; a dedicated `sse-flusher` thread does the actual serialization + network I/O, and
  overwrite-by-path semantics naturally collapse a continuously-animating param to one SSE event per
  flush interval instead of one per tick.
- **`ParamNode.getFullPath()` is lazily computed and cached**, invalidated only on `setParent` —
  explicitly documented as safe to call at 60fps.
- **`CopyOnWriteArrayList` is the right choice** for `BindingSystem.bindings` and the various
  listener lists: iteration happens every tick while mutation is rare (user edits), so `forEach`
  walks the shared backing array with no per-call copy.
- **`CurrentStatePersister`'s full-tree serialize-and-diff runs on its own 5s-interval scheduler
  thread**, not the render thread, with a documented rationale for why change-debouncing wouldn't
  work given continuous animations.
- **`TabBuffer.fill()` reuses its `ShortBuffer` view and parallelizes via
  `IntStream.range(0, height).parallel()`** with per-row `ThreadLocalRandom` (no shared-RNG
  contention) — the fill logic itself is exemplary; only the buffer's *object* lifecycle needs
  fixing (finding #6).
- **`TabMapping.compute()` does not run every frame** in normal operation — regeneration is
  triggered only by explicit user action or param change, executes off the GL thread via
  `BackgroundWorkQueue`, and GPU upload uses `glTexSubImage2D` in place, never a full texture
  reallocation.
- **Generator hot loops are allocation- and boxing-free** — verified across all 22 `TabGenerator`
  implementations: every `new` happens once outside the per-pixel lambda, no boxed types or
  collections appear inside any per-pixel closure.
- **`PaletteMap` dirty-flag gating is correct** — upload only happens when `paletteDirty` is
  explicitly set by a param-tree change, reuses a single persistent buffer, and uses
  `glTexSubImage2D` in steady state.
- **`TransformParams.applyTo()` skips each affine component's matrix multiplication when it's at
  identity** (compared to 10 decimal places) rather than always multiplying by an identity matrix —
  correct, cheap avoidance of unnecessary matrix math (independent of the allocation issue in
  finding #2, which is about the *check*, not this skip logic).
- **`VideoPhase`'s decode/GL handoff** publishes frames into one of two pre-sized, alternating
  buffers via a lock-free `AtomicReference.getAndSet(null)` handoff — zero per-frame allocation in
  steady state on either thread.
- **`GLWindow.getWindow()`** (render-core) returns a cached `Rectangle` updated only on resize — the
  frequent `ctx.getWindow()` calls throughout the phase code are cheap field reads, not allocations.
- **No unexpected render-thread contention** — `JCthugha.doRenderCPU()` is `synchronized` but has
  exactly one caller (the GL thread), so this is free under biased/lightweight locking.

---

## Appendix — reviewed scope

- `display/CthughaWindow.java`, `display/phase/*.java` (all 10 phases), `display/wave/*.java`,
  `display/AudioPipeline.java` and related audio wiring, `display/TextureBakeRenderer.java`,
  `display/SplashRenderer.java`
- `params/*.java`, `params/values/*.java`, `params/action/*.java`, `params/transform/*.java`
- `binding/*.java`
- `remote/*.java`
- `screenconfig/*.java`
- `tab/*.java`, `tab/generators/**/*.java`
- `map/PaletteMap.java`
- `app/build.gradle`, root `build.gradle`/`settings.gradle`, `src/dist/` launch scripts (for JVM
  args)

Render-core library internals (`FrequencyProcessor`, `BlurTextureRenderer`, `TranslateTextureRenderer`,
`PaletteRenderer`, `GLWindow`, `AmplitudeFunction`) were spot-checked via dependency source lookup
only where needed to judge whether this repo's calls into them are expensive — the library itself
was not independently audited.
