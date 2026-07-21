package io.github.duckasteroid.cthugha.binding;

import com.asteroid.duck.opengl.util.timer.StaticClock;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link BindingSystem} as the single merged manager for both {@link ContinuousBinding}
 * ("animation") and {@link EdgeTriggeredBinding} ("trigger") entries — the phase-2 unification
 * called for by issue #2.
 */
class BindingSystemTest {

    private static class Root extends ParamNode {
        final DoubleParameter amplitude = new DoubleParameter("Amplitude", 0.0, 10.0, 2.0);
        final AtomicInteger fired = new AtomicInteger();
        final AbstractAction ping = new AbstractAction("Ping", ctx -> fired.incrementAndGet());

        Root() {
            super("Root");
            addChild(amplitude);
            addChild(ping);
        }
    }

    private static final ActionContext CTX = new ActionContext() {
        @Override public void notify(String message) { }
        @Override public Random rng() { return new Random(1); }
    };

    @Test
    void oneListDrivesBothContinuousAndEdgeTriggeredBindings() {
        Root root = new Root();
        BindingSystem bindings = new BindingSystem();
        root.addChild(bindings);
        bindings.init(new StaticClock(0.0, 0.0), root, CTX);

        bindings.addContinuous("anim", "Amplitude", "0.5");
        bindings.addEdgeTriggered("true", "Ping", 0.0, "");

        bindings.tick();

        assertEquals(5.0, root.amplitude.getValue().doubleValue(), 1e-9, "continuous binding pushed a normalised value");
        assertEquals(1, root.fired.get(), "edge-triggered binding fired the action");
    }

    @Test
    void findContinuousBindingForIgnoresEdgeTriggeredBindingsOnTheSameTargetPath() {
        BindingSystem bindings = new BindingSystem();
        Root root = new Root();
        root.addChild(bindings);
        bindings.init(new StaticClock(0.0, 0.0), root, CTX);

        // An edge-triggered binding happens to target the same leaf an animation would.
        bindings.addEdgeTriggered("true", "Amplitude", 0.0, "1.0");
        assertFalse(bindings.findContinuousBindingFor("Amplitude").isPresent(),
                "an EDGE_TRIGGERED binding on this path must not be mistaken for a CONTINUOUS one");

        ContinuousBinding anim = bindings.addContinuous("anim", "Amplitude", "0.5");
        assertTrue(bindings.findContinuousBindingFor("Amplitude").isPresent());
        assertEquals(anim, bindings.findContinuousBindingFor("Amplitude").get());
    }

    @Test
    void removeBindingSynchronouslyReleasesControlBeforeTheNextTick() {
        BindingSystem bindings = new BindingSystem();
        Root root = new Root();
        root.addChild(bindings);
        bindings.init(new StaticClock(0.0, 0.0), root, CTX);

        ContinuousBinding anim = bindings.addContinuous("anim", "Amplitude", "0.5");
        bindings.tick();
        assertTrue(root.amplitude.isControlled());

        bindings.removeBinding(anim);

        assertFalse(root.amplitude.isControlled(), "release() must run synchronously, before any further tick");
    }

    @Test
    void globalStateIsSharedBetweenBindingsCreatedThroughTheSameSystem() {
        BindingSystem bindings = new BindingSystem();
        Root root = new Root();
        root.addChild(bindings);
        bindings.init(new StaticClock(0.0, 0.0), root, CTX);

        bindings.addContinuous("writer", "Amplitude", "global.set(\"seen\", true) ? 1.0 : 0.0");
        bindings.tick();

        assertTrue((Boolean) bindings.globalState().get("seen"));
    }
}
