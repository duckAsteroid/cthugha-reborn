package com.asteroid.duck.opengl.util.audio.beat;

/**
 * Predicts beat times from an onset signal and a tempo estimate, causally: every beat is
 * reported at its <em>predicted</em> time, as the clock reaches it, and never revised — exactly
 * what a live visual would see.
 *
 * <ul>
 *   <li><b>Acquire:</b> given a beat period, pick the phase whose comb of beats over the last
 *       {@value #COMB_SECONDS} s lands on the most onset strength.</li>
 *   <li><b>Track (phase-locked loop):</b> once {@value #WINDOW_FRACTION} of a period has passed
 *       after a predicted beat, find the strongest onset within that distance of it, weighted
 *       towards the prediction. If it stands out, its timing error {@code e} pulls the next beat
 *       ({@code + PHASE_GAIN·e}) and the period ({@code + PERIOD_GAIN·e}); otherwise the
 *       tracker freewheels at the current period, and {@link #lockConfidence()} starts to fall.</li>
 *   <li><b>Re-acquire:</b> on each tempo update, if the estimate has moved more than
 *       {@value #TEMPO_SNAP} (relative), adopt it; then if another phase scores
 *       {@value #SWITCH_RATIO}× better on the comb than the current one (e.g. the tracker had
 *       locked to the off-beat), jump to it.</li>
 * </ul>
 */
final class BeatPhaseTracker {

    static final double COMB_SECONDS = 6;
    static final double WINDOW_FRACTION = 0.2;
    static final double PHASE_GAIN = 0.3;
    static final double PERIOD_GAIN = 0.05;
    static final double TEMPO_SNAP = 0.04;
    static final double SWITCH_RATIO = 1.5;
    /** An onset must be this many times the running mean onset strength to correct the phase. */
    static final double SIGNIFICANCE = 1.5;
    /** Weight of a full-strength kick relative to the mean onset strength. */
    static final double KICK_WEIGHT = 2.0;
    /** Time constant of the running mean onset strength, seconds. */
    static final double MEAN_SECONDS = 2.0;
    /** Missed beats (after the first) over which {@link #lockConfidence()} halves. */
    static final double MISS_HALF_LIFE = 4;

    private final double frameRate;
    private final BeatListener listener;
    private final float[] flux;
    private final float[] kicks;
    /** The frame each {@link #kicks} slot belongs to; a kick may arrive before its frame does. */
    private final long[] kickFrames;
    /** Onset time of frame 0, seconds; frame f is at {@code timeOffset + f / frameRate}. */
    private double timeOffset = Double.NaN;
    private long frames;
    private double meanFlux;

    private double period = Double.NaN;
    private double nextBeat = Double.NaN;
    private double lastBeat = Double.NaN;
    private boolean correctionPending;
    /** Consecutive beats with no significant onset near them. */
    private int misses;

    BeatPhaseTracker(double frameRate, BeatListener listener) {
        this.frameRate = frameRate;
        this.listener = listener;
        int length = (int) Math.ceil(2 * COMB_SECONDS * frameRate);
        this.flux = new float[length];
        this.kicks = new float[length];
        this.kickFrames = new long[length];
        java.util.Arrays.fill(kickFrames, -1);
    }

    /**
     * Add the next onset frame and emit any beats now due.
     * @param time  onset time the frame represents, seconds
     * @param value onset strength
     */
    void addFrame(double time, float value) {
        if (Double.isNaN(timeOffset)) timeOffset = time;
        int slot = (int) (frames % flux.length);
        flux[slot] = value;
        frames++;
        double alpha = 1 / (MEAN_SECONDS * frameRate);
        meanFlux = frames == 1 ? value : (1 - alpha) * meanFlux + alpha * value;

        if (Double.isNaN(nextBeat)) return;
        if (correctionPending && time >= lastBeat + WINDOW_FRACTION * period) {
            correct();
            correctionPending = false;
        }
        while (time >= nextBeat) {
            listener.onBeat(nextBeat);
            lastBeat = nextBeat;
            nextBeat += period;
            correctionPending = true;
        }
    }

    /**
     * Record a kick onset (added to the onset strength at its frame). Kicks are usually detected
     * before the flux frame for their time has been produced, so the frame may still be ahead.
     * @param time     kick time, seconds
     * @param strength kick strength in {@code [0, 1]}
     */
    void addKick(double time, float strength) {
        if (Double.isNaN(timeOffset)) return;
        long frame = frameAt(time);
        if (frame < 0 || Math.abs(frames - frame) >= kicks.length / 2) return;
        int slot = (int) (frame % kicks.length);
        if (kickFrames[slot] != frame) {
            kickFrames[slot] = frame;
            kicks[slot] = 0;
        }
        kicks[slot] = Math.max(kicks[slot], strength);
    }

