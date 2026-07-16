package io.github.duckasteroid.cthugha.animation;

/**
 * Base class for Janino-compiled boolean condition scripts (e.g. trigger conditions like
 * {@code "bass() > 0.7"}).
 *
 * <p>Subclasses implement {@link #test()} and may freely use the helpers inherited from
 * {@link ScriptHelpers}. {@link #apply(double)} — the {@link BooleanTimeFunction} entry point —
 * stores {@code t} and calls {@link #test()}.</p>
 *
 * <p>See {@link ScriptHelpers} for the full list of helpers available in scripts.</p>
 */
public abstract class ConditionScript extends ScriptHelpers implements BooleanTimeFunction {

    @Override
    public final boolean apply(double tSeconds) {
        this.t = tSeconds;
        return test();
    }

    /** Override this in your script. Return {@code true} when the condition holds. */
    public abstract boolean test();
}
