package io.github.duckasteroid.cthugha.params.transform;

/** Angle helpers shared by {@link TransformParams} and the animation packages. Degrees throughout. */
public final class Angles {

    /** Time constant of {@link #settle}'s ease: within 1% of rest after about half a second. */
    static final double SETTLE_SECONDS = 0.1;
    /** Slowest {@link #settle} closes the last few degrees, so it reaches exactly 0. */
    static final double SETTLE_MIN_DEGREES_PER_SECOND = 20.0;

    private Angles() {}

    /** {@code degrees} within {@code [0, 360)}. */
    public static double wrap(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    /**
     * An accumulated spin angle in {@code [0, 360)}, eased {@code dt} seconds further back towards
     * 0 the short way round, so a spin that stops returns to rest instead of staying where it was.
     */
    public static double settle(double degrees, double dt) {
        double offset = wrap(degrees);
        if (offset > 180.0) offset -= 360.0;
        double remaining = Math.abs(offset) * Math.exp(-dt / SETTLE_SECONDS)
                - SETTLE_MIN_DEGREES_PER_SECOND * dt;
        return remaining <= 0 ? 0.0 : wrap(Math.copySign(remaining, offset));
    }

    /** {@code degrees} advanced by {@code revsPerSecond} over {@code dt}, or {@link #settle}d when that is 0. */
    public static double spin(double degrees, double revsPerSecond, double dt) {
        return revsPerSecond == 0 ? settle(degrees, dt) : wrap(degrees + 360.0 * revsPerSecond * dt);
    }
}
