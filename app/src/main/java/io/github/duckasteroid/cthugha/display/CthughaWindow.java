package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.GLWindow;
import com.asteroid.duck.opengl.util.wave.AmplitudeFunction;
import com.asteroid.duck.opengl.util.wave.AudioWave;
import com.asteroid.duck.opengl.util.wave.RadialSpectrumAnalyser;
import com.asteroid.duck.opengl.util.wave.RadialWave;
import com.asteroid.duck.opengl.util.wave.SpectrumAnalyser;
import com.asteroid.duck.opengl.util.TranslateTextureRenderer;
import com.asteroid.duck.opengl.util.blur.BlurTextureRenderer;
import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariable;
import com.asteroid.duck.opengl.util.resources.texture.DataFormat;
import com.asteroid.duck.opengl.util.resources.texture.TextureFactory;
import com.asteroid.duck.opengl.util.resources.texture.TextureOptions;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.keys.KeyCombination;
import com.asteroid.duck.opengl.util.palette.PaletteRenderer;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.framebuffer.FrameBuffer;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.font.FontTextureFactory;
import com.asteroid.duck.opengl.util.resources.io.PathBasedLoader;
import com.asteroid.duck.opengl.util.resources.manager.ResourceManagerImpl;
import com.asteroid.duck.opengl.util.resources.texture.Filter;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.Wrap;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import com.asteroid.duck.opengl.util.text.StringRenderer;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.config.Config;
import io.github.duckasteroid.cthugha.display.wave.OscilloscopeModel;
import io.github.duckasteroid.cthugha.display.wave.RadialSpectrumModel;
import io.github.duckasteroid.cthugha.display.wave.RadialWaveModel;
import io.github.duckasteroid.cthugha.display.wave.SpectrumModel;
import io.github.duckasteroid.cthugha.img.RandomImageSource;
import io.github.duckasteroid.cthugha.map.PaletteLibraryNode;
import io.github.duckasteroid.cthugha.map.PaletteMap;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.duckasteroid.cthugha.params.AbstractAction;
import io.github.duckasteroid.cthugha.params.AbstractValue;
import io.github.duckasteroid.cthugha.params.Action;
import io.github.duckasteroid.cthugha.params.ActionContext;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.Node;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import io.github.duckasteroid.cthugha.remote.ParamSerializer;
import io.github.duckasteroid.cthugha.remote.QrOverlay;
import io.github.duckasteroid.cthugha.dump.DumpConfig;
import io.github.duckasteroid.cthugha.remote.RemoteConfig;
import io.github.duckasteroid.cthugha.remote.RemoteEventBroadcaster;
import io.github.duckasteroid.cthugha.remote.RemoteServer;
import io.github.duckasteroid.cthugha.remote.TokenStore;

import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import com.asteroid.duck.opengl.util.Monitor;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL30.GL_RG16UI;
import static org.lwjgl.opengl.GL30.GL_RG_INTEGER;

public class CthughaWindow extends GLWindow {

    private static final Logger LOG = LoggerFactory.getLogger(CthughaWindow.class);
    private static final Config CFG = Config.singleton();

    private final JCthugha cthugha;

    // Render buffer dimensions — may differ from the GLFW window size.
    // Set in init() once getWindow() is available; read from [display] render_width/render_height.
    private int renderWidth;
    private int renderHeight;
    private final boolean startFullscreen;

    // Palette LUT (256×1 RGBA)
    private Texture paletteTex;

    // Fixed-role R8 textures:
    //   pingTex = flame output / translate source / display source (always)
    //   pongTex = translate output / overlay bake target / flame source (always)
    private Texture pingTex;
    private Texture pongTex;
    private FrameBuffer pingFBO;
    private FrameBuffer pongFBO;

    // RG16UI translation map (x, y coords per pixel as uint16)
    private Texture translateMapTex;
    private TextureUnit translateMapUnit;

