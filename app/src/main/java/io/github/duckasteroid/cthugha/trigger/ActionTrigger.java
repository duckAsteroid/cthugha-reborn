package io.github.duckasteroid.cthugha.trigger;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.animation.BooleanTimeFunction;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.ParamValues;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.action.Action;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;

import java.util.Optional;

/**
 * Fires against an existing node in the param tree when a boolean {@link ConditionParameter}
 * script (e.g. {@code "bass() > 0.7"}) transitions from false to true, no more than once per
 * {@link #cooldown} seconds. The target may be an {@link Action} (executed) or any settable
 * leaf (set to {@link #value}) — which one determines the behaviour is decided purely by what
 * {@link #target} resolves to at fire time, so there is no separate "kind" field to drift out
 * of sync with it.
 *
 * <p>The target is addressed by full path string ({@link #target}), always chosen from a live
 * picker in the remote UI rather than hand-typed. If the path doesn't resolve, or resolves to
 * neither an {@link Action} nor a settable leaf, {@link #status} is set to an explanatory
 * message instead of silently doing nothing.</p>
 */
public class ActionTrigger extends ParamNode {

    public static final double DEFAULT_COOLDOWN_SECONDS = 0.15;

    public final BooleanParameter enabled = new BooleanParameter("enabled", true);
    public final ConditionParameter condition;
    public final DoubleParameter cooldown;
    public final StringParameter target;
    /** Raw text applied (via {@link ParamValues}) when {@link #target} resolves to a settable leaf rather than an {@link Action}. Hidden from the generic tree render; {@link #target}'s picker renders it inline with a control shaped to the target's type. */
    public final StringParameter value;
    public final StringParameter status = new StringParameter("status", "OK");
    public final AbstractAction delete;

    private Node root;
    private ActionContext actionContext;
    private Clock clock;
    private boolean lastConditionState = false;
    private double lastFireTime = Double.NEGATIVE_INFINITY;

    public ActionTrigger(String name, String defaultCondition, String defaultTargetPath,
                          double defaultCooldown, String defaultValue, Runnable onDelete) {
        super(name);
        this.condition = new ConditionParameter("condition", defaultCondition);
        this.cooldown = new DoubleParameter("cooldown", 0.0, 10.0, defaultCooldown);
        this.target = new StringParameter("target", defaultTargetPath);
        this.target.withUiHint(UiHint.CONTROL_TYPE, UiHint.TARGET_PICKER);
        this.target.withUiHint(UiHint.PAIRED_VALUE_FIELD, "value");
        this.value = new StringParameter("value", defaultValue);
        this.value.withUiHint(UiHint.HIDDEN, "true");
        this.delete = new AbstractAction("Delete", ctx -> onDelete.run());
        this.delete.withUiHint(UiHint.ICON, "trash-2");

        enabled.withDescription("Turns this trigger on or off.");
        condition.withDescription("Boolean expression evaluated each frame (e.g. \"bass() > 0.7\"). "
            + "The trigger fires once on the rising edge (false→true), no more than once per "
            + "cooldown period.");
        cooldown.withDescription("Minimum seconds between fires, even if the condition stays true.");
        target.withDescription("The action to execute, or parameter to set, when this trigger "
            + "fires — picked from the current param tree.");
        status.withDescription("Read-only: whether the referenced target currently resolves.");

        initFields(getClass());
    }

    /** Stores the clock/root/context and compiles the initial condition. Call once from init(). */
    void init(Clock clock, Node root, ActionContext actionContext) {
        this.clock = clock;
        this.root = root;
        this.actionContext = actionContext;
        condition.compile();
    }

    void tick() {
        if (!enabled.value) return;
        BooleanTimeFunction fn = condition.getFunction();
        if (fn == null) return;
        double t = clock.elapsed();
        boolean state = fn.apply(t);
        boolean rising = state && !lastConditionState;
        lastConditionState = state;
        if (!rising || (t - lastFireTime) < cooldown.value) return;
        lastFireTime = t;

        String path = target.getValue();
        Optional<Node> resolved = path.isBlank() ? Optional.empty() : root.getChild(path.split("/"));
        if (resolved.isEmpty()) {
            setStatus("Target not found: " + path);
            return;
        }
        Node node = resolved.get();
        if (node instanceof Action a) {
            setStatus("OK");
            a.execute(actionContext);
            return;
        }
        ParamValues.ApplyResult result = ParamValues.applyText(node, value.getValue());
        switch (result) {
            case OK -> setStatus("OK");
            case NOT_A_LEAF -> setStatus("Not an action or settable parameter: " + path);
            case OUT_OF_RANGE -> setStatus("Value out of range for " + path + ": " + value.getValue());
            case PARSE_ERROR -> setStatus("Invalid value for " + path + ": \"" + value.getValue() + "\"");
        }
    }

    private void setStatus(String s) {
        if (!s.equals(status.getValue())) {
            status.setValue(s);
        }
    }
}
