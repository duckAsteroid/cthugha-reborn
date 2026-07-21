# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cthugha Reborn is a Java port of the 1990s DOS music visualization software Cthugha. It renders real-time audio-reactive visual effects using a palettized (256-color indexed) pixel buffer rendered via OpenGL, translation tables for distortion, and multiple waveform renderers layered on top. A phone/tablet can control runtime parameters via a browser-based remote UI served from an embedded HTTP server.

## Build & Run Commands

Requires Java 17. This is a Gradle multi-project build (`:app` + `:remote-ui`).

```bash
# Build (also builds the remote-ui React SPA and bundles it into the jar)
./gradlew build

# Run (must run from src/dist/ as the working directory for config/maps/images/glsl)
./gradlew run

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "io.github.duckasteroid.cthugha.audio.AudioSampleTest"
```

The `app/src/dist/` directory is the runtime working directory and contains:
- `cthugha.ini` — configuration file (parsed by ini4j)
- `maps/` — palette map files for color translation effects
- `images/` — PNG image files used as flash visualizations
- `glsl/` — GLSL shader source files loaded at runtime (do NOT delete; not embedded in jar)
- `tabs/` — saved tab (translation table) preset files

## Architecture

### Rendering Pipeline

The rendering is OpenGL-based, managed by `CthughaWindow` (extends `render-core`'s `GLWindow`). Each frame executes as two passes driven by an ordered list of `RenderPhase` implementations:

**Indexed pass** (while `renderFBO` is bound — writes directly to the R16 `renderTex`):
1. **CPU tick** (`JCthugha.doRenderCPU()`) — advances animations and frame rate stats; uploads palette LUT when dirty
2. **Translate** (`TranslateTextureRenderer`) — GPU shader reads the RG16UI translation map and shifts every pixel of `displayTex` into `renderTex`
3. **`phase.indexedRender()`** for each phase in order — waves, flash effects, and (optionally) baked quote text write palette indices directly into `renderTex`

**Blur** (between passes):
4. **Two-pass Gaussian blur** (`BlurTextureRenderer` xBlur + yBlur) with fade multiplier — `renderTex` → `flameTex` → `displayTex`, creating the flame spread effect

**Screen pass** (after palette display, renders RGBA overlays):
5. **Display** (`PaletteRenderer`) — palette-converts `displayTex` (R16 indices) to RGBA for the window using a 256-entry LUT texture
6. **`phase.screenRender()`** for each phase in order — quote text overlay, notifications, QR code (each phase manages its own blend state)

**Fixed-role textures**: `displayTex` is always the display/translate source; `renderTex` is the translate output/indexed-render target. Both are R16 format (GL_R16, 16-bit single-channel; palette index stored as `index/paletteSize` normalised to `[0, 1]`). `flameTex` is a GRAY intermediate used only between the two blur passes.

### Render Phase Components

`RenderPhase` (`display/phase/RenderPhase.java`) is a simple interface with default no-op methods: `init`, `indexedRender`, `screenRender`, `dispose`, and `registerActions`. `CthughaWindow` drives an ordered list of phases; `JCthugha.createPhases()` defines the default order.

Five implementations live in `display/phase/`:

- **`WavePhase`** — owns `AudioPipeline` and all four wave/spectrum GL renderers. Renders directly into the R16 render texture using palette-index–compatible colours (red channel = 1.0, the maximum normalised value, targeting the last palette entry).
- **`FlashPhase`** — one-shot texture flash effects (PNG image or white) baked into `renderTex` via `TextureBakeRenderer`. Registers the "Flash White" action; picking a specific image (or a random one) happens via the "Images" tab's `ImagesLibraryNode`.
- **`QuotePhase`** — renders the current quote as a screen overlay (default) or baked into the indexed buffer (`quoteInBuffer` mode). In bake mode, saves/restores the GL framebuffer binding (`glGetIntegerv(GL_FRAMEBUFFER_BINDING)`) to temporarily render RGBA text then bake it back into the restored render FBO. Registers the "Toggle Quote Mode" action; picking a specific quote (or a random one) happens via the "Quotes" tab's `QuotesLibraryNode`.
- **`NotifPhase`** — renders transient notification messages as a 3-second RGBA screen overlay. Notifications are produced by `JCthugha.notify()` and polled each frame.
- **`QrPhase`** — wraps `QrOverlay`; added by `CthughaWindow` only when the remote server is enabled. `show(url)` / `hide()` are thread-safe proxies for the remote server.

### Core Data Structures

- **`TabBuffer`** (`tab/TabBuffer.java`) — holds the pre-allocated RG16UI translation map as a direct `ByteBuffer` viewed as a `ShortBuffer`. Filled by `TabMapping.compute()` and uploaded to `translateMapTex` via `glTexSubImage2D`. Replaces the former `ScreenBuffer`.
- **`PaletteMap`** (`map/PaletteMap.java`) — 256-entry RGB lookup table. Held by `JCthugha`; uploaded to GPU as a texture each frame when dirty.
- **`AudioSample`** (`audio/AudioSample.java`) — a batch of audio data with built-in downsampling support. Waveform models receive this each frame.
- **Translation tables** (`tab/`) — `TabMapping` holds pre-computed RG16UI pixel displacement data. `TabGenerator` implementations (`Spiral`, `Hurricane`, `Smoke`, etc. in `tab/generators/`) generate these mappings. `TabStore` saves/loads named presets to `tabs/` directory. `GeneratorRegistry` manages the set of active generators.

### Parameter System

`ParamNode` / `Node` (`params/`) provide a tree-structured parameter hierarchy. Every major component extends `ParamNode`, exposing typed parameters via `params/values/` (`BooleanParameter`, `DoubleParameter`, `EnumParameter`, `IntegerParameter`, `StringParameter`, etc.) that can be introspected, animated, and serialized. The parameter tree is exposed over the remote HTTP API.

`Action` / `AbstractAction` (`params/`) extend the tree with invokable operations. Actions receive a typed `ActionContext` (sub-interfaces like `TabActionContext`, `PaletteActionContext`, `CthughaActionContext`) that provides access to runtime state without coupling actions to the window.

`ParamSerializer` (`remote/`) serializes the full parameter tree to JSON for the remote API. `UiHint` annotations on parameters guide how the remote UI renders each control. `Node.withDescription(String)` attaches an optional human-readable explanation, surfaced in the remote UI as a tap-to-expand info toggle (`InfoButton` in `remote-ui`) rather than a hover tooltip, since the primary client is a phone.

### Binding System

`BindingSystem` (`binding/`) extends `ParamNode` (mounted as the "Bindings" tab) and manages a single `CopyOnWriteArrayList<Binding>` — the unification of what used to be two separate managers, `AnimationSystem`/`AnimationBinding` and `TriggerSystem`/`ActionTrigger`. `Binding` is the common base: every binding has an `enabled` flag and a `target` — always a path string (`UiHint.TARGET_PICKER`), resolved lazily against the tree root fresh on every tick rather than held as a direct object reference. A target that hasn't been created yet, or one that has since been deleted from the tree (e.g. a removed wave), simply fails to resolve for that tick — no explicit release/cleanup call is required. `BindingMode` (fixed per binding) selects both the evaluation model and the script kind:

- **`CONTINUOUS`** — implemented by `ContinuousBinding`: a numeric `ScriptParameter` (Janino-compiled `AnimScript`, e.g. `"sine(0.05)"`) evaluated once per CPU tick against elapsed time `t`, clamped to `[0,1]`, and pushed into the resolved target's `AbstractValue` via `setNormalisedValue`. Whichever leaf is currently resolved gets the binding published as its `AnimationBindingView` (see `AbstractValue#getAnimationBinding()`), so the remote serializer can embed live script/enabled/error state directly on that leaf's JSON; the association follows the resolved target and clears itself automatically when the path stops resolving. Attached via the "Animate" control on the target parameter itself (`POST`/`PATCH`/`DELETE .../{path}/animation`); `ContinuousBinding` children are marked `withNoRemote()` so they don't also appear in the generic "Bindings" tab listing (that would just be a worse, duplicate editor).
- **`EDGE_TRIGGERED`** — implemented by `EdgeTriggeredBinding`: a boolean `ConditionParameter` script (Janino-compiled `ConditionScript`, e.g. `"bass() > 0.7"`) paired with `cooldown` and `value`. On a false→true transition (no more than once per `cooldown` seconds) it resolves `target` and either calls `execute()` on it (if an `Action`) or applies `value` via `ParamValues.applyText()` (if a settable leaf) — which behaviour applies is decided purely by what `target` resolves to at fire time — or sets the binding's `status` field to an explanatory message if the path doesn't resolve to either. New edge-triggered bindings are created via `New Condition`/`New Target`/`New Cooldown` form fields (plus a hidden `New Value`) and an `Add Trigger` action on the `BindingSystem` node itself (the same form-plus-create-action pattern as `ScreenConfigLibraryNode`), so creation needs no dedicated REST endpoints — it rides the existing generic PATCH-leaf and POST-`/execute` routes. `EdgeTriggeredBinding` children stay remote-visible, listed under the "Bindings" tab.

`ScriptHelpers` is the shared base for both script kinds, providing `t`, wave helpers (`sine`/`cosine`/`saw`/`tri`/`pulse`/`phase`), beat-strength helpers (`bass()`/`snare()`/`hihat()`/`beat(name)`, backed by render-core's `BeatDetector`), random helpers (`random()`/`random(min,max)`), and script state: `state.get(key, default)`/`state.set(key, value)` scoped to the individual `Binding` (backed by `Binding#localState()`), and `global.get`/`global.set` shared across every binding in the owning `BindingSystem` (backed by `BindingSystem#globalState()`). Both are intentionally ephemeral — reset on restart, not captured by screen configs, since `ScreenConfigParams` only walks leaf value nodes and an arbitrary `Map<String,Object>` doesn't fit that model. Scripts are a single expression (no statement sequencing), so `state.set`/`global.set` return the value they just stored, letting a script update and use its own state in one expression, e.g. an incrementing counter: `state.set("n", state.get("n", 0.0) + 1)`. On compile failure a binding keeps running its last-good function; only the reported error changes — the SPA surfaces this without interrupting the binding. `BindingSystem.tick()` runs each CPU tick via `cthugha.bindings.tick()`; the `target` field (and `New Target`) are rendered by the SPA as a searchable list of every `Action` and settable leaf currently in the tree (excluding the `Bindings` subtree itself); `value` (and `New Value`) are `UiHint.HIDDEN` and rendered inline by the target picker as a control matching the picked target's own type/min/max/options — a toggle, slider, dropdown, or text field — via the `paired-value-field` hint, and disappear entirely when the picked target is an `Action`.

### Audio System

`AudioPipeline` (`display/AudioPipeline.java`) owns capture and feeds samples to the wave models, built entirely on render-core's `util.audio` API (see the `com-asteroid-duck-opengl-render-core-0-0-1` skill's `audio.md` for the library-side reference). It holds an `AudioSources` list — real hardware lines discovered via `LineAcquirer.allLinesMatching()` plus an always-available `SimulatedSources.middleC()` fallback (`AudioPipeline.SIMULATED_SOURCE_NAME`) — and owns its own selected-index tracking, since `AudioSources`/`LineAcquirer` track no notion of "current" themselves. `AudioSourceNode` (`display/AudioSourceNode.java`) exposes this list as the remote-UI "Audio" tab; because the param tree is built before `AudioPipeline` exists, it independently re-discovers device names via the same static `LineAcquirer` scan and reconciles by exact name once the live pipeline is up.

FFT is done via render-core's `FrequencyProcessor`/`FFTProcessor` (wrapping JTransforms) and consumed by `SpectrumAnalyser` / `RadialSpectrumAnalyser` (wired up in `WavePhase`), with `BeatDetector` sharing the same FFT pass for beat-strength queries.

### Wave Models

Wave renderers live in `display/wave/` and implement the render-core wave interfaces:
- `OscilloscopeModel` — time-domain waveform oscilloscope
- `RadialWaveModel` — radial time-domain wave
- `SpectrumModel` — frequency spectrum bars
- `RadialSpectrumModel` — radial spectrum analyser

### Remote Control

`RemoteServer` (`remote/`) embeds a Javalin HTTP server that exposes the parameter tree as a REST API and serves a React SPA (built from `:remote-ui`, bundled into the jar). Access is secured with a rotating bearer token stored in `TokenStore` and displayed as a QR code overlay via `QrOverlay`. Server-Sent Events (SSE) via `RemoteEventBroadcaster` push parameter changes to connected browsers. See `REMOTE_CONTROL.md` for the full design spec.

### Palette Library

`PaletteLibraryNode` (`map/`) extends `ParamNode` and exposes the available `.MAP` palette files as a browseable parameter node, allowing the remote UI to list and switch palettes.

### Screen Configs

The `screenconfig/` package implements named, resolution-independent snapshots of the whole parameter tree (waves, animations, blur, active palette, active tab generator and its own params) — surfaced as the "Configs" tab. `ScreenConfigParams` captures/applies an order-preserving flat map of every leaf value, skipping subtrees marked `Node.isPersistExcluded()` (set via `ParamNode.withNoPersist()`, independent of the remote-visibility and UI-hint mechanisms). `ScreenConfigStore` persists named configs as JSON under `configs/`. See `SCREEN_CONFIGS.md` for the full design.

## Key Dependencies

| Library | Purpose |
|---|---|
| render-core 0.0.1 | OpenGL window, shaders, textures, wave renderers, font/string rendering |
| LWJGL 3.4.1 | OpenGL / GLFW bindings |
| JOML 1.10.9 | Vector/matrix types for render-core API |
| Javalin 6.6.0 | Embedded HTTP server for remote control |
| Jackson 2.17.2 | JSON serialization of parameter tree |
| QR code gen 1.8.0 | QR code generation for remote URL |
| JTransforms 3.1 | FFT for spectrum analysis |
| ini4j | INI config file parsing |
| SLF4J + Logback | Logging |
| JUnit 5 + Mockito | Testing |

## Current Branch Context

Branch `render_phase` is active — extracted `RenderPhase` interface and five phase implementations (`WavePhase`, `FlashPhase`, `QuotePhase`, `NotifPhase`, `QrPhase`) from the `CthughaWindow` monolith. Wave renderers now write directly to the R16 indexed buffer (`displayTex`/`renderTex` upgraded from R8 to GL_R16); the RGBA wave overlay FBO and `WaveIndexBakeRenderer` have been deleted. `ActionTreeBuilder.Callbacks` shrunk from 11 to 7 methods; phases register their own actions via `registerActions()`.
