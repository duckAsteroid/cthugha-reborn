# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cthugha Reborn is a Java port of the 1990s DOS music visualization software Cthugha. It renders real-time audio-reactive visual effects using a palettized (colour-indexed; 256 entries by convention) pixel buffer rendered via OpenGL, translation tables for distortion, and multiple waveform renderers layered on top. A phone/tablet can control runtime parameters via a browser-based remote UI served from an embedded HTTP server.

## Build & Run Commands

This is a Gradle multi-project build (`:app` + `:remote-ui`). The Java toolchain is Java 25 from Amazon Corretto; the foojay resolver (`settings.gradle`) downloads it when no matching JDK is installed.

The packaged distribution (`installDist`, `distZip`, `distTar`) bundles a trimmed Corretto 25 runtime under `runtime/`, linked by the `jlinkRuntime` task from the toolchain JDK using the module list `runtimeModules` in `app/build.gradle`. A library that needs a JDK module not on that list fails only at runtime on the bundled JVM, so add the module there. `bin/cthugha-reborn` runs on the bundled runtime and ignores `JAVA_HOME`/`PATH`. Set `CTHUGHA_JAVA_HOME` to run on a different JVM instead (e.g. to benchmark another one).

```bash
# Build (also builds the remote-ui React SPA and bundles it into the jar)
./gradlew build

# Run (working directory is app/src/dist/, for config/maps/images/glsl)
./gradlew run

# Run unit tests (excludes the smoke test)
./gradlew test

# Run a single test class
./gradlew test --tests "io.github.duckasteroid.cthugha.JCthughaTest"

# Launch the installed distribution end-to-end and check startup, rendering and the remote API
# (needs a real display; not part of test/check)
./gradlew smokeTest

# Report dependencies with newer releases
./gradlew :app:dependencyUpdates -Drevision=release
```

Command-line flags (`Main`, `RemoteConfig`, `DumpConfig`): `--stdin` / `--key-input=<path>` inject key presses from stdin or a named pipe, `--remote-token=` fixes the remote bearer token (`./gradlew run` uses `THIS_IS_A_TEST`), `--remote-port=`, `--remote-interface=`, `--remote-qr-timeout=`, `--no-remote`, and `--dump-params[=<file>]` / `--dump-format=` write the parameter tree out. The start script also takes `--audio-source=<pactl-source>` (default: monitor of the current default sink).

The `app/src/dist/` directory is the runtime working directory and contains:
- `cthugha.ini` — configuration file (parsed by ini4j), including `[keys]` key bindings (`KeyBindingConfig`)
- `state.ini` — app/machine preferences remembered across restarts (fullscreen, notifications, …); local, not shipped
- `maps/` — palette map files for color translation effects
- `images/` — PNG image files used as flash visualizations
- `videos/` — video files for the video overlay; local, not shipped
- `quotes.json`, `strings.txt` — quote text sources
- `textures/` — splash images
- `glsl/` — GLSL shader source files loaded at runtime (do NOT delete; not embedded in jar)
- `tabs/` — saved tab (translation table) preset files
- `configs/` — saved screen configs plus the auto-saved `.current.json` session state; local, not shipped
- `beatpresets/` — saved beat-detector presets; local, not shipped

The `distributions` block in `app/build.gradle` excludes the local-only content from the packaged distribution.

Further design docs live in `docs/`: `RENDERING_PIPELINE.md`, `PARAMS.md`, `REMOTE_CONTROL.md`, `SCREEN_CONFIGS.md`.

## Architecture

### Rendering Pipeline

