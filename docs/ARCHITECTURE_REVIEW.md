# Architecture Quality Review — Coupling, Cohesion & Separation of Concerns

Date: 2026-08-23
Scope: full read of `display/`, `display/phase/`, `display/wave/`, `params/` (+ `action`,
`values`, `transform`), `binding/`, `remote/`, `screenconfig/`, `tab/` (+ `generators/**`), `map/`,
`quote/`, `img/`, `video/`, `work/`, `beatpresets/`, `config/`, `dump/`, plus a package-level
import-dependency map of the whole `io.github.duckasteroid.cthugha` tree.

This review is orthogonal to `docs/PERFORMANCE_REVIEW.md` — it judges the code purely on coupling,
cohesion, single-responsibility, encapsulation, testability, and layering, independent of
performance.

**Headline finding:** the codebase has repeatedly invented the *right* decoupling pattern (a small,
package-owned `*ActionContext`/`*View` interface that lets a domain package call back into the
running app without depending on its concrete type) — `TabActionContext`, `PaletteActionContext`,
`CthughaActionContext`, `AnimationBindingView`, `DynamicChildList`, `RestoreAware` are all genuine,
working examples of this. But the pattern wasn't applied consistently, and every circular package
dependency and god-class finding below traces back to a spot where it was skipped rather than to a
structural flaw in the overall design.

---

## Package dependency map

Extracted from `import io.github.duckasteroid.cthugha.*` statements across the whole tree, verified
by hand-checking the interesting edges.

```
(root)              -> beatpresets, binding, config, display, display.phase, display.wave,
                        dump, img, map, params, params.action, params.values, quote, remote,
                        screenconfig, tab, video
beatpresets         -> params, params.action, params.values, screenconfig, tab
binding             -> params, params.action, params.values
display             -> (root), binding, config, display.phase, dump, map, params, params.action,
                        params.values, remote, tab, work
display.phase       -> (root), binding, config, display, display.wave, img, params, params.action,
                        params.transform, params.values, quote, remote, video, work
display.wave        -> params, params.action, params.transform, params.values
dump                -> params, params.action, remote
img                 -> display.phase, params, params.action, params.values
map                 -> params, params.action, params.values
params              -> params.action, params.values
params.action       -> params
params.transform    -> params, params.values
params.values       -> params
quote               -> (root), config, params, params.action, params.values
remote              -> binding, config, img, map, params, params.action, params.values, video
screenconfig        -> params, params.action, params.values, tab
tab                 -> params, params.action, params.values
tab.generators.*    -> params, tab, params.transform, params.values   (never imported by anyone)
video               -> display.phase, params, params.action, params.values
```

**Circular dependencies found (5):**

| Cycle | Evidence | Criticality |
|---|---|---|
| `(root) ↔ display` | `JCthugha.java` imports `display.*`; `CthughaWindow.java:20-21`, `CthughaActionContext.java:4` import `JCthugha`/`ActionTreeBuilder` | Medium-High (root is a defensible composition root, but see Finding 1) |
| `(root) ↔ display.phase` | `JCthugha.java` imports 6 `display.phase.*` classes; `NotifPhase.java:9`, `QuotePhase.java:12`, `DebugBeatsPhase.java:10`, `WavePhase.java:9` import `JCthugha` | Medium-High (same root as Finding 1) |
| `(root) ↔ quote` | `quote/QuotesLibraryNode.java:3,28` calls `JCthugha.showQuote(...)`; `JCthugha.java:24-25` imports `quote.*` | High — avoidable, breaks the codebase's own established pattern |
| `display.phase ↔ img` | `img/ImagesLibraryNode.java:3,18` takes a `FlashPhase` and calls `requestFlash(...)`; `display/phase/FlashPhase.java:8` imports `img.RandomImageSource` | Medium-High |
| `display.phase ↔ video` | `video/VideosLibraryNode.java:3,15` ↔ `display/phase/VideoPhase.java:22-23`, same shape | Medium-High |

**Layering checks that passed clean:**
- `params`/`params.action`/`params.values`/`params.transform` form a strictly one-directional
  dependency chain with **zero** upward references into `display`, `remote`, or any domain package
  — exactly right for a low-level, domain-agnostic tree/parameter framework.
- `remote` fans out to 8 packages but **nothing** imports back from `remote` — only `Main.java`,
  `CthughaWindow` (server wiring), `QrPhase` (remote-controlled overlay), and `dump/JsonDumpFormat`
  (explicit `ParamSerializer` reuse) reach into it. `remote` is a genuinely clean outer layer. Minor
  wrinkle: `dump → remote` couples a debug/CLI feature to the HTTP layer just to reuse
  `ParamSerializer` — not a cycle, low-severity layering smell.
- `config` and `work` have **zero** outbound `cthugha` dependencies; `binding` depends only on
  `params*`; `tab.generators.*` depends only on `tab`+`params*` and, confirmed by grep, is **never**
  imported directly by anything — pure `ServiceLoader` plugin isolation.

