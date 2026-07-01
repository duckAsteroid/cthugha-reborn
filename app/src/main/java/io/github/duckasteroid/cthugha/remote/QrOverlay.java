package io.github.duckasteroid.cthugha.remote;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.geom.Rectangle;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.Uniform;
import com.asteroid.duck.opengl.util.resources.texture.Filter;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import io.nayuki.qrcodegen.QrCode;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_RGBA;

/**
 * Renders a QR code as a centered RGBA overlay on the GL output.
 * Thread-safe: {@link #show(String)} and {@link #hide()} may be called from any thread;
 * texture upload and drawing happen on the GL thread in {@link #doRender(RenderContext)}.
 */
public class QrOverlay implements RenderedItem {

    private static final Logger LOG = LoggerFactory.getLogger(QrOverlay.class);

    private final int timeoutSeconds;
    /** Logo area as a percentage of QR modules (0 = no logo, max 30). */
    private final int logoPercent;

    private final AtomicReference<String> pendingUrl = new AtomicReference<>();
    private volatile boolean visible = false;
    private volatile long showTime = 0;

    private ShaderProgram shader;
    private Rectangle quad;
    private TextureUnit texUnit;
    private Uniform<Vector2f> uQrSize;

    private Texture qrTex;
    private int qrTexSize = 0;

    // language=GLSL
    private static final String VERT = """
            #version 330 core
            in vec2 screenPosition;
            in vec2 texturePosition;
            out vec2 vTex;
            void main() {
                vTex = texturePosition;
                gl_Position = vec4(screenPosition, 0.0, 1.0);
            }
            """;

    // language=GLSL
    private static final String FRAG = """
            #version 330 core
            uniform sampler2D uQr;
            uniform vec2 uQrSize;
            in vec2 vTex;
            out vec4 fragColor;
            void main() {
                vec2 center = vec2(0.5, 0.5);
                vec2 half = uQrSize * 0.5;
                vec2 lo = center - half;
                vec2 hi = center + half;
                if (vTex.x < lo.x || vTex.x > hi.x || vTex.y < lo.y || vTex.y > hi.y) {
                    discard;
                }
                vec2 qrUV = (vTex - lo) / uQrSize;
                fragColor = texture(uQr, qrUV);
            }
            """;

    public QrOverlay(int timeoutSeconds, int logoPercent) {
        this.timeoutSeconds = timeoutSeconds;
        this.logoPercent = logoPercent;
    }

    /** May be called from any thread. Queues URL upload and makes the overlay visible. */
    public void show(String url) {
        System.out.println("QR: " + url);
        pendingUrl.set(url);
        showTime = System.currentTimeMillis();
        visible = true;
    }

    /** May be called from any thread. */
    public void hide() {
        visible = false;
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        shader = ShaderProgram.compile(
                ShaderSource.fromClass(VERT, QrOverlay.class),
                ShaderSource.fromClass(FRAG, QrOverlay.class),
                null);
        shader.use(ctx);
        uQrSize = shader.uniforms().get("uQrSize", Vector2f.class);

        texUnit = ctx.getResourceManager().nextTextureUnit();
        texUnit.useInShader(shader, "uQr");

        quad = new Rectangle(ctx, "screenPosition", "texturePosition");
        quad.getVertexArrayObject().bind(ctx);
        quad.getVertexBufferObject().setup(shader);
    }

    @Override
    public void doRender(RenderContext ctx) {
        if (visible && timeoutSeconds > 0) {
            if (System.currentTimeMillis() - showTime > timeoutSeconds * 1000L) {
                visible = false;
            }
        }
        if (!visible) return;

        String newUrl = pendingUrl.getAndSet(null);
        if (newUrl != null) {
            uploadQr(newUrl);
        }
        if (qrTex == null) return;

        java.awt.Rectangle win = ctx.getWindow();
        // Scale to 2/3 of the smaller screen dimension, pixel-perfect per module
        int maxPx = Math.min(win.width, win.height) * 2 / 3;
        int scale = Math.max(1, maxPx / qrTexSize);
        int px = qrTexSize * scale;
        float qrW = (float) px / win.width;
        float qrH = (float) px / win.height;

        shader.use(ctx);
        texUnit.bind(qrTex);
        uQrSize.set(new Vector2f(qrW, qrH));

        quad.getVertexArrayObject().bind(ctx);
        quad.render(ctx);
    }

