package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.GLWindow;
import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.TranslateTextureRenderer;
import com.asteroid.duck.opengl.util.blur.BlurTextureRenderer;
import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariable;
import com.asteroid.duck.opengl.util.resources.texture.DataFormat;
import com.asteroid.duck.opengl.util.resources.texture.TextureFactory;
import com.asteroid.duck.opengl.util.resources.texture.TextureOptions;
import com.asteroid.duck.opengl.util.audio.AudioReader;
import com.asteroid.duck.opengl.util.audio.LineAcquirer;
import com.asteroid.duck.opengl.util.audio.PboAudioSink;
import com.asteroid.duck.opengl.util.wave.AudioWave;
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
import com.asteroid.duck.opengl.util.timer.Timer;
import com.asteroid.duck.opengl.util.timer.TimerImpl;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.img.RandomImageSource;
import io.github.duckasteroid.cthugha.map.PaletteMap;
import io.github.duckasteroid.cthugha.tab.TranslateTableSource;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL30.GL_RG16UI;
import static org.lwjgl.opengl.GL30.GL_RG_INTEGER;

public class CthughaWindow extends GLWindow {

    private static final Logger LOG = LoggerFactory.getLogger(CthughaWindow.class);

    private final JCthugha cthugha;

    // Palette LUT (256×1 RGBA)
    private Texture paletteTex;

    // CPU overlay: buffer.pixels uploaded here each frame (R8)
    private Texture screenOverlayTex;

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

    // Pass 2 (pongFBO, alpha blend): bake CPU overlay (waves) into pongTex
    private OverlayBakeRenderer overlayBaker;

    // Pass 3 (pingFBO): GPU flame — blur pongTex → pingTex (5-tap average with fade)
    // Two-pass separable Gaussian blur replacing the old FlameRenderer.
    // Multiplier applied only in the Y pass so fade occurs exactly once per frame.
    private BlurTextureRenderer xBlur;
    private BlurTextureRenderer yBlur;
    private Texture flameTex;      // R32F intermediate between X and Y blur passes
    private FrameBuffer flameFBO;
    private volatile float fadeMultiplier = 0.99f;
    private static final int DEFAULT_KERNEL_SIZE = 29;

    // Pass 4 (default FBO): palette-convert pingTex to RGBA for display
    private PaletteRenderer paletteRenderer;
    // Dedicated unit for per-frame palette LUT uploads
    private TextureUnit paletteUploadUnit;

    // Reused buffer for uploading CPU pixels each frame
    private ByteBuffer overlayBuffer;

    private StringRenderer quoteRenderer;
    private StringRenderer notifRenderer;
    private String lastQuote = null;
    private Instant notifExpiry = null;
    private static final Duration NOTIF_DURATION = Duration.ofSeconds(3);

    // Texture flash: one-shot bake of an RGBA texture into pongTex as palette indices
    private final RandomImageSource imageSource = new RandomImageSource(Paths.get("pcx"));
    private TextureBakeRenderer textureBaker;
    private Texture flashWhiteTex;   // 1×1 R=0xFF A=0xFF — palette index 255 full-screen
    private Texture flashImageTex;   // last loaded PCX image (greyscale → palette index)
    private boolean flashPending = false;

    // Quote render mode: true = baked into the indexed buffer (affected by flame/translate);
    //                    false = RGBA overlay on top of the palette output (default)
    private boolean quoteInBuffer = false;
    private Texture quoteOverlayTex;
    private FrameBuffer quoteOverlayFBO;
    private WaveIndexBakeRenderer quoteBaker;

    private final TimerImpl timer = new TimerImpl(() -> (double) System.nanoTime() / 1e9);
    private Double desiredUpdatePeriod = null;

    private final RenderActionQueue renderActions = new RenderActionQueue();
    private final StdinKeyInjector stdinInjector;

    // AudioWave pipeline: LineAcquirer → AudioReader (background thread) → PboAudioSink (GL texture) → AudioWave
    private LineAcquirer waveLineAcquirer;
    private PboAudioSink audioSink;
    private AudioReader audioReader;
    private Thread audioThread;
    private AudioWave audioWave;
    // RGBA offscreen texture: AudioWave renders here (its glClear is isolated)
    private Texture waveOverlayTex;
    private FrameBuffer waveOverlayFBO;
    // Converts RGBA wave overlay → R8 palette indices baked into pongTex alongside translate/overlay
    private WaveIndexBakeRenderer waveBaker;

