/**
 * Real-time audio visualisation renderers.
 *
 * <p>All classes in this package implement
 * {@link com.asteroid.duck.opengl.util.RenderedItem} or serve as helpers consumed by renderers.
 * Audio capture is handled by {@link com.asteroid.duck.opengl.util.audio.AudioReader};
 * FFT processing and beat detection live in
 * {@link com.asteroid.duck.opengl.util.audio.analysis}.</p>
 *
 * <h2>Waveform renderers</h2>
 * <dl>
 *   <dt>{@link com.asteroid.duck.opengl.util.wave.AudioWave}</dt>
 *   <dd>Scrolling oscilloscope-style waveform drawn as a horizontal {@code GL_LINE_STRIP}.
 *       Audio is uploaded each frame via a persistently-mapped PBO to a 1-D
 *       {@code GL_RG16_SNORM} texture sampled directly by the vertex shader.</dd>
 *
 *   <dt>{@link com.asteroid.duck.opengl.util.wave.RadialWave}</dt>
 *   <dd>Oscilloscope waveform deformed around a circle. 1 024 direction vectors are stored
 *       in the VBO; the vertex shader displaces each point radially by the corresponding
 *       audio sample read from the same PBO-backed texture used by {@code AudioWave}.
 *       Aspect-ratio correction is applied automatically via a resize listener.</dd>
 * </dl>
 *
 * <h2>Spectrum renderers</h2>
 * <dl>
 *   <dt>{@link com.asteroid.duck.opengl.util.wave.SpectrumAnalyser}</dt>
 *   <dd>Classic bar-chart spectrum analyser. Each frame, a Hann-windowed FFT (computed by
 *       {@link com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor}) is uploaded
 *       to a 1-D {@code GL_RG32F} texture (R = magnitude, G = peak-hold).
 *       Two draw calls render the filled bars ({@code GL_TRIANGLES}) and white peak-hold
 *       ticks ({@code GL_LINES}). Both frequency and amplitude scales are logarithmic.</dd>
 *
 *   <dt>{@link com.asteroid.duck.opengl.util.wave.RadialSpectrumAnalyser}</dt>
 *   <dd>Spectrum rendered as a smooth radial shape. Magnitude values from
 *       {@link com.asteroid.duck.opengl.util.audio.analysis.FrequencySink#onSpectrum} displace
 *       ring vertices outward; the shape is drawn as a smooth {@code GL_LINE_LOOP}.</dd>
 *
 *   <dt>{@link com.asteroid.duck.opengl.util.wave.FrequencyRenderer}</dt>
 *   <dd>Abstract base for both spectrum renderers. Implements
 *       {@link com.asteroid.duck.opengl.util.audio.analysis.FrequencySink} and manages the
 *       shared magnitude buffer and peak-hold decay logic.</dd>
 * </dl>
 *
 * <h2>Helper</h2>
 * <dl>
 *   <dt>{@link com.asteroid.duck.opengl.util.wave.AmplitudeFunction}</dt>
 *   <dd>Functional interface for per-vertex amplitude envelopes used by {@code AudioWave} and
 *       {@code RadialWave}.</dd>
 * </dl>
 */
package com.asteroid.duck.opengl.util.wave;