    @Override
    public void dispose() {
        if (quad    != null) quad.destroy();
        if (shader  != null) shader.dispose();
        if (qrTex   != null) qrTex.dispose();
        if (texUnit != null) texUnit.dispose();
    }

    private void uploadQr(String url) {
        LOG.debug("Generating QR code for: {}", url);
        // Choose ECC to match logo coverage: HIGH for >25%, QUARTILE for >15%, MEDIUM otherwise
        QrCode.Ecc ecc = logoPercent > 25 ? QrCode.Ecc.HIGH
                       : logoPercent > 15 ? QrCode.Ecc.QUARTILE
                       : QrCode.Ecc.MEDIUM;
        QrCode qr = QrCode.encodeText(url, ecc);
        int modules = qr.size;
        int quiet = 4;
        int total = modules + 2 * quiet;

        ByteBuffer buf = BufferUtils.createByteBuffer(total * total * 4);
        for (int i = 0; i < total * total * 4; i++) buf.put((byte) 0xFF);
        buf.rewind();
        for (int y = 0; y < modules; y++) {
            for (int x = 0; x < modules; x++) {
                byte v = qr.getModule(x, y) ? (byte) 0x00 : (byte) 0xFF;
                int base = ((y + quiet) * total + (x + quiet)) * 4;
                buf.put(base,     v);
                buf.put(base + 1, v);
                buf.put(base + 2, v);
                buf.put(base + 3, (byte) 0xFF);
            }
        }

        if (logoPercent <= 0) {
            buf.rewind();
        } else {
        // Blit logo into the centre; size derived from configured area percentage
        int logoSize = (int) (Math.sqrt(logoPercent / 100.0) * modules);
        int logoX = quiet + (modules - logoSize) / 2;
        int logoY = quiet + (modules - logoSize) / 2;
        try (InputStream is = QrOverlay.class.getResourceAsStream("/logo/cthugha.png")) {
            if (is != null) {
                BufferedImage logo = ImageIO.read(is);
                BufferedImage scaled = new BufferedImage(logoSize, logoSize, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(logo, 0, 0, logoSize, logoSize, null);
                g.dispose();
                for (int y = 0; y < logoSize; y++) {
                    for (int x = 0; x < logoSize; x++) {
                        int argb = scaled.getRGB(x, y);
                        int base = ((logoY + y) * total + (logoX + x)) * 4;
                        buf.put(base,     (byte) ((argb >> 16) & 0xFF)); // R
                        buf.put(base + 1, (byte) ((argb >>  8) & 0xFF)); // G
                        buf.put(base + 2, (byte) ( argb        & 0xFF)); // B
                        buf.put(base + 3, (byte) ((argb >> 24) & 0xFF)); // A
                    }
                }
            } else {
                LOG.warn("QR logo not found at /logo/cthugha.png");
            }
        } catch (IOException e) {
            LOG.warn("Could not load QR logo", e);
        }
        buf.rewind();
        } // end showLogo block

        if (qrTex != null) qrTex.dispose();
        qrTex = new Texture();
        qrTex.setInternalFormat(GL_RGBA);
        qrTex.setImageFormat(GL_RGBA);
        qrTex.setDataType(GL_UNSIGNED_BYTE);
        qrTex.setFilter(Filter.NEAREST);
        qrTex.generate(total, total, buf);
        qrTexSize = total;
    }
}
