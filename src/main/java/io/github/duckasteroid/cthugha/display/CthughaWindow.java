package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.GLWindow;
import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.TranslateTextureRenderer;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.geom.Rectangle;
import com.asteroid.duck.opengl.util.keys.KeyCombination;
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
import com.asteroid.duck.opengl.util.resources.textureunit.TextureUnit;
import com.asteroid.duck.opengl.util.text.StringRenderer;
import com.asteroid.duck.opengl.util.timer.Timer;
import com.asteroid.duck.opengl.util.timer.TimerImpl;
import io.github.duckasteroid.cthugha.JCthugha;
import io.github.duckasteroid.cthugha.map.PaletteMap;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.LineUnavailableException;
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

    // Ping-pong R8 textures and their FBOs.
    // Each frame: translate readTex → writeTex (FBO), then bake CPU overlay into writeTex.
    // writeTex becomes next frame's readTex.
    private Texture pingTex;
    private Texture pongTex;
    private FrameBuffer pingFBO;
    private FrameBuffer pongFBO;
    private boolean pingIsRead = true; // true: ping=read/pong=write, false: reversed

    // RG16UI translation map (x, y coords per pixel as uint16)
    private Texture translateMapTex;

    // TranslateTextureRenderer subclass that exposes the source texture unit for per-frame rebind
    private PingPongTranslateRenderer translateRenderer;

    // Pass 1 (inside writeFBO): bakes CPU overlay onto the translated ping-pong texture
    private OverlayBakeRenderer overlayBaker;

    // Pass 2 (default FBO): converts the finished R8 ping-pong texture to RGBA via palette LUT
    private PaletteDisplayRenderer paletteDisplay;

    // Reused buffer for uploading CPU pixels each frame
    private ByteBuffer overlayBuffer;

    private StringRenderer quoteRenderer;
    private StringRenderer notifRenderer;
    private String lastQuote = null;
    private Instant notifExpiry = null;
    private static final Duration NOTIF_DURATION = Duration.ofSeconds(3);

    private final TimerImpl timer = new TimerImpl(() -> (double) System.nanoTime() / 1e9);
    private Double desiredUpdatePeriod = null;

    public CthughaWindow() throws LineUnavailableException {
        super(new ResourceManagerImpl(new PathBasedLoader(Paths.get("."))),
                "Cthugha Reborn", 1280, 720, null);
        this.cthugha = new JCthugha();
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

        kr.registerKeyAction(KeyCombination.simple('A'), cthugha::toggleAudioSource, "Toggle audio source");
        kr.registerKeyAction(KeyCombination.simple('D'), cthugha::toggleDebug, "Toggle debug");
        kr.registerKeyAction(KeyCombination.simple('F'), this::toggleFullscreen, "Toggle fullscreen");
        kr.registerKeyAction(KeyCombination.simple('I'), cthugha::flashImage, "Flash a random image");
        kr.registerKeyAction(KeyCombination.simple('J'), () -> cthugha.changeAmplitude(0.99), "Decrease amplitude 1%");
        kr.registerKeyAction(KeyCombination.simpleWithMods('J', "SHIFT"), () -> cthugha.changeAmplitude(0.9), "Decrease amplitude 10%");
        kr.registerKeyAction(KeyCombination.simple('N'), cthugha::toggleNotifications, "Toggle notifications");
        kr.registerKeyAction(KeyCombination.simple('P'), cthugha::newPalette, "Change palette");
        kr.registerKeyAction(KeyCombination.simple('Q'), cthugha::showQuote, "Show a quote on screen");
        kr.registerKeyAction(KeyCombination.simple('S'), cthugha::toggleSpeckle, "Toggle speckle wave");
        kr.registerKeyAction(KeyCombination.simple('T'), () -> {
            cthugha.newTranslation(false);
            rebuildTranslateMap();
        }, "Randomise translation");
        kr.registerKeyAction(KeyCombination.simpleWithMods('T', "SHIFT"), () -> {
            cthugha.newTranslation(true);
            rebuildTranslateMap();
        }, "Fully randomise translation");
        kr.registerKeyAction(KeyCombination.simple('U'), () -> cthugha.changeAmplitude(1.01), "Increase amplitude 1%");
        kr.registerKeyAction(KeyCombination.simpleWithMods('U', "SHIFT"), () -> cthugha.changeAmplitude(1.1), "Increase amplitude 10%");
        kr.registerKeyAction(KeyCombination.simple('X'), () -> Arrays.fill(cthugha.buffer.pixels, (byte) 255), "Flash fill screen white");

        kr.registerKeyAction(GLFW_KEY_COMMA, 0, () -> cthugha.autoRotateWave(-1), "Spin waves left");
        kr.registerKeyAction(GLFW_KEY_PERIOD, 0, () -> cthugha.autoRotateWave(1), "Spin waves right");
        kr.registerKeyAction(GLFW_KEY_COMMA, GLFW_MOD_SHIFT, () -> cthugha.rotate(-10), "Rotate wave -10 degrees");
        kr.registerKeyAction(GLFW_KEY_PERIOD, GLFW_MOD_SHIFT, () -> cthugha.rotate(10), "Rotate wave +10 degrees");
        kr.registerKeyAction(GLFW_KEY_1, GLFW_MOD_SHIFT, () -> {
            try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing audio", e); }
            exit();
        }, "Quit");
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

        // Pass 1: bakes CPU overlay into writeTex (run inside writeFBO with alpha blend)
        overlayBaker = new OverlayBakeRenderer("screenOverlay");
        overlayBaker.init(this);

        // Pass 2: palette-converts writeTex for display (run on default FBO)
        // Initial writeTex = pongTex (since pingIsRead=true → writeTex=pong)
        paletteDisplay = new PaletteDisplayRenderer("pongTex", "palette");
        paletteDisplay.init(this);

        // Translate renderer reads pingTex initially (readTex when pingIsRead=true)
        translateRenderer = new PingPongTranslateRenderer("pingTex", "translateMap");
        translateRenderer.init(this);

        overlayBuffer = BufferUtils.createByteBuffer(w * h);

        quoteRenderer = new StringRenderer(quoteFont);
        quoteRenderer.init(this);
        quoteRenderer.setPosition(new Point(40, h / 2));
        quoteRenderer.setTextColor(StandardColors.WHITE.color);

        notifRenderer = new StringRenderer(notifFont);
        notifRenderer.init(this);
        notifRenderer.setPosition(new Point(20, 30));
        notifRenderer.setTextColor(StandardColors.YELLOW.color);
    }

    @Override
    public void render() throws IOException {
        timer.update();

        // 1. CPU pipeline — translate.transform() removed; translate is on GPU now
        cthugha.doRenderCPU();

        // 2. Upload CPU overlay (palette indices) to screenOverlayTex
        overlayBaker.getOverlayUnit().activate();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        overlayBuffer.clear();
        overlayBuffer.put(cthugha.buffer.pixels);
        overlayBuffer.flip();
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                cthugha.buffer.width, cthugha.buffer.height,
                GL_RED, GL_UNSIGNED_BYTE, overlayBuffer);

        // 3. Upload palette LUT
        paletteDisplay.getPaletteUnit().activate();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        ByteBuffer palBuf = buildPaletteBuffer(cthugha.buffer.paletteMap);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 256, 1, GL_RGBA, GL_UNSIGNED_BYTE, palBuf);

        // 4. Determine ping-pong read/write for this frame
        Texture readTex   = pingIsRead ? pingTex  : pongTex;
        Texture writeTex  = pingIsRead ? pongTex  : pingTex;
        FrameBuffer writeFBO = pingIsRead ? pongFBO : pingFBO;

        // 5. Rebind translate source unit to the current read texture
        translateRenderer.getSrcUnit().bind(readTex);

        // 6. Rebind palette-display input unit to the current write texture
        paletteDisplay.getTexUnit().bind(writeTex);

        // 7. GPU translate: readTex → writeTex (FBO)
        //    Then bake CPU overlay INTO writeTex (same FBO, alpha blend).
        //    This means old waves from readTex get translated, then new waves are drawn on top.
        //    The result in writeTex becomes next frame's readTex — true accumulation.
        writeFBO.bind();
        translateRenderer.doRender(this);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        overlayBaker.doRender(this);
        glDisable(GL_BLEND);

        writeFBO.unbind();
        // FrameBuffer.unbind() does NOT restore the viewport
        java.awt.Rectangle win = getWindow();
        glViewport(0, 0, win.width, win.height);

        // 8. Display: R8 writeTex → RGBA via palette LUT → default FBO
        paletteDisplay.doRender(this);

        // 9. Advance ping-pong for next frame
        pingIsRead = !pingIsRead;

        // 10. Text overlays
        String quote = cthugha.getCurrentQuote();
        if (quote != null) {
            if (!quote.equals(lastQuote)) {
                quoteRenderer.setText(quote);
                lastQuote = quote;
            }
            quoteRenderer.doRender(this);
        } else {
            lastQuote = null;
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
    }

    @Override
    public void dispose() {
        if (quoteRenderer     != null) quoteRenderer.dispose();
        if (notifRenderer     != null) notifRenderer.dispose();
        if (overlayBaker      != null) overlayBaker.dispose();
        if (paletteDisplay    != null) paletteDisplay.dispose();
        if (translateRenderer != null) translateRenderer.dispose();
        if (pingFBO           != null) pingFBO.dispose();
        if (pongFBO           != null) pongFBO.dispose();
        // Textures registered with ResourceManager are disposed by super.dispose()
        try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing audio", e); }
        super.dispose();
    }

    // -------------------------------------------------------------------------
    // Translation map helpers
    // -------------------------------------------------------------------------

    private ByteBuffer buildTranslateMapBuffer(int[] table, int w, int h) {
        int maxIdx = w * h - 1;
        ByteBuffer bb = BufferUtils.createByteBuffer(w * h * 4); // 2 × uint16 per pixel
        for (int t : table) {
            int src = Math.max(0, Math.min(t, maxIdx));
            bb.putShort((short) (src % w)); // srcX
            bb.putShort((short) (src / w)); // srcY
        }
        bb.flip();
        return bb;
    }

    private Texture buildTranslateMapTexture(int[] table, int w, int h) {
        Texture t = new Texture();
        t.setInternalFormat(GL_RG16UI);
        t.setImageFormat(GL_RG_INTEGER);
        t.setDataType(GL_UNSIGNED_SHORT);
        t.setFilter(Filter.NEAREST); // integer textures must not use LINEAR
        t.setWrap(Wrap.REPEAT);
        t.generate(w, h, buildTranslateMapBuffer(table, w, h));
        return t;
    }

    private void rebuildTranslateMap() {
        int w = cthugha.buffer.width;
        int h = cthugha.buffer.height;
        ByteBuffer data = buildTranslateMapBuffer(cthugha.getTranslateTable(), w, h);
        // GL_TEXTURE0 is used as a scratch unit; render-core allocates from unit 1 upward
        glActiveTexture(GL_TEXTURE0);
        translateMapTex.bind();
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
    // Inner class: PingPongTranslateRenderer
    // Exposes the source texture unit so CthughaWindow can rebind per frame.
    // -------------------------------------------------------------------------

    private static class PingPongTranslateRenderer extends TranslateTextureRenderer {
        private TextureUnit srcUnit;

        PingPongTranslateRenderer(String texName, String mapName) {
            super(texName, mapName);
        }

        @Override
        protected TextureUnit initTextureUnit(RenderContext ctx) {
            srcUnit = super.initTextureUnit(ctx);
            return srcUnit;
        }

        TextureUnit getSrcUnit() {
            return srcUnit;
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

    // -------------------------------------------------------------------------
    // Inner class: PaletteDisplayRenderer
    // Converts the finished R8 ping-pong texture to RGBA via palette LUT for display.
    // -------------------------------------------------------------------------

    private static class PaletteDisplayRenderer implements RenderedItem {

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
                uniform sampler2D tex;
                uniform sampler2D palette;
                void main() {
                    float idx = texture(tex, texCoords).r;
                    fragColor = texture(palette, vec2(idx, 0.5));
                }
                """;

        private final String texName;
        private final String paletteName;
        private ShaderProgram shader;
        private TextureUnit texUnit;
        private TextureUnit paletteUnit;
        private Rectangle quad;

        PaletteDisplayRenderer(String texName, String paletteName) {
            this.texName     = texName;
            this.paletteName = paletteName;
        }

        @Override
        public void init(RenderContext ctx) throws IOException {
            shader = ShaderProgram.compile(
                    ShaderSource.fromClass(VERT, PaletteDisplayRenderer.class),
                    ShaderSource.fromClass(FRAG, PaletteDisplayRenderer.class),
                    null);
            shader.use(ctx);
            ResourceManager rm = ctx.getResourceManager();
            texUnit = rm.nextTextureUnit();
            texUnit.bind(rm.getTexture(texName));
            texUnit.useInShader(shader, "tex");
            paletteUnit = rm.nextTextureUnit();
            paletteUnit.bind(rm.getTexture(paletteName));
            paletteUnit.useInShader(shader, "palette");
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
            if (texUnit     != null) texUnit.dispose();
            if (paletteUnit != null) paletteUnit.dispose();
        }

        TextureUnit getTexUnit()     { return texUnit; }
        TextureUnit getPaletteUnit() { return paletteUnit; }
    }

    public static void main(String[] args) throws Exception {
        new CthughaWindow().displayLoop();
    }
}