    // Pass 1 (pongFBO): translate pingTex → pongTex
    private TranslateTextureRenderer translateRenderer;

    // Pass 3 (pingFBO): GPU flame — two-pass separable Gaussian blur with fade
    private BlurTextureRenderer xBlur;
    private BlurTextureRenderer yBlur;
    private Texture flameTex;
    private FrameBuffer flameFBO;
    private static final int DEFAULT_KERNEL_SIZE = 5;

    // Blur params — exposed in the remote UI; change listeners drive xBlur/yBlur
    private final BooleanParameter blurEnabled = new BooleanParameter("Enabled", true);
    private final IntegerParameter blurKernelSize = new IntegerParameter(
            "Kernel Size", BlurTextureRenderer.MIN_KERNEL_SIZE, BlurTextureRenderer.MAX_KERNEL_SIZE, DEFAULT_KERNEL_SIZE);
    private final DoubleParameter blurFade = new DoubleParameter("Softening", 0.0, 1.0, 0.99);

    // Pass 4 (default FBO): palette-convert pingTex to RGBA for display
    private PaletteRenderer paletteRenderer;
    private TextureUnit paletteUploadUnit;

    // Pre-allocated palette LUT upload buffer (256 RGBA entries)
    private final ByteBuffer palBuf = BufferUtils.createByteBuffer(256 * 4);
    private boolean paletteDirty = false;

    private StringRenderer quoteRenderer;
    private StringRenderer notifRenderer;
    private String lastQuote = null;
    private Instant notifExpiry = null;
    private static final Duration NOTIF_DURATION = Duration.ofSeconds(3);

    // Texture flash: one-shot bake of an RGBA texture into pongTex as palette indices
    private final RandomImageSource imageSource = new RandomImageSource(Paths.get("pcx"));
    private TextureBakeRenderer textureBaker;
    private Texture flashWhiteTex;
    private Texture flashImageTex;
    private boolean flashPending = false;

    // Quote render mode: true = baked into the indexed buffer; false = RGBA overlay (default)
    private boolean quoteInBuffer = false;

    private Double desiredUpdatePeriod = null;

    private final RenderActionQueue renderActions = new RenderActionQueue();
    private final StdinKeyInjector stdinInjector;

    private CthughaActionContext actionContext;

    // RGBA offscreen texture: all wave renderers draw here
    private Texture waveOverlayTex;
    private FrameBuffer waveOverlayFBO;
    // Converts RGBA wave overlay → R8 palette indices baked into pongTex
    private WaveIndexBakeRenderer waveBaker;

    // Audio pipeline — owns LineAcquirer, AudioReader, PboAudioSink, FrequencyProcessor
    private AudioPipeline audioPipeline;

    // Fixed wave/spectrum GL renderers — always initialized, rendered only when model.enabled
    private static final Vector4f WAVE_COLOUR = new Vector4f(1f, 1f, 1f, 0.85f);
    private AudioWave oscWave;
    private RadialWave radWave;
    private SpectrumAnalyser specAnalyser;
    private RadialSpectrumAnalyser radSpecAnalyser;

    private final RemoteConfig remoteConfig;
    private final DumpConfig dumpConfig;
    private TokenStore tokenStore;
    private RemoteEventBroadcaster broadcaster;
    private RemoteServer remoteServer;
    private QrOverlay qrOverlay;

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
        this.startFullscreen = CFG.getConfigAs("display", "fullscreen", "false", Boolean::parseBoolean);
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

        // Simple action bindings are loaded from [keys] in cthugha.ini (see KeyBindingConfig).
        // Kept hardcoded: blur/fade tuning, quit, and remote token rotation (not action-node ops).

