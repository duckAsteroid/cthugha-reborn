package com.asteroid.duck.opengl.util.timer;

import java.time.Duration;

/**
 * Tracks whether a given duration has elapsed since the moment this object was created,
 * measured against a {@link Clock}.
 *
 * <p>Create via {@link Clock#track(Duration)} to snapshot the current clock position.
 * All subsequent queries ({@link #hasElapsed()}, {@link #elapsed()}, etc.) are live —
 * they read the clock each time they are called.</p>
 *
 * @param clock           the clock used for all time queries
 * @param startTime       the clock value at the moment tracking began (seconds)
 * @param durationSeconds the target duration in seconds
 */
public record Timer(Clock clock, double startTime, double durationSeconds) {

    /**
     * Validates the record components on construction.
     *
     * @throws IllegalArgumentException if {@code durationSeconds} is zero or negative
     */
    public Timer {
        if (durationSeconds <= 0) throw new IllegalArgumentException("duration must be positive");
    }

    /**
     * Snapshot the clock's current position and start tracking {@code duration} from now.
     *
     * @param clock    the clock to track against; must not be {@code null}
     * @param duration the duration to wait for; must be positive
     * @throws IllegalArgumentException if {@code duration} converts to zero or negative seconds
     */
    public Timer(Clock clock, Duration duration) {
        this(clock, clock.elapsed(), duration.toSeconds());
    }

    /**
     * Returns whether the full duration has elapsed.
     *
     * @return {@code true} once {@link #elapsed()} &ge; {@link #durationSeconds()}
     */
    public boolean hasElapsed() {
        return elapsed() >= durationSeconds;
    }

    /**
     * Returns the time elapsed since this timer was created, in seconds.
     *
     * @return seconds since creation; never negative
     */
    public double elapsed() {
        return Math.max(0.0, clock.elapsed() - startTime);
    }

    /**
     * Returns the time remaining until the duration expires.
     *
     * @return seconds until expiry; zero once the duration has elapsed
     */
    public double remaining() {
        return Math.max(0.0, durationSeconds - elapsed());
    }

    /**
     * Returns the fraction of the duration that has passed, in the range [0, 1].
     *
     * <p>Returns {@code 0.0} at creation and {@code 1.0} once the full duration has elapsed;
     * does not exceed {@code 1.0} after expiry.</p>
     *
     * @return progress in [0, 1]
     */
    public double progress() {
        return Math.min(1.0, elapsed() / durationSeconds);
    }
}
