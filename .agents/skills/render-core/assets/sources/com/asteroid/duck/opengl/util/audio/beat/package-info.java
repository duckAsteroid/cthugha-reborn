/**
 * Tempo, beat and kick-drum tracking from raw audio.
 *
 * <p>A {@link com.asteroid.duck.opengl.util.audio.beat.BeatAnalyser} consumes mono samples and
 * reports what it finds to a {@link com.asteroid.duck.opengl.util.audio.beat.BeatListener}.
 * Event times are measured in samples consumed, not wall-clock time, so the same analyser runs
 * live on the audio thread or offline (faster than real time) and is evaluated exactly as it
 * behaves live.</p>
 *
 * <p>The roadmap is in {@code docs/beat-tracking-plan.md}. The offline evaluation harness lives
 * in {@code render-core}'s {@code beatEval} source set: {@code ./gradlew :render-core:beatEval}.</p>
 */
package com.asteroid.duck.opengl.util.audio.beat;
