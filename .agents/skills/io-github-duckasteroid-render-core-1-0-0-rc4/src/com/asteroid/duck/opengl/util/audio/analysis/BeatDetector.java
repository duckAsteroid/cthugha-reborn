package com.asteroid.duck.opengl.util.audio.analysis;

import java.util.List;

/**
 * Per-frame beat detector that operates on {@link FFTProcessor}'s raw, per-bin FFT magnitudes —
 * <em>before</em> they are coarsened onto a display-sized {@code numBins} grid — so it costs no
 * extra FFT work and is immune to the low-frequency bin-duplication a small {@code numBins} would
 * otherwise introduce (a band like "bass" spanning only a handful of display bars can end up
 * driven by a single raw FFT bin, right where mic self-noise and room rumble live).
 *
 * <h2>Algorithm (per band, per frame)</h2>
 * <ol>
 *   <li>Compute the mean magnitude over the band's raw FFT bin range → instant energy.</li>
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
 *     bands, fftSize, sampleRate,
 *     43,    // history: ~0.7 s at 60 fps
 *     1.3f,  // threshold: 30% above average before triggering
 *     2.0f,  // sensitivity: 50% above average → strength 1.0
 *     1f/60f // decay: full scale falls to zero in one second
 * );
 * }</pre>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * BeatDetector beats = new BeatDetector(freqProc);
 * freqProc.addSink(beats);
 * // each frame, after freqProc.process():
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
     * @param fftSize       the FFT window size in samples (e.g. from {@link FrequencyProcessor#getFftSize()})
     * @param sampleRate    the audio sample rate in Hz (e.g. from {@link FrequencyProcessor#getSampleRate()})
     * @param historyLength number of past frames used for the rolling energy average
     * @param threshold     ratio above the rolling average required to start triggering
     *                      (e.g. {@code 1.3} = 30% louder than average)
     * @param sensitivity   scales {@code (ratio − threshold)} to {@code [0, 1]}; higher values
     *                      reach full strength at a lower peak
     *                      (e.g. {@code 2.0} → 50% above average = strength 1.0)
     * @param decayPerFrame per-frame fall rate of the published beat strength;
     *                      {@code 1.0 / 60} causes full scale to decay to zero in one second at 60 fps
     */
    public BeatDetector(List<FrequencyBand> bands, int fftSize, float sampleRate,
                        int historyLength, float threshold, float sensitivity, float decayPerFrame) {
        this.bands = List.copyOf(bands);
        this.threshold = threshold;
        this.sensitivity = sensitivity;
        this.decayPerFrame = decayPerFrame;
        this.states = new BandState[bands.size()];
        for (int i = 0; i < bands.size(); i++) {
            states[i] = new BandState(bands.get(i), fftSize, sampleRate, historyLength);
        }
    }

    /**
     * Convenience constructor using {@link FrequencyBand#defaults()} (bass / snare / hi-hat)
     * and sensible defaults: 43-frame history (~0.7 s at 60 fps), threshold 1.3, sensitivity 2.0,
     * decay 1/60 per frame.
     *
     * @param fftSize    the FFT window size in samples
     * @param sampleRate the audio sample rate in Hz
     */
    public BeatDetector(int fftSize, float sampleRate) {
        this(FrequencyBand.defaults(), fftSize, sampleRate, 43, 1.3f, 2.0f, 1.0f / 60f);
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
             processor.getFftSize(), processor.getSampleRate(),
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
             processor.getFftSize(), processor.getSampleRate(),
             43, 1.3f, 2.0f, 1.0f / 60f);
    }

    /**
     * {@link FrequencySink} implementation — delegates to {@link #update}.
     * Called automatically by {@link FrequencyProcessor#process} when this detector is registered
     * as a sink; do not call directly.
     */
    @Override
    public void onRawSpectrum(float[] rawMagnitudes, int fftSize, float sampleRate) {
        update(rawMagnitudes);
    }

    /**
     * Update all bands from the latest raw FFT magnitudes.
     * Call once per frame, immediately after {@link FFTProcessor#process}, passing
     * {@link FFTProcessor#getRawMagnitudes()}.
     * When using a {@link FrequencyProcessor}, prefer registering via {@link FrequencyProcessor#addSink}
     * and let {@link FrequencyProcessor#process} drive this automatically.
     *
     * @param rawMagnitudes normalised per-bin magnitude array from {@link FFTProcessor#getRawMagnitudes()};
     *                      must have length {@code >= fftSize/2 + 1} passed at construction
     */
    public void update(float[] rawMagnitudes) {
        for (BandState state : states) {
            state.update(rawMagnitudes, threshold, sensitivity, decayPerFrame);
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

        BandState(FrequencyBand band, int fftSize, float sampleRate, int historyLength) {
            this.band = band;
            this.energyHistory = new float[historyLength];
            int[] range = computeBinRange(band, fftSize, sampleRate);
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
     * Map a frequency band's Hz range to {@code [binLow, binHigh)} indices into the raw linear
     * FFT bin array (see {@link FFTProcessor#getRawMagnitudes()}).
     * Returns {@code {0, 0}} if the band has no overlap with the representable frequency range
     * ({@code (0, sampleRate/2]}).
     */
    public static int[] computeBinRange(FrequencyBand band, int fftSize, float sampleRate) {
        int nyquistBin = fftSize / 2;
        float nyquistHz = sampleRate / 2f;
        if (band.fMin() >= nyquistHz || band.fMax() <= 0f) {
            return new int[]{0, 0};
        }
        float effMin = Math.max(band.fMin(), 0f);
        float effMax = Math.min(band.fMax(), nyquistHz);
        int lo = Math.max(1, Math.round(effMin * fftSize / sampleRate));
        int hi = Math.round(effMax * fftSize / sampleRate);
        lo = Math.min(lo, nyquistBin - 1);
        hi = Math.min(Math.max(hi, lo + 1), nyquistBin);
        return new int[]{lo, hi};
    }
}
