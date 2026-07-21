package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.GLWindow;
import com.asteroid.duck.opengl.util.TranslateTextureRenderer;
import com.asteroid.duck.opengl.util.blur.BlurTextureRenderer;
import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariable;
import com.asteroid.duck.opengl.util.resources.texture.DataFormat;
import com.asteroid.duck.opengl.util.resources.texture.TextureFactory;
import com.asteroid.duck.opengl.util.resources.texture.TextureOptions;

import com.asteroid.duck.opengl.util.palette.PaletteRenderer;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.framebuffer.FrameBuffer;
import com.asteroid.duck.opengl.util.resources.io.PathBasedLoader;
import com.asteroid.duck.opengl.util.resources.manager.ResourceManagerImpl;
import com.asteroid.duck.opengl.util.resources.texture.Filter;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.Wrap;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import io.github.duckasteroid.cthugha.ActionTreeBuilder;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.animation.ScriptHelpers;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.display.phase.QrPhase;
import io.github.duckasteroid.cthugha.display.phase.RenderPhase;
import io.github.duckasteroid.cthugha.display.phase.WorkPhase;
import io.github.duckasteroid.cthugha.tab.TabBuffer;
import io.github.duckasteroid.cthugha.work.BackgroundWorkQueue;
import io.github.duckasteroid.cthugha.map.PaletteMap;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.duckasteroid.cthugha.params.action.AbstractAction;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import io.github.duckasteroid.cthugha.remote.NetworkUtils;
import io.github.duckasteroid.cthugha.remote.QrOverlay;
import io.github.duckasteroid.cthugha.dump.DumpConfig;
import io.github.duckasteroid.cthugha.remote.RemoteConfig;
import io.github.duckasteroid.cthugha.remote.RemoteEventBroadcaster;
import io.github.duckasteroid.cthugha.remote.RemoteServer;
import io.github.duckasteroid.cthugha.remote.TokenStore;

import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.asteroid.duck.opengl.util.Monitor;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_R16;
import static org.lwjgl.opengl.GL30.GL_RG16UI;
import static org.lwjgl.opengl.GL30.GL_RG_INTEGER;

public class CthughaWindow extends GLWindow {

    private static final Logger LOG = LoggerFactory.getLogger(CthughaWindow.class);
    private static final Config CFG = Config.singleton();

    private final JCthugha cthugha;

    // Render buffer dimensions — may differ from the GLFW window size.
    private int renderWidth;
    private int renderHeight;
    private final BooleanParameter fullscreenEnabled;
    /** Mirrors the GL-thread-confirmed fullscreen state, so redundant sets don't re-flip it. */
    private boolean actualFullscreen = false;

    // Palette LUT (256×1 RGBA)
    private Texture paletteTex;

    // Fixed-role R16 textures (GL_R16, uint16 normalised to [0,1]):
    //   displayTex = blur output / translate source / display source (always)
    //   renderTex  = translate output / indexed-render target / blur input (always)
    private Texture displayTex;
    private Texture renderTex;
    private FrameBuffer displayFBO;
    private FrameBuffer renderFBO;

    // RG16UI translation map (x, y coords per pixel as uint16)
    private Texture translateMapTex;
    private TextureUnit translateMapUnit;

    // Pass 1 (renderFBO): translate displayTex → renderTex
    private TranslateTextureRenderer translateRenderer;

    // Pass 3 (displayFBO): GPU flame — two-pass separable Gaussian blur with fade
    private BlurTextureRenderer xBlur;
    private BlurTextureRenderer yBlur;
    private Texture flameTex;
    private FrameBuffer flameFBO;
    private static final int DEFAULT_KERNEL_SIZE = 5;

    // Blur params — exposed in the remote UI; change listeners drive xBlur/yBlur
    private final BooleanParameter blurEnabled = new BooleanParameter("Enabled", true);
    private final IntegerParameter blurKernelSize = new IntegerParameter(
            "Kernel Size", BlurTextureRenderer.MIN_KERNEL_SIZE, BlurTextureRenderer.MAX_KERNEL_SIZE, DEFAULT_KERNEL_SIZE);
    private final DoubleParameter blurFade = new DoubleParameter("Softening", 0.0, 1.0, 0.99)
            {{ withUiHint(UiHint.SCALE, UiHint.SCALE_LOG); }};

