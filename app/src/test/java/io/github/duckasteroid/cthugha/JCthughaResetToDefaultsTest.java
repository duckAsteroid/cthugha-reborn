package io.github.duckasteroid.cthugha;

import com.asteroid.duck.opengl.util.blur.BlurTextureRenderer;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import io.github.duckasteroid.cthugha.binding.ContinuousBinding;
import io.github.duckasteroid.cthugha.display.phase.FlashPhase;
import io.github.duckasteroid.cthugha.display.phase.NotifPhase;
import io.github.duckasteroid.cthugha.display.phase.RenderPhase;
import io.github.duckasteroid.cthugha.display.wave.OscilloscopeModel;
import io.github.duckasteroid.cthugha.display.wave.RadialWaveModel;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.map.PaletteActionContext;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Covers issue #3's fix for the "unwanted default animations" half of the non-deterministic
 * startup: {@code JCthugha#resetToDefaults} used to run unconditionally, on every launch, wiring
 * two hardcoded {@code ContinuousBinding}s onto the constructor-seeded wave instances. It's now
 * only invoked when there's no persisted "current" state to restore instead (a genuine first-ever
 * launch), or explicitly via the "Reset to Defaults" action -- see {@code
 * JCthugha#restoreCurrentStateOrDefaults}.
 */
class JCthughaResetToDefaultsTest {

    /** Builds a fully-wired tree (same shape {@code ActionTreeBuilder.build} produces) without any GL context. */
    private static JCthugha buildTree() throws IOException {
        return buildTree(Path.of("configs"));
    }

    /** As {@link #buildTree()}, but pointing named-config/current-state storage at {@code configsRoot}. */
    private static JCthugha buildTree(Path configsRoot) throws IOException {
        JCthugha app = new JCthugha(configsRoot);

        MapFileReader mockReader = mock(MapFileReader.class);
        when(mockReader.paletteFiles()).thenReturn(List.of(Path.of("maps/FAKE.MAP")));
        app.reader = mockReader;

        FlashPhase flashSpy = spy(new FlashPhase());
        doReturn(List.of(Path.of("images/FAKE.PNG"))).when(flashSpy).imageFiles();
        app.flashPhase = flashSpy;

        PaletteActionContext ctx = mock(PaletteActionContext.class);
        when(ctx.currentPalette()).thenReturn(null);
        when(ctx.rng()).thenReturn(new Random(0));

        BooleanParameter blurEnabled = new BooleanParameter("Enabled", true);
        IntegerParameter blurKernelSize = new IntegerParameter(
                "Kernel Size", BlurTextureRenderer.MIN_KERNEL_SIZE, BlurTextureRenderer.MAX_KERNEL_SIZE, 5);
        DoubleParameter blurFade = new DoubleParameter("Softening", 0.0, 1.0, 0.99);
        BooleanParameter fullscreenEnabled = new BooleanParameter("Fullscreen", false);

        ActionTreeBuilder.Callbacks noOp = new ActionTreeBuilder.Callbacks() {
            @Override public void rebuildTranslateMap() {}
            @Override public void markPaletteDirty() {}
            @Override public void screenshot() {}
            @Override public void startRecording() {}
            @Override public void stopRecording() {}
            @Override public void exitApplication() {}
        };

        List<RenderPhase> phases = List.of(app.wavePhase, app.flashPhase, app.quotePhase, new NotifPhase(app));
        new ActionTreeBuilder(app, ctx, new RenderActionQueue(), blurEnabled, blurKernelSize, blurFade, fullscreenEnabled, noOp)
                .build(phases);
        return app;
    }

    /** {@code ContinuousBinding} children are marked {@code withNoRemote()} but remain ordinary tree children. */
    private static List<ContinuousBinding> continuousBindings(JCthugha app) {
        return app.bindings.getChildren()
                .filter(ContinuousBinding.class::isInstance)
                .map(ContinuousBinding.class::cast)
                .toList();
    }

    @Test
    void freshTreeHasNoWavesOrBindingsUntilResetToDefaultsRuns() throws IOException {
        JCthugha app = buildTree();
        assertTrue(app.waveSystem.instances().isEmpty(),
                "constructor must not seed waves any more -- that's resetToDefaults()'s job");
        assertTrue(continuousBindings(app).isEmpty(), "no bindings should be pre-wired either");
    }

    @Test
    void resetToDefaultsSeedsOneOfEachWaveTypePlusTwoRotationAnimations() throws IOException {
        JCthugha app = buildTree();
        app.resetToDefaults();

        List<ParamNode> waves = app.waveSystem.instances();
        assertEquals(4, waves.size());
        assertEquals("Oscilloscope 1", waves.get(0).getName());
        assertEquals("RadialWave 1", waves.get(1).getName());
        assertEquals("Spectrum 1", waves.get(2).getName());
        assertEquals("RadialSpectrum 1", waves.get(3).getName());

        OscilloscopeModel osc = (OscilloscopeModel) waves.get(0);
        RadialWaveModel radial = (RadialWaveModel) waves.get(1);

        List<ContinuousBinding> bindings = continuousBindings(app);
        assertEquals(2, bindings.size());
        assertTrue(bindings.stream().anyMatch(b -> b.target.getValue().equals(osc.transform.rotate.getFullPath())));
        assertTrue(bindings.stream().anyMatch(b -> b.target.getValue().equals(radial.transform.rotate.getFullPath())));
    }

    @Test
    void restoreCurrentStateOrDefaultsFallsBackToDefaultsOnFirstLaunch(@TempDir Path configsDir) throws IOException {
        JCthugha app = buildTree(configsDir);

        app.restoreCurrentStateOrDefaults();

        assertEquals(4, app.waveSystem.instances().size(), "no .current.json yet -- should fall back to defaults");
        app.close();
    }

    @Test
    void restoreCurrentStateOrDefaultsRestoresPersistedStateInsteadOfDefaults(@TempDir Path configsDir) throws IOException {
        JCthugha seed = buildTree(configsDir);
        seed.resetToDefaults();
        // Delete one wave so the persisted state is distinguishable from a fresh resetToDefaults().
        seed.waveSystem.removeWave(seed.waveSystem.instances().get(3));
        seed.currentStateStore.save(seed);

        JCthugha app = buildTree(configsDir);
        app.restoreCurrentStateOrDefaults();

        assertEquals(3, app.waveSystem.instances().size(),
                "the persisted (3-wave) state must win over resetToDefaults()'s implicit 4-wave fallback");
        app.close();
    }
}
