package com.asteroid.duck.opengl.util.audio.beat;

import java.util.Arrays;

/**
 * Detects kick-drum onsets from a low band of the raw signal, in the time domain, so it isn't
 * limited by FFT bin width at low frequencies (a 1024-point FFT at 48 kHz has 47 Hz bins) or by
 * the render frame rate.
 *
 * <h2>Why 80–250 Hz, not the sub-bass</h2>
 * A kick's fundamental (~45–60 Hz) shares the sub-bass with the bass line, which on a loud
 * master is sustained near full scale: there the kick adds barely a dB. What sets a kick apart is
 * its attack — the pitch drop from ~150 Hz and the click — so the default band sits above the
 * sub. Below ~60 Hz there's a second trap: the kick's decaying tail beats against the sub and
 * cancels it, and the rebound ~100 ms later reads as a second onset.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Band-pass (default {@link Settings#DEFAULT}): two Butterworth high-pass and two
 *       low-pass sections (fourth order each side).</li>
 *   <li>Every hop (~{@value #HOP_SECONDS} s) take the band's mean power over the last
 *       {@value #WINDOW_HOPS} hops (~20 ms), in dB. The window must span about a period of the
 *       lowest frequency: over a single 5 ms hop a 45 Hz kick's power swings by many dB with
 *       its phase, and that ripple reads as onsets.</li>
 *   <li>Onset strength = how far that level has risen above its minimum over the previous
 *       {@value #RISE_HOPS} hops, or zero if it fell. A kick rises tens of dB in a few
 *       milliseconds; a sustained bass line barely moves.</li>
 *   <li>Peak-pick, one hop late: the previous hop is a kick if it is a local peak, beats an
 *       adaptive threshold (median + {@code k}·MAD of the last ~{@value #HISTORY_SECONDS} s of
 *       onset strength, and at least a minimum rise), is no more than {@value #GATE_DB} dB
 *       below the recent peak band level and above {@value #FLOOR_DB} dB absolute (so noise in quiet
 *       passages and leakage from the rest of the spectrum don't count), and comes
 *       at least {@value #REFRACTORY_SECONDS} s after the last kick. In practice the minimum
 *       rise decides: onset strength is zero on most hops, so its median and MAD are too.</li>
 * </ol>
 *
 * <p>Known weakness: a bass line with its own sharp attacks in the band (e.g. an off-beat house
 * bass) is reported as kicks too. Telling those apart needs more than a level rise — timbre, or
 * the beat context the tracker will supply.</p>
 *
 * <p>Reports {@link BeatListener#onKick} only. The kick is timed at the start of the rise that
 * peaked, not at the peak, which comes up to a window later.</p>
 */
public final class KickDetector implements BeatAnalyser {

    static final double HOP_SECONDS = 0.005;
    static final int WINDOW_HOPS = 4;
    static final int RISE_HOPS = 4;
    static final double HISTORY_SECONDS = 1.0;
    static final double REFRACTORY_SECONDS = 0.1;
    static final double GATE_DB = 30;
    /** Absolute band level (dBFS power) below which nothing counts as a kick. */
    static final double FLOOR_DB = -60;
    /** How fast the recent-peak band level falls, dB per second. */
    static final double PEAK_FALL_DB_PER_SECOND = 6;
    /** Onset strength (dB of rise) reported as full strength 1.0. */
    static final double FULL_STRENGTH_DB = 24;

    private final float sampleRate;
    private final BeatListener listener;
    private final Settings settings;
    private final Biquad[] filters;
    private final int hop;
    private final double[] levels = new double[RISE_HOPS + 1];
    private final double[] hopPowers = new double[WINDOW_HOPS];
    private final double[] history;
    private final double[] sorted;

    private long samplesConsumed;
    private int inHop;
    private double hopPower;
    private long hops;
    private int historyCount;
    private double peakLevel = Double.NEGATIVE_INFINITY;
    private double previousStrength;
    private double previousLevel;
    private boolean previousRising;
    private double riseStart;
    private double lastKickTime = Double.NEGATIVE_INFINITY;

