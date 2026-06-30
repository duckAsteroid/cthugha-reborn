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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_R8;

/**
 * Renders a QR code as a centered RGBA overlay on the GL output.
 * Thread-safe: {@link #show(String)} and {@link #hide()} may be called from any thread;
 * texture upload and drawing happen on the GL thread in {@link #doRender(RenderContext)}.
 */
public class QrOverlay implements RenderedItem {

    private static final Logger LOG = LoggerFactory.getLogger(QrOverlay.class);

    private final int timeoutSeconds;

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
                float v = texture(uQr, qrUV).r;
                fragColor = vec4(vec3(v), 1.0);
            }
            """;

    public QrOverlay(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /** May be called from any thread. Queues URL upload and makes the overlay visible. */
    public void show(String url) {
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
        QrCode qr = QrCode.encodeText(url, QrCode.Ecc.MEDIUM);
        int modules = qr.size;
        int quiet = 4;
        int total = modules + 2 * quiet;

        ByteBuffer buf = BufferUtils.createByteBuffer(total * total);
        for (int i = 0; i < total * total; i++) buf.put((byte) 0xFF);
        buf.rewind();
        for (int y = 0; y < modules; y++) {
            for (int x = 0; x < modules; x++) {
                buf.put((y + quiet) * total + (x + quiet), qr.getModule(x, y) ? (byte) 0x00 : (byte) 0xFF);
            }
        }
        buf.rewind();

        if (qrTex != null) qrTex.dispose();
        qrTex = new Texture();
        qrTex.setInternalFormat(GL_R8);
        qrTex.setImageFormat(GL_RED);
        qrTex.setDataType(GL_UNSIGNED_BYTE);
        qrTex.setFilter(Filter.NEAREST);
        qrTex.generate(total, total, buf);
        qrTexSize = total;
    }
}
