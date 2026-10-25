package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.GLWindow;
import com.asteroid.duck.opengl.util.blur.BlurTextureRenderer;
import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariable;
import com.asteroid.duck.opengl.util.resources.texture.DataFormat;
import com.asteroid.duck.opengl.util.resources.texture.TextureFactory;
import com.asteroid.duck.opengl.util.resources.texture.TextureOptions;

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
import io.github.duckasteroid.cthugha.binding.ScriptHelpers;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.display.phase.QrPhase;
import io.github.duckasteroid.cthugha.display.phase.RenderPhase;
import io.github.duckasteroid.cthugha.display.phase.WorkPhase;
import io.github.duckasteroid.cthugha.tab.TabBuffer;
import io.github.duckasteroid.cthugha.tab.TableMorph;
import io.github.duckasteroid.cthugha.tab.TranslationRegenerator;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.asteroid.duck.opengl.util.Monitor;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_R16;
import static org.lwjgl.opengl.GL30.GL_RG16UI;
import static org.lwjgl.opengl.GL30.GL_RG_INTEGER;

public class CthughaWindow extends GLWindow {

    private static final Logger LOG = LoggerFactory.getLogger(CthughaWindow.class);
    private static final Config CFG = Config.singleton();

    private static final Path SPLASH_DIR = Paths.get("textures", "splash");

    private final JCthugha cthugha;
    private SplashRenderer splashRenderer;

    // Render buffer dimensions — may differ from the GLFW window size.
    private int renderWidth;
    private int renderHeight;
    private final BooleanParameter fullscreenEnabled;
    /** Mirrors the GL-thread-confirmed fullscreen state, so redundant sets don't re-flip it. */
    private boolean actualFullscreen = false;

    // Palette LUT (256×N RGBA; 256×1 for a standard 256-entry palette)
    private Texture paletteTex;

    // Fixed-role R16 textures (GL_R16, uint16 normalised to [0,1]):
    //   displayTex = blur output / translate source (always) — the next frame's feedback input
    //   renderTex  = translate output / indexed-render target / blur input / display source (always)
    private Texture displayTex;
    private Texture renderTex;
    private FrameBuffer displayFBO;
    private FrameBuffer renderFBO;

    // RG16UI translation map (x, y coords per pixel as uint16)
    // Translation maps for TableMorph: three RG16UI textures whose roles rotate between slot A,
    // slot B and a spare that new tables are uploaded or baked into before being promoted.
    private final Texture[] mapTex = new Texture[3];
    private final FrameBuffer[] mapFBO = new FrameBuffer[3];
    private final int[] slotMap = {0, 1};
    private int spareMap = 2;
    private TextureUnit mapAUnit;
    private TextureUnit mapBUnit;
    private MorphTranslateRenderer.Bake mapBaker;

    // Pass 1 (renderFBO): translate displayTex → renderTex
    private MorphTranslateRenderer translateRenderer;

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

    // Pass 4 (default FBO): palette-convert renderTex to RGBA for display
    private IndexPaletteRenderer paletteRenderer;
    private TextureUnit paletteUploadUnit;

    // Palette LUT upload buffer — palWidth × palHeight texels (padded past the last entry);
    // recreated if the row count changes
    private ByteBuffer palBuf;
    private int palWidth;
    private int palHeight;
    private boolean paletteDirty = false;

    private Double desiredUpdatePeriod = null;
    private FrameTimeStats frameTimeStats;

    private final RenderActionQueue renderActions = new RenderActionQueue();
    private final BackgroundWorkQueue workQueue = new BackgroundWorkQueue();
    private TranslationRegenerator regenerator;
    /** When the last output resize was reported (0 = none pending); GL thread only. */
    private long resizeRequestedNanos;
    /** How long resizes must settle before the render buffer is rebuilt, so a drag rebuilds once. */
    private static final long RESIZE_DEBOUNCE_NANOS = 250_000_000L;
    private final StepClock stepClock = new StepClock();
    /** The current monitor's refresh rate (0 = unknown), re-read every {@link #REFRESH_CHECK_NANOS}. */
    private int refreshHz;
    private long refreshCheckedNanos;
    private static final long REFRESH_CHECK_NANOS = 1_000_000_000L;
    /** How long a regeneration waits for the render thread to adopt its result (e.g. at shutdown). */
    private static final long ADOPT_TIMEOUT_SECONDS = 5;
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

    public CthughaWindow(boolean stdinEnabled, String keyInputPath, RemoteConfig remoteConfig, DumpConfig dumpConfig) {
        this(stdinEnabled, keyInputPath, remoteConfig, dumpConfig, resolveDisplaySize());
    }