    // Pass 4 (default FBO): palette-convert displayTex to RGBA for display
    private PaletteRenderer paletteRenderer;
    private TextureUnit paletteUploadUnit;

    // Palette LUT upload buffer — sized for the current palette; recreated if palette size changes
    private ByteBuffer palBuf;
    private int palWidth;
    private int palHeight;
    private boolean paletteDirty = false;

    private Double desiredUpdatePeriod = null;

    private final RenderActionQueue renderActions = new RenderActionQueue();
    private final BackgroundWorkQueue workQueue = new BackgroundWorkQueue();
    private final StdinKeyInjector stdinInjector;

    private CthughaActionContext actionContext;

    // Populated by ActionTreeBuilder.build(); used to add the Remote node afterwards.
    private ContainerNode generalGroup;

    // Ordered render phase list (Wave, Flash, Quote, Notif, and optionally QrPhase)
    private List<RenderPhase> phases;
    private QrPhase qrPhase;  // non-null only when remote is enabled

    private final RemoteConfig remoteConfig;
    private final DumpConfig dumpConfig;
    private TokenStore tokenStore;
    private RemoteEventBroadcaster broadcaster;
    private RemoteServer remoteServer;

    public CthughaWindow(boolean stdinEnabled, RemoteConfig remoteConfig, DumpConfig dumpConfig) {
        this(stdinEnabled, remoteConfig, dumpConfig, resolveDisplaySize());
    }

    private CthughaWindow(boolean stdinEnabled, RemoteConfig remoteConfig, DumpConfig dumpConfig, int[] size) {
        super(new ResourceManagerImpl(new PathBasedLoader(Paths.get("."))),
                "Cthugha Reborn",
                size[0], size[1], null);
        this.cthugha = new JCthugha();
        this.stdinInjector = stdinEnabled ? new StdinKeyInjector(renderActions) : null;
        this.remoteConfig = remoteConfig;
        this.dumpConfig = dumpConfig;
        this.fullscreenEnabled = new BooleanParameter("Fullscreen",
                Config.state().getConfigAs("display", "fullscreen", "false", Boolean::parseBoolean));
        applyMonitorPosition();
    }

    /** Parses "1280", "1280px", or "30%" into pixels relative to screenDim. */
    private static int parseDimension(String value, int screenDim) {
        String v = value.trim();
        if (v.endsWith("%")) {
            double pct = Double.parseDouble(v.substring(0, v.length() - 1));
            return (int) Math.round(screenDim * pct / 100.0);
        }
        if (v.endsWith("px")) {
            return Integer.parseInt(v.substring(0, v.length() - 2).trim());
        }
        return Integer.parseInt(v);
    }

    /**
     * Resolves [display] width/height (supporting px/% syntax) against the configured monitor's
     * dimensions using AWT, so the values are available before GLFW is initialised in super().
     */
    private static int[] resolveDisplaySize() {
        int monIdx = CFG.getConfigAs("display", "monitor", "0", Integer::parseInt);
        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        GraphicsDevice screen = (monIdx >= 0 && monIdx < screens.length) ? screens[monIdx] : screens[0];
        DisplayMode dm = screen.getDisplayMode();
        int w = parseDimension(CFG.getConfig("display", "width", "1280"), dm.getWidth());
        int h = parseDimension(CFG.getConfig("display", "height", "720"), dm.getHeight());
        return new int[]{w, h};
    }

