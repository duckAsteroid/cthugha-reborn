package io.github.duckasteroid.cthugha.animation;

/**
 * A time-driven function that maps elapsed seconds to a normalised value in {@code [0, 1]}.
 *
 * <p>Implementations are compiled at runtime from user-supplied expressions via
 * {@link ScriptParameter}.  The interface is {@code @FunctionalInterface} so Janino's
 * {@code ClassBodyEvaluator} can satisfy it with a single {@code apply} method, keeping
 * invocation to a plain virtual call with no argument boxing.</p>
 */
@FunctionalInterface
public interface TimeFunction {
    /**
     * Evaluates the function at time {@code t}.
     *
     * @param t elapsed seconds since the render clock started
     * @return a value; should lie in {@code [0, 1]} but will be clamped by the caller
     */
    double apply(double t);
}
