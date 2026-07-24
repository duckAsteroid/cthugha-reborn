package com.asteroid.duck.opengl.util.timer.function;

import com.asteroid.duck.opengl.util.timer.Clock;

import java.util.Objects;

/**
 * Calculates the value of a sinusoidal wave function in the range [-1, 1].
 *
 * <p>The wave is defined by a frequency in hertz and a phase offset in seconds.
 * The output at time {@code t} is {@code sin(2π × frequency × (t + phase))},
 * where {@code t} is taken from the backing {@link Clock}.</p>
 *
 * <p>A static {@link #value(double, double, double)} overload is also provided for
 * one-shot calculations where no clock is needed.</p>
 */
public class WaveFunction {
    private final Clock source;
    private double frequency;
    private double phase;

    /**
     * Create a wave function with the given clock, frequency, and phase.
     *
     * @param source    the clock whose {@link Clock#elapsed()} drives the wave; must not be {@code null}
     * @param frequency the oscillation frequency in hertz; must be &gt; 0 for meaningful output
     * @param phase     the phase offset in seconds; shifts the wave left ({@code +}) or right ({@code -})
     */
    public WaveFunction(Clock source, double frequency, double phase) {
        Objects.requireNonNull(source);
        this.source = source;
        this.frequency = frequency;
        this.phase = phase;
    }

    /**
     * Create a wave function with the given clock and frequency, using a phase of {@code 0.0}.
     *
     * @param source    the clock whose {@link Clock#elapsed()} drives the wave; must not be {@code null}
     * @param frequency the oscillation frequency in hertz
     */
    public WaveFunction(Clock source, double frequency) {
        this(source, frequency, 0.0);
    }

    /**
     * Create a wave function with the given clock, using a frequency of {@code 1.0} Hz
     * and a phase of {@code 0.0}.
     *
     * @param source the clock whose {@link Clock#elapsed()} drives the wave; must not be {@code null}
     */
    public WaveFunction(Clock source) {
        this(source, 1.0);
    }

    /**
     * Returns the clock that drives this wave function.
     *
     * @return the source clock; never {@code null}
     */
    public Clock getSource() {
        return source;
    }

    /**
     * Returns the current oscillation frequency in hertz.
     *
     * @return cycles per second
     */
    public double getFrequency() {
        return frequency;
    }

    /**
     * Set the oscillation frequency.
     *
     * @param frequency the desired frequency in hertz; must be &gt; 0 for meaningful output
     */
    public void setFrequency(double frequency) {
        this.frequency = frequency;
    }

    /**
     * Returns the current phase offset in seconds.
     *
     * @return phase shift applied before the sine calculation
     */
    public double getPhase() {
        return phase;
    }

    /**
     * Set the phase offset.
     *
     * @param phase the phase offset in seconds
     */
    public void setPhase(double phase) {
        this.phase = phase;
    }

    /**
     * Evaluate the wave at the clock's current elapsed time.
     *
     * @return {@code sin(2π × frequency × (elapsed + phase))}, in the range [-1, 1]
     */
    public double value() {
        return Math.sin(2 * Math.PI * frequency * (source.elapsed() + phase));
    }

    /**
     * Evaluate the wave at an explicit elapsed time without a clock instance.
     *
     * @param frequency the oscillation frequency in hertz
     * @param phase     the phase offset in seconds
     * @param elapsed   the time value to evaluate at, in seconds
     * @return {@code sin(2π × frequency × (elapsed + phase))}, in the range [-1, 1]
     */
    public static double value(double frequency, double phase, double elapsed) {
        return Math.sin(2 * Math.PI * frequency * (elapsed + phase));
    }
}
