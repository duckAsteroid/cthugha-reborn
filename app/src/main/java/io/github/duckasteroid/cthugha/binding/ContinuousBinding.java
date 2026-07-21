package io.github.duckasteroid.cthugha.binding;

import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.AnimationBindingView;
import io.github.duckasteroid.cthugha.params.Node;

import java.util.Map;
import java.util.Optional;

/**
 * A {@link BindingMode#CONTINUOUS} binding: evaluates a numeric {@link ScriptParameter} every
 * tick and pushes the clamped result into the target's normalised value via
 * {@link AbstractValue#setNormalisedValue(double)}.
 *
 * <p>Unlike the old {@code AnimationBinding}, the target is re-resolved from {@link #target}'s
 * path on every tick rather than held as a fixed reference (see {@link Binding}). Whichever
 * {@link AbstractValue} is currently resolved also gets this binding published as its {@link
 * AnimationBindingView} (see {@link AbstractValue#getAnimationBinding()}), so the remote
 * serializer can embed live script/enabled/error state directly on that leaf's JSON — the
 * association follows the resolved target and is cleared automatically if the path stops
 * resolving to an {@code AbstractValue} (deleted node, retargeted binding, etc).</p>
 */
public final class ContinuousBinding extends Binding implements AnimationBindingView {

    public final ScriptParameter script;

    /** The AbstractValue this binding is currently published on as its AnimationBindingView, or null. */
    private AbstractValue boundValue;

    ContinuousBinding(String name, String defaultTargetPath, String defaultScript, Map<String, Object> globalState) {
        super(name, BindingMode.CONTINUOUS, defaultTargetPath);
        this.script = new ScriptParameter("script", defaultScript);
        this.script.bindState(localState(), globalState);
        initFields(getClass());

        script.withDescription("Expression evaluated each frame using elapsed time in seconds "
            + "as t (e.g. \"(sin(t * 0.31) + 1.0) / 2.0\"); the result is clamped to [0,1] and "
            + "pushed into the target parameter.");
        enabled.withDescription("Turns this animation binding on or off. When off, the target "
            + "parameter reverts to manual/remote control.");
    }

    @Override
    protected void onInit() {
        script.compile();
        // Publish the AnimationBindingView association immediately (not just on the first tick)
        // so a binding created via the remote API shows up in that same request's response —
        // RemoteServerTest and AnimationEditor.tsx both expect `animation` to be present right
        // after creation, before any tick has run.
        syncBoundValue();
    }

    /** Re-resolves {@link #target} and keeps {@link #boundValue}/{@code AnimationBindingView} in sync with it. */
    private AbstractValue syncBoundValue() {
        Optional<Node> resolved = resolveTarget();
        AbstractValue av = resolved.filter(n -> n instanceof AbstractValue)
                .map(n -> (AbstractValue) n)
                .orElse(null);
        if (av != boundValue) {
            releaseBoundValue();
            boundValue = av;
            if (av != null) {
                av.setAnimationBinding(this);
            }
        }
        return av;
    }

    @Override
    void tick() {
        AbstractValue av = syncBoundValue();
        if (av == null) {
            return;
        }
        TimeFunction fn = script.getFunction();
        if (!enabled.value || fn == null) {
            av.setControlled(false);
            return;
        }
        av.setControlled(true);
        if (!isTargetActive(av)) {
            // Target lives under a disabled section (e.g. a wave whose own "enabled" toggle is
            // off) — nothing downstream can see the value change, so skip the script evaluation
            // and the value push (which would otherwise fire change listeners and, via the
            // remote server, emit a paramChanged SSE event every tick for no visible effect).
            return;
        }
        double t = clock.elapsed();
        av.setNormalisedValue(Math.max(0.0, Math.min(1.0, fn.apply(t))));
    }

    @Override
    void release() {
        releaseBoundValue();
    }

    private void releaseBoundValue() {
        if (boundValue != null) {
            boundValue.setControlled(false);
            if (boundValue.getAnimationBinding() == this) {
                boundValue.setAnimationBinding(null);
            }
            boundValue = null;
        }
    }

    /**
     * Returns {@code false} if some ancestor of {@code resolvedTarget} (other than the target
     * itself) has a sibling child named "enabled" whose value is currently 0/false — the
     * established convention for a section that is switched off (wave models, {@code
     * PerspectiveParams}, etc). An animation directly targeting such an "enabled" flag is
     * unaffected by its own current value, so binding on/off toggles always keep ticking.
     */
    private boolean isTargetActive(Node resolvedTarget) {
        Node current = resolvedTarget.getParent();
        while (current != null) {
            Optional<Node> enabledChild = current.getChild("enabled");
            if (enabledChild.isPresent()) {
                Node ec = enabledChild.get();
                if (ec != resolvedTarget && ec instanceof AbstractValue av && av.getValue().doubleValue() == 0.0) {
                    return false;
                }
            }
            current = current.getParent();
        }
        return true;
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
