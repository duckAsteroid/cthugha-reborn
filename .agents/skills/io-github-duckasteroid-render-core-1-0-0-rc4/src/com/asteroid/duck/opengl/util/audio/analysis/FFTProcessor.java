package com.asteroid.duck.opengl.util.audio.analysis;

import org.jtransforms.fft.FloatFFT_1D;

/**
 * Converts a window of raw audio samples into a log-frequency, log-amplitude (dB) spectrum.
 *
 * <h2>Pipeline per call to {@link #process}</h2>
 * <ol>
 *   <li>Apply a pre-computed Hann window to suppress spectral leakage.</li>
 *   <li>Run an in-place real FFT via JTransforms {@link FloatFFT_1D#realForward}.</li>
 *   <li>Map the {@code fftSize/2} linear bins to {@code numBins} output bars using a
 *       logarithmic frequency scale ({@code fMin}–{@code fMax} Hz).  Each bar takes the
 *       peak magnitude of the FFT bins that fall within its frequency range.</li>
 *   <li>Convert to dB ({@code 20 * log10(magnitude)}) and normalise to [0, 1] between
 *       {@code dBFloor} and {@code dBCeiling}.</li>
 * </ol>
 *
 * <p>All expensive objects (FFT engine, Hann coefficients, bin-range mapping) are pre-computed
 * at construction time.  {@link #process} allocates nothing and is safe to call on the render
 * thread at 60 fps.</p>
 *
 * <p>{@link #process} also retains the per-bin normalised dB magnitude before the log-frequency
 * bucketing step, available via {@link #getRawMagnitudes()}. Consumers that need finer frequency
 * resolution than {@code numBins} affords — e.g. {@link BeatDetector} — should read from there
 * instead of the coarsened {@code output} bars.</p>
 */
public class FFTProcessor {

    private final int   fftSize;
    private final int   numBins;
    private final float sampleRate;
    private final float fMin;
    private final float fMax;

    /** Pre-computed Hann window coefficients, length == fftSize. */
    private final float[] window;

    /** Reusable FFT work buffer — must not be shared across threads. */
    private final float[] workBuffer;

    /**
     * Per-bin normalised dB magnitude from the most recent {@link #process} call, indexed by raw
     * linear FFT bin (length {@code fftSize/2 + 1}; index 0 is the unused DC bin). Populated once
     * per call and reused both to build the log-binned {@code output} bars and to serve raw-bin
     * consumers via {@link #getRawMagnitudes()} — see {@link FrequencySink#onRawSpectrum}.
     */
    private final float[] rawMagnitudes;

    /**
     * Inclusive lower FFT bin index for each output bar.
     * Bins below this index are not included in the bar's magnitude.
     */
    private final int[] binLow;

    /**
     * Exclusive upper FFT bin index for each output bar.
     * Bins at or above this index are not included.
     */
    private final int[] binHigh;

    private final float dBFloor;
    private final float dBRange;

    private final FloatFFT_1D fft;

    /**
     * Construct an FFT processor with the given parameters.
     *
     * @param fftSize    number of samples consumed per FFT call; must be a power of two
     * @param numBins    number of output frequency bars (independent of fftSize)
     * @param sampleRate audio sample rate in Hz (e.g. 48 000)
     * @param fMin       lower frequency bound for the first bar, in Hz (e.g. 20)
     * @param fMax       upper frequency bound for the last bar, in Hz (e.g. 20 000)
     * @param dBFloor    dB level mapped to output 0.0 (e.g. −80)
     * @param dBCeiling  dB level mapped to output 1.0 (e.g. 0)
     */
    public FFTProcessor(int fftSize, int numBins, float sampleRate,
                        float fMin, float fMax, float dBFloor, float dBCeiling) {
        this.fftSize    = fftSize;
        this.numBins    = numBins;
        this.sampleRate = sampleRate;
        this.fMin    = fMin;
        this.fMax    = fMax;
        this.dBFloor = dBFloor;
        this.dBRange = dBCeiling - dBFloor;

        this.fft = new FloatFFT_1D(fftSize);
        this.workBuffer = new float[fftSize];
        this.rawMagnitudes = new float[fftSize / 2 + 1];

        // Hann window: w[i] = 0.5 * (1 - cos(2π i / (N-1)))
        this.window = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            window[i] = 0.5f * (1.0f - (float) Math.cos(2.0 * Math.PI * i / (fftSize - 1)));
        }

