package io.github.duckasteroid.cthugha.display;

/**
 * Turns wall-clock frame times into a whole number of simulation steps per displayed frame, so the
 * flame simulation advances at a fixed rate whatever the display's refresh rate.
 *
 * <p>A translate step moves every pixel by a whole map displacement, so steps can't be fractional:
 * the clock accumulates elapsed time × steps-per-second and runs the whole steps that are due.
 * The accumulator starts half a step in, so when the step rate matches the refresh rate, vsync
 * jitter of up to half a frame still yields exactly one step per frame instead of alternating
 * 0 and 2.
 *
 * <p>After a stall the steps that run in one frame are capped at {@code ceil(rate / refresh) + 1};
 * time beyond that is dropped, so a slow frame doesn't turn into a burst of slow catch-up frames.
 */
final class StepClock {

    private static final double INITIAL_PHASE = 0.5;

    private long lastNanos = -1;
    private double accumulated;

    /**
     * Returns how many steps to run for a frame starting at {@code nowNanos}.
     *
     * @param nowNanos       the frame's start time ({@link System#nanoTime()} scale)
     * @param stepsPerSecond the simulation rate
     * @param refreshHz      the display's refresh rate, used to bound catch-up; {@code <= 0} if unknown
     */
    int stepsDue(long nowNanos, double stepsPerSecond, double refreshHz) {
        if (lastNanos < 0) {
            lastNanos = nowNanos;
            accumulated = INITIAL_PHASE;
            return 1;
        }
        double elapsed = (nowNanos - lastNanos) / 1e9;
        lastNanos = nowNanos;
        accumulated += elapsed * stepsPerSecond;
        int due = (int) Math.floor(accumulated);
        accumulated -= due;
        return Math.min(due, maxSteps(stepsPerSecond, refreshHz));
    }

    static int maxSteps(double stepsPerSecond, double refreshHz) {
        double hz = refreshHz > 0 ? refreshHz : 60.0;
        return (int) Math.ceil(stepsPerSecond / hz) + 1;
    }
}
