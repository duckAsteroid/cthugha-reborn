package io.github.duckasteroid.cthugha.display.wave;

import io.github.duckasteroid.cthugha.params.DynamicChildList.ChildSpec;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.ParamNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link WaveSystem} as the dynamic, ordered list of wave instances that replaced the
 * old fixed one-of-each-type fields (issue #7).
 */
class WaveSystemTest {

    @Test
    void addWaveAutoNamesInstancesByTypeAndIndex() {
        WaveSystem waves = new WaveSystem();

        ParamNode first = waves.addWave(WaveSystem.WaveType.OSCILLOSCOPE);
        ParamNode second = waves.addWave(WaveSystem.WaveType.OSCILLOSCOPE);
        ParamNode radial = waves.addWave(WaveSystem.WaveType.RADIAL_WAVE);

        assertEquals("Oscilloscope 1", first.getName());
        assertEquals("Oscilloscope 2", second.getName());
        assertEquals("RadialWave 1", radial.getName());
        assertEquals(List.of(first, second, radial), waves.instances());
    }

    @Test
    void renderOrderMatchesAppendOrderAndSurvivesDeletionOfAnEarlierEntry() {
        WaveSystem waves = new WaveSystem();

        ParamNode a = waves.addWave(WaveSystem.WaveType.SPECTRUM);
        ParamNode b = waves.addWave(WaveSystem.WaveType.RADIAL_SPECTRUM);
        ParamNode c = waves.addWave(WaveSystem.WaveType.SPECTRUM);

        waves.removeWave(a);
        ParamNode d = waves.addWave(WaveSystem.WaveType.SPECTRUM);

        // b, c keep their relative order; the newly-appended d lands at the end (no reordering).
        assertEquals(List.of(b, c, d), waves.instances());
        assertEquals("Spectrum 3", d.getName(), "auto-naming counter must not reuse a's index");
    }

    @Test
    void deleteActionRemovesTheInstanceFromTheListAndTheTree() {
        WaveSystem waves = new WaveSystem();
        ParamNode osc = waves.addWave(WaveSystem.WaveType.OSCILLOSCOPE);
        assertTrue(waves.getChildren().anyMatch(c -> c == osc));

        Node delete = osc.getChild("Delete").orElseThrow();
        ((io.github.duckasteroid.cthugha.params.action.Action) delete).execute(NoopActionContext.INSTANCE);

        assertTrue(waves.instances().isEmpty());
        assertFalse(waves.getChildren().anyMatch(c -> c == osc), "deleted instance must be detached from the tree");
    }

    @Test
    void describeCapturesNameAndTypeForEveryInstance() {
        WaveSystem waves = new WaveSystem();
        waves.addWave(WaveSystem.WaveType.OSCILLOSCOPE);
        waves.addWave(WaveSystem.WaveType.RADIAL_SPECTRUM);

        List<ChildSpec> specs = waves.describe();

        assertEquals(2, specs.size());
        assertEquals("Oscilloscope 1", specs.get(0).name());
        assertEquals("OSCILLOSCOPE", specs.get(0).type());
        assertEquals("RadialSpectrum 1", specs.get(1).name());
        assertEquals("RADIAL_SPECTRUM", specs.get(1).type());
    }

    @Test
    void recreateClearsAndRebuildsFromSpecsInOrderPreservingNames() {
        WaveSystem waves = new WaveSystem();
        waves.addWave(WaveSystem.WaveType.OSCILLOSCOPE);
        List<ChildSpec> specs = waves.describe();
        // Independently tweak a param so we can prove recreate() rebuilds a fresh instance
        // rather than reusing the old one.
        OscilloscopeModel original = (OscilloscopeModel) waves.instances().get(0);
        original.amplitude.setValue(9.0);

        WaveSystem fresh = new WaveSystem();
        fresh.recreate(specs);

        assertEquals(1, fresh.instances().size());
        OscilloscopeModel recreated = (OscilloscopeModel) fresh.instances().get(0);
        assertEquals("Oscilloscope 1", recreated.getName());
        assertEquals(0.2, recreated.amplitude.value, 1e-9, "recreate() must build a fresh default instance");
    }

    private enum NoopActionContext implements io.github.duckasteroid.cthugha.params.action.ActionContext {
        INSTANCE;

        @Override
        public void notify(String message) { }

        @Override
        public java.util.Random rng() {
            return new java.util.Random(1);
        }
    }
}
