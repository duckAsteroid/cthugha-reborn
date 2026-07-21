package io.github.duckasteroid.cthugha.binding;

import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamValues;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.action.Action;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;

import java.util.Map;
import java.util.Optional;

/**
 * A {@link BindingMode#EDGE_TRIGGERED} binding: fires against its resolved {@link #target} when
 * a boolean {@link ConditionParameter} script (e.g. {@code "bass() > 0.7"}) transitions from
 * false to true, no more than once per {@link #cooldown} seconds. The target may be an
 * {@link Action} (executed) or any settable leaf (set to {@link #value}) — which behaviour
 * applies is decided purely by what {@link #target} resolves to at fire time, so there is no
 * separate "kind" field to drift out of sync with it.
 *
 * <p>If the target path doesn't resolve, or resolves to neither an {@link Action} nor a settable
 * leaf, {@link #status} is set to an explanatory message instead of silently doing nothing.</p>
 */
public final class EdgeTriggeredBinding extends Binding {

    public static final double DEFAULT_COOLDOWN_SECONDS = 0.15;

    public final ConditionParameter condition;
    public final DoubleParameter cooldown;
    /** Raw text applied (via {@link ParamValues}) when {@link #target} resolves to a settable leaf rather than an {@link Action}. Hidden from the generic tree render; {@link #target}'s picker renders it inline with a control shaped to the target's type. */
    public final StringParameter value;
    public final StringParameter status = new StringParameter("status", "OK");
    public final AbstractAction delete;

    private boolean lastConditionState = false;
    private double lastFireTime = Double.NEGATIVE_INFINITY;

    EdgeTriggeredBinding(String name, String defaultCondition, String defaultTargetPath,
                          double defaultCooldown, String defaultValue, Map<String, Object> globalState,
                          Runnable onDelete) {
        super(name, BindingMode.EDGE_TRIGGERED, defaultTargetPath);
        this.condition = new ConditionParameter("condition", defaultCondition);
        this.condition.bindState(localState(), globalState);
        this.cooldown = new DoubleParameter("cooldown", 0.0, 10.0, defaultCooldown);
        this.value = new StringParameter("value", defaultValue);
        this.value.withUiHint(UiHint.HIDDEN, "true");
        this.target.withUiHint(UiHint.PAIRED_VALUE_FIELD, "value");
        this.delete = new AbstractAction("Delete", ctx -> onDelete.run());
        this.delete.withUiHint(UiHint.ICON, "trash-2");

        condition.withDescription("Boolean expression evaluated each frame (e.g. \"bass() > 0.7\"). "
            + "The trigger fires once on the rising edge (false→true), no more than once per "
            + "cooldown period.");
        cooldown.withDescription("Minimum seconds between fires, even if the condition stays true.");
        status.withDescription("Read-only: whether the referenced target currently resolves.");

        initFields(getClass());
    }

    @Override
    protected void onInit() {
        condition.compile();
    }

    @Override
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

        Optional<Node> resolved = resolveTarget();
        if (resolved.isEmpty()) {
            setStatus("Target not found: " + target.getValue());
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
            case NOT_A_LEAF -> setStatus("Not an action or settable parameter: " + target.getValue());
            case OUT_OF_RANGE -> setStatus("Value out of range for " + target.getValue() + ": " + value.getValue());
            case PARSE_ERROR -> setStatus("Invalid value for " + target.getValue() + ": \"" + value.getValue() + "\"");
        }
    }

    private void setStatus(String s) {
        if (!s.equals(status.getValue())) {
            status.setValue(s);
        }
    }
}
