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

The rendering is OpenGL-based, managed by `CthughaWindow` (extends `render-core`'s `GLWindow`). Each frame executes as a sequence of GPU passes:

1. **CPU tick** (`JCthugha.doRenderCPU()`) — advances animations and frame rate stats
2. **Translate pass** (`TranslateTextureRenderer`) — GPU shader reads the RG16UI translation map texture and shifts every pixel in `pingTex` into `pongTex`
3. **Blur/flame pass** (`BlurTextureRenderer`) — two-pass separable Gaussian blur with a fade multiplier applied to `pongTex` back into `pingTex`, creating the flame spread effect
4. **Wave bake** (`WaveIndexBakeRenderer`) — audio waveform models write palette indices into the indexed buffer, baked into `pingTex`
5. **Texture flash** (`TextureBakeRenderer`) — optionally bakes a PCX image or white flash into the indexed buffer
6. **Display pass** (`PaletteRenderer`) — palette-converts `pingTex` (R8 palette indices) to RGBA for display using a 256-entry LUT texture
7. **Overlay** — quote text and notifications rendered by `StringRenderer` (render-core); QR code overlay via `QrOverlay`

**Ping-pong textures**: `pingTex` is always the display/translate source; `pongTex` is the translate output/flame source. Both are R8 format (single-byte palette index per pixel).

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

Branch `web_controls` is active — recent work involves the remote control UI (Javalin server, React SPA, SSE), action system, tab presets, palette browser, and keyboard/remote parameter controls.
