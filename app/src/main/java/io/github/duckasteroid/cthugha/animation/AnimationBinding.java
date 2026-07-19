package io.github.duckasteroid.cthugha.animation;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.AnimationBindingView;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;

import java.util.Optional;

/**
 * Drives a single {@link AbstractValue} parameter using a user-supplied {@link ScriptParameter}.
 *
 * <p>Each frame, {@link #tick()} evaluates the compiled {@link TimeFunction} with the current
 * elapsed time in seconds and pushes the clamped result into the target via
 * {@link AbstractValue#setNormalisedValue(double)}.</p>
 *
 * <p>Also publishes itself as the target's {@link AnimationBindingView} (see
 * {@link AbstractValue#getAnimationBinding()}) so the remote serializer can embed this binding's
 * script/enabled/error state directly on the target leaf's JSON.</p>
 */
public class AnimationBinding extends ParamNode implements AnimationBindingView {

    public final BooleanParameter enabled = new BooleanParameter("enabled", true);
    public final ScriptParameter script;

    private final AbstractValue target;
    private Clock clock;

    public AnimationBinding(String name, AbstractValue target, String defaultScript) {
        super(name);
        this.target = target;
        this.script = new ScriptParameter("script", defaultScript);
        initFields(getClass());

        enabled.withDescription("Turns this animation binding on or off. When off, the target "
            + "parameter reverts to manual/remote control.");
        script.withDescription("Expression evaluated each frame using elapsed time in seconds "
            + "as t (e.g. \"(sin(t * 0.31) + 1.0) / 2.0\"); the result is clamped to [0,1] and "
            + "pushed into the target parameter.");
        target.setAnimationBinding(this);
    }

    /** The parameter this binding drives. */
    public AbstractValue getTarget() {
        return target;
    }

    /** Stores the clock and compiles the initial script. Call once from {@code init()}. */
    void init(Clock clock) {
        this.clock = clock;
        script.compile();
    }

    void tick() {
        TimeFunction fn = script.getFunction();
        if (!enabled.value || fn == null) {
            target.setControlled(false);
            return;
        }
        target.setControlled(true);
        if (!isTargetActive()) {
            // Target lives under a disabled section (e.g. a wave whose own "enabled" toggle is
            // off) — nothing downstream can see the value change, so skip the script evaluation
            // and the value push (which would otherwise fire change listeners and, via the
            // remote server, emit a paramChanged SSE event every tick for no visible effect).
            return;
        }
        double t = clock.elapsed();
        target.setNormalisedValue(Math.max(0.0, Math.min(1.0, fn.apply(t))));
    }

    /**
     * Returns {@code false} if some ancestor of {@link #target} (other than the target itself)
     * has a sibling child named "enabled" whose value is currently 0/false — the established
     * convention for a section that is switched off (wave models, {@code PerspectiveParams},
     * etc). An animation directly targeting such an "enabled" flag is unaffected by its own
     * current value, so binding on/off toggles always keep ticking.
     */
    private boolean isTargetActive() {
        Node current = target.getParent();
        while (current != null) {
            Optional<Node> enabledChild = current.getChild("enabled");
            if (enabledChild.isPresent()) {
                Node ec = enabledChild.get();
                if (ec != target && ec instanceof AbstractValue av && av.getValue().doubleValue() == 0.0) {
                    return false;
                }
            }
            current = current.getParent();
        }
        return true;
    }

    /** Detaches this binding from its target, releasing control. Call once before discarding. */
    void release() {
        target.setControlled(false);
        target.setAnimationBinding(null);
    }

    @Override
    public String getScript() {
        return script.getValue();
    }

    @Override
    public boolean isEnabled() {
        return enabled.value;
    }

    @Override
    public String getCompileError() {
        return script.getLastCompileError();
    }
}
