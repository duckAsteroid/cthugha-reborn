package io.github.duckasteroid.cthugha.trigger;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a collection of {@link ActionTrigger}s that fire existing tree actions when a boolean
 * condition script goes true (e.g. flash an image on a kick drum hit).
 *
 * <p>New triggers are created via the {@code New Condition}/{@code New Action}/{@code New
 * Cooldown} form fields and the {@code Add Trigger} action — the same "form + create action"
 * pattern used by {@link io.github.duckasteroid.cthugha.screenconfig.ScreenConfigLibraryNode} —
 * so trigger creation/edit/delete all ride the existing generic PATCH-leaf and POST-{@code
 * /execute} endpoints with no bespoke REST surface.</p>
 */
public class TriggerSystem extends ParamNode {

    public final BooleanParameter enabled = new BooleanParameter("enabled", true);

    private final StringParameter newCondition = new StringParameter("New Condition", "");
    private final StringParameter newTarget = new StringParameter("New Target", "");
    private final StringParameter newValue = new StringParameter("New Value", "");
    private final DoubleParameter newCooldown =
            new DoubleParameter("New Cooldown", 0.0, 10.0, ActionTrigger.DEFAULT_COOLDOWN_SECONDS);
    private final AbstractAction addTriggerAction;

    private final CopyOnWriteArrayList<ActionTrigger> triggers = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();

    private volatile Clock clock;
    private volatile Node root;
    private volatile ActionContext actionContext;
    private volatile Runnable onTreeChanged = () -> {};

    public TriggerSystem() {
        super("Triggers");
        withUiHint(UiHint.ICON, "target");
        withDescription("Fires an existing action automatically when a beat/time/random "
            + "condition script goes true (e.g. flash an image on a kick drum hit).");

        enabled.withDescription("Master switch for all triggers. When off, no trigger fires "
            + "regardless of its own enabled state.");

        newCondition.withUiHint(UiHint.CONTROL_TYPE, UiHint.CODE_EDITOR);
        newCondition.withDescription("Boolean expression for the new trigger, e.g. \"bass() > 0.7\".");
        newCondition.withNoPersist();

        newTarget.withUiHint(UiHint.CONTROL_TYPE, UiHint.TARGET_PICKER);
        newTarget.withUiHint(UiHint.PAIRED_VALUE_FIELD, "New Value");
        newTarget.withDescription("Action to execute, or parameter to set, when the new trigger fires.");
        newTarget.withNoPersist();

        newValue.withUiHint(UiHint.HIDDEN, "true");
        newValue.withNoPersist();

        newCooldown.withDescription("Minimum seconds between fires for the new trigger.");
        newCooldown.withNoPersist();

        addTriggerAction = new AbstractAction("Add Trigger", ctx -> {
            if (newTarget.getValue().isBlank()) {
                ctx.notify("Pick a target first");
                return;
            }
            addTrigger(newCondition.getValue(), newTarget.getValue(), newCooldown.value, newValue.getValue());
            newCondition.setValue("");
            newTarget.setValue("");
            newValue.setValue("");
            newCooldown.setValue(ActionTrigger.DEFAULT_COOLDOWN_SECONDS);
        });
        addTriggerAction.withUiHint(UiHint.ICON, "circle-plus");

        addChild(enabled);
        addChild(newCondition);
        addChild(newTarget);
        addChild(newValue);
        addChild(newCooldown);
        addChild(addTriggerAction);
    }

    /**
     * Creates a new trigger, naming it uniquely for this process's lifetime. If {@link #init}
     * has already run, the trigger is compiled and starts ticking immediately.
     */
    public ActionTrigger addTrigger(String condition, String targetPath, double cooldownSeconds, String value) {
        String name = "Trigger " + counter.incrementAndGet();
        ActionTrigger trigger = new ActionTrigger(name, condition, targetPath, cooldownSeconds, value,
                () -> removeTriggerNamed(name));
        triggers.add(trigger);
        addChild(trigger);
        if (clock != null) {
            trigger.init(clock, root, actionContext);
        }
        onTreeChanged.run();
        return trigger;
    }

    /** Removes a trigger, detaching it from the param tree. */
    public void removeTrigger(ActionTrigger trigger) {
        triggers.remove(trigger);
        removeChild(trigger);
        onTreeChanged.run();
    }

    private void removeTriggerNamed(String name) {
        triggers.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .ifPresent(this::removeTrigger);
    }

    /** Called once, after the render clock/param root/action context are all known. */
    public void init(Clock clock, Node root, ActionContext actionContext) {
        this.clock = clock;
        this.root = root;
        this.actionContext = actionContext;
        triggers.forEach(t -> t.init(clock, root, actionContext));
    }

    /** Advances all active triggers by one frame. Call once per frame from the render loop. */
    public void tick() {
        if (!enabled.value) return;
        triggers.forEach(ActionTrigger::tick);
    }

    /** Registers a callback invoked whenever a trigger is added or removed. */
    public void setOnTreeChanged(Runnable r) {
        this.onTreeChanged = r != null ? r : () -> {};
    }
}