**Hub packages** (widest fan-out, worth watching): `(root)` (17 outbound deps — expected for a
composition root, but also the epicenter of 3 of the 5 cycles) and `display.phase` (13 outbound
deps — expected for a package housing 5+ differently-themed render phases, but also the site of 2
of the 5 cycles). Criticality: Medium for both — not wrong on their own, but worth not growing
further without addressing the cycles.

---

## Summary table

| # | Finding | Category | Criticality |
|---|---|---|---|
| 1 | `JCthugha` ↔ `display.phase` cyclic, field-level coupling | Coupling / SRP | **Critical** |
| 2 | `JCthugha` is a public mutable data bag | Encapsulation | **Critical** |
| 3 | `RemoteServer` is an 862-line god class, Javalin threaded everywhere | Coupling / SRP | **Critical** |
| 4 | `ParamNode` is a fat base class bundling 6 unrelated concerns | SRP / Cohesion | **High** |
| 5 | `ParamSerializer` breaks its own abstraction for trigger bindings | Layering | **High** |
| 6 | Screen-config apply order depends on unspecified JVM field-reflection order | Coupling / Testability | **High** |
| 7 | `CthughaWindow.init()` is a 228-line method fusing 5 responsibilities | SRP | **High** |
| 8 | `PaletteMap.ensureForegroundUnique()` copy-paste bug (blue case reads green) | Duplication → Correctness | **High** |
| 9 | `quote` package breaks the narrow-interface convention → root↔quote cycle | Coupling | **High** |
| 10 | `display.phase ↔ img` / `display.phase ↔ video` cycles, same `*LibraryNode` pattern | Coupling | **Medium-High** |
| 11 | Three near-identical dual-analyser inner classes in `WavePhase` | Duplication | **Medium-High** |
| 12 | Janino compilation logic duplicated verbatim (Script/ConditionParameter) | Duplication / Coupling | **Medium-High** |
| 13 | Library-manager route authorization decoupled from route registration | Coupling / Duplication (security-relevant) | **Medium-High** |
| 14 | `OscilloscopeEntry`/`RadialWaveEntry` verbatim duplicates | Duplication | Medium |
| 15 | `WavePhase.resyncWaves()` mixes pure diffing with GL construction | Testability | Medium |
| 16 | `ScriptHelpers` mixes unrelated helpers + static mutable global state | Cohesion / Testability | Medium |
| 17 | `ParamNode.asParam` always returns `null` | Encapsulation (dead API) | Medium |
| 18 | `BigHalfWheel`/`DownSpiral` ~90% duplicated algorithm | Duplication | Medium |
| 19 | `MapFileReader` mixes parsing, caching, preview generation, and a CLI tool | SRP | Medium |
| 20 | `GeneratorRegistry` carries 5 distinct responsibilities | SRP / Cohesion | Medium |
| 21 | `VideoLibrary` mixes manifest CRUD with FFmpeg frame decoding | SRP | Medium |
| 22 | `AudioPipeline` conflates source-selection, beat-detector rebuild, and thread supervision | SRP | Low-Medium |
| 23 | `QrOverlay` fuses pure QR generation with GL upload, bypasses its own logger | SRP | Low-Medium |
| 24 | `quote.RandomQuoteSource` reaches into global `Config.singleton()` | Testability | Low-Medium |
| 25 | Duplicated "rebuild children from disk" boilerplate in 4 places | Duplication | Low-Medium |
| 26 | `screenconfig → tab.TabParams.slugify` dependency for a generic string op | Layering | Low |
| 27 | Duplicated thumbnail-scaling code between `img` and `video` | Duplication | Low |
| 28 | `Smoke` keeps dead parameters "for preset compatibility" | Cohesion | Low |
| 29 | `LinearSlider` breaks the generator naming convention | Consistency | Low |
| 30 | `PaletteMap.colors` is a public mutable array on a shared cached instance | Encapsulation | Low |
| 31 | `HtmlColors` eager singleton with no injection seam | Testability | Low |
| 32 | `AbstractValue` inherits tree-mutation methods nonsensical for leaf values | Encapsulation (LSP) | Low |

---

## Critical

### 1. Cyclic, field-level coupling between `JCthugha` (core) and `display.phase` (rendering)
**Files:** `JCthugha.java:9-17,82-86`, `display/phase/WavePhase.java:88,98,103-106,183`,
`QuotePhase.java:95-97,136,169`, `NotifPhase.java:47-49,77`, `DebugBeatsPhase.java:58-60,64,141`

`JCthugha` directly instantiates five phase classes as fields —
`public final QuotePhase quotePhase = new QuotePhase(this);`,
`public final WavePhase wavePhase = new WavePhase(this);`, etc. Those phase constructors take
`JCthugha` back and reach directly into its fields: `cthugha.beatDetector`,
`cthugha.audioSource.beatDetectorSettings`, `cthugha.waveSystem.instances()`,
`cthugha.getCurrentQuote()`, `cthugha.pollNotification()`.

