package io.github.duckasteroid.cthugha;

import com.asteroid.duck.opengl.util.blur.BlurTextureRenderer;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import io.github.duckasteroid.cthugha.display.phase.FlashPhase;
import io.github.duckasteroid.cthugha.display.phase.NotifPhase;
import io.github.duckasteroid.cthugha.display.phase.RenderPhase;
import io.github.duckasteroid.cthugha.display.phase.WavePhase;
import io.github.duckasteroid.cthugha.map.MapFileReader;
import io.github.duckasteroid.cthugha.map.PaletteActionContext;
import io.github.duckasteroid.cthugha.map.PaletteMap;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the param-tree structure.
 *
 * <p>Every path that appears in cthugha.ini [keys] must resolve to a node after
 * {@link ActionTreeBuilder#build()} runs.  If a node is renamed or the tree is restructured,
 * this test will catch it before a key binding silently stops working at runtime.</p>
 */
class ActionTreePathTest {

    private JCthugha app;

    @BeforeEach
    void setUp() throws IOException {
        app = new JCthugha();

        MapFileReader mockReader = mock(MapFileReader.class);
        when(mockReader.paletteFiles()).thenReturn(List.of(Path.of("maps/FAKE.MAP")));
        app.reader = mockReader;

        // Spy (not mock) so registerActions()/requestFlash() keep their real behaviour —
        // only the disk listing is stubbed, since the test doesn't run from src/dist.
        FlashPhase flashSpy = spy(new FlashPhase());
        doReturn(List.of(Path.of("images/FAKE.PNG"))).when(flashSpy).imageFiles();
        app.flashPhase = flashSpy;

        PaletteActionContext ctx = mock(PaletteActionContext.class);
        when(ctx.currentPalette()).thenReturn(null);
        when(ctx.rng()).thenReturn(new Random(0));

        BooleanParameter blurEnabled = new BooleanParameter("Enabled", true);
        IntegerParameter blurKernelSize = new IntegerParameter(
                "Kernel Size",
                BlurTextureRenderer.MIN_KERNEL_SIZE,
                BlurTextureRenderer.MAX_KERNEL_SIZE,
                5);
        DoubleParameter blurFade = new DoubleParameter("Softening", 0.0, 1.0, 0.99);

        ActionTreeBuilder.Callbacks noOp = new ActionTreeBuilder.Callbacks() {
            @Override public void rebuildTranslateMap() {}
            @Override public void markPaletteDirty() {}
            @Override public void screenshot() {}
            @Override public void startRecording() {}
            @Override public void stopRecording() {}
            @Override public void toggleFullscreen() {}
            @Override public void exitApplication() {}
        };

        // Phase objects register their own actions (Flash White, etc.)
        // No GL init() needed — registerActions() is pure param-tree wiring.
        List<RenderPhase> phases = List.of(
                new WavePhase(app),
                app.flashPhase,
                app.quotePhase,
                new NotifPhase(app));

        new ActionTreeBuilder(app, ctx, new RenderActionQueue(), blurEnabled, blurKernelSize, blurFade, noOp)
                .build(phases);
    }

    /** Every path referenced in cthugha.ini [keys] must exist in the built tree. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // App
            "General/Quit",
            // Display
            "General/Toggle Fullscreen",
            "General/Screenshot",
            "General/Record 5s",
            "Render/Palette/Random",
            "Images/Random",
            "Images/Flash White",
            "Quotes/Random",
            "General/Toggle Notifications",
            "Quotes/Toggle Quote Mode",
            "General/Cycle Audio",
            // Blur
            "Render/Blur/Fade -",
            "Render/Blur/Fade +",
            "Render/Blur/Kernel -",
            "Render/Blur/Kernel +",
            // Translation
            "Tab/Translate Source/Randomise",
            "Tab/Translate Source/New Source",
            "Tab/Translate Source/Next",
            "Tab/Translate Source/Previous",
            "Tab/Translate Source/Save",
            // Remote (added only when remote is enabled; excluded from path test)
    })
    void pathResolvesInTree(String path) {
        assertTrue(
                app.getChild(path.split("/")).isPresent(),
                "Expected param tree path not found: " + path);
    }

    @Test
    void rootHasTabsLayout() {
        assertTrue(UiHint.TABS.equals(app.getUiHints().get(UiHint.CONTROL_TYPE)),
                "Root node should have control-type=TABS hint");
    }
}