    private CthughaWindow(boolean stdinEnabled, String keyInputPath, RemoteConfig remoteConfig, DumpConfig dumpConfig, int[] size) {
        super(new ResourceManagerImpl(new PathBasedLoader(Paths.get("."))),
                "Cthugha Reborn",
                size[0], size[1], null);
        this.cthugha = new JCthugha();
        this.stdinInjector = createKeyInjector(stdinEnabled, keyInputPath);
        this.remoteConfig = remoteConfig;
        this.dumpConfig = dumpConfig;
        this.fullscreenEnabled = new BooleanParameter("Fullscreen",
                Config.state().getConfigAs("display", "fullscreen", "false", Boolean::parseBoolean));
        applyMonitorPosition();
    }

    /** {@code --key-input=<path>} takes priority over {@code --stdin}; the path may be a plain file or a FIFO. */
    private StdinKeyInjector createKeyInjector(boolean stdinEnabled, String keyInputPath) {
        if (keyInputPath != null) {
            return new StdinKeyInjector(renderActions, () -> new java.io.FileInputStream(keyInputPath));
        }
        return stdinEnabled ? new StdinKeyInjector(renderActions, () -> System.in) : null;
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
        int w = RenderSize.parseDimension(CFG.getConfig("display", "width", "1280"), dm.getWidth());
        int h = RenderSize.parseDimension(CFG.getConfig("display", "height", "720"), dm.getHeight());
        return new int[]{w, h};
    }

