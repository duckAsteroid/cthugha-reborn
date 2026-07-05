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
- `pcx/` — PCX image files used as flash visualizations
- `glsl/` — GLSL shader source files loaded at runtime (do NOT delete; not embedded in jar)
- `tabs/` — saved tab (translation table) preset files

## Architecture

### Rendering Pipeline

The rendering is OpenGL-based, managed by `CthughaWindow` (extends `render-core`'s `GLWindow`). Each frame executes as two passes driven by an ordered list of `RenderPhase` implementations:

**Indexed pass** (while `pongFBO` is bound — writes directly to the R8 `pongTex`):
1. **CPU tick** (`JCthugha.doRenderCPU()`) — advances animations and frame rate stats; uploads palette LUT when dirty
2. **Translate** (`TranslateTextureRenderer`) — GPU shader reads the RG16UI translation map and shifts every pixel of `pingTex` into `pongTex`
3. **`phase.indexedRender()`** for each phase in order — waves, flash effects, and (optionally) baked quote text write palette indices directly into `pongTex`

**Blur** (between passes):
4. **Two-pass Gaussian blur** (`BlurTextureRenderer` xBlur + yBlur) with fade multiplier — `pongTex` → `flameTex` → `pingTex`, creating the flame spread effect

**Screen pass** (after palette display, renders RGBA overlays):
5. **Display** (`PaletteRenderer`) — palette-converts `pingTex` (R8 indices) to RGBA for the window using a 256-entry LUT texture
6. **`phase.screenRender()`** for each phase in order — quote text overlay, notifications, QR code (each phase manages its own blend state)

**Ping-pong textures**: `pingTex` is always the display/translate source; `pongTex` is the translate output/indexed-render target. Both are R8 format (single-byte palette index per pixel).

### Render Phase Components

`RenderPhase` (`display/phase/RenderPhase.java`) is a simple interface with default no-op methods: `init`, `indexedRender`, `screenRender`, `dispose`, and `registerActions`. `CthughaWindow` drives an ordered list of phases; `JCthugha.createPhases()` defines the default order.

Five implementations live in `display/phase/`:

- **`WavePhase`** — owns `AudioPipeline` and all four wave/spectrum GL renderers. Renders directly into the R8 pong texture using palette-index–compatible colours (red channel = index/255, so index 200 ≈ 0.784). Registers the "Cycle Audio" action.
- **`FlashPhase`** — one-shot texture flash effects (PCX image or white) baked into `pongTex` via `TextureBakeRenderer`. Registers "Flash Image" and "Flash White" actions.
- **`QuotePhase`** — renders the current quote as a screen overlay (default) or baked into the indexed buffer (`quoteInBuffer` mode). In bake mode, saves/restores the GL framebuffer binding (`glGetIntegerv(GL_FRAMEBUFFER_BINDING)`) to temporarily render RGBA text then bake it back into the restored pong FBO. Registers "Show Quote" and "Toggle Quote Mode" actions.
- **`NotifPhase`** — renders transient notification messages as a 3-second RGBA screen overlay. Notifications are produced by `JCthugha.notify()` and polled each frame.
- **`QrPhase`** — wraps `QrOverlay`; added by `CthughaWindow` only when the remote server is enabled. `show(url)` / `hide()` are thread-safe proxies for the remote server.

### Core Data Structures

- **`TabBuffer`** (`tab/TabBuffer.java`) — the central indexed pixel buffer. Stores pixels as 8-bit palette indices in a `ByteBuffer`; also holds the RG16UI translation map. Replaces the former `ScreenBuffer`.
- **`PaletteMap`** (`map/PaletteMap.java`) — 256-entry RGB lookup table. Held by `JCthugha`; uploaded to GPU as a texture each frame when dirty.
- **`AudioSample`** (`audio/AudioSample.java`) — a batch of audio data with built-in downsampling support. Waveform models receive this each frame.
- **Translation tables** (`tab/`) — `TabMapping` holds pre-computed RG16UI pixel displacement data. `TabGenerator` implementations (`Spiral`, `Hurricane`, `Smoke`, etc. in `tab/generators/`) generate these mappings. `TabStore` saves/loads named presets to `tabs/` directory. `GeneratorRegistry` manages the set of active generators.

### Parameter System

`ParamNode` / `Node` (`params/`) provide a tree-structured parameter hierarchy. Every major component extends `ParamNode`, exposing typed parameters via `params/values/` (`BooleanParameter`, `DoubleParameter`, `EnumParameter`, `IntegerParameter`, `StringParameter`, etc.) that can be introspected, animated, and serialized. The parameter tree is exposed over the remote HTTP API.

`Action` / `AbstractAction` (`params/`) extend the tree with invokable operations. Actions receive a typed `ActionContext` (sub-interfaces like `TabActionContext`, `PaletteActionContext`, `CthughaActionContext`) that provides access to runtime state without coupling actions to the window.

`ParamSerializer` (`remote/`) serializes the full parameter tree to JSON for the remote API. `UiHint` annotations on parameters guide how the remote UI renders each control.

### Animation System

`AnimationSystem` (`animation/`) extends `ParamNode` and manages `AnimationBinding` entries that map animated curves to `DoubleParameter` values in the tree. Animations advance each CPU tick via `animation.tick()`.

### Audio System

`AudioPipeline` (`display/AudioPipeline.java`) owns the `AudioSource` and feeds samples to the wave models. Three `AudioSource` implementations:
- `SampledAudioSource` — real system microphone/line-in via `javax.sound`
- `SimulatedFrequenciesAudioSource` — synthetic audio at configurable frequencies (useful for testing)
- `RandomSimulatedAudio` — random noise

FFT is done in `audio/dsp/FastFourierTransform.java` (wrapping JTransforms) and consumed by `SpectrumModel` / `RadialSpectrumModel`.

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
| Apache Commons Imaging | PCX image loading |
| ini4j | INI config file parsing |
| SLF4J + Logback | Logging |
| JUnit 5 + Mockito | Testing |

## Current Branch Context

Branch `render_phase` is active — extracted `RenderPhase` interface and five phase implementations (`WavePhase`, `FlashPhase`, `QuotePhase`, `NotifPhase`, `QrPhase`) from the `CthughaWindow` monolith. Wave renderers now write directly to the R8 ping-pong buffer; the RGBA wave overlay FBO and `WaveIndexBakeRenderer` have been deleted. `ActionTreeBuilder.Callbacks` shrunk from 11 to 7 methods; phases register their own actions via `registerActions()`.