    /**
     * A new tempo estimate: adopt it if it has moved, then re-check the phase.
     * @param bpm  estimated tempo
     * @param time current time, seconds
     */
    void onTempo(double bpm, double time) {
        double estimate = 60 / bpm;
        if (Double.isNaN(period) || Math.abs(estimate - period) > TEMPO_SNAP * period) period = estimate;
        if (Double.isNaN(nextBeat)) {
            nextBeat = time + phaseAfter(bestPhase(), time);
            return;
        }
        double best = bestPhase();
        double current = phaseOf(nextBeat);
        if (combScore(best) > SWITCH_RATIO * combScore(current)) {
            nextBeat = time + phaseAfter(best, time);
            correctionPending = false;
        }
    }

    /**
     * How well the predicted beats are still landing on onsets: 1 while they are, falling as
     * consecutive beats pass with nothing significant near them (a breakdown, or the track
     * changed) — down to ~0.5 after {@value #MISS_HALF_LIFE} missed beats.
     * @return lock confidence in {@code (0, 1]}
     */
    float lockConfidence() {
        return (float) Math.pow(0.5, Math.max(0, misses - 1) / (double) MISS_HALF_LIFE);
    }

    /** Forget the tempo and phase (e.g. the audio went silent); re-acquire from scratch. */
    void reset() {
        period = Double.NaN;
        nextBeat = Double.NaN;
        lastBeat = Double.NaN;
        correctionPending = false;
        misses = 0;
    }

    /** @return the current beat period in seconds, or {@code NaN} before the first tempo */
    double period() {
        return period;
    }

    private void correct() {
        double radius = WINDOW_FRACTION * period;
        double bestScore = 0;
        double bestTime = Double.NaN;
        double bestStrength = 0;
        for (long f = frameAt(lastBeat - radius); f <= frameAt(lastBeat + radius); f++) {
            double strength = onsetAt(f);
            double offset = (timeOf(f) - lastBeat) / (radius / 2);
            double score = strength * Math.exp(-0.5 * offset * offset);
            if (score > bestScore) {
                bestScore = score;
                bestTime = timeOf(f);
                bestStrength = strength;
            }
        }
        if (Double.isNaN(bestTime) || bestStrength < SIGNIFICANCE) {
            misses++;
            return;
        }
        misses = 0;
        double error = bestTime - lastBeat;
        nextBeat += PHASE_GAIN * error;
        period += PERIOD_GAIN * error;
    }

    /** Phase in {@code [0, period)}, measured back from the latest frame, of the best comb. */
    private double bestPhase() {
        double best = 0;
        double bestScore = -1;
        int steps = Math.max(1, (int) Math.round(period * frameRate));
        for (int step = 0; step < steps; step++) {
            double phase = step / frameRate;
            double score = combScore(phase);
            if (score > bestScore) {
                bestScore = score;
                best = phase;
            }
        }
        return best;
    }

    /** Onset strength summed over a comb of beats, {@code phase} back from the latest frame. */
    private double combScore(double phase) {
        double latest = timeOf(frames - 1);
        double score = 0;
        for (double t = latest - phase; t > latest - COMB_SECONDS; t -= period) {
            long f = frameAt(t);
            if (f < 0 || frames - f > flux.length) break;
            score += onsetAt(f);
        }
        return score;
    }

    /** How far back from the latest frame a beat at {@code beatTime} (± periods) falls. */
    private double phaseOf(double beatTime) {
        double latest = timeOf(frames - 1);
        double back = (latest - beatTime) % period;
        return back < 0 ? back + period : back;
    }

    /** Time from {@code now} to the next beat of the comb with the given phase. */
    private double phaseAfter(double phase, double now) {
        double latest = timeOf(frames - 1);
        double beat = latest - phase;
        while (beat <= now) beat += period;
        return beat - now;
    }

    /** Onset strength at a frame, relative to the running mean, plus any kick there. */
    private double onsetAt(long frame) {
        if (frame < 0 || frame >= frames || frames - frame > flux.length) return 0;
        int slot = (int) (frame % flux.length);
        float kick = kickFrames[slot] == frame ? kicks[slot] : 0;
        return flux[slot] / Math.max(meanFlux, 1e-9) + KICK_WEIGHT * kick;
    }

    private long frameAt(double time) {
        return Math.round((time - timeOffset) * frameRate);
    }

    private double timeOf(long frame) {
        return timeOffset + frame / frameRate;
    }
}