    /**
     * Tuning.
     * @param lowHz     band-pass lower edge, Hz
     * @param highHz    band-pass upper edge, Hz
     * @param k         MAD multiplier in the adaptive threshold
     * @param minRiseDb minimum rise, in dB, for any kick
     */
    public record Settings(double lowHz, double highHz, double k, double minRiseDb) {
        /** Default tuning. */
        public static final Settings DEFAULT = new Settings(80, 250, 2.0, 10.0);
    }

    /**
     * Detector with {@link Settings#DEFAULT default tuning}.
     * @param sampleRate sample rate of the audio, in Hz
     * @param listener   receives kick onsets
     */
    public KickDetector(float sampleRate, BeatListener listener) {
        this(sampleRate, listener, Settings.DEFAULT);
    }

    /**
     * @param sampleRate sample rate of the audio, in Hz
     * @param listener   receives kick onsets
     * @param settings   tuning
     */
    public KickDetector(float sampleRate, BeatListener listener, Settings settings) {
        this.sampleRate = sampleRate;
        this.listener = listener;
        this.settings = settings;
        this.filters = new Biquad[] {
                Biquad.highPass(settings.lowHz(), sampleRate), Biquad.highPass(settings.lowHz(), sampleRate),
                Biquad.lowPass(settings.highHz(), sampleRate), Biquad.lowPass(settings.highHz(), sampleRate)};
        this.hop = Math.max(1, (int) Math.round(sampleRate * HOP_SECONDS));
        int historyLength = (int) Math.round(HISTORY_SECONDS * sampleRate / hop);
        this.history = new double[historyLength];
        this.sorted = new double[historyLength];
        Arrays.fill(levels, Double.NEGATIVE_INFINITY);
    }

    @Override
    public float getSampleRate() {
        return sampleRate;
    }

    @Override
    public void process(float[] samples, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            double y = samples[i];
            for (Biquad filter : filters) y = filter.process(y);
            hopPower += y * y;
            samplesConsumed++;
            if (++inHop == hop) {
                endHop(hopPower / hop);
                inHop = 0;
                hopPower = 0;
            }
        }
    }

    private void endHop(double power) {
        hopPowers[(int) (hops % WINDOW_HOPS)] = power;
        double windowPower = 0;
        for (double p : hopPowers) windowPower += p;
        double level = 10 * Math.log10(windowPower / WINDOW_HOPS + 1e-12);
        double hopSeconds = hop / (double) sampleRate;
        peakLevel = Math.max(level, peakLevel - PEAK_FALL_DB_PER_SECOND * hopSeconds);

        double floor = Double.POSITIVE_INFINITY;
        for (double l : levels) floor = Math.min(floor, l);
        System.arraycopy(levels, 0, levels, 1, levels.length - 1);
        levels[0] = level;
        double strength = Double.isInfinite(floor) ? 0 : Math.max(0, level - floor);

        // Is the previous hop a peak? (Needs this hop to see it has stopped rising.)
        double now = samplesConsumed / (double) sampleRate;
        double previousTime = now - hopSeconds;
        if (previousRising && strength <= previousStrength
                && previousStrength >= threshold()
                && previousLevel >= Math.max(FLOOR_DB, peakLevel - GATE_DB)
                && riseStart - lastKickTime >= REFRACTORY_SECONDS) {
            lastKickTime = riseStart;
            listener.onKick(riseStart, (float) Math.min(1, previousStrength / FULL_STRENGTH_DB));
        }
        // The rise began during the first hop whose strength went up; time it at that hop's start.
        if (strength > previousStrength && !previousRising) riseStart = now - 2 * hopSeconds;

        history[(int) (hops % history.length)] = strength;
        historyCount = Math.min(historyCount + 1, history.length);
        hops++;
        previousRising = strength > previousStrength;
        previousStrength = strength;
        previousLevel = level;
    }

    /** Median + k·MAD of recent onset strength, never below {@code minRiseDb}. */
    private double threshold() {
        if (historyCount == 0) return settings.minRiseDb();
        System.arraycopy(history, 0, sorted, 0, historyCount);
        Arrays.sort(sorted, 0, historyCount);
        double median = sorted[historyCount / 2];
        for (int i = 0; i < historyCount; i++) sorted[i] = Math.abs(history[i] - median);
        Arrays.sort(sorted, 0, historyCount);
        double mad = sorted[historyCount / 2];
        return Math.max(settings.minRiseDb(), median + settings.k() * mad);
    }
}