The well-designed `ActionContext` indirection (built specifically to keep actions from coupling to
window/`JCthugha` internals) is never extended to phases. None of these phases can be constructed or
tested against a narrow contract — every one needs a real or heavily-stubbed `JCthugha`. Splitting
or refactoring `JCthugha` requires touching every phase constructor, and vice versa.

**Fix direction:** narrow interfaces per concern (`BeatSource`, `QuoteSource`,
`NotificationSource`, `WaveInstanceSource`) that `JCthugha` implements and phases depend on instead
of the concrete class; move phase construction out of `JCthugha` into `CthughaWindow` or a
dedicated factory.

---

### 2. `JCthugha` is a public mutable data bag
**File:** `JCthugha.java:71-100`

Public (or package-visible) mutable fields with no accessor discipline: `waveSystem`, `bindings`,
`audioSource`, `tabStore`, `translateSource`, `paletteMap`, `bufferWidth`, `bufferHeight`,
`beatDetector` (volatile), `reader`, `rng`, `translate`. Read/written directly from
`CthughaWindow`, `ActionTreeBuilder`, and the phases in Finding 1. No invariant can be enforced —
e.g. resizing `bufferWidth`/`bufferHeight` without also rebuilding `translate` would silently
corrupt state — and every future change to their shape is a whole-codebase grep-and-fix exercise.

**Fix direction:** convert to getters (narrow setters where mutation is legitimate), starting with
the highest-fan-out fields (`paletteMap`, `beatDetector`, `bufferWidth`/`bufferHeight`).

---

### 3. `RemoteServer` is an 862-line god class with Javalin threaded through every domain concern
**File:** `remote/RemoteServer.java` (whole file)

At least six unrelated responsibilities in one class: param-tree REST CRUD (270-405), animation
sub-resources (553-599), trigger sub-resources (635-692), image library CRUD (178-200, 823-861),
video library + chapter CRUD (202-268, 694-799), map/palette CRUD (108-176), plus HTTP
auth/caching infrastructure (449-519). Every handler takes Javalin's `Context` directly
(`handleCreateAnimation(Context ctx, String nodePath)`, `handleUpdateVideoMetadata(Context ctx,
String file)`) — there is no adapter/controller layer between Javalin and domain logic, so swapping
HTTP frameworks would mean rewriting every handler, and this class cannot be tested or reasoned
about per-concern.

**Fix direction:** split into per-domain controllers (`ParamsController`, `MediaLibraryController`,
`BindingController`) behind a thin routing layer, with a small request/response seam instead of raw
`Context`.

---

## High

### 4. `ParamNode` is a fat base class bundling six unrelated concerns
**File:** `params/ParamNode.java`

Combines composite tree structure (`addChild`/`removeChild`/`getChildren`, 285-334), lazy path
caching (`getFullPath`, 242-252), UI-hint metadata (`withUiHint`/`withVisibleWhen`, 140-155),
remote-API access control (`remoteAllowed`, 162-170), screen-config persistence exclusion
(`persistExclude`, 176-184), structural-hash exclusion (`structureHashExcluded`, 193-201), and
subtree change-notification fan-out (254-277) — inherited by every node regardless of whether it
needs them (a pure `ContainerNode` gets remote-allowlisting and persist-exclusion flags it will
likely never touch).

Notably, this codebase clearly *knows* how to do opt-in composition instead — `RestoreAware`,
`DynamicChildList`, `CompilableValue`, and `AnimationBindingView` are all separate, small, opt-in
interfaces — it just wasn't applied to these equally-optional concerns baked into the base class. A
domain-agnostic tree framework can't be extracted from this without carrying remote-control and
screen-config baggage.

**Fix direction:** pull `uiHints`, `remoteAllowed`, `persistExclude`, `structureHashExcluded`, and
the listener bus into the same opt-in-interface pattern already used elsewhere.

---

### 5. `ParamSerializer` — the declared tree→JSON boundary — reaches past itself into `binding` internals
**File:** `remote/ParamSerializer.java:6-7,133-154`

Imports `BindingSystem`/`EdgeTriggeredBinding` directly and `attachTriggers` reads
`binding.condition.getValue()`, `binding.cooldown.value`, `binding.value.getValue()` — concrete
field access on a `binding`-package type. This is inconsistent with the codebase's *own* established
fix for the identical problem: `ContinuousBinding` is exposed to `params`/`remote` through the
`AnimationBindingView` interface (defined in `params`, implemented in `binding` — correct
dependency-inversion), but `EdgeTriggeredBinding`/"triggers" never got the equivalent treatment.