    /**
     * Logs all connected monitors (so the user can pick an index for [display] monitor),
     * then moves the window to the configured monitor (including 0: otherwise the window manager
     * picks, e.g. whichever monitor has focus).
     * GLFW is available here because super() has already called glfwInit().
     */
    private void applyMonitorPosition() {
        java.util.List<Monitor> monitors = Monitor.getAll();
        for (int i = 0; i < monitors.size(); i++) {
            LOG.info("Monitor {}: {}", i, monitors.get(i));
        }
        int monIdx = CFG.getConfigAs("display", "monitor", "0", Integer::parseInt);
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

    /**
     * Shows a random splash image before {@link #init()} runs, so the window has at least one
     * visible frame and event-pump/buffer-swap before the slow synchronous startup work begins
     * (window managers otherwise flag a long-blocking {@code init()} as "not responding").
     */
    @Override
    protected void drawSplash() throws IOException {
        List<Path> candidates;
        try (var stream = Files.list(SPLASH_DIR)) {
            candidates = stream
                    .filter(p -> p.toString().toUpperCase().endsWith(".PNG"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return; // no splash directory — continue without a splash
        }
        if (candidates.isEmpty()) {
            return;
        }
        Path chosen = candidates.get(new Random().nextInt(candidates.size()));
        String relPath = Paths.get("textures").relativize(chosen).toString();
        Texture tex = getResourceManager().getTexture("splash", relPath);
        if (tex == null) {
            return;
        }
        splashRenderer = new SplashRenderer();
        splashRenderer.init(this);
        splashRenderer.setTexture(tex);
        clearScreen();
        splashRenderer.doRender(this);
    }

    @Override
    public void init() throws IOException {
        java.awt.Rectangle win = getWindow();
        // Render buffer follows the output's shape, capped (see RenderSize); rebuilt on resize.
        Dimension renderSize = renderSizeFor(win.width, win.height);
        this.renderWidth  = renderSize.width;
        this.renderHeight = renderSize.height;
        int w = renderWidth;
        int h = renderHeight;

        double targetFps = CFG.getConfigAs("display", "target_fps", "0", Double::parseDouble);
        if (targetFps > 0) {
            this.desiredUpdatePeriod = 1.0 / targetFps;
        }
        double frameStatsInterval = CFG.getConfigAs("display", "frame_stats_interval", "0", Double::parseDouble);
        if (frameStatsInterval > 0) {
            frameTimeStats = new FrameTimeStats(frameStatsInterval);
        }

        cthugha.init(new Dimension(w, h), getRandom());
        actionContext = new CthughaActionContext(cthugha, getRandom(), renderActions,
                this::rebuildTranslateMap);

        // Create phase list from JCthugha factory
        phases = new ArrayList<>(cthugha.createPhases());
        phases.add(new WorkPhase(workQueue));

        // Wire translation-layer callbacks before starting the remote server so SSE events work.
        // Requests coalesce: one regeneration runs at a time, and changes made meanwhile cost a
        // single follow-up run, so the table always catches up with the latest params. Runs
        // happen quietly, with no work indicator, since they are frequent and brief.
        regenerator = new TranslationRegenerator(
                drain -> Thread.ofVirtual().name("translate-regen").start(drain),
                this::regenerateTranslation);
        cthugha.translateSource.setOnRegenerateNeeded(
                () -> regenerator.request(TranslationRegenerator.Request.REGENERATE));
        cthugha.translateSource.setOnNewGeneratorSelected(
                () -> regenerator.request(TranslationRegenerator.Request.RANDOMISE));
        cthugha.translateSource.setOnRestored(
                () -> regenerator.request(TranslationRegenerator.Request.RESTORE));

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
            remoteServer = new RemoteServer(cthugha, cthugha.bindings, tokenStore, broadcaster, remoteConfig, actionContext,
                    () -> cthugha.libraryManagerEnabled.value);
            remoteServer.start();

            cthugha.translateSource.setOnTreeChanged(
                    () -> broadcaster.broadcastAll("treeChanged", "{}"));
            cthugha.bindings.setOnTreeChanged(
                    () -> broadcaster.broadcastAll("treeChanged", "{}"));
            cthugha.waveSystem.setOnTreeChanged(
                    () -> broadcaster.broadcastAll("treeChanged", "{}"));
            treeBuilder.getConfigsGroup().setOnTreeChanged(
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

        // Translation maps (RG16UI: absolute pixel coords per texel), two slots plus a spare
        for (int i = 0; i < mapTex.length; i++) {
            mapTex[i] = buildTranslateMapTexture(cthugha.getTranslateBuffer(), w, h);
            mapFBO[i] = new FrameBuffer(mapTex[i]);
        }
        mapAUnit = getResourceManager().nextTextureUnit();
        mapBUnit = getResourceManager().nextTextureUnit();
        mapAUnit.bind(mapTex[slotMap[0]]);
        mapBUnit.bind(mapTex[slotMap[1]]);

        // Palette LUT: 256-wide × (size/256)-tall RGBA texture; width stays 256 for all palette sizes
        int palSize = cthugha.paletteMap.size();
        palWidth  = 256;
        palHeight = (palSize + 255) / 256;
        palBuf = BufferUtils.createByteBuffer(palWidth * palHeight * 4);
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

        // Pass 1: translate displayTex → renderTex (via renderFBO), morphing between the two map slots
        translateRenderer = new MorphTranslateRenderer("displayTex", mapAUnit, mapBUnit,
                () -> cthugha.tableMorph.mix.value);
        translateRenderer.init(this);
        mapBaker = new MorphTranslateRenderer.Bake("displayTex", mapAUnit, mapBUnit);
        mapBaker.init(this);
        cthugha.tableMorph.setSlots(new MapSlots());
        cthugha.tableMorph.fillBoth(cthugha.getTabBuffer());

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

        // Pass 4: palette-convert renderTex → RGBA for display (default FBO). Reads renderTex,
        // not the blurred displayTex, so this frame's waves/flashes show at full value and
        // only start blurring/fading on the next trip round the feedback loop.
        paletteRenderer = new IndexPaletteRenderer("renderTex", palSize);
        paletteRenderer.init(this);
        paletteUploadUnit = getResourceManager().nextTextureUnit();
        paletteUploadUnit.bind(paletteTex);

        // Initialise all phases (GL context is now fully set up)
        for (RenderPhase p : phases) {
            p.init(this);
        }

        ScriptHelpers.setContext(cthugha.beatDetector, cthugha.rng);
        // Restores the persisted "current" state (waves, bindings, palette, tab generator, blur,
        // etc. — everything ScreenConfigParams captures) if one exists from a previous session,
        // otherwise falls back to deterministic first-launch defaults. Either way this also starts
        // the periodic background writer that keeps "current" up to date for next time (see
        // issue #3). Must run after the tree above is fully built, since it resolves/records
        // param paths.
        cthugha.restoreCurrentStateOrDefaults();
        cthugha.bindings.init(getClock(), cthugha, actionContext);

        // The render buffer follows the output: rebuild it once resizes (incl. fullscreen toggles) settle.
        addResizeListener((fbWidth, fbHeight) -> resizeRequestedNanos = System.nanoTime());

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
        if (frameTimeStats != null) frameTimeStats.frame();
        renderActions.processAll(this);
        workQueue.processAll(this);

        // CPU pipeline: advance parameter animators
        cthugha.doRenderCPU();

        // Upload palette LUT only when changed
        if (paletteDirty) {
            paletteDirty = false;
            PaletteMap pm = cthugha.paletteMap;
            int newSize   = pm.size();
            int newWidth  = 256;
            int newHeight = (newSize + 255) / 256;
            paletteUploadUnit.activate();
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            if (newWidth != palWidth || newHeight != palHeight) {
                palWidth  = newWidth;
                palHeight = newHeight;
                palBuf = BufferUtils.createByteBuffer(palWidth * palHeight * 4);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, palWidth, palHeight, 0,
                        GL_RGBA, GL_UNSIGNED_BYTE, fillPaletteBuffer(pm));
            } else {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, palWidth, palHeight,
                        GL_RGBA, GL_UNSIGNED_BYTE, fillPaletteBuffer(pm));
            }
            paletteRenderer.setEntries(newSize);
        }

        if (resizeRequestedNanos != 0 && System.nanoTime() - resizeRequestedNanos >= RESIZE_DEBOUNCE_NANOS) {
            resizeRequestedNanos = 0;
            followOutputSize();
        }

        for (RenderPhase p : phases) p.frameUpdate(this);
        cthugha.tableMorph.frame(System.nanoTime());

        // Simulation steps at a fixed rate (Render/Speed), independent of the refresh rate: a frame
        // runs zero or more steps; with none, renderTex still holds the last step and is re-presented.
        long now = System.nanoTime();
        if (refreshHz <= 0 || now - refreshCheckedNanos > REFRESH_CHECK_NANOS) {
            refreshCheckedNanos = now;
            refreshHz = getCurrentMonitor().refreshRate();
        }
        int steps = stepClock.stepsDue(now, cthugha.simSpeed.value, refreshHz);
        if (steps > 0) {
            glViewport(0, 0, renderWidth, renderHeight);
            for (int i = 0; i < steps; i++) simulationStep();
        }

        // Display: renderTex (R16) → RGBA via palette LUT → default FBO (window viewport)
        java.awt.Rectangle win = getWindow();
        glViewport(0, 0, win.width, win.height);
        paletteRenderer.doRender(this);

        // Screen overlays: each phase manages its own blend state
        for (RenderPhase p : phases) p.screenRender(this);

        if (frameTimeStats != null) frameTimeStats.renderEnd();
    }

    /**
     * Best-effort synchronous flush of the "current" state, safe to call from a JVM shutdown hook
     * (see {@code Main}) as a backstop for abnormal termination that skips the normal {@link
     * #dispose()} path (e.g. SIGTERM, an uncaught exception escaping the render loop). Does no GL
     * work — only a param-tree read + JSON write — so it is safe off the GL thread. Idempotent and
     * safe to call even if {@link #init()} never completed.
     */
    public void flushCurrentStateOnShutdown() {
        cthugha.flushCurrentStateNow();
    }

    @Override
    public void dispose() {
        if (splashRenderer != null) splashRenderer.dispose();
        if (phases != null) {
            for (RenderPhase p : phases) p.dispose();
        }
        if (xBlur             != null) xBlur.dispose();
        if (yBlur             != null) yBlur.dispose();
        if (flameFBO          != null) flameFBO.dispose();
        if (paletteRenderer   != null) paletteRenderer.dispose();
        if (paletteUploadUnit != null) paletteUploadUnit.dispose();
        if (translateRenderer != null) translateRenderer.dispose();
        if (mapBaker          != null) mapBaker.dispose();
        for (FrameBuffer fbo : mapFBO) if (fbo != null) fbo.dispose();
        for (Texture t : mapTex) if (t != null) t.dispose();
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

    /** The render buffer size for an output of the given size, from the {@code [display]} settings. */
    private static Dimension renderSizeFor(int outputWidth, int outputHeight) {
        return RenderSize.compute(outputWidth, outputHeight,
                CFG.getConfig("display", "render_width", ""),
                CFG.getConfig("display", "render_height", ""),
                CFG.getConfig("display", "render_max", ""));
    }

    /**
     * Rebuilds the render buffer at the size the current output calls for, if that changed:
     * the R16 textures, the blur intermediate and the map slots get new storage in place (so
     * FBOs and renderers keep their references), the table becomes an identity table at the new
     * size, and the current generator's table is regenerated and morphs in. The flame trail
     * restarts. GL thread only.
     */
    private void followOutputSize() {
        java.awt.Rectangle win = getWindow();
        if (win.width <= 0 || win.height <= 0) return; // minimised
        Dimension size = renderSizeFor(win.width, win.height);
        if (size.width == renderWidth && size.height == renderHeight) return;
        LOG.info("Render buffer {}x{} -> {}x{} for output {}x{}", renderWidth, renderHeight,
                size.width, size.height, win.width, win.height);
        renderWidth = size.width;
        renderHeight = size.height;
        displayTex.generate(renderWidth, renderHeight, 0L);
        renderTex.generate(renderWidth, renderHeight, 0L);
        flameTex.generate(renderWidth, renderHeight, 0L);
        for (Texture t : mapTex) t.generate(renderWidth, renderHeight, 0L);
        cthugha.tableMorph.fillBoth(cthugha.resizeBuffer(size));
        regenerator.request(TranslationRegenerator.Request.REGENERATE);
    }

    /** One simulation step: translate, in-buffer drawing, then blur/fade (viewport already set). */
    private void simulationStep() throws IOException {
        // Indexed pass: translate displayTex → renderTex, then phases write directly into renderTex
        renderFBO.bind();
        translateRenderer.doRender(this);
        for (RenderPhase p : phases) p.indexedRender(this);
        renderFBO.unbind();

        // Flame: two-pass Gaussian blur with fade — feeds the next step's translate, not this frame's display
        flameFBO.bind();
        xBlur.doRender(this);
        flameFBO.unbind();

        displayFBO.bind();
        yBlur.doRender(this);
        displayFBO.unbind();
    }

    /**
     * One {@link TranslationRegenerator} run: computes the table off the GL thread, then waits
     * until the GL thread has adopted and uploaded it, so the buffer it replaced is free to be
     * reused as the next run's spare.
     */
    private void regenerateTranslation(TranslationRegenerator.Request request) {
        JCthugha.ComputedTranslation computed =
                cthugha.computeTranslation(request == TranslationRegenerator.Request.RANDOMISE);
        CompletableFuture<Void> adopted = new CompletableFuture<>();
        renderActions.enqueue("adoptTranslation", rc -> {
            try {
                if (cthugha.adoptTranslation(computed)) {
                    // Name the generator only when it (or its params) changed wholesale, not on
                    // every parameter tweak.
                    if (request == TranslationRegenerator.Request.RANDOMISE) {
                        cthugha.notify(cthugha.translateSource.getLastGenerated());
                    }
                    if (request == TranslationRegenerator.Request.RESTORE) {
                        // A restored config shows its own table at once, in both slots.
                        cthugha.tableMorph.fillBoth(cthugha.getTabBuffer());
                    } else {
                        rebuildTranslateMap();
                    }
                }
            } finally {
                adopted.complete(null);
            }
        });
        try {
            adopted.get(ADOPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            LOG.warn("Translation not adopted by the render thread within {}s", ADOPT_TIMEOUT_SECONDS);
        }
    }

    /** The active translation table changed (GL thread): morph to it. */
    private void rebuildTranslateMap() {
        cthugha.tableMorph.onNewTable(cthugha.getTabBuffer(), System.nanoTime());
    }

    /** {@link TableMorph.Slots} over {@link #mapTex}. GL thread only. */
    private final class MapSlots implements TableMorph.Slots {
        @Override
        public void uploadSpare(TabBuffer table) {
            Texture spare = mapTex[spareMap];
            if (table.width != spare.getWidth() || table.height != spare.getHeight()) {
                // Built at a size the buffer has since left (e.g. a preset loaded during a resize).
                LOG.warn("Ignoring a {}x{} translation table for a {}x{} buffer",
                        table.width, table.height, spare.getWidth(), spare.getHeight());
                return;
            }
            ByteBuffer data = table.getBuffer();
            // Unit 0 is render-core's unmanaged scratch unit, so this leaves the slot units alone.
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, spare.getId());
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, table.width, table.height,
                    GL_RG_INTEGER, GL_UNSIGNED_SHORT, data);
            data.rewind();
        }

        @Override
        public void bakeIntoSpare(double mix) {
            mapFBO[spareMap].bind();
            mapBaker.setMix(mix);
            mapBaker.doRender(CthughaWindow.this);
            mapFBO[spareMap].unbind();
        }

        @Override
        public void promoteSpare(int slot) {
            int old = slotMap[slot];
            slotMap[slot] = spareMap;
            spareMap = old;
            (slot == 0 ? mapAUnit : mapBUnit).bind(mapTex[slotMap[slot]]);
        }
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
        // Zero-pad the rest of the last row; the shader never addresses past the last entry
        while (palBuf.hasRemaining()) palBuf.put((byte) 0);
        palBuf.flip();
        return palBuf;
    }

}
