/**
 * Audio signal-analysis classes: FFT processing, frequency-domain sinks, beat detection.
 *
 * <p>These classes operate entirely on CPU-side audio data and have no OpenGL dependency.
 * They sit between the raw-capture layer ({@link com.asteroid.duck.opengl.util.audio}) and
 * the rendering layer ({@link com.asteroid.duck.opengl.util.wave}).</p>
 *
 * <h2>Classes</h2>
 * <dl>
 *   <dt>{@link com.asteroid.duck.opengl.util.audio.analysis.FFTProcessor}</dt>
 *   <dd>Core FFT pipeline: Hann window → JTransforms real FFT → log-frequency bin mapping →
 *       dB normalisation. Pre-allocates all buffers at construction; {@code process()} is
 *       allocation-free and safe to call at 60 fps on the render thread.</dd>
 *
 *   <dt>{@link com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor}</dt>
 *   <dd>Bridges the raw-audio pipeline to the frequency domain. Implements
 *       {@link com.asteroid.duck.opengl.util.audio.AudioSink} so an
 *       {@link com.asteroid.duck.opengl.util.audio.AudioReader} can write PCM bytes into it.
 *       Call {@code process()} once per frame on the render thread to run the FFT and fan the
 *       normalised magnitudes out to all registered
 *       {@link com.asteroid.duck.opengl.util.audio.analysis.FrequencySink}s — one FFT for any
 *       number of consumers.</dd>
 *
 *   <dt>{@link com.asteroid.duck.opengl.util.audio.analysis.FrequencySink}</dt>
 *   <dd>Callback interface for objects that consume per-frame FFT magnitudes distributed by
 *       {@link com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor}.</dd>
 *
 *   <dt>{@link com.asteroid.duck.opengl.util.audio.analysis.BeatDetector}</dt>
 *   <dd>Per-frame onset detector — implements
 *       {@link com.asteroid.duck.opengl.util.audio.analysis.FrequencySink}.
 *       Tracks energy across configurable
 *       {@link com.asteroid.duck.opengl.util.audio.analysis.FrequencyBand}s
 *       (default: bass / snare / hi-hat) and publishes a {@code [0, 1]} beat strength per band
 *       that rises instantly on onset and decays at a tunable rate.</dd>
 *
 *   <dt>{@link com.asteroid.duck.opengl.util.audio.analysis.FrequencyBand}</dt>
 *   <dd>Immutable record describing a named Hz range. Provides {@code BASS}, {@code SNARE}, and
 *       {@code HI_HAT} presets and a {@code defaults()} factory.</dd>
 * </dl>
 *
 * <h2>Typical wiring</h2>
 * <pre>{@code
 * FrequencyProcessor freqProc = new FrequencyProcessor(
 *     1024, 128, 48_000f, 20f, 20_000f, -80f, 0f);
 *
 * BeatDetector beats = new BeatDetector(freqProc);
 * freqProc.addSink(beats);
 *
 * SpectrumAnalyser analyser = new SpectrumAnalyser(freqProc);
 * freqProc.addSink(analyser);
 *
 * AudioReader audioReader = new AudioReader(List.of(freqProc));
 *
 * // doRender() — one FFT per frame, all sinks notified:
 * freqProc.process();
 * analyser.doRender(ctx);
 * float kick = beats.getBeatStrength("bass");
 * }</pre>
 */
package com.asteroid.duck.opengl.util.audio.analysis;
