package io.github.duckasteroid.cthugha.binding;

/**
 * How a {@link Binding}'s script evaluation drives its {@link Binding#target}.
 */
public enum BindingMode {

    /**
     * A numeric script is evaluated every tick and pushed into the target's normalised value
     * (today's "animation" behaviour). Implemented by {@link ContinuousBinding}.
     */
    CONTINUOUS,

    /**
     * A boolean script is evaluated every tick; the binding fires once on a false→true
     * transition (no more than once per cooldown period), executing the target if it's an
     * {@link io.github.duckasteroid.cthugha.params.action.Action} or applying {@code value} to
     * it otherwise (today's "trigger" behaviour). Implemented by {@link EdgeTriggeredBinding}.
     */
    EDGE_TRIGGERED
}