**Fix direction:** add a `TriggerView`-style interface in `params` mirroring
`AnimationBindingView`, and have `ParamSerializer` depend only on that.

---

### 6. Screen-config apply order silently depends on unspecified JVM reflection field order
**File:** `params/ParamNode.java:60-64,109-123`, `screenconfig/ScreenConfigParams.java:81-83`

`ParamNode()`'s no-arg constructor calls `initFields(getClass())`, which discovers children via
`clazz.getFields()` — whose order the JLS does **not** guarantee. 57 of 57 grepped `ParamNode`
subclasses rely on this reflective wiring. `ScreenConfigParams.apply` explicitly documents depending
on it: "a selector node ... is always captured before the subtree it selects, since it is
registered as an earlier sibling." If a JVM/JIT ever returns fields in a different order
(declaration order is a HotSpot implementation detail, not a contract), screen-config load ordering
breaks silently with no compiler or test signal.

**Fix direction:** make ordering explicit — an `@Order` annotation, or require `initChildren(...)`
with an explicit list everywhere instead of implicit reflection scanning.

---

### 7. `CthughaWindow.init()` is a 228-line method fusing five unrelated responsibilities
**File:** `display/CthughaWindow.java:274-501`

Combines: translation-map background-work orchestration (296-320, includes real business logic —
submitting work, computing buffers, notifying — not just GL setup), full remote-server bootstrap
(322-381, ~60 lines: QR overlay, token store, broadcaster, remote UI node construction, token
rotation), GL texture/FBO/blur-pipeline construction (383-457, ~75 lines), state restoration +
fullscreen wiring (464-489), and debug param-tree dump (491-500). `CthughaWindow` itself has 38
instance fields and 17 methods. This is the class that was most recently and deliberately
refactored (the "extracted `RenderPhase`..." commit) — yet still has a single method that's the
natural place any future feature gets wedged into, growing it further.

**Fix direction:** extract a `RemoteBootstrap` collaborator for the remote-server setup, and a small
coordinator for the translate-map background-work wiring that can be unit tested with a fake work
queue — mirroring the extraction that already produced `RenderPhase`.

---

### 8. `PaletteMap.ensureForegroundUnique()` copy-paste bug: the blue branch reads/writes green
**File:** `map/PaletteMap.java:53-61`

```java
default:
  channel = 0;
  int blue = c.getGreen();      // should be c.getBlue()
  blue--;
  ...
  c = new Color(c.getRed(), c.getGreen(), blue);   // green also wrong here
  break;
```

The three-case switch decrementing R, then G, then B was clearly written by copying the green case;
the blue branch reads and writes the green channel instead of blue. The "make the last palette
entry unique" invariant (relied on by `WavePhase` for the foreground wave colour) can silently fail
to converge on the blue channel, and there's latent infinite-loop risk since the real blue value is
never inspected. No unit test exists for `PaletteMap`. This is a real correctness bug, found via the
duplication/copy-paste lens this review was looking through.

**Fix direction:** extract one `decrementChannel(Color, int)` helper used identically for R/G/B; add
a test that forces the blue branch.

---

### 9. `quote` package breaks the codebase's own narrow-interface convention, creating a root↔quote cycle
**File:** `quote/QuotesLibraryNode.java:3,28`, `JCthugha.java:24-25`

`QuotesLibraryNode` imports and calls `JCthugha` directly (`cthugha.showQuote(...)`), while
`JCthugha` imports `quote.Quote`/`quote.RandomQuoteSource`. The codebase already solves exactly this
problem elsewhere — `tab.TabActionContext` and `map.PaletteActionContext` are narrow, package-owned
interfaces so `tab`/`map` never depend on the concrete orchestrator. `quote` skips that pattern and
depends on the god object directly, so it can't be unit-tested or reused independent of the full
`JCthugha` graph.

**Fix direction:** add a `QuoteActionContext { void showQuote(Quote q); }`-style interface,
mirroring the existing pattern.

---

## Medium-High

### 10. `display.phase ↔ img` and `display.phase ↔ video` cycles via the same `*LibraryNode` pattern
**Files:** `img/ImagesLibraryNode.java:3,18`, `display/phase/FlashPhase.java:8`,
`video/VideosLibraryNode.java:3,15`, `display/phase/VideoPhase.java:22-23`

`ImagesLibraryNode` takes a `FlashPhase` and calls `flashPhase.requestFlash(...)`; `FlashPhase`
imports `img.RandomImageSource`. Same shape for `VideosLibraryNode` ↔ `VideoPhase`. `img` and
`video` are meant to be pure media-library domain packages but reach up into the render-phase layer
just to get a "load/flash this now" callback — the same smell as Finding 9, twice more.

**Fix direction:** same narrow-interface fix (e.g. a `FlashActionContext` owned by `img`,
implemented by `FlashPhase`).

---

