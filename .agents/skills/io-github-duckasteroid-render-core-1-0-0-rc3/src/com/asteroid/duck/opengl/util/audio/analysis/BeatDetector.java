package com.asteroid.duck.opengl.util.audio.analysis;

import java.util.List;

/**
 * Per-frame beat detector that operates on the {@code float[] magnitudes} output of
 * {@link FFTProcessor} — no extra FFT cost.
 *
 * <h2>Algorithm (per band, per frame)</h2>
 * <ol>
 *   <li>Compute the mean magnitude over the band's mapped FFT output bins → instant energy.</li>
 *   <li>Update a rolling history of length {@code historyLength} frames.</li>
 *   <li>Compute {@code ratio = instantEnergy / (rollingAverage + ε)}.</li>
 *   <li>If {@code ratio > threshold}: {@code raw = clamp((ratio − threshold) × sensitivity, 0, 1)}.
 *       </li>
 *   <li>Published beat strength rises instantly to {@code raw} if higher; otherwise decays by
 *       {@code decayPerFrame} — same peak-hold pattern as {@link com.asteroid.duck.opengl.util.wave.SpectrumAnalyser}.</li>
 * </ol>
 *
 * <h2>Constructing with custom bands</h2>
 * <pre>{@code
 * List<FrequencyBand> bands = List.of(
 *     new FrequencyBand("sub",  20f,   80f),
 *     new FrequencyBand("kick", 80f,  200f),
 *     FrequencyBand.HI_HAT
 * );
 * BeatDetector beats = new BeatDetector(
 *     bands, numBins, 20f, 20_000f,
 *     43,    // history: ~0.7 s at 60 fps
 *     1.3f,  // threshold: 30% above average before triggering
 *     2.0f,  // sensitivity: 50% above average → strength 1.0
 *     1f/60f // decay: full scale falls to zero in one second
 * );
 * }</pre>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * BeatDetector beats = new BeatDetector(numBins, 20f, 20_000f);
 * // each frame, after fftProcessor.process(samples, magnitudes):
 * beats.update(magnitudes);
 * float kick  = beats.getBeatStrength("bass");
 * float snare = beats.getBeatStrength(1);
 * }</pre>
 */
public class BeatDetector implements FrequencySink {

    private static final float EPSILON = 1e-10f;

    private final List<FrequencyBand> bands;
    private final BandState[] states;
    private final float threshold;
    private final float sensitivity;
    private final float decayPerFrame;

    /**
     * Full constructor.
     *
     * @param bands         frequency bands to track; arbitrary size and Hz ranges
     * @param numBins       number of output bars from {@link FFTProcessor} (e.g. 128)
     * @param fftFMin       lowest frequency in the FFTProcessor's output range in Hz (e.g. 20)
     * @param fftFMax       highest frequency in the FFTProcessor's output range in Hz (e.g. 20 000)
     * @param historyLength number of past frames used for the rolling energy average
     * @param threshold     ratio above the rolling average required to start triggering
     *                      (e.g. {@code 1.3} = 30% louder than average)
     * @param sensitivity   scales {@code (ratio − threshold)} to {@code [0, 1]}; higher values
     *                      reach full strength at a lower peak
     *                      (e.g. {@code 2.0} → 50% above average = strength 1.0)
     * @param decayPerFrame per-frame fall rate of the published beat strength;
     *                      {@code 1.0 / 60} causes full scale to decay to zero in one second at 60 fps
     */
    public BeatDetector(List<FrequencyBand> bands, int numBins, float fftFMin, float fftFMax,
                        int historyLength, float threshold, float sensitivity, float decayPerFrame) {
        this.bands = List.copyOf(bands);
        this.threshold = threshold;
        this.sensitivity = sensitivity;
        this.decayPerFrame = decayPerFrame;
        this.states = new BandState[bands.size()];
        for (int i = 0; i < bands.size(); i++) {
            states[i] = new BandState(bands.get(i), numBins, fftFMin, fftFMax, historyLength);
        }
    }

    /**
     * Convenience constructor using {@link FrequencyBand#defaults()} (bass / snare / hi-hat)
     * and sensible defaults: 43-frame history (~0.7 s at 60 fps), threshold 1.3, sensitivity 2.0,
     * decay 1/60 per frame.
     *
     * @param numBins  number of output bars from {@link FFTProcessor}
     * @param fftFMin  lowest Hz of the FFTProcessor's output range
     * @param fftFMax  highest Hz of the FFTProcessor's output range
     */
    public BeatDetector(int numBins, float fftFMin, float fftFMax) {
        this(FrequencyBand.defaults(), numBins, fftFMin, fftFMax, 43, 1.3f, 2.0f, 1.0f / 60f);
    }

    /**
     * Convenience constructor that derives geometry directly from a {@link FrequencyProcessor},
     * using {@link FrequencyBand#defaults()} and sensible defaults for all tuning parameters.
     *
     * <p>The caller is still responsible for registering this detector as a sink:
     * {@code processor.addSink(detector)}.</p>
     *
     * @param processor the {@link FrequencyProcessor} this detector will consume from
     */
    public BeatDetector(FrequencyProcessor processor) {
        this(FrequencyBand.defaults(),
             processor.getNumBins(), processor.getFMin(), processor.getFMax(),
             43, 1.3f, 2.0f, 1.0f / 60f);
    }

