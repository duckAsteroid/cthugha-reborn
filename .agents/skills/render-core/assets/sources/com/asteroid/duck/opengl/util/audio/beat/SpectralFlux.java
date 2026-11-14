package com.asteroid.duck.opengl.util.audio.beat;

import org.jtransforms.fft.FloatFFT_1D;

import java.util.ArrayList;
import java.util.List;

/**
 * Broadband onset strength: log-compressed, log-frequency-banded spectral flux at
 * {@value #FRAME_RATE} frames per second — the standard input for tempo and beat estimation
 * (Böck et al.). Any new sound, in any band, makes it jump; sustained sound doesn't.
 *
 * <ol>
 *   <li>Every {@code 1/}{@value #FRAME_RATE} s, Hann-window the last ~{@value #WINDOW_SECONDS}
 *       s of samples and take the magnitude spectrum.</li>
 *   <li>Sum magnitudes into bands {@value #BANDS_PER_OCTAVE} per octave from {@value #MIN_HZ}
 *       Hz to {@value #MAX_HZ} Hz (or Nyquist), and compress: {@code ln(1 + γ·m)}.</li>
 *   <li>Onset strength = sum over bands of the increase since the previous frame (decreases
 *       ignored).</li>
 * </ol>
 */
public final class SpectralFlux {

    /** Receives one onset-strength value per frame. */
    @FunctionalInterface
    public interface Listener {
        /**
         * @param time  seconds on the sample clock at the end of the frame's window
         * @param value onset strength, {@code >= 0}
         */
        void onFrame(double time, float value);
    }

    /** Nominal frames per second; the exact rate depends on the sample rate (see {@link #frameRate()}). */
    public static final int FRAME_RATE = 100;
    static final double WINDOW_SECONDS = 0.043;
    static final double MIN_HZ = 30;
    static final double MAX_HZ = 16_000;
    static final int BANDS_PER_OCTAVE = 3;
    /** Log compression factor. */
    static final double GAMMA = 1000;

    private final float sampleRate;
    private final Listener listener;
    private final int fftSize;
    private final int hop;
    private final FloatFFT_1D fft;
    private final float[] window;
    private final float[] ring;
    private final float[] work;
    private final int[] bandStart;
    private final int[] bandEnd;
    private final double[] bands;
    private final double[] previousBands;

    private int writePos;
    private int sinceHop;
    private long samplesConsumed;
    private boolean primed;

    /**
     * @param sampleRate sample rate of the audio, in Hz
     * @param listener   receives one onset-strength value per frame
     */
    public SpectralFlux(float sampleRate, Listener listener) {
        this.sampleRate = sampleRate;
        this.listener = listener;
        this.fftSize = Integer.highestOneBit((int) Math.round(sampleRate * WINDOW_SECONDS * 1.5));
        this.hop = Math.round(sampleRate / FRAME_RATE);
        this.fft = new FloatFFT_1D(fftSize);
        this.window = new float[fftSize];
        for (int i = 0; i < fftSize; i++) window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / fftSize));
        this.ring = new float[fftSize];
        this.work = new float[fftSize];

        List<int[]> ranges = new ArrayList<>();
        double binHz = sampleRate / fftSize;
        double top = Math.min(MAX_HZ, sampleRate / 2);
        int previousEnd = 1;
        for (double low = MIN_HZ; low < top; low *= Math.pow(2, 1.0 / BANDS_PER_OCTAVE)) {
            double high = Math.min(top, low * Math.pow(2, 1.0 / BANDS_PER_OCTAVE));
            int start = Math.max(previousEnd, (int) Math.round(low / binHz));
            int end = Math.min(fftSize / 2, Math.max(start + 1, (int) Math.round(high / binHz)));
            if (start >= end) continue;
            ranges.add(new int[] {start, end});
            previousEnd = end;
        }
        this.bandStart = ranges.stream().mapToInt(r -> r[0]).toArray();
        this.bandEnd = ranges.stream().mapToInt(r -> r[1]).toArray();
        this.bands = new double[ranges.size()];
        this.previousBands = new double[ranges.size()];
    }

    /**
     * The exact frame rate: the hop is a whole number of samples, so this is only approximately
     * {@value #FRAME_RATE} (at 22.05 kHz the hop is 221 samples: 99.77 frames/s). Use this, not
     * the nominal rate, to convert frame counts to time — the difference drifts by ~2 ms per
     * second.
     * @return frames per second
     */
    public double frameRate() {
        return sampleRate / (double) hop;
    }

    /**
     * The current frame's log-compressed band levels, {@code ln(1 + γ·m)} per band, lowest band
     * first. Valid only during {@link Listener#onFrame}; the array is reused — copy it to keep it,
     * and don't modify it.
     * @return band levels
     */
    public double[] bands() {
        return bands;
    }

    /**
     * @param band band index
     * @return the band's lower edge in Hz
     */
    public double bandLowHz(int band) {
        return bandStart[band] * (double) sampleRate / fftSize;
    }

    /** @return the FFT size chosen for this sample rate */
    int fftSize() {
        return fftSize;
    }

    /**
     * Consume samples, reporting a frame to the listener every {@code 1/}{@value #FRAME_RATE} s.
     * @param samples mono samples in {@code [-1, 1]}
     * @param offset  index of the first sample
     * @param length  number of samples
     */
    public void process(float[] samples, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            ring[writePos] = samples[i];
            writePos = (writePos + 1) % fftSize;
            samplesConsumed++;
            if (++sinceHop == hop) {
                sinceHop = 0;
                frame();
            }
        }
    }

    private void frame() {
        for (int i = 0; i < fftSize; i++) work[i] = ring[(writePos + i) % fftSize] * window[i];
        fft.realForward(work);
        float scale = 2f / fftSize;
        for (int b = 0; b < bands.length; b++) {
            double sum = 0;
            for (int k = bandStart[b]; k < bandEnd[b]; k++) {
                float re = work[2 * k];
                float im = work[2 * k + 1];
                sum += Math.sqrt(re * re + im * im) * scale;
            }
            bands[b] = Math.log1p(GAMMA * sum);
        }
        double flux = 0;
        if (primed) {
            for (int b = 0; b < bands.length; b++) flux += Math.max(0, bands[b] - previousBands[b]);
        }
        System.arraycopy(bands, 0, previousBands, 0, bands.length);
        primed = true;
        listener.onFrame(samplesConsumed / (double) sampleRate, (float) flux);
    }
}