### 11. Three near-identical dual-analyser inner classes in `WavePhase`
**File:** `display/phase/WavePhase.java` — `SpectrumEntry` (300-402), `RadialSpectrumEntry`
(404-509), `RadialClockEntry` (511-620)

Each independently re-implements the same ~90-line skeleton: two live analysers
(buffer-colour/overlay-colour), `volatile boolean ...Dirty` flags, `reinitBuffer`/`reinitOverlay`
(dispose+rebuild+re-register on `freqProc`), and a matching `dispose()`. The only real differences
are the analyser/model type and which model fields feed `buildBuffer()`/`buildOverlay()`. The
subtle invariant "only rebuild on the pass that actually changed, never lose the sink registration
mid-swap" is implemented three times — a bug fix or new shared feature must be applied identically
in three places, already carrying triplicated risk of divergence.

**Fix direction:** extract a generic `DualTargetAnalyserEntry<TAnalyser>` base holding the
two-analyser/dirty-flag/reinit/dispose machinery, parameterized by `buildBuffer`/`buildOverlay`/
`applyToActive` hooks per wave type.

---

### 12. Janino compilation logic duplicated verbatim between `ScriptParameter` and `ConditionParameter`
**Files:** `binding/ScriptParameter.java:62-83`, `binding/ConditionParameter.java:62-83`

Both directly construct `org.codehaus.janino.ClassBodyEvaluator`, call
`setExtendedClass`/`setDefaultImports`/`cook`, reflectively instantiate, call `bindState`, and catch
a bare `Exception` for `lastError`. Janino specifics aren't isolated behind any abstraction — the
two call sites must be kept in lockstep by hand, and swapping the scripting engine means touching
both identically.

**Fix direction:** extract a small generic `CompiledScriptParameter<T extends ScriptHelpers>` base
(or a `ScriptCompiler` strategy) parameterized by the extended class and wrapper-method text.

---

### 13. Library-manager route authorization is decoupled from route registration (security-relevant)
**File:** `remote/RemoteServer.java:152-268` (registration) vs. `483-492`
(`isLibraryManagerOnlyRoute`)

`isLibraryManagerOnlyRoute` re-derives which routes are "library-manager only" via string
`path.startsWith(...)`/method checks, entirely separate from where those routes are actually
registered. Adding a new mutating video/image/map route requires remembering to also update this
separate classification method — forgetting leaves a route reachable even when the library manager
is disabled, silently.

**Fix direction:** attach the classification to route registration itself (e.g. a wrapper
`app.patch(path, LIBRARY_MANAGER, handler)` or a metadata map built alongside route definitions).

---

## Medium

### 14. `OscilloscopeEntry` and `RadialWaveEntry` are verbatim duplicates
**File:** `display/phase/WavePhase.java:226-261` and `263-298`

Identical bodies (constructor, `mode()`, `render()`, `dispose()`), differing only in the concrete
`OscilloscopeModel`/`RadialWaveModel` and `AudioWave`/`RadialWave` types.

**Fix direction:** if render-core's `AudioWave`/`RadialWave` share a common settable-line-wave
interface, factor one generic entry; otherwise a small local adapter interface would let one entry
class serve both.

---

### 15. `WavePhase.resyncWaves()` mixes pure diffing with GL-object construction
**File:** `display/phase/WavePhase.java:182-206`

The reconciliation (`entries.keySet().removeIf(...)`, `entries.computeIfAbsent(model,
this::buildEntry)`) is a pure list-diff algorithm, but `buildEntry` directly constructs GL-backed
`WaveEntry` objects inline, so the diff logic can't be exercised without a GL context — the one
piece of this phase most amenable to a fast unit test ("does resync correctly add/remove entries as
the wave list changes?") is untestable as written.

**Fix direction:** separate the diff (`Set<ParamNode> toAdd, Set<ParamNode> toRemove`) from the
apply step; the diff can be unit tested against a fake `waveSystem`.

---

### 16. `ScriptHelpers` mixes unrelated helper families plus process-wide mutable static state
**File:** `binding/ScriptHelpers.java`

Bundles time (`t`), wave functions (`sine`/`cosine`/`saw`/`tri`/`pulse`/`phase`), beat detection
(`bass`/`snare`/`hihat`/`beat`), randomness (`random()`), remapping (`range`), and script state
(`state`/`global`) in one class with no internal grouping. More concretely: `beatDetector`/`random`
are `static volatile` fields set via `setContext` — every compiled script instance in the process
shares the same global mutable state. This is invisible global coupling: two `BindingSystem`
instances (e.g. in parallel tests) interfere with each other, and nothing in a
`Binding`/`ScriptHelpers` signature reveals the dependency.

**Fix direction:** group helpers into composed sub-objects (`WaveFunctions`, `BeatFunctions`), and
inject the beat detector/RNG per-evaluation rather than through static state.

---

### 17. `ParamNode.asParam` always returns `null`
**File:** `params/ParamNode.java:294-297`

Unconditionally `return null;`, overriding `Node`'s default `asParam` (which itself has backwards
`isAssignableFrom` logic at `Node.java:43`). Since `AbstractValue extends ParamNode` and never
re-overrides `asParam`, calling `.asParam(DoubleParameter.class)` on *any* leaf value in the entire
tree returns `null` instead of the instance. A repo-wide grep found zero call sites — currently dead
code, not an active bug — but it's public API on the base `Node` interface that any future caller
will hit as a silent-`null` trap.