    /**
     * Convenience constructor with custom bands, deriving geometry from a {@link FrequencyProcessor}.
     *
     * @param bands     frequency bands to track
     * @param processor the {@link FrequencyProcessor} this detector will consume from
     */
    public BeatDetector(List<FrequencyBand> bands, FrequencyProcessor processor) {
        this(bands,
             processor.getNumBins(), processor.getFMin(), processor.getFMax(),
             43, 1.3f, 2.0f, 1.0f / 60f);
    }

    /**
     * {@link FrequencySink} implementation — delegates to {@link #update}.
     * Called automatically by {@link FrequencyProcessor#process} when this detector is registered
     * as a sink; do not call directly.
     */
    @Override
    public void onSpectrum(float[] magnitudes) {
        update(magnitudes);
    }

    /**
     * Update all bands from the latest FFT output.
     * Call once per frame, immediately after {@link FFTProcessor#process}.
     * When using a {@link FrequencyProcessor}, prefer registering via {@link FrequencyProcessor#addSink}
     * and let {@link FrequencyProcessor#process} drive this automatically.
     *
     * @param magnitudes normalised magnitude array from {@link FFTProcessor#process};
     *                   must have length {@code >= numBins} passed at construction
     */
    public void update(float[] magnitudes) {
        for (BandState state : states) {
            state.update(magnitudes, threshold, sensitivity, decayPerFrame);
        }
    }

    /**
     * Beat strength for the band at position {@code index} in the list passed at construction.
     *
     * @param index band index in [0, numBands)
     * @return beat strength in [0, 1]; 0 = at or below average, 1 = at or above peak threshold
     */
    public float getBeatStrength(int index) {
        return states[index].beatStrength;
    }

    /**
     * Beat strength for the named band.
     *
     * @param name band name as given in the {@link FrequencyBand} record
     * @return beat strength in [0, 1]
     * @throws IllegalArgumentException if no band with that name was registered
     */
    public float getBeatStrength(String name) {
        for (BandState state : states) {
            if (state.band.name().equals(name)) return state.beatStrength;
        }
        throw new IllegalArgumentException("No band named '" + name + "'");
    }

    /**
     * Snapshot of all band beat strengths in declaration order.
     * Each value is in [0, 1].
     */
    public float[] getBeatStrengths() {
        float[] result = new float[states.length];
        for (int i = 0; i < states.length; i++) {
            result[i] = states[i].beatStrength;
        }
        return result;
    }

    /** The ordered list of bands this detector monitors. */
    public List<FrequencyBand> getBands() {
        return bands;
    }

    /** Number of bands. */
    public int getBandCount() {
        return states.length;
    }

    // ── Per-band state ───────────────────────────────────────────────────────────

    private static final class BandState {
        final FrequencyBand band;
        final int binLow;
        final int binHigh;
        final float[] energyHistory;
        int historyPos;
        float historySum;
        float beatStrength;

        BandState(FrequencyBand band, int numBins, float fftFMin, float fftFMax, int historyLength) {
            this.band = band;
            this.energyHistory = new float[historyLength];
            int[] range = computeBinRange(band, numBins, fftFMin, fftFMax);
            this.binLow  = range[0];
            this.binHigh = range[1];
        }

        void update(float[] magnitudes, float threshold, float sensitivity, float decayPerFrame) {
            float energy = 0;
            int width = binHigh - binLow;
            if (width > 0) {
                for (int k = binLow; k < binHigh; k++) {
                    energy += magnitudes[k];
                }
                energy /= width;
            }

            // O(1) rolling history update via running sum
            historySum -= energyHistory[historyPos];
            energyHistory[historyPos] = energy;
            historySum += energy;
            historyPos = (historyPos + 1) % energyHistory.length;

            float avg = historySum / energyHistory.length;
            float ratio = energy / (avg + EPSILON);
            float raw = (ratio > threshold)
                    ? Math.min(1.0f, (ratio - threshold) * sensitivity)
                    : 0.0f;

            // Rise instantly to peak; decay at decayPerFrame
            beatStrength = Math.max(raw, beatStrength - decayPerFrame);
        }
    }

    /**
     * Map a frequency band's Hz range to [binLow, binHigh) indices in the FFTProcessor output,
     * using the same log-frequency scale that FFTProcessor uses internally.
     * Returns {0, 0} if the band has no overlap with the FFT's output range.
     */
    public static int[] computeBinRange(FrequencyBand band, int numBins, float fftFMin, float fftFMax) {
        if (band.fMin() >= fftFMax || band.fMax() <= fftFMin) {
            return new int[]{0, 0};
        }
        double logScale = Math.log((double) fftFMax / fftFMin);
        double effMin = Math.max(band.fMin(), fftFMin);
        double effMax = Math.min(band.fMax(), fftFMax);
        int lo = (int) Math.floor(numBins * Math.log(effMin / fftFMin) / logScale);
        // floor for hi keeps adjacent bands exactly contiguous (no 1-bin overlap at boundaries)
        int hi = (int) Math.floor(numBins * Math.log(effMax / fftFMin) / logScale);
        lo = Math.max(0, lo);
        hi = Math.min(numBins, Math.max(lo + 1, hi));
        return new int[]{lo, hi};
    }
}
