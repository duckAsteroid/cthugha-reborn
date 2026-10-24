package com.asteroid.duck.opengl.util.audio.analysis;

/**
 * Receiver of per-frame FFT magnitude data distributed by {@link FrequencyProcessor}.
 *
 * <p>Two callbacks are available, both optional (default no-op) — implement whichever fits the
 * consumer:</p>
 * <ul>
 *   <li>{@link #onSpectrum} — the display-sized, log-frequency-binned array (e.g. for
 *       {@link com.asteroid.duck.opengl.util.wave.SpectrumAnalyser}). Coarsened onto
 *       {@code numBins} bars, so low frequencies can span very few underlying raw FFT bins.</li>
 *   <li>{@link #onRawSpectrum} — the un-coarsened per-bin magnitude array, indexed by raw linear
 *       FFT bin (e.g. for {@link BeatDetector}). Use this when frequency resolution matters more
 *       than a fixed display grid.</li>
 * </ul>
 *
 * <p>Both arrays are <em>shared</em> across all sinks registered with the same
 * {@link FrequencyProcessor} for that call. Implementations <strong>must not modify</strong>
 * either array. Copy values out (e.g. with {@link System#arraycopy}) if they need to outlive the
 * method call.</p>
 *
 * <p>All calls arrive on the render (GL) thread, from within
 * {@link FrequencyProcessor#process()}, before any dependent {@link com.asteroid.duck.opengl.util.RenderedItem#doRender}
 * invocations in the same frame.</p>
 *
 * @see FrequencyProcessor
 * @see BeatDetector
 */
public interface FrequencySink {

    /**
     * Called once per frame with the latest normalised, log-frequency-binned FFT magnitudes.
     *
     * @param magnitudes read-only array of length equal to the {@code numBins} the
     *                   {@link FrequencyProcessor} was constructed with; values in {@code [0, 1]},
     *                   ordered from lowest to highest frequency bar. Do not modify.
     */
    default void onSpectrum(float[] magnitudes) {}

    /**
     * Called once per frame with the latest normalised FFT magnitudes, indexed by raw linear FFT
     * bin rather than a coarsened display grid.
     *
     * @param rawMagnitudes read-only array of length {@code fftSize/2 + 1}; values in
     *                      {@code [0, 1]}, index 0 is the unused DC bin. Do not modify.
     * @param fftSize       the FFT window size in samples, for converting between bin index and Hz
     * @param sampleRate    the audio sample rate in Hz, for converting between bin index and Hz
     */
    default void onRawSpectrum(float[] rawMagnitudes, int fftSize, float sampleRate) {}
}
