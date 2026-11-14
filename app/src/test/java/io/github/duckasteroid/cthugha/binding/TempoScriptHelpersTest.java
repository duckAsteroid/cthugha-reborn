package io.github.duckasteroid.cthugha.binding;

import com.asteroid.duck.opengl.util.timer.StaticClock;
import io.github.duckasteroid.cthugha.display.BeatClock;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TempoScriptHelpersTest {

    private record Fixed(double bpm, float confidence, double beatPhase, long beatCount, float kickStrength)
            implements BeatClock.Source {}

    private static class Section extends ParamNode {
        final DoubleParameter value = new DoubleParameter("value", 0.0, 1.0, 0.0);

        Section() {
            super("Section");
            addChild(value);
        }
    }

    @AfterEach
    void clearContext() {
        ScriptHelpers.setContext(null, null, null);
    }

    private static double evaluate(String script) {
        Section section = new Section();
        ContinuousBinding binding = new ContinuousBinding("b", "value", script, new HashMap<>());
        binding.init(new StaticClock(0.0, 0.0), section, null);
        binding.tick();
        return section.value.value;
    }

    private static void tracker(Fixed source) {
        ScriptHelpers.setContext(null, new BeatClock(source, () -> BeatClock.TempoScale.NORMAL), new Random(1));
    }

    @Test
    void tempoHelpersReadTheBeatClock() {
        tracker(new Fixed(128, 0.9f, 0.5, 6, 0.4f));

        assertEquals(128 / 200.0, evaluate("bpm() / 200"), 1e-9);
        assertEquals(0.9, evaluate("beatConfidence()"), 1e-6);
        assertEquals(0.5, evaluate("beatPhase()"), 1e-9);
        assertEquals(0.5, evaluate("beatCount() % 4 / 4.0"), 1e-9);
        assertEquals(Math.exp(-3) * 0.9, evaluate("beatPulse()"), 1e-6);
        assertEquals(2.5 / 4, evaluate("beatRamp(4)"), 1e-9);
        assertEquals(0.0, evaluate("beatSine(1)"), 1e-9);
        assertEquals(0.4, evaluate("kick()"), 1e-6);
    }

    @Test
    void bpmIsZeroBeforeLockingOn() {
        tracker(new Fixed(Double.NaN, 0f, 0, 0, 0f));

        assertEquals(0.0, evaluate("bpm()"), 1e-9);
    }

    @Test
    void tempoHelpersAreZeroWithoutATracker() {
        assertEquals(0.0, evaluate("bpm() + beatPhase() + beatPulse() + kick() + beatCount()"), 1e-9);
    }
}
