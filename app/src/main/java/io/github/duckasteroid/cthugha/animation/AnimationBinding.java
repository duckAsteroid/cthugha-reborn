package io.github.duckasteroid.cthugha.animation;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.AnimationBindingView;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;

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
        double t = clock.elapsed();
        target.setNormalisedValue(Math.max(0.0, Math.min(1.0, fn.apply(t))));
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
