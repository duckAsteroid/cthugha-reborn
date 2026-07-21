package io.github.duckasteroid.cthugha.binding;

/**
 * A compiled boolean condition script's entry point — the {@link ConditionScript} analogue of
 * {@link TimeFunction}.
 */
@FunctionalInterface
public interface BooleanTimeFunction {
    /** Evaluates the condition at elapsed time {@code t} (seconds since start). */
    boolean apply(double t);
}
