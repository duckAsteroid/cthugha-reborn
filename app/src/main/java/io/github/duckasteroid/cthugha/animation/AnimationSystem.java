package io.github.duckasteroid.cthugha.animation;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.params.AbstractNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a collection of {@link AnimationBinding}s that drive param-model values from
 * render-core timer functions.
 *
 * <p>Wire bindings with {@link #addBinding} before calling {@link #init(Clock)}, then call
 * {@link #tick()} once per frame to push updated values into all active targets.</p>
 */
public class AnimationSystem extends AbstractNode {

    public final BooleanParameter enabled = new BooleanParameter("enabled", true);

    private final List<AnimationBinding> bindings = new ArrayList<>();

    public AnimationSystem() {
        super("Animation");
        addChild(enabled);
        withUiHint(UiHint.ICON, "zap");
    }

    /**
     * Registers a new wave-driven binding. The binding appears as a child of this node in the
     * param tree, exposing its own {@code enabled}, {@code frequency}, and {@code phase} params.
     *
     * @param name      display name for this binding in the param tree
     * @param target    the parameter to animate
     * @param frequency initial oscillation frequency in Hz
     * @return the created binding (for further configuration)
     */
    public AnimationBinding addBinding(String name, AbstractValue target, double frequency) {
        AnimationBinding binding = new AnimationBinding(name, target, frequency);
        bindings.add(binding);
        addChild(binding);
        return binding;
    }

    /** Initialises all bindings with the render-core clock. Call once from {@code init()}. */
    public void init(Clock clock) {
        bindings.forEach(b -> b.init(clock));
    }

    /** Advances all active bindings by one frame. Call once per frame from the render loop. */
    public void tick() {
        if (!enabled.value) return;
        bindings.forEach(AnimationBinding::tick);
    }
}
