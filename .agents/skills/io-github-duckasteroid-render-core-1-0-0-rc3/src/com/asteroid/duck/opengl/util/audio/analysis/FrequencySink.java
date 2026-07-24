package com.asteroid.duck.opengl.util.audio.analysis;

/**
 * Receiver of per-frame FFT magnitude data distributed by {@link FrequencyProcessor}.
 *
 * <p>The {@code magnitudes} array passed to {@link #onSpectrum} is <em>shared</em> across all
 * sinks registered with the same {@link FrequencyProcessor} for that call. Implementations
 * <strong>must not modify</strong> the array. Copy values out (e.g. with
 * {@link System#arraycopy}) if they need to outlive the method call.</p>
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
     * Called once per frame with the latest normalised FFT magnitudes.
     *
     * @param magnitudes read-only array of length equal to the {@code numBins} the
     *                   {@link FrequencyProcessor} was constructed with; values in {@code [0, 1]},
     *                   ordered from lowest to highest frequency bar. Do not modify.
     */
    void onSpectrum(float[] magnitudes);
}