    /**
     * Logs all connected monitors (so the user can pick an index for [display] monitor),
     * then moves the window to the configured monitor if it is not the primary (index 0).
     * GLFW is available here because super() has already called glfwInit().
     */
    private void applyMonitorPosition() {
        java.util.List<Monitor> monitors = Monitor.getAll();
        for (int i = 0; i < monitors.size(); i++) {
            LOG.info("Monitor {}: {}", i, monitors.get(i));
        }
        int monIdx = CFG.getConfigAs("display", "monitor", "0", Integer::parseInt);
        if (monIdx == 0) return;
        if (monIdx >= monitors.size()) {
            LOG.warn("[display] monitor={} not found ({} monitors available)", monIdx, monitors.size());
            return;
        }
        moveToMonitor(monitors.get(monIdx));
    }

    @Override
    public Double getDesiredUpdatePeriod() {
        return desiredUpdatePeriod;
    }

    @Override
    public void setDesiredUpdatePeriod(Double period) {
        this.desiredUpdatePeriod = period;
    }

    @Override
    public void registerKeys() {
        var kr = getKeyRegistry();
        // All key bindings are driven from [keys] in cthugha.ini (see KeyBindingConfig).
        // Must run after init() → registerDisplayActions() has populated the param tree.
        new KeyBindingConfig(cthugha, actionContext).register(kr);
        if (stdinInjector != null) {
            stdinInjector.start(kr);
        }
    }

