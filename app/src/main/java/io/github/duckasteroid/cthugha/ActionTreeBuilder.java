package io.github.duckasteroid.cthugha;

import com.asteroid.duck.opengl.util.blur.BlurTextureRenderer;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import io.github.duckasteroid.cthugha.display.phase.RenderPhase;
import io.github.duckasteroid.cthugha.map.PaletteActionContext;
import io.github.duckasteroid.cthugha.map.PaletteLibraryNode;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.action.ActionContext;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import io.github.duckasteroid.cthugha.screenconfig.ScreenConfigLibraryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Assembles the full parameter tree onto a {@link JCthugha} root node.
 *
 * <p>Extracted from {@code CthughaWindow.registerDisplayActions()} so that tree wiring
 * can be tested independently of the GL context. The caller supplies a {@link Callbacks}
 * implementation that delegates GL-thread and window-level operations back to the window.</p>
 */
public class ActionTreeBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(ActionTreeBuilder.class);

    /**
     * GL-thread and window-level operations provided by the caller (typically
     * {@code CthughaWindow}).  Methods marked "GL thread" are invoked inside a
     * {@code renderActions.enqueue()} lambda and run on the render thread.
     */
    public interface Callbacks {
        void rebuildTranslateMap();  // GL thread
        void markPaletteDirty();
        void screenshot();           // GL thread
        void startRecording();       // GL thread
        void stopRecording();        // GL thread
        void toggleFullscreen();     // GL thread
        void exitApplication();
    }

    private final JCthugha cthugha;
    private final PaletteActionContext actionContext;
    private final RenderActionQueue renderActions;
    private final BooleanParameter blurEnabled;
    private final IntegerParameter blurKernelSize;
    private final DoubleParameter blurFade;
    private final Callbacks callbacks;

    private ContainerNode generalGroup;

    public ActionTreeBuilder(JCthugha cthugha,
                             PaletteActionContext actionContext,
                             RenderActionQueue renderActions,
                             BooleanParameter blurEnabled,
                             IntegerParameter blurKernelSize,
                             DoubleParameter blurFade,
                             Callbacks callbacks) {
        this.cthugha = cthugha;
        this.actionContext = actionContext;
        this.renderActions = renderActions;
        this.blurEnabled = blurEnabled;
        this.blurKernelSize = blurKernelSize;
        this.blurFade = blurFade;
        this.callbacks = callbacks;
    }

    /**
     * Builds the Wave / Tab / Render / General tab groups and mounts them on the
     * {@link JCthugha} root node.  Must be called exactly once after
     * {@link JCthugha#init} and before the render loop starts.
     *
     * @param phases the ordered list of render phases; each phase registers its own actions
     */
    public void build(List<RenderPhase> phases) {
        // ---- Wave tab: audio-reactive renderers and animation ----
        ContainerNode waveGroup = new ContainerNode("Wave");
        waveGroup.withUiHint(UiHint.ICON, "music");
        waveGroup.addChild(cthugha.oscilloscope);
        waveGroup.addChild(cthugha.radialWave);
        waveGroup.addChild(cthugha.spectrum);
        waveGroup.addChild(cthugha.radialSpectrum);
        waveGroup.addChild(cthugha.animation);

        // ---- Tab tab: translation-table generator ----
        ContainerNode tabGroup = new ContainerNode("Tab");
        tabGroup.withUiHint(UiHint.ICON, "layers");
        tabGroup.addChild(cthugha.translateSource);

        cthugha.translateSource.addChild(action("Randomise", "shuffle", ctx -> {
            cthugha.newTranslation(ctx.rng());
            renderActions.enqueue("rebuildTranslateMap", rc -> callbacks.rebuildTranslateMap());
        }));
        cthugha.translateSource.addChild(action("New Source", "plus-circle", ctx ->
                cthugha.translateSource.selectRandom(ctx.rng())));
        // Next/Previous are hidden from remote UI but remain in the tree for INI key bindings.
        AbstractAction nextGen = action("Next", "skip-forward", ctx ->
                cthugha.translateSource.stepSelection(+1));
        nextGen.withUiHint(UiHint.HIDDEN, "true");
        cthugha.translateSource.addChild(nextGen);
        AbstractAction prevGen = action("Previous", "skip-back", ctx ->
                cthugha.translateSource.stepSelection(-1));
        prevGen.withUiHint(UiHint.HIDDEN, "true");
        cthugha.translateSource.addChild(prevGen);

        // ---- Render tab: palette and blur ----
        ContainerNode renderGroup = new ContainerNode("Render");
        renderGroup.withUiHint(UiHint.ICON, "monitor");
        try {
            renderGroup.addChild(new PaletteLibraryNode(cthugha.reader, actionContext,
                    callbacks::markPaletteDirty));
        } catch (IOException e) {
            LOG.error("Failed to build palette library node", e);
            renderGroup.addChild(action("New Palette", "plus-circle", ctx -> {
                cthugha.newPalette();
                callbacks.markPaletteDirty();
            }));
        }

        ContainerNode blurNode = new ContainerNode("Blur");
        blurNode.withUiHint(UiHint.ICON, "wind");
        blurEnabled.withDescription("Turns the flame-spread blur pass on or off.");
        blurKernelSize.withDescription("Size of the Gaussian blur kernel, in pixels. Larger spreads the flame further per frame.");
        blurFade.withDescription("How much brightness the blurred image loses each frame. Higher fades trails out faster.");
        blurNode.addChild(blurEnabled);
        blurNode.addChild(blurKernelSize);
        blurNode.addChild(blurFade);
        blurNode.addChild(action("Kernel -", "minus", ctx -> {
            if (blurEnabled.value && blurKernelSize.value <= BlurTextureRenderer.MIN_KERNEL_SIZE) {
                blurEnabled.setValue(0);
                cthugha.notify("blur: OFF");
            } else if (blurEnabled.value) {
                blurKernelSize.setValue(blurKernelSize.value - 2);
                cthugha.notify("blur kernel: " + blurKernelSize.value);
            }
        }));
        blurNode.addChild(action("Kernel +", "plus", ctx -> {
            if (!blurEnabled.value) {
                blurEnabled.setValue(1);
                blurKernelSize.setValue(BlurTextureRenderer.MIN_KERNEL_SIZE);
                cthugha.notify("blur kernel: " + blurKernelSize.value);
            } else {
                blurKernelSize.setValue(blurKernelSize.value + 2);
                cthugha.notify("blur kernel: " + blurKernelSize.value);
            }
        }));
        blurNode.addChild(action("Fade -", "minus-circle", ctx -> {
            blurFade.setValue(Math.max(0.0, blurFade.value - 0.005));
            cthugha.notify(String.format("fade: %.3f", blurFade.value));
        }));
        blurNode.addChild(action("Fade +", "plus-circle", ctx -> {
            blurFade.setValue(Math.min(1.0, blurFade.value + 0.005));
            cthugha.notify(String.format("fade: %.3f", blurFade.value));
        }));
        renderGroup.addChild(blurNode);

        // ---- Configs tab: named whole-tree snapshots ("screen configs") ----
        ScreenConfigLibraryNode configsGroup = new ScreenConfigLibraryNode(cthugha.screenConfigStore, cthugha);

        // ---- General group: persistent expander below the tabs ----
        generalGroup = new ContainerNode("General");
        generalGroup.withUiHint(UiHint.ICON, "settings");
        generalGroup.withUiHint(UiHint.CONTROL_TYPE, UiHint.EXPANDER);
        generalGroup.addChild(action("Quit", "x-circle", ctx -> {
            try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing", e); }
            callbacks.exitApplication();
        }).withNoRemote());
        generalGroup.addChild(action("Screenshot", "camera", ctx -> {
            renderActions.enqueue("screenshot", rc -> callbacks.screenshot());
            cthugha.notify("screenshot saved");
        }));
        generalGroup.addChild(action("Record 5s", "video", ctx -> {
            renderActions.enqueue("startRecording", rc -> callbacks.startRecording());
            cthugha.notify("recording 5s…");
        }));
        generalGroup.addChild(action("Stop Recording", "square", ctx -> {
            renderActions.enqueue("stopRecording", rc -> callbacks.stopRecording());
            cthugha.notify("recording stopped");
        }));
        generalGroup.addChild(action("Toggle Fullscreen", "maximize-2", ctx ->
                renderActions.enqueue("toggleFullscreen", rc -> callbacks.toggleFullscreen())));
        generalGroup.addChild(action("Toggle Debug", "bug", ctx -> cthugha.toggleDebug()));
        generalGroup.addChild(action("Toggle Notifications", "bell", ctx -> cthugha.toggleNotifications()));

        // Each phase registers its own actions (Flash Image, Flash White, Show Quote,
        // Toggle Quote Mode, Cycle Audio, etc.)
        for (RenderPhase phase : phases) {
            phase.registerActions(generalGroup, renderActions);
        }

        // ---- Root layout ----
        cthugha.withUiHint(UiHint.CONTROL_TYPE, UiHint.TABS);
        cthugha.addChild(waveGroup);
        cthugha.addChild(tabGroup);
        cthugha.addChild(renderGroup);
        cthugha.addChild(configsGroup);
        cthugha.addChild(generalGroup);
    }

    /** The General group node; available after {@link #build()} for adding the Remote sub-node. */
    public ContainerNode getGeneralGroup() {
        return generalGroup;
    }

    private static AbstractAction action(String name, String icon, Consumer<ActionContext> body) {
        AbstractAction a = new AbstractAction(name, body);
        a.withUiHint(UiHint.ICON, icon);
        return a;
    }
}