    public CthughaWindow(boolean stdinEnabled) {
        super(new ResourceManagerImpl(new PathBasedLoader(Paths.get("."))),
                "Cthugha Reborn", 1280, 720, null);
        this.cthugha = new JCthugha();
        this.stdinInjector = stdinEnabled ? new StdinKeyInjector(renderActions) : null;
    }

    @Override
    public Timer getTimer() {
        return timer;
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

        kr.registerKeyAction(KeyCombination.simple('D'), cthugha::toggleDebug, "Toggle debug");
        kr.registerKeyAction(KeyCombination.simple('F'), this::toggleFullscreen, "Toggle fullscreen");
        kr.registerKeyAction(KeyCombination.simple('I'), this::flashImage, "Flash a random image");
        kr.registerKeyAction(KeyCombination.simple('N'), cthugha::toggleNotifications, "Toggle notifications");
        kr.registerKeyAction(KeyCombination.simple('P'), cthugha::newPalette, "Change palette");
        kr.registerKeyAction(KeyCombination.simple('Q'), cthugha::showQuote, "Show a quote on screen");
        kr.registerKeyAction(KeyCombination.simple('B'), () -> {
            quoteInBuffer = !quoteInBuffer;
            cthugha.notify("quote: " + (quoteInBuffer ? "in buffer" : "overlay"));
        }, "Toggle quote render mode (overlay vs. in-buffer)");
        kr.registerKeyAction(KeyCombination.simple('T'), () -> {
            cthugha.newTranslation(false);
            rebuildTranslateMap();
        }, "Randomise translation");
        kr.registerKeyAction(KeyCombination.simpleWithMods('T', "SHIFT"), () -> {
            cthugha.newTranslation(true);
            rebuildTranslateMap();
        }, "Fully randomise translation");
        kr.registerKeyAction(KeyCombination.simple('X'), () -> {
            textureBaker.setTexture(flashWhiteTex);
            flashPending = true;
        }, "Flash fill screen white");

        kr.registerKeyAction(GLFW_KEY_COMMA, GLFW_MOD_SHIFT, () -> {
            if (xBlur.isBlur() && xBlur.getKernelSize() <= BlurTextureRenderer.MIN_KERNEL_SIZE) {
                xBlur.setBlur(false);
                yBlur.setBlur(false);
                cthugha.notify("blur: OFF");
            } else if (xBlur.isBlur()) {
                xBlur.decreaseKernelSize();
                yBlur.setKernelSize(xBlur.getKernelSize());
                cthugha.notify("blur kernel: " + xBlur.getKernelSize());
            }
        }, "Decrease blur kernel size (OFF at minimum)");
        kr.registerKeyAction(GLFW_KEY_PERIOD, GLFW_MOD_SHIFT, () -> {
            if (!xBlur.isBlur()) {
                xBlur.setBlur(true);
                yBlur.setBlur(true);
                xBlur.setKernelSize(BlurTextureRenderer.MIN_KERNEL_SIZE);
                yBlur.setKernelSize(BlurTextureRenderer.MIN_KERNEL_SIZE);
                cthugha.notify("blur kernel: " + xBlur.getKernelSize());
            } else {
                xBlur.increaseKernelSize();
                yBlur.setKernelSize(xBlur.getKernelSize());
                cthugha.notify("blur kernel: " + xBlur.getKernelSize());
            }
        }, "Increase blur kernel size (ON from OFF)");

        kr.registerKeyAction(GLFW_KEY_COMMA, 0, () -> {
            fadeMultiplier = Math.max(0.0f, fadeMultiplier - 0.005f);
            cthugha.notify(String.format("fade: %.3f", fadeMultiplier));
        }, "Decrease fade (faster decay)");
        kr.registerKeyAction(GLFW_KEY_PERIOD, 0, () -> {
            fadeMultiplier = Math.min(1.0f, fadeMultiplier + 0.005f);
            cthugha.notify(String.format("fade: %.3f", fadeMultiplier));
        }, "Increase fade (slower decay)");

        kr.registerKeyAction(GLFW_KEY_ESCAPE, () -> {
            try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing audio", e); }
            exit();
        }, "Quit");
        kr.registerKeyAction(GLFW_KEY_F12, 0, this::captureNextFrame, "Capture screenshot");

        if (stdinInjector != null) {
            stdinInjector.start(kr);
        }
    }