        kr.registerKeyAction(GLFW_KEY_COMMA, GLFW_MOD_SHIFT, () -> {
            if (blurEnabled.value && blurKernelSize.value <= BlurTextureRenderer.MIN_KERNEL_SIZE) {
                blurEnabled.setValue(0);
                cthugha.notify("blur: OFF");
            } else if (blurEnabled.value) {
                blurKernelSize.setValue(blurKernelSize.value - 2);
                cthugha.notify("blur kernel: " + blurKernelSize.value);
            }
        }, "Decrease blur kernel size (OFF at minimum)");
        kr.registerKeyAction(GLFW_KEY_PERIOD, GLFW_MOD_SHIFT, () -> {
            if (!blurEnabled.value) {
                blurEnabled.setValue(1);
                blurKernelSize.setValue(BlurTextureRenderer.MIN_KERNEL_SIZE);
                cthugha.notify("blur kernel: " + blurKernelSize.value);
            } else {
                blurKernelSize.setValue(blurKernelSize.value + 2);
                cthugha.notify("blur kernel: " + blurKernelSize.value);
            }
        }, "Increase blur kernel size (ON from OFF)");

        kr.registerKeyAction(GLFW_KEY_COMMA, 0, () -> {
            blurFade.setValue(Math.max(0.0, blurFade.value - 0.005));
            cthugha.notify(String.format("fade: %.3f", blurFade.value));
        }, "Decrease fade (faster decay)");
        kr.registerKeyAction(GLFW_KEY_PERIOD, 0, () -> {
            blurFade.setValue(Math.min(1.0, blurFade.value + 0.005));
            cthugha.notify(String.format("fade: %.3f", blurFade.value));
        }, "Increase fade (slower decay)");

        kr.registerKeyAction(GLFW_KEY_ESCAPE, () -> {
            try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing audio", e); }
            exit();
        }, "Quit");
        // Generator step — ] next, [ previous (carousel arrows on remote duplicate these)
        kr.registerKeyAction(GLFW_KEY_RIGHT_BRACKET, 0,
                () -> cthugha.translateSource.stepSelection(+1), "Next generator");
        kr.registerKeyAction(GLFW_KEY_LEFT_BRACKET, 0,
                () -> cthugha.translateSource.stepSelection(-1), "Previous generator");

        // Load INI key bindings — must come after registerDisplayActions() has populated the tree
        new KeyBindingConfig(cthugha, actionContext).register(kr);

        if (remoteConfig != null && remoteConfig.enabled) {
            kr.registerKeyAction(KeyCombination.simple('R'), () -> {
                String url = tokenStore.rotate(detectBaseUrl());
                LOG.info("Remote URL: {}", url);
                if (qrOverlay != null) qrOverlay.show(url);
                broadcaster.broadcastAll("tokenRotated", "{}");
                remoteServer.resetFirstAuth();
            }, "Rotate remote access token");
        }

