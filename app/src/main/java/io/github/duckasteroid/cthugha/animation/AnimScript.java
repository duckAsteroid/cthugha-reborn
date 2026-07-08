package io.github.duckasteroid.cthugha.animation;

/**
 * Base class for Janino-compiled animation scripts.
 *
 * <p>Subclasses implement {@link #compute()} and may use the protected helper methods and
 * the {@link #t} field (elapsed seconds) freely.  {@link #apply(double)} — the
 * {@link TimeFunction} entry point — stores {@code t} and calls {@link #compute()}, so the
 * per-frame call is a single field write + virtual dispatch with no allocation.</p>
 *
 * <h2>Available in scripts</h2>
 * <pre>
 *   t              — elapsed seconds since start
 *   sine(hz)       — sine wave at hz, normalised to [0, 1]
 *   cosine(hz)     — cosine wave at hz, normalised to [0, 1]
 *   saw(hz)        — sawtooth ramp 0→1 at hz
 *   tri(hz)        — triangle wave 0→1→0 at hz
 *   pulse(hz,duty) — square wave; duty in [0,1], e.g. 0.5 = 50%
 *   phase(hz)      — raw angular phase (radians) at hz; use with Math.sin/cos
 *   TWO_PI         — 2π constant
 *   Math.*         — all java.lang.Math static members
 * </pre>
 */
public abstract class AnimScript implements TimeFunction {

    protected static final double TWO_PI = 2.0 * Math.PI;

    /** Elapsed seconds since the render clock started. Set by {@link #apply} before each call. */
    protected double t;

    @Override
    public final double apply(double tSeconds) {
        this.t = tSeconds;
        return compute();
    }

    /** Override this in your script. Return a value in {@code [0, 1]}. */
    public abstract double compute();

    /** Sine wave at {@code hz} Hz, normalised to [0, 1]. */
    protected double sine(double hz) {
        return (Math.sin(t * TWO_PI * hz) + 1.0) / 2.0;
    }

    /** Cosine wave at {@code hz} Hz, normalised to [0, 1]. */
    protected double cosine(double hz) {
        return (Math.cos(t * TWO_PI * hz) + 1.0) / 2.0;
    }

    /** Sawtooth wave: linear ramp 0→1, resetting at each period. */
    protected double saw(double hz) {
        double p = (t * hz) % 1.0;
        return p < 0 ? p + 1.0 : p;
    }

    /** Triangle wave: linear 0→1→0 per period. */
    protected double tri(double hz) {
        double s = saw(hz);
        return s < 0.5 ? s * 2.0 : (1.0 - s) * 2.0;
    }

    /**
     * Square (pulse) wave. Returns 1.0 for the first {@code duty} fraction of each period,
     * 0.0 for the rest. {@code duty=0.5} is a 50% square wave.
     */
    protected double pulse(double hz, double duty) {
        return saw(hz) < duty ? 1.0 : 0.0;
    }

    /**
     * Raw angular phase in radians at {@code hz} Hz. Useful for combining waves manually:
     * {@code (Math.sin(phase(10) + phase(0.1)) + 1) / 2}
     */
    protected double phase(double hz) {
        return t * TWO_PI * hz;
    }
}
