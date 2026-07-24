package com.asteroid.duck.opengl.util.timer.function;

import com.asteroid.duck.opengl.util.timer.Clock;

/**
 * Calculates a sawtooth (linear ramp) value driven by a {@link Clock}.
 *
 * <p>Within each period the output rises linearly from {@code 0} to {@link #getMaxY()},
 * then resets. The period is {@code 1 / frequency} seconds.</p>
 */
public class LinearFunction {
    private final Clock source;
    private double maxY;
    private double frequency;

    /**
     * Create a linear function driven by the given clock, with default {@code maxY=0} and
     * {@code frequency=0}. Call {@link #setMaxY} and {@link #setFrequency} before using
     * {@link #value()}.
     *
     * @param source the clock whose {@link Clock#elapsed()} drives the function; must not be {@code null}
     */
    public LinearFunction(Clock source) {
        this.source = source;
    }

    /**
     * Returns the clock that drives this function.
     *
     * @return the source clock; never {@code null}
     */
    public Clock getSource() {
        return source;
    }

    /**
     * Returns the peak output value reached at the end of each period.
     *
     * @return the maximum Y value
     */
    public double getMaxY() {
        return maxY;
    }

    /**
     * Set the peak output value reached at the end of each period (the sawtooth ceiling).
     *
     * @param maxY the maximum Y value; negative values produce a descending sawtooth
     */
    public void setMaxY(double maxY) {
        this.maxY = maxY;
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
     * Set the oscillation frequency, which determines how quickly the sawtooth resets.
     *
     * @param frequency the desired frequency in hertz; must be &gt; 0
     */
    public void setFrequency(double frequency) {
        this.frequency = frequency;
    }
    /**
     * Evaluate the sawtooth function at the clock's current elapsed time.
     *
     * @return the current ramp value in [0, {@link #getMaxY()}]
     */
    public double value() {
        double period = 1.0 / frequency;
        double progress = (source.elapsed() % period) / period;
        return progress * maxY;
    }
}
