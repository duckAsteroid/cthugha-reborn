package io.github.duckasteroid.cthugha.animation;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages a collection of {@link AnimationBinding}s that drive param-model values from
 * user-supplied time expressions.
 *
 * <p>Bindings can be registered up front via {@link #addBinding} before {@link #init(Clock)} is
 * called, or created later at runtime (e.g. from a remote HTTP request) — in that case the
 * binding is compiled and ticking immediately since the clock is already known. Call
 * {@link #tick()} once per frame to push updated values into all active targets.</p>
 */
public class AnimationSystem extends ParamNode {

    public final BooleanParameter enabled = new BooleanParameter("enabled", true);

    private final CopyOnWriteArrayList<AnimationBinding> bindings = new CopyOnWriteArrayList<>();
    private volatile Clock clock;

    public AnimationSystem() {
        super("Animation");
        addChild(enabled);
        withUiHint(UiHint.ICON, "zap");
        enabled.withDescription("Master switch for all animation bindings. When off, every "
            + "binding stops updating and releases control of its target parameter.");
        // Every binding also publishes itself on its target leaf (see AnimationBinding), which
        // the remote UI renders inline with a proper editor. Listing bindings here too would
        // just duplicate that with a worse, generic editor — so this node (and its bindings)
        // stays out of the remote tree. It remains a normal tree member otherwise: still walked
        // by ScreenConfigParams capture/apply, so binding on/off + script state still round-trips
        // through saved screen configs.
        withNoRemote();
    }

    /**
     * Registers a script-driven binding. The binding appears as a child of this node in the
     * param tree, exposing its own {@code enabled} and {@code script} params. If the render
     * clock is already known (i.e. this is called after {@link #init(Clock)}, as happens when a
     * binding is created at runtime rather than at startup), the binding is initialised and its
     * script compiled immediately.
     *
     * @param name          display name for this binding in the param tree
     * @param target        the parameter to animate
     * @param defaultScript initial expression, e.g. {@code "(sin(t * 0.31) + 1.0) / 2.0"}
     * @return the created binding (for further configuration)
     */
    public AnimationBinding addBinding(String name, AbstractValue target, String defaultScript) {
        AnimationBinding binding = new AnimationBinding(name, target, defaultScript);
        bindings.add(binding);
        addChild(binding);
        if (clock != null) {
            binding.init(clock);
        }
        return binding;
    }

    /** Removes a binding, releasing control of its target and detaching it from the param tree. */
    public void removeBinding(AnimationBinding binding) {
        bindings.remove(binding);
        removeChild(binding);
        binding.release();
    }

    /** Returns the binding currently driving {@code target}, if any. */
    public Optional<AnimationBinding> findBindingFor(AbstractValue target) {
        return bindings.stream().filter(b -> b.getTarget() == target).findFirst();
    }

    /** Initialises all bindings with the render-core clock. Call once from {@code init()}. */
    public void init(Clock clock) {
        this.clock = clock;
        bindings.forEach(b -> b.init(clock));
    }

    /** Advances all active bindings by one frame. Call once per frame from the render loop. */
    public void tick() {
        if (!enabled.value) return;
        bindings.forEach(AnimationBinding::tick);
    }
}