    @Override
    public void init() throws IOException {
        java.awt.Rectangle win = getWindow();
        int w = win.width;
        int h = win.height;

        cthugha.init(new Dimension(w, h));

        // Font textures first to avoid disturbing the active texture unit
        FontTexture quoteFont = new FontTextureFactory(new Font("Serif", Font.ITALIC, 28), true).createFontTexture();
        FontTexture notifFont = new FontTextureFactory(new Font("Monospaced", Font.BOLD, 18), true).createFontTexture();

        // CPU overlay texture (R8)
        screenOverlayTex = new Texture();
        screenOverlayTex.setInternalFormat(GL_R8);
        screenOverlayTex.setImageFormat(GL_RED);
        screenOverlayTex.setDataType(GL_UNSIGNED_BYTE);
        screenOverlayTex.setFilter(Filter.NEAREST);
        screenOverlayTex.generate(w, h, 0L);
        getResourceManager().putTexture("screenOverlay", screenOverlayTex);

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
        translateMapTex = buildTranslateMapTexture(cthugha.getTranslateTable(), w, h);
        getResourceManager().putTexture("translateMap", translateMapTex);
        translateMapUnit = getResourceManager().nextTextureUnit();

        // Palette LUT (256×1 RGBA)
        paletteTex = new Texture();
        paletteTex.setInternalFormat(GL_RGBA);
        paletteTex.setImageFormat(GL_RGBA);
        paletteTex.setDataType(GL_UNSIGNED_BYTE);
        paletteTex.setFilter(Filter.NEAREST);
        paletteTex.generate(256, 1, buildPaletteBuffer(cthugha.buffer.paletteMap));
        getResourceManager().putTexture("palette", paletteTex);

        // FBOs — constructor immediately does GL setup, must be on GL thread
        pingFBO = new FrameBuffer(pingTex);
        pongFBO = new FrameBuffer(pongTex);

        // Pass 1: translate pingTex → pongTex (via pongFBO)
        translateRenderer = new TranslateTextureRenderer("pingTex", "translateMap");
        translateRenderer.init(this);

        // Pass 2: bake CPU overlay (waves) into pongTex (via pongFBO, alpha blend)
        overlayBaker = new OverlayBakeRenderer("screenOverlay");
        overlayBaker.init(this);

        // Pass 3: GPU flame — blur pongTex → pingTex (via pingFBO)
        // Flame: two-pass separable Gaussian blur with a per-frame fade on the Y pass
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
        yBlur.addVariable(ShaderVariable.floatVariable("multiplier", () -> fadeMultiplier));
        yBlur.init(this);

        // Pass 4: palette-convert pingTex → RGBA for display (default FBO)
        paletteRenderer = new PaletteRenderer("pingTex");
        paletteRenderer.init(this);
        paletteUploadUnit = getResourceManager().nextTextureUnit();
        paletteUploadUnit.bind(paletteTex);

        overlayBuffer = BufferUtils.createByteBuffer(w * h);

        quoteRenderer = new StringRenderer(quoteFont);
        quoteRenderer.init(this);
        quoteRenderer.setTransform(new Matrix4f().translate(40.0f, h / 2.0f, 0.0f));
        quoteRenderer.setTextColor(StandardColors.WHITE.color);

        notifRenderer = new StringRenderer(notifFont);
        notifRenderer.init(this);
        notifRenderer.setTransform(new Matrix4f().translate(20.0f, 30.0f, 0.0f));
        notifRenderer.setTextColor(StandardColors.YELLOW.color);

        // AudioWave — RGBA offscreen render target so its built-in glClear is isolated
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

        // Quote in-buffer mode: RGBA offscreen texture baked into pongTex as a palette index
        quoteOverlayTex = new Texture();
        quoteOverlayTex.setInternalFormat(GL_RGBA);
        quoteOverlayTex.setImageFormat(GL_RGBA);
        quoteOverlayTex.setDataType(GL_UNSIGNED_BYTE);
        quoteOverlayTex.setFilter(Filter.LINEAR);
        quoteOverlayTex.generate(w, h, 0L);
        getResourceManager().putTexture("quoteOverlay", quoteOverlayTex);
        quoteOverlayFBO = new FrameBuffer(quoteOverlayTex);

        quoteBaker = new WaveIndexBakeRenderer("quoteOverlay");
        quoteBaker.init(this);

        // 1×1 white flash texture: R=0xFF maps to palette index 255, A=0xFF fully opaque
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

        audioSink = PboAudioSink.create(AudioWave.AUDIO_BUFFER_SIZE, this);
        audioWave = new AudioWave(audioSink);
        audioWave.init(this);
        audioWave.setLineColour(new Vector4f(1f, 1f, 1f, 0.85f));
        audioWave.setLineWidth(2.0f);

        waveLineAcquirer = new LineAcquirer();
        waveLineAcquirer.init(this, LineAcquirer.IDEAL);
        audioReader = new AudioReader(List.of(audioSink));
        audioReader.setLine(waveLineAcquirer.getSelectedSource());
        audioThread = Thread.ofVirtual().start(audioReader);
    }

