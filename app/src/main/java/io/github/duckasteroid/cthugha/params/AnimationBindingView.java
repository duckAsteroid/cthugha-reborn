package io.github.duckasteroid.cthugha.params;

/**
 * Read-only view of the animation currently bound to an {@link AbstractValue}, exposed via
 * {@link AbstractValue#getAnimationBinding()}. Lets the remote serializer embed live script/
 * enabled/error state directly on the target leaf's JSON without the {@code params} package
 * depending on the {@code animation} package.
 */
public interface AnimationBindingView {

    /** The expression currently bound to this parameter, e.g. {@code "sine(0.05)"}. */
    String getScript();

    /** Whether this binding is currently driving its target (independent of compile errors). */
    boolean isEnabled();

    /** The last compile error for {@link #getScript()}, or {@code null} if it compiled cleanly. */
    String getCompileError();
}
