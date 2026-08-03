package com.asteroid.duck.opengl.util.timer.function;

import com.asteroid.duck.opengl.util.timer.Clock;

import java.util.Objects;

/**
 * Produces a monotonically increasing value by accumulating scaled time deltas from a
 * {@link Clock} source.
 *
 * <p>Each call to {@link #value()} computes how much time has elapsed since the previous call,
 * multiplies it by the current {@link #getSpeed() speed}, and adds the result to an internal
 * accumulator. The accumulator grows indefinitely — it never wraps or resets — so it is suitable
 * for driving continuous animations such as rotation angles or texture offsets that should run
 * at a controllable pace without periodic discontinuities.</p>
 *
 * <p>Speed is clamped to [{@link #getMinSpeed()}, {@link #getMaxSpeed()}] on every
 * {@link #setSpeed} call, so it can be driven directly from user input without needing to guard
 * against runaway values.</p>
 */
public class AccumulatorFunction {
    private final Clock source;
    private double speed = 1.0;
    private double accumulated;
    private double lastElapsed;
    private double minSpeed = 0.01;
    private double maxSpeed = 100.0;

    /**
     * Create an accumulator backed by the given clock. The accumulator starts at zero and begins
     * advancing on the first call to {@link #value()}.
     *
     * @param source the clock that supplies elapsed-time deltas; must not be {@code null}
     */
    public AccumulatorFunction(Clock source) {
        Objects.requireNonNull(source);
        this.source = source;
        lastElapsed = accumulated = source.elapsed();
    }

    /**
     * Returns the clock that drives this accumulator.
     *
     * @return the source clock; never {@code null}
     */
    public Clock getSource() {
        return source;
    }

    /**
     * Returns the current speed multiplier applied to each time delta.
     * A speed of {@code 1.0} accumulates in real time; {@code 2.0} doubles the rate.
     *
     * @return the current speed multiplier
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * Set the speed multiplier, clamped to [{@link #getMinSpeed()}, {@link #getMaxSpeed()}].
     *
     * @param speed desired speed multiplier; values outside the allowed range are silently clamped
     */
    public void setSpeed(double speed) {
        this.speed = Math.max(minSpeed, Math.min(maxSpeed, speed));
    }

    /**
     * Returns the upper bound for the speed multiplier.
     *
     * @return the maximum speed value; {@link #setSpeed} will not exceed this
     */
    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * Set the upper bound for the speed multiplier. If the current speed exceeds the new maximum
     * it is not automatically reduced — call {@link #setSpeed} if you need to enforce the cap
     * immediately.
     *
     * @param maxSpeed the new upper bound; should be greater than {@link #getMinSpeed()}
     */
    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
        if (speed > maxSpeed) maxSpeed = speed;
    }

    /**
     * Returns the lower bound for the speed multiplier. A minimum above zero prevents the
     * accumulator from stalling completely when speed is reduced to nearly nothing.
     *
     * @return the minimum speed value; {@link #setSpeed} will not go below this
     */
    public double getMinSpeed() {
        return minSpeed;
    }

    /**
     * Set the lower bound for the speed multiplier. If the current speed is below the new minimum
     * it is not automatically raised — call {@link #setSpeed} if you need to enforce the floor
     * immediately.
     *
     * @param minSpeed the new lower bound; should be a small positive value (e.g. {@code 0.01})
     */
    public void setMinSpeed(double minSpeed) {
        this.minSpeed = minSpeed;
        if (speed < minSpeed) speed = minSpeed;
    }

    /**
     * Advance the accumulator by the elapsed time since the last call, scaled by {@link #getSpeed()},
     * and return the new accumulated value.
     *
     * <p>The first call establishes the baseline; subsequent calls add {@code delta * speed} where
     * {@code delta} is the real time (in seconds) since the previous call. The return value grows
     * without bound as long as speed is positive.</p>
     *
     * @return the current accumulated value; always &ge; the value returned by the previous call
     *         when speed is non-negative
     */
    public double value() {
        double elapsed = source.elapsed();
        double delta = elapsed - lastElapsed;
        lastElapsed = elapsed;
        accumulated += delta * speed;
        return accumulated;
    }

}
