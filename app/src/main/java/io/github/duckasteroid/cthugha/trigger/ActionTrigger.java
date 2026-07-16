package io.github.duckasteroid.cthugha.trigger;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.animation.BooleanTimeFunction;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.action.Action;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;

import java.util.Optional;

/**
 * Fires an existing {@link Action} in the param tree when a boolean {@link ConditionParameter}
 * script (e.g. {@code "bass() > 0.7"}) transitions from false to true, no more than once per
 * {@link #cooldown} seconds.
 *
 * <p>The referenced action is addressed by full path string ({@link #action}), always chosen
 * from a live picker in the remote UI rather than hand-typed. If the path doesn't resolve to an
 * {@link Action} at fire time, {@link #status} is set to an explanatory message instead of
 * silently doing nothing.</p>
 */
public class ActionTrigger extends ParamNode {

    public static final double DEFAULT_COOLDOWN_SECONDS = 0.15;

    public final BooleanParameter enabled = new BooleanParameter("enabled", true);
    public final ConditionParameter condition;
    public final DoubleParameter cooldown;
    public final StringParameter action;
    public final StringParameter status = new StringParameter("status", "OK");
    public final AbstractAction delete;

    private Node root;
    private ActionContext actionContext;
    private Clock clock;
    private boolean lastConditionState = false;
    private double lastFireTime = Double.NEGATIVE_INFINITY;

    public ActionTrigger(String name, String defaultCondition, String defaultActionPath,
                          double defaultCooldown, Runnable onDelete) {
        super(name);
        this.condition = new ConditionParameter("condition", defaultCondition);
        this.cooldown = new DoubleParameter("cooldown", 0.0, 10.0, defaultCooldown);
        this.action = new StringParameter("action", defaultActionPath);
        this.action.withUiHint(UiHint.CONTROL_TYPE, UiHint.ACTION_PICKER);
        this.delete = new AbstractAction("Delete", ctx -> onDelete.run());
        this.delete.withUiHint(UiHint.ICON, "trash-2");

        enabled.withDescription("Turns this trigger on or off.");
        condition.withDescription("Boolean expression evaluated each frame (e.g. \"bass() > 0.7\"). "
            + "The action fires once on the rising edge (false→true), no more than once per "
            + "cooldown period.");
        cooldown.withDescription("Minimum seconds between fires, even if the condition stays true.");
        action.withDescription("The action to execute when this trigger fires, picked from the "
            + "current param tree.");
        status.withDescription("Read-only: whether the referenced action currently resolves.");

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

        String path = action.getValue();
        Optional<Node> target = path.isBlank() ? Optional.empty() : root.getChild(path.split("/"));
        if (target.isPresent() && target.get() instanceof Action a) {
            setStatus("OK");
            a.execute(actionContext);
        } else {
            setStatus("Action not found: " + path);
        }
    }

    private void setStatus(String s) {
        if (!s.equals(status.getValue())) {
            status.setValue(s);
        }
    }
}