The rendering is OpenGL-based, managed by `CthughaWindow` (extends render-core's `GLWindow`). Each frame executes as two passes driven by an ordered list of `RenderPhase` implementations:

**Indexed pass** (while `renderFBO` is bound — writes directly to the R16 `renderTex`):
1. **CPU tick** (`JCthugha.doRenderCPU()`) — advances animations and frame rate stats; uploads palette LUT when dirty
2. **Translate** (render-core's `TranslateTextureRenderer`) — GPU shader reads the RG16UI translation map and shifts every pixel of `displayTex` into `renderTex`
3. **`phase.indexedRender()`** for each phase in order — waves, flash effects and quote text in `BUFFER`/`BOTH` mode write palette indices directly into `renderTex`

**Blur** (between passes):
4. **Two-pass Gaussian blur** (render-core's `BlurTextureRenderer`, `xBlur` + `yBlur`) with fade multiplier — `renderTex` → `flameTex` → `displayTex`, creating the flame spread effect. This feeds the *next* frame's translate, not this frame's display, so fresh waves appear at full value before they start fading

**Screen pass** (after palette display, renders RGBA overlays):
5. **Display** (`IndexPaletteRenderer`) — palette-converts `renderTex` (R16 indices) to RGBA for the window using a 256×N LUT texture (256×1 for a standard palette)
6. **`phase.screenRender()`** for each phase in order — waves and quote text in `OVERLAY`/`BOTH` mode, video, notifications, the work spinner, the beat debug HUD and the QR code (each phase manages its own blend state)

**Fixed-role textures**: `displayTex` is always the blur output/translate source; `renderTex` is the translate output/indexed-render target/display source. Both are R16 format (GL_R16, 16-bit single-channel; palette index stored as `index/(paletteSize-1)` normalised to `[0, 1]`, so 1.0 is the last entry — `IndexPaletteRenderer` rounds to the nearest entry, unlike render-core's `PaletteRenderer`, whose `floor` decode wraps 1.0 to entry 0). `flameTex` is a GRAY intermediate used only between the two blur passes.

`RenderMode` (`params/RenderMode.java`: `OVERLAY`, `BUFFER`, `BOTH`) is the per-element choice between drawing as a crisp RGBA screen overlay, baking into the indexed buffer (subject to blur/translate), or both. Every wave model and the quote phase have a `mode` parameter of this type.

### Render Phase Components

`RenderPhase` (`display/phase/RenderPhase.java`) is a simple interface with default no-op methods: `init`, `indexedRender`, `screenRender`, `dispose`, and `registerActions`. `CthughaWindow` drives an ordered list of phases: `JCthugha.createPhases()` defines the core order (`WavePhase`, `FlashPhase`, `VideoPhase`, `QuotePhase`, `NotifPhase`, `DebugBeatsPhase`), and `CthughaWindow` appends `WorkPhase` and, when the remote server is enabled, `QrPhase`. Phases register their own actions via `registerActions()`.

The implementations live in `display/phase/`:

- **`WavePhase`** — owns `AudioPipeline` and reconciles its live GL renderer objects against `cthugha.waveSystem`'s dynamic instance list once per frame (building/disposing renderers as instances are added/removed), rendering each in list order. In the indexed pass a wave writes palette indices into the R16 render texture (its `index` parameter picks the entry); in the screen pass it draws in its RGB `colour`.
- **`FlashPhase`** — one-shot texture flash effects (PNG image or white) baked into `renderTex` via `TextureBakeRenderer`. Registers the "Flash White" action; picking a specific image (or a random one) happens via the "Images" tab's `ImagesLibraryNode`.
- **`VideoPhase`** — plays a randomly chosen video from `videos/` on loop as an alpha-blended full-screen RGBA overlay. Decoding (javacv/FFmpeg) runs on a background thread, paced against each frame's PTS; the GL thread only uploads frames with `glTexSubImage2D`. Videos are picked via the "Videos" tab's `VideosLibraryNode`.
- **`QuotePhase`** — renders the current quote according to its `RenderMode`. When baking into the indexed buffer, it saves/restores the GL framebuffer binding (`glGetIntegerv(GL_FRAMEBUFFER_BINDING)`) to temporarily render RGBA text then bake it back into the restored render FBO. Registers the "Toggle Quote Mode" action; picking a specific quote (or a random one) happens via the "Quotes" tab's `QuotesLibraryNode`.
- **`NotifPhase`** — renders transient notification messages as a 3-second RGBA screen overlay. Notifications are produced by `JCthugha.notify()` and polled each frame.
- **`DebugBeatsPhase`** — HUD listing each beat-detection band's name, a bar (drawn with `SolidQuad`) and its strength, for tuning `[beats]` band ranges and binding thresholds. Shown while `AudioSourceNode.debugBeats` is on (the "Audio" tab, or the `D` key); configured in `[DebugBeats]`.
- **`WorkPhase`** — spinning indicator and status label shown while `BackgroundWorkQueue` (`work/`) has work in flight; configured in `[Work]`.
- **`QrPhase`** — wraps `QrOverlay`. `show(url)` / `hide()` are thread-safe proxies for the remote server.

`PhaseConfig` holds the INI-parsing helpers the phases share.

### Core Data Structures

- **`TabBuffer`** (`tab/TabBuffer.java`) — holds the pre-allocated RG16UI translation map as a direct `ByteBuffer` viewed as a `ShortBuffer`. Filled by `TabMapping.compute()` and uploaded to `translateMapTex` via `glTexSubImage2D`.
- **`PaletteMap`** (`map/PaletteMap.java`) — RGB lookup table (256 entries for every shipped `.MAP`, but any count is accepted). Held by `JCthugha`; uploaded to GPU as a texture each frame when dirty.
- **Translation tables** (`tab/`) — `TabMapping` holds pre-computed RG16UI pixel displacement data. `TabGenerator` implementations in `tab/generators/`, grouped by family (`rotation/` e.g. `Spiral`, `Hurricane`; `wave/` e.g. `Smoke`, `Ripple`; `lens/`; `chaos/`; `fractal/`; plus `NoDistortion`), generate these mappings. `TabStore` saves/loads named presets to the `tabs/` directory. `GeneratorRegistry` manages the set of active generators.
- **Transforms** (`params/transform/`) — `TransformParams`, `PerspectiveParams` and `XYParam` are composite parameter nodes for 2-D spatial transforms, used by both wave models and tab generators.

### Parameter System

`ParamNode` / `Node` (`params/`) provide a tree-structured parameter hierarchy. Every major component extends `ParamNode`, exposing typed parameters via `params/values/` (`BooleanParameter`, `DoubleParameter`, `EnumParameter`, `IntegerParameter`, `StringParameter`, etc.) that can be introspected, animated, and serialized. The parameter tree is exposed over the remote HTTP API.

`Action` / `AbstractAction` (`params/action/`) extend the tree with invokable operations. Actions receive a typed `ActionContext` (sub-interfaces like `TabActionContext`, `PaletteActionContext`, `CthughaActionContext`) that provides access to runtime state without coupling actions to the window. `ActionTreeBuilder` assembles the top-level tree, whose children are the remote UI's tabs: "Wave", "Audio", "Tab", "Render" (palettes and blur), "Images", "Videos", "Quotes", "Configs" and "General".

`ParamSerializer` (`remote/`) serializes the full parameter tree to JSON for the remote API. `UiHint` (`params/UiHint.java`) string constants attached to parameters (control type, `hidden`, `visible-when`, `scale`, …) guide how the remote UI renders each control. `Node.withDescription(String)` attaches an optional human-readable explanation, surfaced in the remote UI as a tap-to-expand info toggle (`InfoButton` in `remote-ui`) rather than a hover tooltip, since the primary client is a phone.

### Binding System

`BindingSystem` (`binding/`) extends `ParamNode` and manages a single `CopyOnWriteArrayList<Binding>` of script-driven bindings. It is an internal manager: it and every binding are marked `withNoRemote()`, so bindings never appear as a tab of their own. Instead each binding is shown and edited on the parameter or action it targets.

`Binding` is the common base: every binding has an `enabled` flag and a `target` — always a path string, resolved lazily against the tree root fresh on every tick rather than held as a direct object reference. A target that hasn't been created yet, or one that has since been deleted from the tree (e.g. a removed wave), simply fails to resolve for that tick — no explicit release/cleanup call is required. `BindingMode` (fixed per binding) selects both the evaluation model and the script kind:

- **`CONTINUOUS`** — implemented by `ContinuousBinding`: a numeric `ScriptParameter` (Janino-compiled `AnimScript`, e.g. `"sine(0.05)"`) evaluated once per CPU tick against elapsed time `t`, clamped to `[0,1]`, and pushed into the resolved target's `AbstractValue` via `setNormalisedValue`. Whichever leaf is currently resolved gets the binding published as its `AnimationBindingView` (see `AbstractValue#getAnimationBinding()`), so the remote serializer embeds live script/enabled/error state directly on that leaf's JSON; the association follows the resolved target and clears itself automatically when the path stops resolving. Created, edited and removed via the "Animate" control on the target parameter (`POST`/`PATCH`/`DELETE /api/v1/params/{path}/animation`).
- **`EDGE_TRIGGERED`** — implemented by `EdgeTriggeredBinding`: a boolean `ConditionParameter` script (Janino-compiled `ConditionScript`, e.g. `"bass() > 0.7"`) paired with `cooldown` and `value`. On a false→true transition (no more than once per `cooldown` seconds) it resolves `target` and either calls `execute()` on it (if an `Action`) or applies `value` via `ParamValues.applyText()` (if a settable leaf) — which behaviour applies is decided purely by what `target` resolves to at fire time — or sets the binding's `status` field to an explanatory message if the path doesn't resolve to either. `ParamSerializer` embeds a `triggers` array on whatever node each trigger targets, and triggers are created, edited and removed through `POST /api/v1/params/{path}/triggers` and `PATCH`/`DELETE /api/v1/params/{path}/triggers/{name}`; they are auto-named `"Trigger N"`. In the SPA, `TriggerEditor` renders the fire-value as a control matching the target leaf's own type/min/max/options, and omits it for an `Action` target.

`ScriptHelpers` is the shared base for both script kinds, providing `t`, wave helpers (`sine`/`cosine`/`saw`/`tri`/`pulse`/`phase`), `range(min, max, value)` to remap a `[0,1]` value into a sub-range, beat-strength helpers (`bass()`/`snare()`/`hihat()`/`beat(name)`, backed by render-core's `BeatDetector`), random helpers (`random()`/`random(min,max)`), and script state: `state.get(key, default)`/`state.set(key, value)` scoped to the individual `Binding` (backed by `Binding#localState()`), and `global.get`/`global.set` shared across every binding in the owning `BindingSystem` (backed by `BindingSystem#globalState()`). Both are intentionally ephemeral — reset on restart, not captured by screen configs, since `ScreenConfigParams` only walks leaf value nodes and an arbitrary `Map<String,Object>` doesn't fit that model. Scripts are a single expression (no statement sequencing), so `state.set`/`global.set` return the value they just stored, letting a script update and use its own state in one expression, e.g. an incrementing counter: `state.set("n", state.get("n", 0.0) + 1)`. On compile failure a binding keeps running its last-good function; only the reported error changes — the SPA surfaces this without interrupting the binding. `BindingSystem.tick()` runs each CPU tick via `cthugha.bindings.tick()`.

### Audio System

`AudioPipeline` (`display/AudioPipeline.java`) owns capture and feeds samples to the wave renderers, built entirely on render-core's `util.audio` API (see the render-core skill in `.agents/skills/` — its folder name includes the render-core version — and its `audio.md` for the library-side reference). It holds an `AudioSources` list — real hardware lines discovered via `LineAcquirer.allLinesMatching()` plus an always-available `SimulatedSources.middleC()` fallback (`AudioPipeline.SIMULATED_SOURCE_NAME`) — and owns its own selected-index tracking, since `AudioSources`/`LineAcquirer` track no notion of "current" themselves. `AudioSourceNode` (`display/AudioSourceNode.java`) exposes this list as the remote-UI "Audio" tab; because the param tree is built before `AudioPipeline` exists, it independently re-discovers device names via the same static `LineAcquirer` scan and reconciles by exact name once the live pipeline is up.

FFT is done via render-core's `FrequencyProcessor`/`FFTProcessor` (wrapping JTransforms) and consumed by render-core's `SpectrumAnalyser` / `RadialSpectrumAnalyser` and the app's own `RadialClockAnalyser` (wired up in `WavePhase`), with `BeatDetector` sharing the same FFT pass for beat-strength queries. Beat-detector band/tuning settings can be saved and recalled as named presets (`beatpresets/`, `BeatPresetLibraryNode`).

### Wave Models

Wave models live in `display/wave/`. Each is a `ParamNode` holding one wave's parameters (render mode, palette index, colour, transform, …); `WavePhase` builds the matching render-core renderer for each:
- `OscilloscopeModel` — time-domain waveform oscilloscope (render-core `AudioWave`)
- `RadialWaveModel` — radial time-domain wave (render-core `RadialWave`)
- `SpectrumModel` — frequency spectrum bars (render-core `SpectrumAnalyser`)
- `RadialSpectrumModel` — radial spectrum analyser (render-core `RadialSpectrumAnalyser`)
- `RadialClockModel` — "clock face" spectrum analyser, one capsule per FFT bin around a circle, over a configurable bin range (the app's own `RadialClockAnalyser`)

`WaveSystem` (`display/wave/WaveSystem.java`) extends `ParamNode` (mounted directly as the "Wave" tab) and manages a dynamic, ordered `CopyOnWriteArrayList<ParamNode>` of wave instances, following the same pattern `BindingSystem` uses for its list of `Binding`s. It holds any number of independently-configured instances of any of the five types (`WaveSystem.WaveType`), auto-named `"{Type} {N}"` (e.g. "Oscilloscope 1", "Oscilloscope 2") in the order they were added via the "New Wave" action's type picker; each instance gets a "Delete" action appended as an extra child. Render order (in `WavePhase`) matches list order, so later entries composite on top of earlier ones — reordering is not supported. `WaveSystem` implements `DynamicChildList` (`describe()`/`recreate()`) so a screen config round-trips the set of instances (name + type; per-instance param values are captured separately as ordinary leaf values). Because `Binding#target` resolves lazily by path (see Binding System above), a binding targeting a param inside a deleted wave instance simply stops resolving on its next tick — no explicit cleanup is needed on wave deletion.

### Session State and Defaults

The live parameter tree is auto-saved as the unnamed "current" state: `CurrentStatePersister` re-captures it via `ScreenConfigParams` on a fixed interval (default 5 s, `[session] current_state_interval` in `cthugha.ini`) and on clean exit, and `CurrentStateStore` writes it to `configs/.current.json`; it is restored on the next launch. `JCthugha.resetToDefaults()` — run when there is no saved current state, and as the user-invoked "Reset to Defaults" action — leaves one "Oscilloscope 1" wave with an "osc rotation" continuous binding (`sine(0.05)`) on its rotation. App/machine preferences that aren't part of a visualisation (e.g. fullscreen, notifications) are remembered separately in `state.ini` and excluded from screen configs.

### Remote Control

`RemoteServer` (`remote/`) embeds a Javalin HTTP server that exposes the parameter tree as a REST API under `/api/v1/` (plus palette, image and video library endpoints) and serves a React SPA (built from `:remote-ui`, bundled into the jar under `remote/`). Access is secured with a rotating bearer token stored in `TokenStore` and displayed as a QR code overlay via `QrOverlay`. Server-Sent Events (SSE) via `RemoteEventBroadcaster` push parameter changes to connected browsers. See `docs/REMOTE_CONTROL.md` for the full design spec.

### Palette Library

`PaletteLibraryNode` (`map/`) extends `ParamNode` (mounted under the "Render" tab) and exposes the available `.MAP` palette files as a browseable parameter node, allowing the remote UI to list and switch palettes.

### Screen Configs

The `screenconfig/` package implements named, resolution-independent snapshots of the whole parameter tree (waves, bindings, blur, active palette, active tab generator and its own params) — surfaced as the "Configs" tab. `ScreenConfigParams.capture()` returns a `Snapshot` record pairing an order-preserving flat map of every leaf value (skipping subtrees marked `Node.isPersistExcluded()`, set via `ParamNode.withNoPersist()`, independent of the remote-visibility and UI-hint mechanisms) with, for every opted-in `DynamicChildList` subtree, the structural description needed to recreate its runtime-created children. `DynamicChildList` (`params/`) is a small opt-in contract — `describe(): List<ChildSpec>` / `recreate(List<ChildSpec>)` — for a subtree whose children are added/removed at runtime via their own API rather than fixed at construction — implemented by `BindingSystem`'s list of `Binding`s and `WaveSystem`'s list of wave instances. `ScreenConfigParams.apply()` calls `recreate(...)` on every opted-in subtree named in the snapshot *before* walking leaf values, so a config saved with N bindings loads correctly into a session with zero — the recreated children's paths (e.g. `Bindings/Trigger 1/condition`) already exist by the time leaf-value application runs. `ScreenConfigStore` persists named configs (leaf-value map + dynamic-children map) as JSON under `configs/`. See `docs/SCREEN_CONFIGS.md` for the full design.

### Other Packages

- `img/`, `quote/`, `video/` — the image, quote and video libraries behind the "Images", "Quotes" and "Videos" tabs.
- `dump/` — `--dump-params` output of the parameter tree as text or JSON.
- `config/` — `Config`, which loads `cthugha.ini` and the auto-persisted `state.ini`.
- `GameModeIntegration` — best-effort request for a CPU priority boost from Linux's GameMode daemon over D-Bus; a no-op when it's unavailable.

## Key Dependencies

Versions live in `app/build.gradle` and `remote-ui/package.json`.

| Library | Purpose |
|---|---|
| render-core | OpenGL window, shaders, textures, wave renderers, audio capture/FFT/beat detection, font/string rendering |
| LWJGL | OpenGL / GLFW bindings |
| JOML | Vector/matrix types for render-core API |
| Javalin | Embedded HTTP server for remote control |
| Jackson | JSON serialization of parameter tree, screen configs and session state |
| Nayuki QR Code generator | QR code generation for remote URL |
| JTransforms | FFT for spectrum analysis |
| Janino | Runtime compilation of binding scripts |
| javacv / javacpp / FFmpeg | Video decoding for the video overlay (Linux x86-64 natives only) |
| ini4j | INI config file parsing |
| commons-lang3, commons-numbers-fraction | Primitive array utilities, fractions |
| AutoService | Generates `META-INF/services` entries (annotation processor) |
| SLF4J + Logback | Logging |
| JUnit 5 + Mockito | Testing |
| React, Radix UI, Tailwind CSS, lucide-react, Vite, TypeScript | Remote UI SPA (`:remote-ui`) |

## Initiatives (tickets / issues)
Planned work lives in Brindley collections — "initiative", "ticket" and "issue" all mean the same
thing: folders whose `README.md` front-matter contains
`brindley: 1`, holding one Markdown file per initiative, named `<number>-<slug>.md`
(format: https://github.com/duckAsteroid/brindley/blob/main/FORMAT.md). This repo's collection is
`docs/initiatives/` (name `cthugha`, alias `ct`, e.g. `ct#3`).
- Never rename or move initiative files or their asset directories. Status is the `status`
  front-matter field.
- Dependencies are the links under an initiative's `## Dependencies` heading (non-blocking
  links go under `## Related`). Implement only an initiative that is `designed` and whose
  linked initiatives are all `done`. Confirm external (http) dependencies yourself; report any
  you cannot confirm.
- Ask about open questions before implementing; `(implementation)` questions are yours to
  settle — record the decision in the initiative.
- If, while building, you reach a decision the initiative doesn't settle and that matters to
  what you build, don't guess: add it as an ordinary open question and stop to ask. The
  initiative stays in-progress, and readiness checks fail until it is resolved. Mark a question
  `(implementation)` only when the choice is genuinely yours to make.
- When discussing open questions with the user: one at a time, in open chat (no form or
  multiple-choice prompts), grounded in the actual code (cite `path:line`; say "unclear from
  code" rather than guess), with concrete examples from the real domain. Record each decision
  in the initiative as it is made.
- On completion, in the same commit as the code: update the project docs to describe the code
  as it now is — present tense, no history, no rejected alternatives, no links to initiatives —
  and record `docs_impact` (files updated, or `none: <reason>`); settle the initiative's
  wording, moving rejected alternatives into `## Appendix: Rejected alternatives`; set
  `status: done` and `updated`.
- A spike (`type: spike`) measures what its `## Measures` section asks and records
  `## Findings`; its code stays on its own branch and may become the basis of the real
  implementation, so write it to be built on.
- New initiative: next number in the collection, filename `<number>-<slug>.md`,
  `status: draft`, and a `type` (e.g. `feature`, `bug`, `refactor`).
