package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.GLWindow;
import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.TranslateTextureRenderer;
import com.asteroid.duck.opengl.util.audio.LineAcquirer;
import com.asteroid.duck.opengl.util.wave.AudioWave;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.geom.Rectangle;
import com.asteroid.duck.opengl.util.keys.KeyCombination;
import com.asteroid.duck.opengl.util.palette.PaletteRenderer;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.framebuffer.FrameBuffer;
import com.asteroid.duck.opengl.util.resources.manager.ResourceManager;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
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
import io.github.duckasteroid.cthugha.map.PaletteMap;
import io.github.duckasteroid.cthugha.tab.TranslateTableSource;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

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
    private FlameRenderer flameRenderer;

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

    // AudioWave uses its own LineAcquirer (separate from JCthugha's audio to avoid racing reads)
    private LineAcquirer waveLineAcquirer;
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
        kr.registerKeyAction(KeyCombination.simple('I'), cthugha::flashImage, "Flash a random image");
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
        kr.registerKeyAction(KeyCombination.simple('X'), () -> Arrays.fill(cthugha.buffer.pixels, (byte) 255), "Flash fill screen white");

        kr.registerKeyAction(GLFW_KEY_1, GLFW_MOD_SHIFT, () -> {
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
        flameRenderer = new FlameRenderer("pongTex");
        flameRenderer.init(this);

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

        waveLineAcquirer = new LineAcquirer();
        waveLineAcquirer.init(this, LineAcquirer.IDEAL);
        audioWave = new AudioWave();
        audioWave.init(this);
        audioWave.setLine(waveLineAcquirer.getSelectedSource());
        audioWave.setLineColour(new Vector4f(1f, 1f, 1f, 0.85f));
        audioWave.setLineWidth(2.0f);
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
        overlayBaker.doRender(this);
        glDisable(GL_BLEND);
        pongFBO.unbind();
        glViewport(0, 0, win.width, win.height);

        // 6. pingFBO: GPU flame — blur pongTex → pingTex (5-tap average with fade)
        pingFBO.bind();
        flameRenderer.doRender(this);
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
        if (flameRenderer     != null) flameRenderer.dispose();
        if (paletteRenderer   != null) paletteRenderer.dispose();
        if (paletteUploadUnit != null) paletteUploadUnit.dispose();
        if (translateRenderer != null) translateRenderer.dispose();
        if (pingFBO           != null) pingFBO.dispose();
        if (pongFBO           != null) pongFBO.dispose();
        if (waveBaker         != null) waveBaker.dispose();
        if (quoteBaker        != null) quoteBaker.dispose();
        if (audioWave         != null) audioWave.dispose();
        if (waveOverlayFBO    != null) waveOverlayFBO.dispose();
        if (quoteOverlayFBO   != null) quoteOverlayFBO.dispose();
        // Textures registered with ResourceManager are disposed by super.dispose()
        if (stdinInjector != null) stdinInjector.close();
        try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing audio", e); }
        super.dispose();
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

    // -------------------------------------------------------------------------
    // Inner class: WaveIndexBakeRenderer
    // Reads the RGBA waveform overlay and writes palette index 200 (as a normalised
    // R8 value) wherever the wave alpha exceeds 0.1. Rendered into pongFBO with
    // GL_SRC_ALPHA blending so transparent pixels preserve the translated content
    // while opaque wave pixels overwrite with the palette index.
    // -------------------------------------------------------------------------

    private static class WaveIndexBakeRenderer implements RenderedItem {

        private static final String VERT = """
                #version 330 core
                in vec2 screenPosition;
                in vec2 texturePosition;
                out vec2 texCoords;
                void main() {
                    texCoords = texturePosition;
                    gl_Position = vec4(screenPosition, 0.0, 1.0);
                }
                """;

        private static final String FRAG = """
                #version 330 core
                in vec2 texCoords;
                out vec4 fragColor;
                uniform sampler2D waveOverlay;
                void main() {
                    float a = texture(waveOverlay, texCoords).a;
                    float idx = 200.0 / 255.0;
                    fragColor = vec4(idx, 0.0, 0.0, a > 0.1 ? 1.0 : 0.0);
                }
                """;

        private final String overlayName;
        private ShaderProgram shader;
        private TextureUnit overlayUnit;
        private Rectangle quad;

        WaveIndexBakeRenderer(String overlayName) {
            this.overlayName = overlayName;
        }

        @Override
        public void init(RenderContext ctx) throws IOException {
            shader = ShaderProgram.compile(
                    ShaderSource.fromClass(VERT, WaveIndexBakeRenderer.class),
                    ShaderSource.fromClass(FRAG, WaveIndexBakeRenderer.class),
                    null);
            shader.use(ctx);
            ResourceManager rm = ctx.getResourceManager();
            overlayUnit = rm.nextTextureUnit();
            overlayUnit.bind(rm.getTexture(overlayName));
            overlayUnit.useInShader(shader, "waveOverlay");
            quad = new Rectangle(ctx, "screenPosition", "texturePosition");
            quad.getVertexArrayObject().bind(ctx);
            quad.getVertexBufferObject().setup(shader);
        }

        @Override
        public void doRender(RenderContext ctx) {
            shader.use(ctx);
            quad.render(ctx);
        }

        @Override
        public void dispose() {
            if (quad        != null) quad.destroy();
            if (shader      != null) shader.dispose();
            if (overlayUnit != null) overlayUnit.dispose();
        }
    }

    // -------------------------------------------------------------------------
    // Inner class: FlameRenderer
    // GPU equivalent of JavaFlame: 5-tap box average (N/S/E/W/center) with a
    // 1-palette-step fade each frame, preventing infinite accumulation.
    // Reads from pongTex (translate+overlay result), writes to pingTex (via pingFBO).
    // -------------------------------------------------------------------------

    private static class FlameRenderer implements RenderedItem {

        private static final String VERT = """
                #version 330 core
                in vec2 screenPosition;
                in vec2 texturePosition;
                out vec2 texCoords;
                void main() {
                    texCoords = texturePosition;
                    gl_Position = vec4(screenPosition, 0.0, 1.0);
                }
                """;

        private static final String FRAG = """
                #version 330 core
                in vec2 texCoords;
                out vec4 fragColor;
                uniform sampler2D src;
                uniform vec2 texelSize;
                void main() {
                    float px = texelSize.x;
                    float py = texelSize.y;
                    float c = texture(src, texCoords).r;
                    float l = texture(src, texCoords + vec2(-px,  0.0)).r;
                    float r = texture(src, texCoords + vec2(+px,  0.0)).r;
                    float u = texture(src, texCoords + vec2( 0.0, +py)).r;
                    float d = texture(src, texCoords + vec2( 0.0, -py)).r;
                    float avg = (c + l + r + u + d) * 0.2;
                    fragColor = vec4(max(0.0, avg - 1.0 / 255.0), 0.0, 0.0, 1.0);
                }
                """;

        private final String srcName;
        private ShaderProgram shader;
        private TextureUnit srcUnit;
        private com.asteroid.duck.opengl.util.resources.shader.Uniform<Vector2f> uTexelSize;
        private Rectangle quad;

        FlameRenderer(String srcName) {
            this.srcName = srcName;
        }

        @Override
        public void init(RenderContext ctx) throws IOException {
            shader = ShaderProgram.compile(
                    ShaderSource.fromClass(VERT, FlameRenderer.class),
                    ShaderSource.fromClass(FRAG, FlameRenderer.class),
                    null);
            shader.use(ctx);
            ResourceManager rm = ctx.getResourceManager();
            srcUnit = rm.nextTextureUnit();
            Texture t = rm.getTexture(srcName);
            srcUnit.bind(t);
            srcUnit.useInShader(shader, "src");
            uTexelSize = shader.uniforms().get("texelSize", Vector2f.class);
            uTexelSize.set(new Vector2f(1.0f / t.getWidth(), 1.0f / t.getHeight()));
            quad = new Rectangle(ctx, "screenPosition", "texturePosition");
            quad.getVertexArrayObject().bind(ctx);
            quad.getVertexBufferObject().setup(shader);
        }

        @Override
        public void doRender(RenderContext ctx) {
            shader.use(ctx);
            quad.render(ctx);
        }

        @Override
        public void dispose() {
            if (quad    != null) quad.destroy();
            if (shader  != null) shader.dispose();
            if (srcUnit != null) srcUnit.dispose();
        }
    }

    // -------------------------------------------------------------------------
    // Inner class: OverlayBakeRenderer
    // Renders CPU overlay (R8) into the currently bound FBO using alpha blending:
    // non-zero palette indices overwrite the underlying translated content;
    // zero indices (background) are transparent, preserving the translated pixel.
    // -------------------------------------------------------------------------

    private static class OverlayBakeRenderer implements RenderedItem {

        private static final String VERT = """
                #version 330 core
                in vec2 screenPosition;
                in vec2 texturePosition;
                out vec2 texCoords;
                void main() {
                    texCoords = texturePosition;
                    gl_Position = vec4(screenPosition, 0.0, 1.0);
                }
                """;

        // Writes the palette index to R; alpha=1 for foreground, alpha=0 for background (index 0).
        // With GL_SRC_ALPHA/GL_ONE_MINUS_SRC_ALPHA blending on the R8 FBO:
        //   dest.R = src.R * src.A + dest.R * (1 - src.A)
        // Background (alpha=0): keeps existing translated pixel. Foreground (alpha=1): overwrites.
        private static final String FRAG = """
                #version 330 core
                in vec2 texCoords;
                out vec4 fragColor;
                uniform sampler2D overlay;
                void main() {
                    float idx = texture(overlay, texCoords).r;
                    fragColor = vec4(idx, 0.0, 0.0, idx > 0.0 ? 1.0 : 0.0);
                }
                """;

        private final String overlayName;
        private ShaderProgram shader;
        private TextureUnit overlayUnit;
        private Rectangle quad;

        OverlayBakeRenderer(String overlayName) {
            this.overlayName = overlayName;
        }

        @Override
        public void init(RenderContext ctx) throws IOException {
            shader = ShaderProgram.compile(
                    ShaderSource.fromClass(VERT, OverlayBakeRenderer.class),
                    ShaderSource.fromClass(FRAG, OverlayBakeRenderer.class),
                    null);
            shader.use(ctx);
            ResourceManager rm = ctx.getResourceManager();
            overlayUnit = rm.nextTextureUnit();
            overlayUnit.bind(rm.getTexture(overlayName));
            overlayUnit.useInShader(shader, "overlay");
            quad = new Rectangle(ctx, "screenPosition", "texturePosition");
            quad.getVertexArrayObject().bind(ctx);
            quad.getVertexBufferObject().setup(shader);
        }

        @Override
        public void doRender(RenderContext ctx) {
            shader.use(ctx);
            quad.render(ctx);
        }

        @Override
        public void dispose() {
            if (quad        != null) quad.destroy();
            if (shader      != null) shader.dispose();
            if (overlayUnit != null) overlayUnit.dispose();
        }

        TextureUnit getOverlayUnit() { return overlayUnit; }
    }

    public static void main(String[] args) throws Exception {
        boolean stdinEnabled = Arrays.asList(args).contains("--stdin");
        new CthughaWindow(stdinEnabled).displayLoop();
    }
}
