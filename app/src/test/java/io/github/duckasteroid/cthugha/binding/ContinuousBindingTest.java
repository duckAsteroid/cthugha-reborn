package io.github.duckasteroid.cthugha.binding;

import com.asteroid.duck.opengl.util.timer.StaticClock;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousBindingTest {

    /** Mimics a wave model: a container with its own "enabled" toggle plus a driven value. */
    private static class Section extends ParamNode {
        final BooleanParameter enabled = new BooleanParameter("enabled", true);
        final DoubleParameter value = new DoubleParameter("value", 0.0, 1.0, 0.0);

        Section() {
            super("Section");
            addChild(enabled);
            addChild(value);
        }
    }

    private static ContinuousBinding bindingFor(Section section, String targetName, String script) {
        ContinuousBinding binding = new ContinuousBinding("b", targetName, script, new HashMap<>());
        binding.init(new StaticClock(0.0, 0.0), section, null);
        return binding;
    }

    @Test
    void ticksNormallyWhenSectionEnabled() {
        Section section = new Section();
        ContinuousBinding binding = bindingFor(section, "value", "0.75");

        binding.tick();

        assertEquals(0.75, section.value.value, 1e-9);
    }

    @Test
    void skipsUpdatingValueWhenSectionDisabled() {
        Section section = new Section();
        section.enabled.value = false;
        section.value.value = 0.2;
        ContinuousBinding binding = bindingFor(section, "value", "0.75");

        binding.tick();

        // Nobody can see this value while the section is disabled — the binding should leave
        // it untouched rather than pointlessly evaluating the script and firing change/SSE events.
        assertEquals(0.2, section.value.value, 1e-9);
    }

    @Test
    void resumesUpdatingValueOnceSectionReenabled() {
        Section section = new Section();
        section.enabled.value = false;
        section.value.value = 0.2;
        ContinuousBinding binding = bindingFor(section, "value", "0.75");
        binding.tick();
        assertEquals(0.2, section.value.value, 1e-9);

        section.enabled.value = true;
        binding.tick();

        assertEquals(0.75, section.value.value, 1e-9);
    }

    @Test
    void animatingTheEnabledFlagItselfIsNotGatedByItself() {
        Section section = new Section();
        section.enabled.value = false;
        ContinuousBinding binding = bindingFor(section, "enabled", "1.0");

        binding.tick();

        assertTrue(section.enabled.value);
    }

    @Test
    void targetIsResolvedLazilyEachTickRatherThanHeldAsADirectReference() {
        Section section = new Section();
        ContinuousBinding binding = bindingFor(section, "does-not-exist-yet", "0.75");

        // Target doesn't resolve: no exception, nothing driven.
        binding.tick();
        assertFalse(section.value.isControlled());

        // Retarget by editing the path — the very next tick picks it up with no re-init.
        binding.target.setValue("value");
        binding.tick();

        assertEquals(0.75, section.value.value, 1e-9);
        assertTrue(section.value.isControlled());
    }

    @Test
    void deletingTheTargetReleasesControlWithNoExplicitCleanupCall() {
        Section section = new Section();
        ContinuousBinding binding = bindingFor(section, "value", "0.75");
        binding.tick();
        assertTrue(section.value.isControlled());
        assertSame(binding, section.value.getAnimationBinding());

        // Simulate the target node being deleted from the tree (e.g. a removed wave, see #7).
        section.removeChild(section.value);

        binding.tick();

        assertFalse(section.value.isControlled(), "binding should release control once its target stops resolving");
        assertNull(section.value.getAnimationBinding());
    }

    @Test
    void scriptLocalStateSurvivesAcrossTicksAndIsPrivateToThisBinding() {
        // Scripts are a single expression (no statement sequencing), so state.set returns the
        // value it just stored — letting a script both update and use its own counter in one go.
        Section section = new Section();
        Map<String, Object> globalState = new HashMap<>();
        ContinuousBinding counter = new ContinuousBinding("counter", "value",
                "state.set(\"n\", state.get(\"n\", 0.0) + 1) >= 3 ? 1.0 : 0.0",
                globalState);
        counter.init(new StaticClock(0.0, 0.0), section, null);

        counter.tick();
        assertEquals(0.0, section.value.value, 1e-9, "n==1 after the first tick");
        counter.tick();
        assertEquals(0.0, section.value.value, 1e-9, "n==2 after the second tick");
        counter.tick();
        assertEquals(1.0, section.value.value, 1e-9, "n==3 on the third tick, so the script returns 1.0");

        assertEquals(3.0, counter.localState().get("n"), "state is exposed on the binding for inspection/tests");
    }

    @Test
    void globalStateIsSharedAcrossBindings() {
        Section section = new Section();
        Map<String, Object> globalState = new HashMap<>();
        ContinuousBinding writer = new ContinuousBinding("writer", "value",
                "global.set(\"flag\", true) ? 1.0 : 0.0", globalState);
        writer.init(new StaticClock(0.0, 0.0), section, null);

        assertFalse((Boolean) globalState.getOrDefault("flag", false));
        writer.tick();
        assertTrue((Boolean) globalState.get("flag"), "global.set from one binding's script is visible via the shared map");
    }
}
