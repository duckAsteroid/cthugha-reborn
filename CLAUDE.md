# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cthugha Reborn is a Java port of the 1990s DOS music visualization software Cthugha. It renders real-time audio-reactive visual effects using a palettized (256-color indexed) pixel buffer, translation tables for distortion, and multiple waveform renderers layered on top.

## Build & Run Commands

Requires Java 17.

```bash
# Build
./gradlew build

# Run (must run from src/dist/ as the working directory for config/maps/images)
./gradlew run

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "io.github.duckasteroid.cthugha.audio.AudioSampleTest"
```

The `src/dist/` directory is the runtime working directory and contains:
- `cthugha.ini` — configuration file (parsed by ini4j)
- `maps/` — palette map files for color translation effects
- `pcx/` — PCX image files used as visualizations

## Architecture

### Rendering Pipeline

Each frame in `JCthugha.doRender()` executes in order:
1. **Translation** (`tab/Translate`) — shifts every pixel according to a pre-computed translation table, creating the fluid distortion effect
2. **Flame** (`flame/JavaFlame`) — applies convolution blur to spread/blur pixels
3. **Audio sampling** — captures a batch of audio from the active `AudioSource`
4. **Waveform rendering** — draws `SimpleWave`, `RadialWave`, `SpeckleWave`, `SpectraBars` on top
5. **String rendering** (`strings/StringRenderer`) — overlays quote text
6. **Display** — blits the palettized `ScreenBuffer` to the AWT window via `BufferStrategy`

### Core Data Structures

- **`ScreenBuffer`** (`display/ScreenBuffer.java`) — the central pixel buffer. Stores pixels as 8-bit palette indices; holds the `PaletteMap` for index→RGB conversion. All renderers write palette indices into this buffer.
- **`AudioSample`** (`audio/AudioSample.java`) — a batch of audio data with built-in downsampling support. Waveform renderers receive this each frame.
- **Translation tables** (`tab/`) — pre-computed `int[]` arrays where `table[pixelIndex]` gives the source pixel index to copy from. `TranslateTableSource` implementations (`Spiral`, `Hurricane`, `Smoke`, etc.) generate these tables.

### Parameter System

`AbstractNode` / `Node` (`params/`) provide a tree-structured parameter hierarchy. Every major component extends `AbstractNode`, exposing typed parameters (`BooleanValue`, `DoubleValue`, `EnumValue`, etc.) that can be introspected, animated, and serialized. The parameter tree is the mechanism for runtime tunability and keyboard-driven adjustments.

### Animation System

`Animator` implementations (`animation/`) produce time-varying `[0,1]` values using sine, linear, and sigmoid curves. `AnimatorPool` manages a collection and routes animated values to parameters.

### Audio System

`AudioSource` interface with three implementations:
- `SampledAudioSource` — real system microphone/line-in via `javax.sound`
- `SimulatedFrequenciesAudioSource` — synthetic audio at configurable frequencies (useful for testing)
- `RandomSimulatedAudio` — random noise

FFT is done in `audio/dsp/FastFourierTransform.java` (wrapping JTransforms) and consumed by `SpectraBars`.

## Key Dependencies

| Library | Purpose |
|---|---|
| JTransforms 3.1 | FFT for spectrum analysis |
| Apache Commons Imaging | PCX image loading |
| ini4j | INI config file parsing |
| SLF4J + Logback | Logging |
| JUnit 5 + Mockito | Testing |

## Current Branch Context

Branch `strings` is active — recent work involves parameter system, boolean values, and tab (translation table) parameters.
