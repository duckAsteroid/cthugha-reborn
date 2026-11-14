package com.asteroid.duck.opengl.util.audio.beat;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Estimates the tempo from a fixed-rate onset-strength signal (e.g. {@link SpectralFlux}).
 *
 * <p>On each {@link #estimate()}: autocorrelate the last {@value #WINDOW_SECONDS} s of onset
 * strength (mean removed); score each candidate beat period {@code L} as
 * {@code acf(L) + ½·acf(2L) + ¼·acf(4L)}, so a period whose two-beat and bar multiples also
 * repeat beats one that only repeats itself; weight by a log-normal tempo prior; then fold into
 * a running average of scores, which gives hysteresis — one odd window doesn't flip the tempo.
 * The winning period is refined by parabolic interpolation.</p>
 *
 * <p>The autocorrelation is computed by FFT (zero-padded, so it is linear, not circular): the
 * direct sum took ~2 ms every update, too long a stall for the audio thread.</p>
 */
public final class TempoEstimator {

    static final double WINDOW_SECONDS = 8;
    static final double MIN_SECONDS = 3;
    static final double MIN_BPM = 60;
    static final double MAX_BPM = 200;
    /** Exponential smoothing weight given to each new score vector. */
    static final double SMOOTHING = 0.3;

    private final double frameRate;
    private final double priorBpm;
    private final double priorOctaves;
    private final float[] ring;
    private final double[] frame;
    private final double[] padded;
    private final DoubleFFT_1D fft;
    private final double[] acf;
    private final double[] smoothed;
    private final int minLag;
    private final int maxLag;

    private long count;
    private double bpm = Double.NaN;
    private float confidence;

    /**
     * @param frameRate    onset frames per second
     * @param priorBpm     centre of the tempo prior
     * @param priorOctaves width (standard deviation, in octaves) of the tempo prior
     */
    public TempoEstimator(double frameRate, double priorBpm, double priorOctaves) {
        this.frameRate = frameRate;
        this.priorBpm = priorBpm;
        this.priorOctaves = priorOctaves;
        int window = (int) Math.round(WINDOW_SECONDS * frameRate);
        this.ring = new float[window];
        this.frame = new double[window];
        int size = Integer.highestOneBit(2 * window - 1) << 1;
        this.padded = new double[size];
        this.fft = new DoubleFFT_1D(size);
        this.minLag = (int) Math.floor(60 * frameRate / MAX_BPM);
        this.maxLag = (int) Math.ceil(60 * frameRate / MIN_BPM);
        this.acf = new double[Math.min(window, 4 * maxLag + 1)];
        this.smoothed = new double[maxLag + 2];
    }

    /**
     * @param value the next onset-strength frame
     */
    public void add(float value) {
        ring[(int) (count++ % ring.length)] = value;
    }

    /**
     * Re-estimate from the frames added so far. Does nothing until {@value #MIN_SECONDS} s of
     * frames have arrived.
     * @return {@code true} if an estimate is available
     */
    public boolean estimate() {
        int n = (int) Math.min(count, ring.length);
        if (n < MIN_SECONDS * frameRate) return false;
        int start = (int) (count - n);
        double mean = 0;
        for (int i = 0; i < n; i++) mean += frame[i] = ring[(int) ((start + i) % ring.length)];
        mean /= n;
        for (int i = 0; i < n; i++) frame[i] -= mean;
        int lags = Math.min(acf.length, n);
        autocorrelate(n, lags);
        if (acf[0] <= 0) return !Double.isNaN(bpm);

        for (int lag = minLag; lag <= maxLag; lag++) {
            double score = acf[lag] + 0.5 * at(2 * lag, lags) + 0.25 * at(4 * lag, lags);
            double octaves = Math.log(60 * frameRate / lag / priorBpm) / Math.log(2) / priorOctaves;
            score = Math.max(0, score / acf[0]) * Math.exp(-0.5 * octaves * octaves);
            smoothed[lag] = (1 - SMOOTHING) * smoothed[lag] + SMOOTHING * score;
        }

        int best = minLag;
        double total = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            total += smoothed[lag];
            if (smoothed[lag] > smoothed[best]) best = lag;
        }
        if (smoothed[best] <= 0) return !Double.isNaN(bpm);
        double offset = 0;
        if (best > minLag && best < maxLag) {
            double a = smoothed[best - 1];
            double b = smoothed[best];
            double c = smoothed[best + 1];
            double denominator = a - 2 * b + c;
            if (denominator < 0) offset = Math.clamp(0.5 * (a - c) / denominator, -0.5, 0.5);
        }
        bpm = 60 * frameRate / (best + offset);
        double average = total / (maxLag - minLag + 1);
        confidence = (float) Math.clamp((smoothed[best] - average) / smoothed[best], 0, 1);
        return true;
    }

    /** {@code acf[lag] = Σ frame[i]·frame[i+lag] / (n − lag)} for {@code lag < lags}, via FFT. */
    private void autocorrelate(int n, int lags) {
        java.util.Arrays.fill(padded, 0);
        System.arraycopy(frame, 0, padded, 0, n);
        fft.realForward(padded);
        // realForward layout: [Re0, Re(N/2), Re1, Im1, Re2, Im2, ...] → power spectrum in place
        padded[0] *= padded[0];
        padded[1] *= padded[1];
        for (int k = 2; k < padded.length; k += 2) {
            padded[k] = padded[k] * padded[k] + padded[k + 1] * padded[k + 1];
            padded[k + 1] = 0;
        }
        fft.realInverse(padded, true);
        for (int lag = 0; lag < lags; lag++) acf[lag] = padded[lag] / (n - lag);
    }

    /** Forget everything heard so far (e.g. the audio went silent). */
    public void reset() {
        count = 0;
        java.util.Arrays.fill(smoothed, 0);
        bpm = Double.NaN;
        confidence = 0;
    }

    private double at(int lag, int lags) {
        return lag < lags ? acf[lag] : 0;
    }

    /** @return the current tempo estimate in BPM, or {@code NaN} before the first */
    public double bpm() {
        return bpm;
    }

    /** @return how clearly the winning tempo stands out, in {@code [0, 1]} */
    public float confidence() {
        return confidence;
    }
}
