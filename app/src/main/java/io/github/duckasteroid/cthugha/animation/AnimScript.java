package io.github.duckasteroid.cthugha.animation;

/**
 * Base class for Janino-compiled animation scripts.
 *
 * <p>Subclasses implement {@link #compute()} and may freely use the helpers inherited from
 * {@link ScriptHelpers} (the {@code t} field, wave/beat/random helpers). {@link #apply(double)}
 * — the {@link TimeFunction} entry point — stores {@code t} and calls {@link #compute()}, so the
 * per-frame call is a single field write + virtual dispatch with no allocation.</p>
 *
 * <p>See {@link ScriptHelpers} for the full list of helpers available in scripts.</p>
 */
public abstract class AnimScript extends ScriptHelpers implements TimeFunction {

    @Override
    public final double apply(double tSeconds) {
        this.t = tSeconds;
        return compute();
    }

    /** Override this in your script. Return a value in {@code [0, 1]}. */
    public abstract double compute();
}
