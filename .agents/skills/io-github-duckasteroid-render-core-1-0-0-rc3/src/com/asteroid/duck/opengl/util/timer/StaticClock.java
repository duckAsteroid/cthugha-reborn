package com.asteroid.duck.opengl.util.timer;

/**
 * An immutable {@link Clock} snapshot that always returns fixed values for elapsed time and the
 * current timestamp.
 *
 * <p>Useful in tests, offline rendering, or any context where real wall-clock time should be
 * decoupled from the values presented to renderers. Because the values never change, every call
 * to {@link #elapsed()} or {@link #now()} returns the same result for the lifetime of the
 * record.</p>
 *
 * <p>Instances are typically obtained via {@link ClockImpl#snapshot()} rather than constructed
 * directly.</p>
 *
 * @param elapsed the frozen elapsed time in seconds, returned by {@link #elapsed()}
 * @param now     the frozen raw timestamp in seconds, returned by {@link #now()}
 */
public record StaticClock(double elapsed, double now) implements Clock {

    /**
     * Returns the frozen raw timestamp captured when this snapshot was created.
     *
     * @return the fixed timestamp in seconds
     */
    @Override
    public double now() {
        return now;
    }
}
