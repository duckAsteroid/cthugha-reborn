package io.github.duckasteroid.cthugha.animation;

import com.asteroid.duck.opengl.util.timer.StaticClock;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationBindingTest {

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

    @Test
    void ticksNormallyWhenSectionEnabled() {
        Section section = new Section();
        AnimationBinding binding = new AnimationBinding("b", section.value, "0.75");
        binding.init(new StaticClock(0.0, 0.0));

        binding.tick();

        assertEquals(0.75, section.value.value, 1e-9);
    }

    @Test
    void skipsUpdatingValueWhenSectionDisabled() {
        Section section = new Section();
        section.enabled.value = false;
        section.value.value = 0.2;
        AnimationBinding binding = new AnimationBinding("b", section.value, "0.75");
        binding.init(new StaticClock(0.0, 0.0));

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
        AnimationBinding binding = new AnimationBinding("b", section.value, "0.75");
        binding.init(new StaticClock(0.0, 0.0));
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
        AnimationBinding binding = new AnimationBinding("b", section.enabled, "1.0");
        binding.init(new StaticClock(0.0, 0.0));

        binding.tick();

        assertTrue(section.enabled.value);
    }
}
