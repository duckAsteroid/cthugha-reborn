package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.GLWindow;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.keys.KeyCombination;
import com.asteroid.duck.opengl.util.palette.PaletteRenderer;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.font.FontTextureFactory;
import com.asteroid.duck.opengl.util.resources.io.PathBasedLoader;
import com.asteroid.duck.opengl.util.resources.manager.ResourceManagerImpl;
import com.asteroid.duck.opengl.util.resources.texture.Filter;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
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

public class CthughaWindow extends GLWindow {

    private static final Logger LOG = LoggerFactory.getLogger(CthughaWindow.class);

    private final JCthugha cthugha;

    private Texture screenTex;
    private Texture paletteTex;
    private PaletteRenderer paletteRenderer;
    private ByteBuffer pixelBuffer;

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

        // Alphabetic bindings — simple(char) requires uppercase; no-modifier fires on lowercase keypress
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
        kr.registerKeyAction(KeyCombination.simple('T'), () -> cthugha.newTranslation(false), "Randomise translation");
        kr.registerKeyAction(KeyCombination.simpleWithMods('T', "SHIFT"), () -> cthugha.newTranslation(true), "Fully randomise translation");
        kr.registerKeyAction(KeyCombination.simple('U'), () -> cthugha.changeAmplitude(1.01), "Increase amplitude 1%");
        kr.registerKeyAction(KeyCombination.simpleWithMods('U', "SHIFT"), () -> cthugha.changeAmplitude(1.1), "Increase amplitude 10%");
        kr.registerKeyAction(KeyCombination.simple('X'), () -> Arrays.fill(cthugha.buffer.pixels, (byte) 255), "Flash fill screen white");

        // Non-alphabetic keys via raw GLFW key codes
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

        // Font textures MUST be created before paletteRenderer.init().
        // FontTextureFactory.createFontTexture() calls Texture.generate() which calls
        // bind() then unbind() on the currently active texture unit. After paletteRenderer.init()
        // the active unit is 2 (the screen texture), so creating fonts after that would call
        // unbind() on unit 2, leaving the screen texture unbound and causing a black screen.
        FontTexture quoteFont = new FontTextureFactory(new Font("Serif", Font.ITALIC, 28), true).createFontTexture();
        FontTexture notifFont = new FontTextureFactory(new Font("Monospaced", Font.BOLD, 18), true).createFontTexture();

        // Single-channel (palette-index) screen texture
        screenTex = new Texture();
        screenTex.setInternalFormat(GL_R8);
        screenTex.setImageFormat(GL_RED);
        screenTex.setDataType(GL_UNSIGNED_BYTE);
        screenTex.setFilter(Filter.NEAREST);
        screenTex.generate(w, h, 0L);
        getResourceManager().putTexture("screen", screenTex);

        // 256×1 RGBA palette LUT texture
        paletteTex = new Texture();
        paletteTex.setInternalFormat(GL_RGBA);
        paletteTex.setImageFormat(GL_RGBA);
        paletteTex.setDataType(GL_UNSIGNED_BYTE);
        paletteTex.setFilter(Filter.NEAREST);
        paletteTex.generate(256, 1, buildPaletteBuffer(cthugha.buffer.paletteMap));
        getResourceManager().putTexture("palette", paletteTex);

        paletteRenderer = new PaletteRenderer("screen");
        paletteRenderer.init(this);

        pixelBuffer = BufferUtils.createByteBuffer(w * h);

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

        cthugha.doRenderCPU();

        // PaletteRenderer.init() allocated texture units in this order:
        //   unit 1 → palette texture  (first nextTextureUnit() call in initTexture)
        //   unit 2 → screen texture   (second call in initTextureUnit)
        // We must NOT unbind; just activate the right unit and upload in-place.

        // Upload palette indices (screen texture, unit 2)
        glActiveTexture(GL_TEXTURE0 + 2);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        pixelBuffer.clear();
        pixelBuffer.put(cthugha.buffer.pixels);
        pixelBuffer.flip();
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                cthugha.buffer.width, cthugha.buffer.height,
                GL_RED, GL_UNSIGNED_BYTE, pixelBuffer);

        // Upload palette LUT (palette texture, unit 1)
        glActiveTexture(GL_TEXTURE0 + 1);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        ByteBuffer palBuf = buildPaletteBuffer(cthugha.buffer.paletteMap);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 256, 1, GL_RGBA, GL_UNSIGNED_BYTE, palBuf);

        // Restore active unit to where screenTex lives before rendering
        glActiveTexture(GL_TEXTURE0 + 2);

        paletteRenderer.doRender(this);

        // Quote overlay — shown until expiry
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

        // Notification overlay — shown for NOTIF_DURATION after each notify()
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
        if (quoteRenderer != null) quoteRenderer.dispose();
        if (notifRenderer != null) notifRenderer.dispose();
        if (paletteRenderer != null) paletteRenderer.dispose();
        try { cthugha.close(); } catch (IOException e) { LOG.error("Error closing audio", e); }
        super.dispose();
    }

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
        new CthughaWindow().displayLoop();
    }
}
