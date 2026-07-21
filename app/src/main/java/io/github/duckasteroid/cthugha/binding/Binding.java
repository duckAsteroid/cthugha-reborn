package io.github.duckasteroid.cthugha.binding;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Common base for a script evaluated once per CPU tick that drives something else in the param
 * tree — the unification of what used to be two separate concepts, {@code AnimationBinding} and
 * {@code ActionTrigger}.
 *
 * <p>{@link #target} is always a path, resolved lazily against the tree root fresh on every tick
 * via {@link #resolveTarget()} — never held as a direct object reference. This is a strict
 * improvement over the old direct-reference {@code AnimationBinding}: a target that hasn't been
 * created yet (e.g. while {@code ScreenConfigParams.apply} is still working through an
 * out-of-order snapshot) or one that has since been deleted from the tree (e.g. a removed wave)
 * simply fails to resolve for that tick — no explicit release/cleanup call is required, and nothing
 * throws.</p>
 *
 * <p>{@link #mode} is fixed for the life of a binding and determines both the script kind and the
 * evaluation model: {@link ContinuousBinding} for {@link BindingMode#CONTINUOUS} (numeric script,
 * pushed into the target's normalised value every tick) or {@link EdgeTriggeredBinding} for
 * {@link BindingMode#EDGE_TRIGGERED} (boolean script, fires once on a false→true transition).</p>
 */
public abstract class Binding extends ParamNode {

    public final BindingMode mode;
    public final BooleanParameter enabled = new BooleanParameter("enabled", true);
    public final StringParameter target;

    /**
     * Script-local key/value state for this binding, backing its script's {@code state.*}
     * helpers (see {@link ScriptHelpers}). Ephemeral: not persisted, reset on restart.
     */
    private final Map<String, Object> localState = new ConcurrentHashMap<>();

    protected volatile Clock clock;
    protected volatile Node root;
    protected volatile ActionContext actionContext;

    protected Binding(String name, BindingMode mode, String defaultTargetPath) {
        super(name);
        this.mode = mode;
        this.target = new StringParameter("target", defaultTargetPath != null ? defaultTargetPath : "");
        this.target.withUiHint(UiHint.CONTROL_TYPE, UiHint.TARGET_PICKER);
        this.target.withDescription("Full path of the parameter or action this binding targets, "
            + "resolved fresh from the live tree every tick — a target created or deleted at "
            + "runtime is picked up automatically, with no cleanup step required.");
        enabled.withDescription("Turns this binding on or off.");
    }

    /** Script-local key/value map for this binding; see {@link ScriptHelpers}. */
    public Map<String, Object> localState() {
        return localState;
    }

    /**
     * Stores the clock/root/action-context and runs the subclass's compile step. Call once from
     * the owning {@link BindingSystem}'s {@code init()} (or immediately, if the binding is
     * created after the system is already initialised).
     */
    void init(Clock clock, Node root, ActionContext actionContext) {
        this.clock = clock;
        this.root = root;
        this.actionContext = actionContext;
        onInit();
    }

    /**
     * Resolves {@link #target}'s path against the live tree. Empty if blank, unresolved, or the
     * binding hasn't been initialised yet (root not yet known).
     */
    protected Optional<Node> resolveTarget() {
        Node r = root;
        String path = target.getValue();
        if (r == null || path == null || path.isBlank()) return Optional.empty();
        return r.getChild(path.split("/"));
    }

    /** Subclass hook: called once clock/root/actionContext become available — compile the script. */
    protected abstract void onInit();

    /** Advances this binding by one frame. Call once per frame from the owning system. */
    abstract void tick();

    /**
     * Called by the owning system just before this binding is detached from the tree —
     * synchronously release any live association with the currently-resolved target (e.g. an
     * {@code AnimationBindingView} published on an {@code AbstractValue}). No-op by default;
     * {@link ContinuousBinding} overrides this since it's the only mode that publishes such a
     * live association.
     */
    void release() {
    }
}