**Fix direction:** either implement correctly (`clazz.isInstance(this) ? clazz.cast(this) :
throwing`) or delete it.

---

### 18. `BigHalfWheel` and `DownSpiral` are ~90% duplicated algorithms
**Files:** `tab/generators/rotation/BigHalfWheel.java:33-63`,
`tab/generators/rotation/DownSpiral.java:42-67`

Both implement the same "wheel spiral" pixel math (edge handling, `atan`/polar rotation, flat-index
wrap); `DownSpiral` parameterizes the two magic constants (`0.75`→`a`, `10`→`b`) that
`BigHalfWheel` hardcodes, and `BigHalfWheel` has an extra `dist < height` edge-shrink branch
`DownSpiral` lacks — evidence the two have already drifted apart under independent copy-paste
maintenance.

**Fix direction:** factor the shared wheel-warp math into one parameterized helper both call.

---

### 19. `MapFileReader` mixes parsing, caching, preview-image generation/verification, and a CLI tool
**File:** `map/MapFileReader.java` (CLI entry point 169-188, preview logic 82-112)

Three reasons to change (palette format, preview rendering, CI arg-parsing) live in one class;
testing the parser drags in `javax.imageio` and file-comparison logic.

**Fix direction:** split into `MapFileReader` (parse/write/cache) + a separate preview/CI tool that
depends on it.

---

### 20. `GeneratorRegistry` carries five distinct responsibilities
**File:** `tab/GeneratorRegistry.java` (whole file, ~300 lines)

In one class: generator selection state machine, batch-mode suppression for restores
(`beginBatch`/`beginRestore`), "Save preset" UI wiring (`StringParameter` + `AbstractAction` +
`AllPresetsNode` refresh), and change-listener plumbing for regeneration callbacks (three separate
`Runnable` setters).

**Fix direction:** extract the save-UI construction into its own small node, mirroring how
`BeatPresetLibraryNode` isolates its own save-action builder.

---

### 21. `VideoLibrary` mixes manifest/metadata CRUD with FFmpeg frame decoding
**File:** `video/VideoLibrary.java` (CRUD ~100-220, FFmpeg thumbnail/byte-buffer packing 251-305)

Testing manifest CRUD requires the native FFmpeg dependency on the classpath even though it's
unrelated.

**Fix direction:** extract a `VideoThumbnailer` that `VideoLibrary.thumbnailFile` delegates to.

---

## Low-Medium

### 22. `AudioPipeline` conflates source-selection, beat-detector rebuild, and thread supervision
**File:** `display/AudioPipeline.java`

Combines audio-source selection/switching (`selectSource`, `selectExact`,
`selectPreferredSource`), beat-detector rebuild coordination (`requestBeatDetectorReload`,
`rebuildBeatDetector`), and crash-supervised capture-thread lifecycle management
(`startAudioThread`, `onAudioThreadDied`, restart-window counters). The thread-supervision logic
(generically reusable) is welded to audio-specific state.

**Fix direction:** extract the restart-window/crash-supervision logic into a small reusable
`SupervisedThread` helper.

---

### 23. `QrOverlay` fuses pure QR-bitmap generation with GL texture upload, bypasses its own logger
**File:** `remote/QrOverlay.java:162-229` (`uploadQr`), `95` (`show()`)

`uploadQr` builds the RGBA pixel buffer (ECC selection, module rendering, logo blit) and uploads it
to a GL texture in the same method — the GL-free pixel-generation logic can't be unit tested without
LWJGL. Separately, `show()` does `System.out.println("QR: " + url)` despite the class already having
a configured `LOG` used elsewhere in the same file.

---

### 24. `quote.RandomQuoteSource` reaches into global `Config.singleton()`
**File:** `quote/RandomQuoteSource.java:26`

A repo-wide pattern (13 call sites), not unique to `quote`, but within this review's scope it's the
one instance — the class can't be unit-tested with a different quote-file list without touching
global JVM/file state.

**Fix direction:** inject the file list.

---

### 25. Duplicated "rebuild all children from disk" boilerplate in four places
**Files:** `tab/AllPresetsNode.java:36-47`, `tab/SavedPresetsNode.java:35-40`,
`beatpresets/BeatPresetLibraryNode.java:60-68`, `screenconfig/ScreenConfigLibraryNode.java:75`

All four repeat identical `getChildren().collect(...); forEach(this::removeChild); ...` logic.

