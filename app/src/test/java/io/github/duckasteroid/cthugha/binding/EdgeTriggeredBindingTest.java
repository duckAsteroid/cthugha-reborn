package io.github.duckasteroid.cthugha.binding;

import com.asteroid.duck.opengl.util.timer.Clock;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeTriggeredBindingTest {

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

    private static class MutableClock implements Clock {
        double t;
        @Override public double elapsed() { return t; }
    }

    private static final ActionContext CTX = new ActionContext() {
        @Override public void notify(String message) { }
        @Override public Random rng() { return new Random(1); }
    };

    @Test
    void firesActionOnceOnRisingEdge() {
        Root root = new Root();
        MutableClock clock = new MutableClock();
        EdgeTriggeredBinding binding = new EdgeTriggeredBinding("t", "true", "Ping", 0.0, "",
                new HashMap<>(), () -> {});
        binding.init(clock, root, CTX);

        binding.tick();
        binding.tick();
        binding.tick();

        assertEquals(1, root.fired.get(), "condition never goes false→true again, so it only fires once");
    }

    @Test
    void respectsCooldownBetweenFires() {
        Root root = new Root();
        MutableClock clock = new MutableClock();
        // Toggle every tick via state, so the rising edge is re-armed each time — cooldown is
        // then the only thing standing between "condition true" and "fires again".
        EdgeTriggeredBinding binding = new EdgeTriggeredBinding("t",
                "state.set(\"on\", !state.get(\"on\", false))", "Ping", 5.0, "", new HashMap<>(), () -> {});
        binding.init(clock, root, CTX);

        clock.t = 0.0;
        binding.tick(); // on=true: rising edge, fires
        clock.t = 1.0;
        binding.tick(); // on=false
        clock.t = 2.0;
        binding.tick(); // on=true again, but within cooldown (5s) of the first fire — suppressed

        assertEquals(1, root.fired.get());

        clock.t = 6.0;
        binding.tick(); // on=false
        clock.t = 7.0;
        binding.tick(); // on=true, cooldown has elapsed — fires again

        assertEquals(2, root.fired.get());
    }

    @Test
    void appliesValueToASettableLeafInsteadOfExecutingWhenTargetIsNotAnAction() {
        Root root = new Root();
        MutableClock clock = new MutableClock();
        EdgeTriggeredBinding binding = new EdgeTriggeredBinding("t", "true", "Amplitude", 0.0, "7.5",
                new HashMap<>(), () -> {});
        binding.init(clock, root, CTX);

        binding.tick();

        assertEquals(7.5, root.amplitude.getValue().doubleValue(), 1e-9);
        assertEquals("OK", binding.status.getValue());
    }

    @Test
    void setsStatusWhenTargetPathDoesNotResolve() {
        Root root = new Root();
        MutableClock clock = new MutableClock();
        EdgeTriggeredBinding binding = new EdgeTriggeredBinding("t", "true", "NoSuchNode", 0.0, "",
                new HashMap<>(), () -> {});
        binding.init(clock, root, CTX);

        binding.tick();

        assertTrue(binding.status.getValue().startsWith("Target not found"));
        assertEquals(0, root.fired.get());
    }
}