    @Override
    public void init() throws IOException {
        java.awt.Rectangle win = getWindow();
        // Render buffer defaults to display size; can be set smaller for a retro low-res look.
        this.renderWidth  = parseDimension(CFG.getConfig("display", "render_width",  String.valueOf(win.width)),  win.width);
        this.renderHeight = parseDimension(CFG.getConfig("display", "render_height", String.valueOf(win.height)), win.height);
        int w = renderWidth;
        int h = renderHeight;

        double targetFps = CFG.getConfigAs("display", "target_fps", "0", Double::parseDouble);
        if (targetFps > 0) {
            this.desiredUpdatePeriod = 1.0 / targetFps;
        }

        cthugha.init(new Dimension(w, h), getRandom());
        actionContext = new CthughaActionContext(cthugha, getRandom(), renderActions,
                this::rebuildTranslateMap);

        // Create phase list from JCthugha factory
        phases = new ArrayList<>(cthugha.createPhases());
        phases.add(new WorkPhase(workQueue));

        // Wire translation-layer callbacks before starting the remote server so SSE events work.
        cthugha.translateSource.setOnRegenerateNeeded(() -> {
            boolean started = workQueue.submit("translateMap", "Tab calculating…", wctx -> {
                wctx.setStatus("Computing…");
                TabBuffer newBuf =cthugha.computeRegeneratedTranslation();
                wctx.enqueueRenderAction(rc -> {
                    cthugha.loadTabBuffer(newBuf);
                    cthugha.notify(cthugha.translateSource.getLastGenerated());
                    rebuildTranslateMap();
                });
            });
            if (!started) cthugha.notify("Tab calculation in progress");
        });
        cthugha.translateSource.setOnNewGeneratorSelected(() -> {
            boolean started = workQueue.submit("translateMap", "Tab calculating…", wctx -> {
                wctx.setStatus("Computing…");
                TabBuffer newBuf =cthugha.computeNewTranslation();
                wctx.enqueueRenderAction(rc -> {
                    cthugha.loadTabBuffer(newBuf);
                    cthugha.notify(cthugha.translateSource.getLastGenerated());
                    rebuildTranslateMap();
                });
            });
            if (!started) cthugha.notify("Tab calculation in progress");
        });

        // Remote setup: create QrPhase and add to phase list before tree build
        if (remoteConfig != null && remoteConfig.enabled) {
            QrOverlay qrOverlay = new QrOverlay(remoteConfig.qrTimeoutSeconds, remoteConfig.qrLogoPercent);
            qrPhase = new QrPhase(qrOverlay);
            phases.add(qrPhase);
        }

        // Build action tree (phases register their own actions)
        ActionTreeBuilder treeBuilder = new ActionTreeBuilder(
                cthugha, actionContext, renderActions,
                blurEnabled, blurKernelSize, blurFade, fullscreenEnabled,
                new ActionTreeBuilder.Callbacks() {
                    @Override public void rebuildTranslateMap() { CthughaWindow.this.rebuildTranslateMap(); }
                    @Override public void markPaletteDirty() { paletteDirty = true; }
                    @Override public void screenshot() { captureNextFrame(); }
                    @Override public void startRecording() { CthughaWindow.this.startRecording(Duration.ofSeconds(5)); }
                    @Override public void stopRecording() { CthughaWindow.this.stopRecording(); }
                    @Override public void exitApplication() { exit(); }
                },
                remoteConfig == null || remoteConfig.screenCaptureToolbar);
        treeBuilder.build(phases);
        generalGroup = treeBuilder.getGeneralGroup();

        // Start remote server and add remote node to the param tree
        if (remoteConfig != null && remoteConfig.enabled) {
            tokenStore = new TokenStore(remoteConfig.fixedToken);
            broadcaster = new RemoteEventBroadcaster();
            remoteServer = new RemoteServer(cthugha, cthugha.animation, tokenStore, broadcaster, remoteConfig, actionContext);
            remoteServer.start();

            cthugha.translateSource.setOnTreeChanged(
                    () -> broadcaster.broadcastAll("treeChanged", "{}"));
            cthugha.triggers.setOnTreeChanged(
                    () -> broadcaster.broadcastAll("treeChanged", "{}"));

            ContainerNode remoteNode = new ContainerNode("Remote");
            remoteNode.withUiHint(UiHint.ICON, "wifi");
            AbstractAction rotateToken = new AbstractAction("Rotate Token", ctx -> {
                String url = tokenStore.rotate(NetworkUtils.detectBaseUrl(remoteConfig));
                LOG.info("Remote URL: {}", url);
                if (qrPhase != null) qrPhase.show(url);
                broadcaster.broadcastAll("tokenRotated", "{}");
                remoteServer.resetFirstAuth();
            });
            rotateToken.withUiHint(UiHint.ICON, "refresh-cw");
            rotateToken.withNoRemote();
            remoteNode.addChild(rotateToken);
            generalGroup.addChild(remoteNode);

            remoteServer.setOnFirstAuth(() -> qrPhase.hide());

            String initialUrl = tokenStore.rotate(NetworkUtils.detectBaseUrl(remoteConfig));
            LOG.info("Remote URL: {}", initialUrl);
            qrPhase.show(initialUrl);  // safe to call before init()
        }

        // GL backbone: fixed-role R16 indexed textures (single-channel, 0-65535 palette index range)
        displayTex = new Texture();
        displayTex.setInternalFormat(GL_R16);
        displayTex.setImageFormat(GL_RED);
        displayTex.setDataType(GL_UNSIGNED_SHORT);
        displayTex.setFilter(Filter.NEAREST);
        displayTex.generate(w, h, 0L);
        getResourceManager().putTexture("displayTex", displayTex);

        renderTex = new Texture();
        renderTex.setInternalFormat(GL_R16);
        renderTex.setImageFormat(GL_RED);
        renderTex.setDataType(GL_UNSIGNED_SHORT);
        renderTex.setFilter(Filter.NEAREST);
        renderTex.generate(w, h, 0L);
        getResourceManager().putTexture("renderTex", renderTex);

        // Translation map (RG16UI: absolute pixel coords per texel)
        translateMapTex = buildTranslateMapTexture(cthugha.getTranslateBuffer(), w, h);
        getResourceManager().putTexture("translateMap", translateMapTex);
        translateMapUnit = getResourceManager().nextTextureUnit();

        // Palette LUT: 256-wide × (size/256)-tall RGBA texture; width stays 256 for all palette sizes
        int palSize = cthugha.paletteMap.size();
        palWidth  = 256;
        palHeight = (palSize + 255) / 256;
        palBuf = BufferUtils.createByteBuffer(palSize * 4);
        paletteTex = new Texture();
        paletteTex.setInternalFormat(GL_RGBA);
        paletteTex.setImageFormat(GL_RGBA);
        paletteTex.setDataType(GL_UNSIGNED_BYTE);
        paletteTex.setFilter(Filter.NEAREST);
        paletteTex.generate(palWidth, palHeight, fillPaletteBuffer(cthugha.paletteMap));
        getResourceManager().putTexture("palette", paletteTex);

        // FBOs — constructor immediately does GL setup, must be on GL thread
        displayFBO = new FrameBuffer(displayTex);
        renderFBO = new FrameBuffer(renderTex);

        // Pass 1: translate displayTex → renderTex (via renderFBO)
        translateRenderer = new TranslateTextureRenderer("displayTex", "translateMap");
        translateRenderer.init(this);

        // Pass 3: GPU flame — two-pass separable Gaussian blur with fade
        TextureOptions flameOpts = new TextureOptions(DataFormat.GRAY, Filter.LINEAR, Wrap.REPEAT);
        flameTex = TextureFactory.createTexture(new java.awt.Rectangle(w, h), null, flameOpts);
        getResourceManager().putTexture("flameTex", flameTex);
        flameFBO = new FrameBuffer(flameTex);

        xBlur = new BlurTextureRenderer("renderTex");
        xBlur.setXAxis(true);
        xBlur.setKernelSize(DEFAULT_KERNEL_SIZE);
        xBlur.addVariable(ShaderVariable.floatVariable("multiplier", () -> 1.0f));
        xBlur.init(this);

        yBlur = new BlurTextureRenderer("flameTex");
        yBlur.setXAxis(false);
        yBlur.setKernelSize(DEFAULT_KERNEL_SIZE);
        yBlur.addVariable(ShaderVariable.floatVariable("multiplier", () -> (float) blurFade.value));
        yBlur.init(this);

        blurEnabled.addChangeListener(() -> {
            xBlur.setBlur(blurEnabled.value);
            yBlur.setBlur(blurEnabled.value);
        });
        blurKernelSize.addChangeListener(() -> {
            xBlur.setKernelSize(blurKernelSize.value);
            yBlur.setKernelSize(blurKernelSize.value);
        });

        // Pass 4: palette-convert displayTex → RGBA for display (default FBO)
        paletteRenderer = new PaletteRenderer("displayTex");
        paletteRenderer.init(this);
        paletteUploadUnit = getResourceManager().nextTextureUnit();
        paletteUploadUnit.bind(paletteTex);

        // Initialise all phases (GL context is now fully set up)
        for (RenderPhase p : phases) {
            p.init(this);
        }

        ScriptHelpers.setContext(cthugha.beatDetector, cthugha.rng);
        cthugha.animation.init(getClock());
        cthugha.triggers.init(getClock(), cthugha, actionContext);

        // The change listener only runs on subsequent toggles (construction doesn't fire it),
        // so apply the loaded initial value once here, then keep actualFullscreen in sync.
        if (fullscreenEnabled.value) {
            toggleFullscreen();
            actualFullscreen = true;
        }
        fullscreenEnabled.addChangeListener(() -> {
            boolean desired = fullscreenEnabled.value;
            renderActions.enqueue("toggleFullscreen", rc -> {
                if (desired != actualFullscreen) {
                    toggleFullscreen();
                    actualFullscreen = desired;
                }
            });
            Config.state().setConfig("display", "fullscreen", String.valueOf(desired));
        });

        if (dumpConfig.enabled) {
            System.out.println("Dumping param tree to " + dumpConfig.outputFile.toAbsolutePath() + " in format " + dumpConfig.format);
            try (Writer out = Files.newBufferedWriter(dumpConfig.outputFile)) {
                dumpConfig.format.dump(cthugha, getKeyRegistry(), out);
                LOG.info("Param tree dumped to {}", dumpConfig.outputFile.toAbsolutePath());
            } catch (IOException e) {
                LOG.error("Failed to dump param tree", e);
            }
            exit();
        }
    }