**Fix direction:** add `ParamNode.replaceChildren(List<? extends Node>)`, used by all four.

---

## Low

- **26. `screenconfig → tab.TabParams.slugify` dependency.** `screenconfig/ScreenConfigStore.java:6`
  imports `tab.TabParams` solely for a generic filename-slugification helper — an avoidable
  dependency from an otherwise clean package onto a domain-specific one.
- **27. Duplicated thumbnail-scaling code.** `img/RandomImageSource.java:79-96` and
  `video/VideoLibrary.java:307-324` are near byte-identical bilinear-scale implementations.
- **28. `Smoke` keeps dead parameters "for preset compatibility."**
  `tab/generators/wave/Smoke.java:14-15,30-31` — `speed`/`randomness` fields whose own doc string
  says they're not read; the remote UI exposes controls that visibly do nothing. Safe to delete
  since `TabParams.apply` already no-ops on unknown paths.
- **29. `LinearSlider` breaks the generator naming convention.**
  `tab/generators/wave/LinearSlider.java:20-27` — uses the implicit no-arg `ParamNode()`
  constructor (name = bare class name `"LinearSlider"`) instead of `super("Human Name")` like every
  sibling, and redundantly calls `initChildren(...)` a second time. UI shows `"LinearSlider"`
  instead of a spaced name like its siblings.
- **30. `PaletteMap.colors` is a public mutable array on a shared cached instance.**
  `map/PaletteMap.java:20`, combined with `MapFileReader`'s cache returning the same instance to
  every caller. Not currently exploited (one read-only external usage found), but invites future
  corruption of the shared cache.
- **31. `HtmlColors` eager singleton with no injection seam.** `HtmlColors.java:16-27` — private
  constructor does classpath I/O and throws on class-load if the resource is missing, no way to
  substitute a source for tests.
- **32. `AbstractValue` inherits tree-mutation methods nonsensical for leaf values.**
  `params/AbstractValue.java:31` extends `ParamNode` without overriding `addChild`/`removeChild`,
  so e.g. a `DoubleParameter` — a scalar leaf — still exposes a working `addChild(Node)` that
  silently succeeds. No evidence of misuse today, but the type system doesn't prevent it.

---

## Good design decisions (for balance)

