package com.asteroid.duck.opengl.util.timer;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A mutable, pauseable {@link Clock} backed by an arbitrary {@link TimeSource}.
 *
 * <p>The clock accumulates elapsed time on every call to {@link #update()}, which must be called
 * once per frame from the render loop. Time can be paused ({@link #setPaused(boolean)}), stepped
 * manually ({@link #step(double)}), or reset to zero ({@link #reset()}) at any point.</p>
 *
 * <p>The clock is decoupled from the underlying time source via a {@link Callable}{@code <Double>},
 * which makes it trivially replaceable in tests. Use {@link TimeSource} for the standard
 * GLFW- and system-backed factory methods.</p>
 */
public class ClockImpl implements Clock {

    private final AtomicReference<Double> paused = new AtomicReference<>(null);

    /** A source of timestamps in seconds since some arbitrary epoch. */
    private final Callable<Double> timeSource;

    /**
     * The elapsed time accumulated so far.
     * Grows in {@link #update()} when not paused, or by explicit {@link #step(double)} calls.
     */
    private double elapsed;

    /** The raw timestamp at the last {@link #update()} call. */
    private double lastUpdate;

    private long updateCount;
    private double updateDeltaSum;

    /**
     * Create a clock backed by the given time source and immediately reset it to zero.
     *
     * @param timeSource a {@link Callable} that returns the current timestamp in seconds;
     *                   must not be {@code null}
     */
    public ClockImpl(Callable<Double> timeSource) {
        this.timeSource = timeSource;
        reset();
    }

    /**
     * Advance the clock by the time elapsed since the last call to this method.
     *
     * <p>Must be called once per frame. When the clock is paused the accumulator is not
     * updated, but {@code lastUpdate} still advances so that resuming does not produce a
     * sudden jump.</p>
     *
     * @return the wall-clock delta since the last update in seconds, regardless of pause state
     */
    public double update() {
        double now = now();
        double delta = now - lastUpdate;
        lastUpdate = now;
        updateCount++;
        updateDeltaSum += delta;
        if (paused.get() == null) {
            elapsed += delta;
        }
        return delta;
    }

    /**
     * Capture the current clock state as an immutable {@link StaticClock} snapshot.
     * Useful for passing a frozen view of the clock to code that should not observe
     * further time changes.
     *
     * @return a {@link StaticClock} whose {@code elapsed} and {@code now} match the current values
     */
    public StaticClock snapshot() {
        return new StaticClock(elapsed(), now());
    }

    /**
     * Reset the accumulated elapsed time to zero and restart from the current {@link #now()} value.
     * Any existing pause state is also cleared.
     */
    public void reset() {
        setPaused(false);
        this.lastUpdate = now();
        this.elapsed = 0;
    }

    /**
     * Manually advance or rewind the accumulated elapsed time by {@code stepSize} seconds.
     * Useful when the clock is paused and the user wants to scrub through time frame by frame.
     *
     * @param stepSize the amount to add to elapsed time in seconds; negative values rewind
     */
    public void step(double stepSize) {
        elapsed += stepSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double elapsed() {
        return elapsed;
    }

    /**
     * Query the underlying time source for the current raw timestamp.
     *
     * @return the current timestamp in seconds from the time source
     * @throws RuntimeException wrapping the source exception if the time source call fails,
     *                          or wrapping {@link InterruptedException} if the calling thread
     *                          was interrupted (the interrupt flag is restored)
     */
    public double now() {
        try {
            return timeSource.call();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get time from timeSource", e);
        }
    }

    /**
     * Returns the raw timestamp recorded during the most recent {@link #update()} call.
     *
     * @return the timestamp of the last update in seconds
     */
    public double lastUpdated() {
        return lastUpdate;
    }

    /**
     * Returns the mean frame period computed across all {@link #update()} calls since
     * the last {@link #reset()}.
     *
     * @return average seconds between updates; {@code NaN} if {@link #update()} has never been called
     */
    public double averageUpdatePeriod() {
        return updateDeltaSum / updateCount;
    }

    /**
     * Returns the total number of {@link #update()} calls since the last {@link #reset()}.
     *
     * @return the update invocation count
     */
    public long updateCount() {
        return updateCount;
    }

    /**
     * Returns whether the clock is currently paused.
     *
     * @return {@code true} if the clock is paused and {@link #update()} will not advance elapsed time
     */
    public boolean isPaused() {
        return paused.get() != null;
    }

    /**
     * Pause or unpause the clock.
     * While paused, {@link #update()} records wall-clock deltas but does not advance
     * {@link #elapsed()}.
     *
     * @param paused {@code true} to pause, {@code false} to resume
     */
    public void setPaused(boolean paused) {
        Double value = paused ? now() : null;
        this.paused.set(value);
    }

    /**
     * Toggle the pause state: pauses if currently running, resumes if currently paused.
     *
     * @see #isPaused()
     * @see #setPaused(boolean)
     */
    public void togglePaused() {
        setPaused(!isPaused());
    }

    @Override
    public String toString() {
        double averageDelta = updateDeltaSum / updateCount;
        return elapsed() + " ms (" + averageDelta + "s)";
    }
}