        if (stdinInjector != null) {
            stdinInjector.start(kr);
        }
    }

    @Override
    public void init() throws IOException {
        java.awt.Rectangle win = getWindow();
        // Render buffer defaults to display size; can be set smaller for a retro low-res look.
        this.renderWidth  = CFG.getConfigAs("display", "render_width",  String.valueOf(win.width),  Integer::parseInt);
        this.renderHeight = CFG.getConfigAs("display", "render_height", String.valueOf(win.height), Integer::parseInt);
        int w = renderWidth;
        int h = renderHeight;

        cthugha.init(new Dimension(w, h), getRandom());
        actionContext = new CthughaActionContext(cthugha, getRandom());
        registerDisplayActions();
        cthugha.animation.init(getClock());

        // Wire translation-layer callbacks before starting the remote server so SSE events work.
        cthugha.translateSource.setOnRegenerateNeeded(() -> {
            cthugha.regenerateTranslation();
            renderActions.enqueue("rebuildTranslateMap", rc -> rebuildTranslateMap());
        });
        cthugha.translateSource.setOnNewGeneratorSelected(() -> {
            cthugha.newTranslation(cthugha.rng);
            renderActions.enqueue("rebuildTranslateMap", rc -> rebuildTranslateMap());
        });

        if (remoteConfig != null && remoteConfig.enabled) {
            tokenStore = new TokenStore();
            broadcaster = new RemoteEventBroadcaster();
            ParamSerializer serializer = new ParamSerializer();
            remoteServer = new RemoteServer(cthugha, tokenStore, broadcaster, remoteConfig, actionContext);
            wireListeners(cthugha, "", broadcaster, serializer);
            remoteServer.start();

            cthugha.translateSource.setOnTreeChanged(
                    () -> broadcaster.broadcastAll("treeChanged", "{}"));

            qrOverlay = new QrOverlay(remoteConfig.qrTimeoutSeconds, remoteConfig.qrLogoPercent);
            qrOverlay.init(this);
            remoteServer.setOnFirstAuth(() -> qrOverlay.hide());

            String initialUrl = tokenStore.rotate(detectBaseUrl());
            LOG.info("Remote URL: {}", initialUrl);
            qrOverlay.show(initialUrl);
        }

        // Font textures first to avoid disturbing the active texture unit
        FontTexture quoteFont = new FontTextureFactory(new Font("Serif", Font.ITALIC, 28), true).createFontTexture();
        FontTexture notifFont = new FontTextureFactory(new Font("Monospaced", Font.BOLD, 18), true).createFontTexture();

        // Ping-pong R8 textures
        pingTex = new Texture();
        pingTex.setInternalFormat(GL_R8);
        pingTex.setImageFormat(GL_RED);
        pingTex.setDataType(GL_UNSIGNED_BYTE);
        pingTex.setFilter(Filter.NEAREST);
        pingTex.generate(w, h, 0L);
        getResourceManager().putTexture("pingTex", pingTex);

        pongTex = new Texture();
        pongTex.setInternalFormat(GL_R8);
        pongTex.setImageFormat(GL_RED);
        pongTex.setDataType(GL_UNSIGNED_BYTE);
        pongTex.setFilter(Filter.NEAREST);
        pongTex.generate(w, h, 0L);
        getResourceManager().putTexture("pongTex", pongTex);

        // Translation map (RG16UI: absolute pixel coords per texel)
        translateMapTex = buildTranslateMapTexture(cthugha.getTranslateBuffer(), w, h);
        getResourceManager().putTexture("translateMap", translateMapTex);
        translateMapUnit = getResourceManager().nextTextureUnit();

        // Palette LUT (256×1 RGBA)
        paletteTex = new Texture();
        paletteTex.setInternalFormat(GL_RGBA);
        paletteTex.setImageFormat(GL_RGBA);
        paletteTex.setDataType(GL_UNSIGNED_BYTE);
        paletteTex.setFilter(Filter.NEAREST);
        paletteTex.generate(256, 1, fillPaletteBuffer(cthugha.paletteMap));
        getResourceManager().putTexture("palette", paletteTex);

        // FBOs — constructor immediately does GL setup, must be on GL thread
        pingFBO = new FrameBuffer(pingTex);
        pongFBO = new FrameBuffer(pongTex);

        // Pass 1: translate pingTex → pongTex (via pongFBO)
        translateRenderer = new TranslateTextureRenderer("pingTex", "translateMap");
        translateRenderer.init(this);

        // Pass 3: GPU flame — two-pass separable Gaussian blur with fade
        TextureOptions flameOpts = new TextureOptions(DataFormat.GRAY, Filter.LINEAR, Wrap.REPEAT);
        flameTex = TextureFactory.createTexture(new java.awt.Rectangle(w, h), null, flameOpts);
        getResourceManager().putTexture("flameTex", flameTex);
        flameFBO = new FrameBuffer(flameTex);

        xBlur = new BlurTextureRenderer("pongTex");
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

        // Pass 4: palette-convert pingTex → RGBA for display (default FBO)
        paletteRenderer = new PaletteRenderer("pingTex");
        paletteRenderer.init(this);
        paletteUploadUnit = getResourceManager().nextTextureUnit();
        paletteUploadUnit.bind(paletteTex);

        quoteRenderer = new StringRenderer(quoteFont);
        quoteRenderer.init(this);
        quoteRenderer.setTransform(new Matrix4f().translate(40.0f, h / 2.0f, 0.0f));
        quoteRenderer.setTextColor(StandardColors.WHITE.color);

        notifRenderer = new StringRenderer(notifFont);
        notifRenderer.init(this);
        notifRenderer.setTransform(new Matrix4f().translate(20.0f, 30.0f, 0.0f));
        notifRenderer.setTextColor(StandardColors.YELLOW.color);

        // Wave overlay — RGBA offscreen FBO shared by all wave/spectrum renderers
        waveOverlayTex = new Texture();
        waveOverlayTex.setInternalFormat(GL_RGBA);
        waveOverlayTex.setImageFormat(GL_RGBA);
        waveOverlayTex.setDataType(GL_UNSIGNED_BYTE);
        waveOverlayTex.setFilter(Filter.LINEAR);
        waveOverlayTex.generate(w, h, 0L);
        getResourceManager().putTexture("waveOverlay", waveOverlayTex);
        waveOverlayFBO = new FrameBuffer(waveOverlayTex);

        waveBaker = new WaveIndexBakeRenderer("waveOverlay");
        waveBaker.init(this);

        // 1×1 white flash texture
        ByteBuffer whitePx = BufferUtils.createByteBuffer(4);
        whitePx.put((byte) 0xFF).put((byte) 0).put((byte) 0).put((byte) 0xFF).flip();
        flashWhiteTex = new Texture();
        flashWhiteTex.setInternalFormat(GL_RGBA);
        flashWhiteTex.setImageFormat(GL_RGBA);
        flashWhiteTex.setDataType(GL_UNSIGNED_BYTE);
        flashWhiteTex.setFilter(Filter.NEAREST);
        flashWhiteTex.generate(1, 1, whitePx);

        textureBaker = new TextureBakeRenderer();
        textureBaker.init(this);
        textureBaker.setTexture(flashWhiteTex);

        // Audio pipeline
        audioPipeline = new AudioPipeline();
        audioPipeline.init(this);

        // GL wave renderers — all 4 fixed slots always initialized
        oscWave = new AudioWave(audioPipeline.getPboSink());
        oscWave.setLineColour(WAVE_COLOUR);
        oscWave.setLineWidth(2.0f);
        oscWave.setClearBeforeRender(false);
        oscWave.init(this);

        radWave = new RadialWave(audioPipeline.getPboSink());
        radWave.setLineColour(WAVE_COLOUR);
        radWave.setLineWidth(2.0f);
        radWave.setClearBeforeRender(false);
        radWave.init(this);

        specAnalyser = new SpectrumAnalyser(audioPipeline.getFreqProc());
        specAnalyser.setClearBeforeRender(false);
        audioPipeline.getFreqProc().addSink(specAnalyser);
        specAnalyser.init(this);

        radSpecAnalyser = new RadialSpectrumAnalyser(audioPipeline.getFreqProc());
        radSpecAnalyser.setClearBeforeRender(false);
        audioPipeline.getFreqProc().addSink(radSpecAnalyser);
        radSpecAnalyser.init(this);

        if (startFullscreen) {
            toggleFullscreen();
        }

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

        // 1. CPU pipeline: advance parameter animators
        cthugha.doRenderCPU();

        // 2. Upload palette LUT only when changed
        if (paletteDirty) {
            paletteDirty = false;
            paletteUploadUnit.activate();
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 256, 1, GL_RGBA, GL_UNSIGNED_BYTE,
                    fillPaletteBuffer(cthugha.paletteMap));
        }

        java.awt.Rectangle win = getWindow();

        // Sync quote text whenever it changes
        String quote = cthugha.getCurrentQuote();
        if (quote != null && !quote.equals(lastQuote)) {
            quoteRenderer.setText(quote);
            lastQuote = quote;
        } else if (quote == null) {
            lastQuote = null;
        }

        // 3. Upload audio data once for all renderers this frame
        audioPipeline.update();

        // 4. Wave offscreen: render enabled waves into shared RGBA overlay texture
        glViewport(0, 0, renderWidth, renderHeight);
        waveOverlayFBO.bind();
        glClearColor(0f, 0f, 0f, 0f);
        glClear(GL_COLOR_BUFFER_BIT);
        OscilloscopeModel om = cthugha.oscilloscope;
        if (om.enabled.value) {
            float amp = (float) om.amplitude.value;
            oscWave.setAmplitudeFunction(om.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            oscWave.setTransform(om.transform.applyTo(new Matrix4f()));
            oscWave.doRender(this);
        }
        RadialWaveModel rm = cthugha.radialWave;
        if (rm.enabled.value) {
            float amp = (float) rm.amplitude.value;
            radWave.setAmplitudeFunction(rm.ellipse.value ? AmplitudeFunction.ellipse(amp) : AmplitudeFunction.constant(amp));
            radWave.setTransform(rm.transform.applyTo(new Matrix4f()));
            radWave.doRender(this);
        }
        SpectrumModel sm = cthugha.spectrum;
        if (sm.enabled.value) {
            specAnalyser.setTransform(sm.transform.applyTo(new Matrix4f()));
            specAnalyser.doRender(this);
        }
        RadialSpectrumModel rsm = cthugha.radialSpectrum;
        if (rsm.enabled.value) {
            radSpecAnalyser.withRepeats(rsm.repeats.value);
            radSpecAnalyser.setTransform(rsm.transform.applyTo(new Matrix4f()));
            radSpecAnalyser.doRender(this);
        }
        if (quoteInBuffer && quote != null) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            quoteRenderer.doRender(this);
            glDisable(GL_BLEND);
        }
        waveOverlayFBO.unbind();
        glClearColor(0f, 0f, 0f, 1f);

        // 5. pongFBO: translate pingTex → pongTex, bake wave overlay and flash
        pongFBO.bind();
        translateRenderer.doRender(this);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        waveBaker.doRender(this);
        if (flashPending) {
            textureBaker.doRender(this);
            flashPending = false;
        }
        glDisable(GL_BLEND);
        pongFBO.unbind();

        // 6. Flame: two-pass Gaussian blur with fade
        flameFBO.bind();
        xBlur.doRender(this);
        flameFBO.unbind();

        pingFBO.bind();
        yBlur.doRender(this);
        pingFBO.unbind();

        // 7. Display: pingTex (R8) → RGBA via palette LUT → default FBO (window viewport)
        glViewport(0, 0, win.width, win.height);
        paletteRenderer.doRender(this);

        // 8. Text overlays — alpha-blend over the palette output (overlay mode only)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (!quoteInBuffer && quote != null) {
            quoteRenderer.doRender(this);
        }

        String notif = cthugha.pollNotification();
        if (notif != null) {
            notifRenderer.setText(notif);
            notifExpiry = Instant.now().plus(NOTIF_DURATION);
        }
        if (notifExpiry != null && Instant.now().isBefore(notifExpiry)) {
            notifRenderer.doRender(this);
        } else {
            notifExpiry = null;
        }

        if (qrOverlay != null) {
            qrOverlay.doRender(this);
        }

        glDisable(GL_BLEND);
    }

    @Override
    public void dispose() {
        if (oscWave           != null) oscWave.dispose();
        if (radWave           != null) radWave.dispose();
        if (specAnalyser      != null) specAnalyser.dispose();
        if (radSpecAnalyser   != null) radSpecAnalyser.dispose();
        if (audioPipeline     != null) audioPipeline.dispose();
        if (quoteRenderer     != null) quoteRenderer.dispose();
        if (notifRenderer     != null) notifRenderer.dispose();
        if (xBlur             != null) xBlur.dispose();
        if (yBlur             != null) yBlur.dispose();
        if (flameFBO          != null) flameFBO.dispose();
        if (paletteRenderer   != null) paletteRenderer.dispose();
        if (paletteUploadUnit != null) paletteUploadUnit.dispose();
        if (translateRenderer != null) translateRenderer.dispose();
        if (pingFBO           != null) pingFBO.dispose();
        if (pongFBO           != null) pongFBO.dispose();
        if (waveBaker         != null) waveBaker.dispose();
        if (textureBaker      != null) textureBaker.dispose();
        if (flashWhiteTex     != null) flashWhiteTex.dispose();
        if (flashImageTex     != null) flashImageTex.dispose();
        if (waveOverlayFBO    != null) waveOverlayFBO.dispose();
        if (qrOverlay         != null) qrOverlay.dispose();
        if (stdinInjector     != null) stdinInjector.close();
        if (remoteServer      != null) remoteServer.stop();
        try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing audio", e); }
        super.dispose();
    }

    // -------------------------------------------------------------------------
    // Display action nodes — mounted on cthugha root during init()
    // -------------------------------------------------------------------------

    private void registerDisplayActions() {
        // Display / app actions on the root node
        cthugha.addChild(action("Screenshot", "camera", ctx -> {
            renderActions.enqueue("screenshot", rc -> captureNextFrame());
            cthugha.notify("screenshot saved");
        }));
        cthugha.addChild(action("Record 5s", "video", ctx -> {
            renderActions.enqueue("startRecording", rc -> startRecording(Duration.ofSeconds(5)));
            cthugha.notify("recording 5s…");
        }));
        cthugha.addChild(action("Stop Recording", "square", ctx -> {
            renderActions.enqueue("stopRecording", rc -> stopRecording());
            cthugha.notify("recording stopped");
        }));
        cthugha.addChild(action("Flash Image", "image", ctx ->
            renderActions.enqueue("flashImage", rc -> flashImage())));
        cthugha.addChild(action("Flash White", "sun", ctx ->
            renderActions.enqueue("flashWhite", rc -> {
                textureBaker.setTexture(flashWhiteTex);
                flashPending = true;
            })));
        cthugha.addChild(action("Show Quote", "quote", ctx -> cthugha.showQuote()));
        try {
            cthugha.addChild(new PaletteLibraryNode(cthugha.reader, actionContext, () -> paletteDirty = true));
        } catch (java.io.IOException e) {
            LOG.error("Failed to build palette library node", e);
            cthugha.addChild(action("New Palette", "plus-circle", ctx -> {
                cthugha.newPalette();
                paletteDirty = true;
            }));
        }
        cthugha.addChild(action("Toggle Fullscreen", "maximize-2", ctx ->
            renderActions.enqueue("toggleFullscreen", rc -> toggleFullscreen())));
        cthugha.addChild(action("Toggle Debug", "bug", ctx -> cthugha.toggleDebug()));
        cthugha.addChild(action("Toggle Notifications", "bell", ctx -> cthugha.toggleNotifications()));
        cthugha.addChild(action("Toggle Quote Mode", "message-square", ctx -> {
            quoteInBuffer = !quoteInBuffer;
            cthugha.notify("quote: " + (quoteInBuffer ? "in buffer" : "overlay"));
        }));
        cthugha.addChild(action("Cycle Audio", "mic", ctx ->
            cthugha.notify("audio: " + audioPipeline.cycleSource().getName())));

        ContainerNode blurNode = new ContainerNode("Blur");
        blurNode.withUiHint(UiHint.ICON, "wind");
        blurNode.addChild(blurEnabled);
        blurNode.addChild(blurKernelSize);
        blurNode.addChild(blurFade);
        cthugha.addChild(blurNode);

        // Translation actions on the GeneratorRegistry node
        cthugha.translateSource.addChild(action("Randomise", "shuffle", ctx -> {
            cthugha.newTranslation(ctx.rng());
            renderActions.enqueue("rebuildTranslateMap", rc -> rebuildTranslateMap());
        }));
        cthugha.translateSource.addChild(action("New Source", "plus-circle", ctx ->
            cthugha.translateSource.selectRandom(ctx.rng())));
    }

    // -------------------------------------------------------------------------
    // Action factory helper
    // -------------------------------------------------------------------------

    private static AbstractAction action(String name, String icon, java.util.function.Consumer<ActionContext> body) {
        AbstractAction a = new AbstractAction(name, body);
        a.withUiHint(UiHint.ICON, icon);
        return a;
    }

    // -------------------------------------------------------------------------
    // Flash image helper
    // -------------------------------------------------------------------------

    private void flashImage() {
        try {
            BufferedImage img = imageSource.nextImage();
            if (flashImageTex != null) {
                flashImageTex.dispose();
            }
            flashImageTex = new Texture();
            flashImageTex.setInternalFormat(GL_RGBA);
            flashImageTex.setImageFormat(GL_RGBA);
            flashImageTex.setDataType(GL_UNSIGNED_BYTE);
            flashImageTex.setFilter(Filter.LINEAR);
            flashImageTex.generate(img.getWidth(), img.getHeight(), imageToRGBA(img));
            textureBaker.setTexture(flashImageTex);
            flashPending = true;
        } catch (IOException e) {
            LOG.error("Error loading flash image", e);
        }
    }

    private ByteBuffer imageToRGBA(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;
                buf.put((byte) luma);
                buf.put((byte) 0);
                buf.put((byte) 0);
                buf.put((byte) 0xFF);
            }
        }
        buf.flip();
        return buf;
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

    public static void main(String[] args) throws Exception {
        boolean stdinEnabled = Arrays.asList(args).contains("--stdin");
        RemoteConfig remoteConfig = RemoteConfig.parse(args);
        DumpConfig dumpConfig = DumpConfig.parse(args);
        new CthughaWindow(stdinEnabled, remoteConfig, dumpConfig).displayLoop();
    }

    private String detectBaseUrl() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                boolean named = remoteConfig.networkInterface != null
                        && remoteConfig.networkInterface.equalsIgnoreCase(ni.getName());
                LOG.debug("Network interface: {} loopback={} up={} virtual={} named={}",
                        ni.getName(), ni.isLoopback(), ni.isUp(), ni.isVirtual(), named);
                // When the user names an interface, trust it; otherwise skip loopback/virtual/down.
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;
                if (remoteConfig.networkInterface != null && !named) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    LOG.debug("  address: {} ({})", addr.getHostAddress(), addr.getClass().getSimpleName());
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return "http://" + addr.getHostAddress() + ":" + remoteConfig.port + "/";
                    }
                }
            }
        } catch (SocketException e) {
            LOG.warn("Could not detect local IP", e);
        }
        LOG.warn("No suitable network interface found (configured: {}), falling back to localhost",
                remoteConfig.networkInterface);
        return "http://localhost:" + remoteConfig.port + "/";
    }

    private void wireListeners(Node node, String path, RemoteEventBroadcaster evtBroadcaster,
                               ParamSerializer serializer) {
        if (node instanceof AbstractValue value) {
            String nodePath = path;
            value.addChangeListener(() -> {
                try {
                    String payload = serializer.getMapper()
                            .writeValueAsString(serializer.buildChangeEvent(nodePath, value));
                    evtBroadcaster.broadcast("paramChanged", payload);
                } catch (Exception ignored) {}
            });
        }
        node.getChildren().forEach(child -> {
            String childPath = path.isEmpty() ? child.getName() : path + "/" + child.getName();
            wireListeners(child, childPath, evtBroadcaster, serializer);
        });
    }
}