    @Override
    public void render() throws IOException {
        renderActions.processAll(this);
        workQueue.processAll(this);

        // CPU pipeline: advance parameter animators
        cthugha.doRenderCPU();

        // Upload palette LUT only when changed
        if (paletteDirty) {
            paletteDirty = false;
            int newSize   = cthugha.paletteMap.size();
            int newWidth  = 256;
            int newHeight = (newSize + 255) / 256;
            paletteUploadUnit.activate();
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            if (newWidth != palWidth || newHeight != palHeight) {
                palWidth  = newWidth;
                palHeight = newHeight;
                palBuf = BufferUtils.createByteBuffer(newSize * 4);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, palWidth, palHeight, 0,
                        GL_RGBA, GL_UNSIGNED_BYTE, fillPaletteBuffer(cthugha.paletteMap));
            } else {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, palWidth, palHeight,
                        GL_RGBA, GL_UNSIGNED_BYTE, fillPaletteBuffer(cthugha.paletteMap));
            }
        }

        java.awt.Rectangle win = getWindow();
        glViewport(0, 0, renderWidth, renderHeight);

        // Indexed pass: translate displayTex → renderTex, then phases write directly into renderTex
        renderFBO.bind();
        translateRenderer.doRender(this);
        for (RenderPhase p : phases) p.indexedRender(this);
        renderFBO.unbind();

        // Flame: two-pass Gaussian blur with fade
        flameFBO.bind();
        xBlur.doRender(this);
        flameFBO.unbind();

        displayFBO.bind();
        yBlur.doRender(this);
        displayFBO.unbind();

        // Display: displayTex (R16) → RGBA via palette LUT → default FBO (window viewport)
        glViewport(0, 0, win.width, win.height);
        paletteRenderer.doRender(this);

        // Screen overlays: each phase manages its own blend state
        for (RenderPhase p : phases) p.screenRender(this);
    }

    @Override
    public void dispose() {
        if (phases != null) {
            for (RenderPhase p : phases) p.dispose();
        }
        if (xBlur             != null) xBlur.dispose();
        if (yBlur             != null) yBlur.dispose();
        if (flameFBO          != null) flameFBO.dispose();
        if (paletteRenderer   != null) paletteRenderer.dispose();
        if (paletteUploadUnit != null) paletteUploadUnit.dispose();
        if (translateRenderer != null) translateRenderer.dispose();
        if (displayFBO        != null) displayFBO.dispose();
        if (renderFBO         != null) renderFBO.dispose();
        if (stdinInjector     != null) stdinInjector.close();
        if (remoteServer      != null) remoteServer.stop();
        try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing audio", e); }
        super.dispose();
    }

    // -------------------------------------------------------------------------
    // Translation map helpers
    // -------------------------------------------------------------------------

    private Texture buildTranslateMapTexture(ByteBuffer data, int w, int h) {
        Texture t = new Texture();
        t.setInternalFormat(GL_RG16UI);
        t.setImageFormat(GL_RG_INTEGER);
        t.setDataType(GL_UNSIGNED_SHORT);
        t.setFilter(Filter.NEAREST);
        t.setWrap(Wrap.REPEAT);
        t.generate(w, h, data);
        return t;
    }

    private void rebuildTranslateMap() {
        int w = cthugha.bufferWidth;
        int h = cthugha.bufferHeight;
        ByteBuffer data = cthugha.getTranslateBuffer();
        translateMapUnit.activate();
        translateMapUnit.bind(translateMapTex);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h,
                GL_RG_INTEGER, GL_UNSIGNED_SHORT, data);
        data.rewind();
    }

    // -------------------------------------------------------------------------
    // Palette buffer builder
    // -------------------------------------------------------------------------

    private ByteBuffer fillPaletteBuffer(PaletteMap pm) {
        palBuf.clear();
        for (int c : pm.colors) {
            palBuf.put((byte) ((c >> 16) & 0xFF));
            palBuf.put((byte) ((c >> 8) & 0xFF));
            palBuf.put((byte) (c & 0xFF));
            palBuf.put((byte) 0xFF);
        }
        palBuf.flip();
        return palBuf;
    }

}