- **`RenderPhase`'s default-no-op interface** is the right shape, not an ISP violation — several
  phases genuinely move between `indexedRender`/`screenRender` at runtime (`QuotePhase`'s
  mode-driven dispatch, `WavePhase`'s per-entry `RenderMode`), so one interface with defaults beats
  splitting into marker interfaces.
- **`CthughaActionContext`** is a clean, thin adapter — the `ActionContext` pattern working exactly
  as intended, letting key bindings and the remote server share one execution context without either
  depending on `CthughaWindow`.
- **`TabActionContext`/`PaletteActionContext`** — the correct pattern that `quote`/`img`/`video`
  should have followed (see Findings 9-10).
- **`DynamicChildList`** — a clean opt-in `describe()`/`recreate()` contract letting
  `ScreenConfigParams` round-trip `BindingSystem`'s and `WaveSystem`'s runtime-created children with
  *zero* concrete-type coupling from `screenconfig` to `binding` (confirmed by import audit).
- **`AnimationBindingView`** — correct dependency-inversion: the interface lives in `params`,
  `ContinuousBinding` (in `binding`) implements it, so `params`/serialization never imports
  `binding`. (Notably not extended to `EdgeTriggeredBinding` — Finding 5.)
- **`RestoreAware`** — same opt-in-interface pattern, letting `ScreenConfigParams.apply` suppress
  side-effecting change listeners during snapshot replay without either package knowing the other's
  concrete types.
- **`ParamValues.applyText`** centralizes leaf coercion/validation shared between the remote PATCH
  route and `EdgeTriggeredBinding`'s trigger-fire path, preventing the two from drifting apart.
- **`Binding`'s lazy path-based target resolution** deliberately avoids holding a direct object
  reference to its target, so deleted/not-yet-created targets fail closed each tick with no explicit
  cleanup/release protocol required elsewhere.
- **`TabGenerator`/`TabMapping` plugin abstraction** — every one of 22 generator implementations
  follows the identical, clean shape (`ParamNode` fields + `generate()` returning a capturing,
  allocation-light lambda + shared static geometry helpers on the interface itself). No fragile
  inheritance hierarchy.
- **`TabStore`/`TabConfig`/`TabParams`/`TabBuffer`** cleanly separate persistence I/O, pure data,
  static capture/apply/checksum utilities, and buffer management — no logic bleed across
  boundaries.
- **`work.BackgroundWorkQueue`/`WorkContext`** — small, single-purpose, zero dependencies on any
  other `cthugha` package.
- **`config.Config`** — zero dependencies on any other `cthugha` package, a genuinely clean leaf
  (independent of the singleton-usage wart in Finding 24).
- **Graceful-degrade convention**: `ImageLibrary`, `VideoLibrary`, and `RandomQuoteSource` were each
  independently written to never throw on missing/malformed metadata, logging a warning instead — a
  deliberate, consistently-applied cross-cutting convention.
- **Pure-logic extraction at leaf level** is consistently good where it appears:
  `RadialClockAnalyser`'s `computeHalfWidth`/`activeBinCount`/`clampedBin*` are pure and separated
  from `doRender`'s GL calls; `VideoPhase`'s `resolveChapter`/`resolveDefaultChapterIndex` are pure
  and testable; `WavePhase.positionBase` is a pure static method; `StdinKeyInjector.parseCombination`
  cleanly separates parsing from the I/O thread; `BeatDetectorConfig` is fully pure and statically
  testable with explicit `Config`-injecting overloads built for tests.
- **`JCthugha`'s dedicated test constructor** (`JCthugha(Path configsRoot)`) is a deliberate,
  documented testability accommodation (JUnit `@TempDir` isolation) — evidence the team already
  values testable core logic, even though Finding 1 undercuts it.
- **`VideoPhase`'s decode-thread/GL-thread split** via `AtomicReference<ByteBuffer>` double
  buffering is a clean, well-documented concurrency boundary — impure decode I/O fully isolated from
  the render call.
- **`WaveSystem`/`BindingSystem`'s shared `DynamicChildList` pattern** is a deliberate, consistent
  cross-cutting abstraction reused between two otherwise-unrelated subsystems.
- **`CthughaWindow.dispose()`** mirrors `init()` symmetrically with careful null-guarded teardown of
  every GL resource — solid resource-lifecycle discipline despite the class's overall size.
- Existing binding tests (`ContinuousBindingTest`) show the design *is* testable without a GL context
  or a full `BindingSystem` — a `StaticClock` and a minimal hand-built `ParamNode` tree fragment
  suffice.

---

## Overall assessment

The `params`/`params.action`/`params.values`/`params.transform` framework and the
`tab`/`tab.generators` plugin system are the strongest parts of this codebase architecturally:
strictly layered with no upward dependencies, a genuinely clean interface+closure plugin model, and
a well-factored persistence layer with no responsibility bleed. The codebase independently invented
the right decoupling pattern more than once — `TabActionContext`, `PaletteActionContext`,
`AnimationBindingView`, `DynamicChildList`, `RestoreAware` are all real, working instances of
dependency inversion done correctly.

The recurring weakness is that this pattern wasn't applied consistently. `quote`, `img`, and `video`
each reach directly into `JCthugha` or a `display.phase.*Phase` class instead of defining their own
context interface — this alone produces 3 of the 5 real circular package dependencies found (the
other 2, `root↔display`/`root↔display.phase`, are softer cases inherent to `JCthugha` being a
composition root, but are made worse by Finding 1's field-level reach-back). `RemoteServer` and
`ParamNode` are the two largest concentration points of unrelated responsibility in the codebase —
both are extraction candidates using patterns the team has already proven out elsewhere
(`AnimationBindingView` for the `ParamSerializer`/`EdgeTriggeredBinding` gap; the opt-in-interface
style for `ParamNode`'s bundled concerns).

None of these are the kind of layering violation that requires broad rework — Java doesn't enforce
package acyclicity and the app clearly works — but they are the concrete, fixable instances of "hub
coupling" this review was asked to look for, and in every case the fix is the same move the
codebase already knows how to make. The one outright bug found in this pass (`PaletteMap`'s
copy-pasted blue-channel case, Finding 8) is a good illustration of why the smaller duplication
findings (`BigHalfWheel`/`DownSpiral`, the three `WavePhase` dual-analyser classes) are worth
cleaning up before they produce a second one.

---

## Appendix — reviewed scope

- `display/CthughaWindow.java`, `JCthugha.java`, `display/phase/*.java` (all 10 phases),
  `display/wave/*.java`, `display/AudioPipeline.java` and related audio wiring,
  `display/TextureBakeRenderer.java`, `display/SplashRenderer.java`, `display/StdinKeyInjector.java`,
  `display/KeyBindingConfig.java`, `display/HtmlColors.java`, `display/BeatDetectorConfig.java`,
  `display/BeatDetectorSettingsNode.java`
- `params/*.java`, `params/values/*.java`, `params/action/*.java`, `params/transform/*.java`
- `binding/*.java`
- `remote/*.java`
- `screenconfig/*.java`
- `tab/*.java`, `tab/generators/**/*.java`
- `map/*.java`
- `quote/*.java`, `img/*.java`, `video/*.java`, `work/*.java`, `beatpresets/*.java`, `config/*.java`,
  `dump/*.java`
- Package-level import-dependency map across the entire `io.github.duckasteroid.cthugha` tree,
  verified by hand-checking the interesting edges
