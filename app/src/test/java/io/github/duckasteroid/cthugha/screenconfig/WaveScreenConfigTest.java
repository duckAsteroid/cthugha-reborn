package io.github.duckasteroid.cthugha.screenconfig;

import com.asteroid.duck.opengl.util.timer.StaticClock;
import io.github.duckasteroid.cthugha.binding.BindingSystem;
import io.github.duckasteroid.cthugha.binding.ContinuousBinding;
import io.github.duckasteroid.cthugha.display.wave.OscilloscopeModel;
import io.github.duckasteroid.cthugha.display.wave.RadialSpectrumModel;
import io.github.duckasteroid.cthugha.display.wave.WaveSystem;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers issue #7's two headline requirements for {@code WaveSystem}: that its dynamic instance
 * list round-trips through a screen config (via {@link io.github.duckasteroid.cthugha.params.DynamicChildList}),
 * and that a binding targeting a param inside a since-deleted wave instance detaches cleanly
 * rather than erroring — the behaviour issue #7 expected to get "for free" from #12's path-based
 * lazy target resolution (see {@code Binding#resolveTarget}).
 */
class WaveScreenConfigTest {

    private static final ActionContext CTX = new ActionContext() {
        @Override public void notify(String message) { }
        @Override public Random rng() { return new Random(1); }
    };

    private static class Root extends ParamNode {
        final WaveSystem waveSystem = new WaveSystem();
        final BindingSystem bindings = new BindingSystem();

        Root() {
            super("Root");
            addChild(waveSystem);
            addChild(bindings);
        }
    }

    @Test
    void savingAndReloadingAConfigRecreatesMixedWaveTypesWithTheirParams() {
        Root saved = new Root();
        OscilloscopeModel osc1 = (OscilloscopeModel) saved.waveSystem.addWave(WaveSystem.WaveType.OSCILLOSCOPE);
        osc1.amplitude.setValue(3.5);
        RadialSpectrumModel rs1 = (RadialSpectrumModel) saved.waveSystem.addWave(WaveSystem.WaveType.RADIAL_SPECTRUM);
        rs1.repeats.setValue(4);
        OscilloscopeModel osc2 = (OscilloscopeModel) saved.waveSystem.addWave(WaveSystem.WaveType.OSCILLOSCOPE);
        osc2.amplitude.setValue(7.0);

        ScreenConfigParams.Snapshot snapshot = ScreenConfigParams.capture(saved);

        Root fresh = new Root();
        assertTrue(fresh.waveSystem.instances().isEmpty(), "fresh session starts with zero wave instances");

        ScreenConfigParams.apply(fresh, snapshot);

        List<ParamNode> restored = fresh.waveSystem.instances();
        assertEquals(3, restored.size());
        assertEquals("Oscilloscope 1", restored.get(0).getName());
        assertEquals("RadialSpectrum 1", restored.get(1).getName());
        assertEquals("Oscilloscope 2", restored.get(2).getName());

        assertEquals(3.5, ((OscilloscopeModel) restored.get(0)).amplitude.value, 1e-9);
        assertEquals(4, ((RadialSpectrumModel) restored.get(1)).repeats.value);
        assertEquals(7.0, ((OscilloscopeModel) restored.get(2)).amplitude.value, 1e-9);
    }

    @Test
    void bindingTargetingADeletedWaveStopsResolvingInsteadOfThrowing() {
        Root root = new Root();
        root.bindings.init(new StaticClock(0.0, 0.0), root, CTX);

        OscilloscopeModel osc = (OscilloscopeModel) root.waveSystem.addWave(WaveSystem.WaveType.OSCILLOSCOPE);
        String path = osc.amplitude.getFullPath(); // e.g. "Wave/Oscilloscope 1/amplitude"

        root.bindings.addContinuous("wave amp", path, "0.5");
        root.bindings.tick();
        assertTrue(osc.amplitude.isControlled(), "binding should take control once its target resolves");

        root.waveSystem.removeWave(osc);

        assertDoesNotThrow(root.bindings::tick,
                "a binding whose target subtree was deleted must not throw on tick");
        assertFalse(osc.amplitude.isControlled(),
                "binding must release control of the deleted wave's param once its path stops resolving");
    }
}