        // Pre-compute log-frequency bin mapping: bar i covers [fLow_i, fHigh_i) Hz,
        // which corresponds to linear FFT bins [binLow[i], binHigh[i]).
        this.binLow  = new int[numBins];
        this.binHigh = new int[numBins];
        int nyquistBin = fftSize / 2;
        for (int i = 0; i < numBins; i++) {
            double fLow  = fMin * Math.pow((double) fMax / fMin, (double)  i      / numBins);
            double fHigh = fMin * Math.pow((double) fMax / fMin, (double) (i + 1) / numBins);
            int lo = Math.max(1, (int) Math.round(fLow  * fftSize / sampleRate));
            int hi = Math.max(lo + 1, (int) Math.round(fHigh * fftSize / sampleRate));
            binLow[i]  = Math.min(lo, nyquistBin - 1);
            binHigh[i] = Math.min(hi, nyquistBin);
        }
    }

    /**
     * Process a window of audio samples and write normalised dB magnitudes into {@code output}.
     *
     * <p>The caller must ensure {@code samples.length >= fftSize}; only the first
     * {@code fftSize} elements are read (oldest sample first, i.e. chronological order).</p>
     *
     * @param samples input samples in [−1, 1], oldest first; length >= fftSize
     * @param output  destination array of length {@link #getNumBins()}; values written in [0, 1]
     */
    public void process(float[] samples, float[] output) {
        // Apply Hann window to reduce spectral leakage
        for (int i = 0; i < fftSize; i++) {
            workBuffer[i] = samples[i] * window[i];
        }

        // In-place real FFT.
        // JTransforms realForward output layout for even N:
        //   workBuffer[0]    = Re[0]    (DC)
        //   workBuffer[1]    = Re[N/2]  (Nyquist)
        //   workBuffer[2*k]  = Re[k]    for k = 1 .. N/2-1
        //   workBuffer[2*k+1]= Im[k]    for k = 1 .. N/2-1
        fft.realForward(workBuffer);

        // Normalised dB magnitude per raw linear FFT bin — computed once and shared by the
        // log-binned bars below and by raw-bin consumers (see getRawMagnitudes()).
        int nyquistBin = fftSize / 2;
        for (int k = 1; k <= nyquistBin; k++) {
            float re, im;
            if (k == nyquistBin) {
                re = workBuffer[1];
                im = 0.0f;
            } else {
                re = workBuffer[2 * k];
                im = workBuffer[2 * k + 1];
            }
            float mag = (float) Math.sqrt(re * re + im * im) / fftSize;
            float dB = 20.0f * (float) Math.log10(Math.max(mag, 1e-10f));
            rawMagnitudes[k] = Math.max(0.0f, Math.min(1.0f, (dB - dBFloor) / dBRange));
        }

        for (int b = 0; b < numBins; b++) {
            float peakMag = 0.0f;
            for (int k = binLow[b]; k < binHigh[b]; k++) {
                if (rawMagnitudes[k] > peakMag) peakMag = rawMagnitudes[k];
            }
            output[b] = peakMag;
        }
    }

    /**
     * Read-only view of the most recent {@link #process} call's per-bin normalised dB magnitudes,
     * indexed by raw linear FFT bin — <em>not</em> the log-binned {@code numBins} bars.
     *
     * <p>Unlike {@code output}, this array is not coarsened onto a display-sized grid, so it does
     * not suffer the low-frequency bin-duplication a small {@code numBins} produces (see
     * {@link BeatDetector}). Values are only valid until the next {@link #process} call; copy out
     * if they need to outlive it. Do not modify.</p>
     *
     * @return array of length {@code fftSize/2 + 1}; index 0 (DC) is always {@code 0}
     */
    public float[] getRawMagnitudes() { return rawMagnitudes; }

    /**
     * Maps a frequency to its nearest raw linear FFT bin index, clamped to the valid range.
     *
     * @param hz frequency in Hz
     * @return bin index in {@code [0, fftSize/2]}
     */
    public int binForFrequency(float hz) {
        int nyquistBin = fftSize / 2;
        int bin = Math.round(hz * fftSize / sampleRate);
        return Math.max(0, Math.min(nyquistBin, bin));
    }

    /**
     * Audio sample rate in Hz, as passed at construction.
     *
     * @return the sample rate
     */
    public float getSampleRate() { return sampleRate; }

    /**
     * Number of audio samples consumed per {@link #process} call.
     *
     * @return FFT window size in samples; always a power of two
     */
    public int getFftSize() { return fftSize; }

    /**
     * Number of output frequency bars written by {@link #process}.
     *
     * @return the bar count passed at construction
     */
    public int getNumBins() { return numBins; }

    /**
     * Lower bound of the displayed frequency range in Hz, as passed at construction.
     *
     * @return the frequency mapped to the first output bar
     */
    public float getFMin() { return fMin; }

    /**
     * Upper bound of the displayed frequency range in Hz, as passed at construction.
     *
     * @return the frequency mapped to the last output bar
     */
    public float getFMax() { return fMax; }
}