    @Override
    public void render() throws IOException {
        renderActions.processAll(this);
        timer.update();

        // 1. CPU pipeline: waves write palette indices into buffer.pixels
        cthugha.doRenderCPU();

        // 2. Upload CPU overlay (wave palette indices) to screenOverlayTex
        overlayBaker.getOverlayUnit().activate();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        overlayBuffer.clear();
        overlayBuffer.put(cthugha.buffer.pixels);
        overlayBuffer.flip();
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                cthugha.buffer.width, cthugha.buffer.height,
                GL_RED, GL_UNSIGNED_BYTE, overlayBuffer);

        // 3. Upload palette LUT
        paletteUploadUnit.activate();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        ByteBuffer palBuf = buildPaletteBuffer(cthugha.buffer.paletteMap);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 256, 1, GL_RGBA, GL_UNSIGNED_BYTE, palBuf);

        java.awt.Rectangle win = getWindow();

        // 4. AudioWave offscreen: render waveform into RGBA overlay texture.
        //    AudioWave.doRender() calls glClear() before drawing — isolating it here
        //    prevents it from clearing the indexed pipeline textures.
        glClearColor(0f, 0f, 0f, 0f);
        waveOverlayFBO.bind();
        audioSink.upload();
        audioWave.doRender(this);
        waveOverlayFBO.unbind();
        glViewport(0, 0, win.width, win.height);
        glClearColor(0f, 0f, 0f, 1f);

        // Sync quote text whenever it changes
        String quote = cthugha.getCurrentQuote();
        if (quote != null && !quote.equals(lastQuote)) {
            quoteRenderer.setText(quote);
            lastQuote = quote;
        } else if (quote == null) {
            lastQuote = null;
        }

        // 4b. Quote offscreen (in-buffer mode): render quote text into quoteOverlayFBO so it
        //     gets baked into the indexed pipeline and is affected by flame and translate.
        if (quoteInBuffer && quote != null) {
            glClearColor(0f, 0f, 0f, 0f);
            quoteOverlayFBO.bind();
            glClear(GL_COLOR_BUFFER_BIT);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            quoteRenderer.doRender(this);
            glDisable(GL_BLEND);
            quoteOverlayFBO.unbind();
            glViewport(0, 0, win.width, win.height);
            glClearColor(0f, 0f, 0f, 1f);
        }

        // 5. pongFBO: translate pingTex → pongTex, then bake wave, optional quote, and CPU overlay.
        //    waveBaker/quoteBaker read RGBA overlay textures (no feedback loop: pongTex is the FBO).
        pongFBO.bind();
        translateRenderer.doRender(this);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        waveBaker.doRender(this);
        if (quoteInBuffer && quote != null) {
            quoteBaker.doRender(this);
        }
        if (flashPending) {
            textureBaker.doRender(this);
            flashPending = false;
        }
        overlayBaker.doRender(this);
        glDisable(GL_BLEND);
        pongFBO.unbind();
        glViewport(0, 0, win.width, win.height);

        // 6. Flame: two-pass Gaussian blur with fade.
        //    X pass: pongTex → flameTex (multiplier=1.0, no fade)
        //    Y pass: flameTex → pingTex (multiplier=0.99, fade applied once)
        flameFBO.bind();
        xBlur.doRender(this);
        flameFBO.unbind();
        glViewport(0, 0, win.width, win.height);

        pingFBO.bind();
        yBlur.doRender(this);
        pingFBO.unbind();
        glViewport(0, 0, win.width, win.height);

        // 7. Display: pingTex (R8) → RGBA via palette LUT → default FBO
        paletteRenderer.doRender(this);

        // 9. Text overlays — alpha-blend over the palette output (overlay mode only)
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

        glDisable(GL_BLEND);

    }

    @Override
    public void dispose() {
        if (quoteRenderer     != null) quoteRenderer.dispose();
        if (notifRenderer     != null) notifRenderer.dispose();
        if (overlayBaker      != null) overlayBaker.dispose();
        if (xBlur             != null) xBlur.dispose();
        if (yBlur             != null) yBlur.dispose();
        if (flameFBO          != null) flameFBO.dispose();
        if (paletteRenderer   != null) paletteRenderer.dispose();
        if (paletteUploadUnit != null) paletteUploadUnit.dispose();
        if (translateRenderer != null) translateRenderer.dispose();
        if (pingFBO           != null) pingFBO.dispose();
        if (pongFBO           != null) pongFBO.dispose();
        if (waveBaker         != null) waveBaker.dispose();
        if (quoteBaker        != null) quoteBaker.dispose();
        if (textureBaker      != null) textureBaker.dispose();
        if (flashWhiteTex     != null) flashWhiteTex.dispose();
        if (flashImageTex     != null) flashImageTex.dispose();
        if (audioReader       != null) { audioReader.setRunning(false); }
        if (audioThread       != null) { audioThread.interrupt(); }
        if (audioWave         != null) audioWave.dispose();
        if (waveOverlayFBO    != null) waveOverlayFBO.dispose();
        if (quoteOverlayFBO   != null) quoteOverlayFBO.dispose();
        // Textures registered with ResourceManager are disposed by super.dispose()
        if (stdinInjector != null) stdinInjector.close();
        try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing audio", e); }
        super.dispose();
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
                buf.put((byte) luma); // R → palette index
                buf.put((byte) 0);
                buf.put((byte) 0);
                buf.put((byte) 0xFF); // fully opaque
            }
        }
        buf.flip();
        return buf;
    }

    // -------------------------------------------------------------------------
    // Translation map helper
    // -------------------------------------------------------------------------

    private Texture buildTranslateMapTexture(int[] table, int w, int h) {
        Texture t = new Texture();
        t.setInternalFormat(GL_RG16UI);
        t.setImageFormat(GL_RG_INTEGER);
        t.setDataType(GL_UNSIGNED_SHORT);
        t.setFilter(Filter.NEAREST); // integer textures must not use LINEAR
        t.setWrap(Wrap.REPEAT);
        t.generate(w, h, TranslateTableSource.tableToRG16Buffer(table, w, h));
        return t;
    }

    private void rebuildTranslateMap() {
        int w = cthugha.buffer.width;
        int h = cthugha.buffer.height;
        ByteBuffer data = TranslateTableSource.tableToRG16Buffer(cthugha.getTranslateTable(), w, h);
        translateMapUnit.activate();
        translateMapUnit.bind(translateMapTex);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h,
                GL_RG_INTEGER, GL_UNSIGNED_SHORT, data);
    }

    // -------------------------------------------------------------------------
    // Palette buffer builder
    // -------------------------------------------------------------------------

    private ByteBuffer buildPaletteBuffer(PaletteMap pm) {
        ByteBuffer buf = BufferUtils.createByteBuffer(256 * 4);
        for (int c : pm.colors) {
            buf.put((byte) ((c >> 16) & 0xFF)); // R
            buf.put((byte) ((c >> 8) & 0xFF));  // G
            buf.put((byte) (c & 0xFF));          // B
            buf.put((byte) 0xFF);               // A
        }
        buf.flip();
        return buf;
    }

    public static void main(String[] args) throws Exception {
        boolean stdinEnabled = Arrays.asList(args).contains("--stdin");
        new CthughaWindow(stdinEnabled).displayLoop();
    }
}
