package io.github.duckasteroid.cthugha.screenconfig;

import com.asteroid.duck.opengl.util.timer.StaticClock;
import io.github.duckasteroid.cthugha.binding.BindingSystem;
import io.github.duckasteroid.cthugha.binding.ContinuousBinding;
import io.github.duckasteroid.cthugha.binding.EdgeTriggeredBinding;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.DynamicChildList.ChildSpec;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.StringParameter;
import io.github.duckasteroid.cthugha.tab.GeneratorRegistry;
import io.github.duckasteroid.cthugha.tab.TabGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenConfigParamsTest {

    private static final ActionContext CTX = new ActionContext() {
        @Override public void notify(String message) { }
        @Override public Random rng() { return new Random(1); }
    };

    private static class Root extends ParamNode {
        final DoubleParameter amplitude = new DoubleParameter("Amplitude", 0.0, 10.0, 2.0);
        final AtomicInteger fired = new AtomicInteger();
        final AbstractAction ping = new AbstractAction("Ping", ctx -> fired.incrementAndGet());
        final BindingSystem bindings = new BindingSystem();

        Root() {
            super("Root");
            addChild(amplitude);
            addChild(ping);
            addChild(bindings);
        }
    }

    @Test
    void capturesAndRestoresLeafValues() {
        ContainerNode root = new ContainerNode("Root");
        DoubleParameter amp = new DoubleParameter("amplitude", 0, 10, 2.0);
        BooleanParameter enabled = new BooleanParameter("enabled", true);
        root.addChild(amp);
        root.addChild(enabled);

        ScreenConfigParams.Snapshot snapshot = ScreenConfigParams.capture(root);

        amp.setValue(9.0);
        enabled.setValue(0);
        ScreenConfigParams.apply(root, snapshot);

        assertEquals(2.0, amp.value);
        assertTrue(enabled.value);
    }

    @Test
    void skipsPersistExcludedSubtrees() {
        ContainerNode root = new ContainerNode("Root");
        StringParameter transientField = new StringParameter("Save Name", "typed text");
        transientField.withNoPersist();
        DoubleParameter kept = new DoubleParameter("kept", 0, 10, 5.0);
        root.addChild(transientField);
        root.addChild(kept);

        ScreenConfigParams.Snapshot snapshot = ScreenConfigParams.capture(root);

        assertFalse(snapshot.values().containsKey("Save Name"));
        assertTrue(snapshot.values().containsKey("kept"));
    }

    /**
     * {@link GeneratorRegistry} rebuilds its own children when its "Generator" selector param
     * changes (a different generator instance is swapped in as a child). This is the real-world
     * case that motivates per-entry re-resolution in {@link ScreenConfigParams#apply}: a naive
     * single recursive descent would iterate a children list that gets mutated out from under it
     * as soon as the selector's value is applied.
     */
    @Test
    void appliesThroughSelectorDrivenTreeRestructuring() {
        GeneratorRegistry registry = new GeneratorRegistry();
        TabGenerator first = registry.getSources().get(0);
        TabGenerator other = registry.getSources().get(registry.getSources().size() - 1);
        assertNotEquals(first.getClass(), other.getClass(), "test needs >1 registered generator");

        registry.selectBySimpleName(first.getClass().getSimpleName());
        ScreenConfigParams.Snapshot snapshot = ScreenConfigParams.capture(registry);

        registry.selectBySimpleName(other.getClass().getSimpleName());
        assertEquals(other.getClass(), registry.getSelected().getClass());

        ScreenConfigParams.apply(registry, snapshot);

        assertEquals(first.getClass(), registry.getSelected().getClass());
    }

    @Test
    void captureDescribesDynamicChildListSubtrees() {
        Root root = new Root();
        root.bindings.init(new StaticClock(0.0, 0.0), root, CTX);
        root.bindings.addContinuous("anim", "Amplitude", "0.5");
        root.bindings.addEdgeTriggered("true", "Ping", 0.25, "");

        ScreenConfigParams.Snapshot snapshot = ScreenConfigParams.capture(root);

        List<ChildSpec> specs = snapshot.dynamicChildren().get("Bindings");
        assertEquals(2, specs.size());
        assertEquals("anim", specs.get(0).name());
        assertEquals("CONTINUOUS", specs.get(0).type());
        assertEquals("0.5", specs.get(0).fields().get("script"));
        assertEquals("Trigger 1", specs.get(1).name());
        assertEquals("EDGE_TRIGGERED", specs.get(1).type());
        assertEquals("true", specs.get(1).fields().get("condition"));
    }

    /**
     * The scenario from issue #6: a config saved with bindings, loaded into a session that
     * currently has none, must recreate them (via {@code recreate()}) *before* the flat
     * leaf-value map is applied — otherwise paths like {@code Bindings/Trigger 1/condition}
     * resolve to nothing and the values are silently dropped.
     */
    @Test
    void applyRecreatesDynamicChildrenBeforeLeafValuesSoTheyLandOnTheRightChild() {
        Root saved = new Root();
        saved.bindings.init(new StaticClock(0.0, 0.0), saved, CTX);
        ContinuousBinding anim = saved.bindings.addContinuous("anim", "Amplitude", "0.9");
        EdgeTriggeredBinding trigger = saved.bindings.addEdgeTriggered("bass() > 0.5", "Ping", 0.4, "");
        ScreenConfigParams.Snapshot snapshot = ScreenConfigParams.capture(saved);

        // A fresh session: zero bindings, matching the issue's "loading into 0" scenario.
        Root fresh = new Root();
        fresh.bindings.init(new StaticClock(0.0, 0.0), fresh, CTX);
        assertTrue(fresh.bindings.getChildren().noneMatch(c -> c.getName().equals("anim")));

        ScreenConfigParams.apply(fresh, snapshot);

        ContinuousBinding restoredAnim = fresh.bindings.findContinuousBindingFor("Amplitude").orElseThrow();
        assertEquals("0.9", restoredAnim.script.getValue());

        EdgeTriggeredBinding restoredTrigger = (EdgeTriggeredBinding) fresh.bindings.getChildren()
                .filter(c -> c.getName().equals("Trigger 1"))
                .findFirst()
                .orElseThrow();
        assertEquals("bass() > 0.5", restoredTrigger.condition.getValue());
        assertEquals(0.4, restoredTrigger.cooldown.value, 1e-9);
    }
}
