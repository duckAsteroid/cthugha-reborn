package com.asteroid.duck.opengl.util.text;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.color.StandardColors;
import com.asteroid.duck.opengl.util.geom.Vertice;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.buffer.BufferDrawMode;
import com.asteroid.duck.opengl.util.resources.buffer.UpdateHint;
import com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject;
import com.asteroid.duck.opengl.util.resources.buffer.ebo.ElementBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.*;
import com.asteroid.duck.opengl.util.resources.font.FontTexture;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Renders a text string using a {@link FontTexture}.
 * <p>
 * VBO vertices are in string-relative (origin) space — the baseline datum sits at (0, 0). Screen
 * position, rotation, and scale are all applied via a single {@code model} matrix uniform, so
 * calling {@link #setTransform} only updates a uniform and never touches the vertex buffer.
 * <p>
 * To position, rotate, or scale the text, build a JOML {@link Matrix4f} and pass it to
 * {@link #setTransform}. The matrix is applied before the orthographic projection:
 * <pre>{@code
 * // translate only
 * renderer.setTransform(new Matrix4f().translate(x, y, 0));
 *
 * // translate + rotate around the text origin
 * renderer.setTransform(new Matrix4f().translate(x, y, 0).rotateZ(angle));
 *
 * // translate + uniform scale + rotate
 * renderer.setTransform(new Matrix4f().translate(x, y, 0).rotateZ(angle).scale(s, s, 1));
 * }</pre>
 * <p>
 * The VBO and EBO are allocated to exactly match the current text length. When the text length
 * changes the buffers are reallocated; when only the content changes (same length) the existing
 * buffers are updated in place. No rendering occurs until {@link #setText} has been called at
 * least once.
 */
public class StringRenderer implements RenderedItem {
    private static final Logger LOG = LoggerFactory.getLogger(StringRenderer.class);
    private static final List<Vertice> fourCorners = Vertice.standardFourVertices().toList();
    private static final int[] indices = Vertice.standardSixVertices().mapToInt(fourCorners::indexOf).toArray();

    // language="GLSL"
    private static final String VERTEX_GLSL = """
            #version 460
            in vec2 screenPosition;
            in vec2 texturePosition;
            out vec2 texCoords;
            uniform mat4 projection;
            uniform mat4 model;
            void main() {
                gl_Position = projection * model * vec4(screenPosition, 1.0, 1.0);
                texCoords = texturePosition;
            }
            """;


    // language="GLSL"
    private static final String FRAGMENT_GLSL = """
            #version 460
            precision mediump float;
            uniform sampler2D tex;
            uniform vec4 textColor;
            in vec2 texCoords;
            out vec4 fragColor;
            void main() {
                vec4 color = texture(tex, texCoords);
                float mask = color.r;
                fragColor = vec4(textColor.rgb * mask, textColor.a * color.a);
            }
            """;

    private static final String TEXT_UPDATE   = "textUpdate";
    private static final String TEXT_COLOR    = "textColor";
    private static final String TEXT_POSITION = "textPosition";

    private final RenderActionQueue renderActions = new RenderActionQueue(TEXT_UPDATE, TEXT_COLOR, TEXT_POSITION);

    private final FontTexture fontTexture;

    private String text;

    private ShaderProgram shaderProgram;
    private TextureUnit textureUnit;

    private final Matrix4f model = new Matrix4f();

    private final VertexElement screenPosition  = new VertexElement(VertexElementType.VEC_2F, "screenPosition");
    private final VertexElement texturePosition = new VertexElement(VertexElementType.VEC_2F, "texturePosition");

    private final VertexArrayObject vao = new VertexArrayObject();
    private ElementBufferObject ebo;
    private VertexBufferObject vbo;

    /** Number of characters the current VBO/EBO were allocated for; 0 means not yet allocated. */
    private int allocatedLength = 0;


    public StringRenderer(FontTexture fontTexture) {
        this.fontTexture = Objects.requireNonNull(fontTexture, "Font texture cannot be null");
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        initTexture(ctx);
        initShader(ctx);
        textureUnit.useInShader(shaderProgram, "tex");
        initUniforms(ctx);
    }

    private void initTexture(RenderContext ctx) {
        textureUnit = ctx.getResourceManager().nextTextureUnit();
        textureUnit.activate();
        textureUnit.bind(fontTexture.getTexture());
    }

    private void initShader(RenderContext ctx) {
        shaderProgram = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_GLSL, StringRenderer.class),
                ShaderSource.fromClass(FRAGMENT_GLSL, StringRenderer.class),
                null);
        shaderProgram.use(ctx);
    }

    private void initUniforms(RenderContext ctx) {
        shaderProgram.uniforms().get("projection", Matrix4f.class).set(ctx.ortho());
        shaderProgram.uniforms().get("model", Matrix4f.class).set(model);
        shaderProgram.uniforms().get("textColor", Vector4f.class).set(StandardColors.WHITE.color);
    }

    public FontTexture getFontTexture() {
        return fontTexture;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text must not be null");
        renderActions.enqueue(TEXT_UPDATE, ctx -> rebuildBuffers(ctx));
    }

    /**
     * Sets the full model transform applied to the text quad.
     * <p>
     * The text vertices sit at origin in string space; the matrix maps them to screen space before
     * the orthographic projection is applied. Callers can combine translation, rotation, and scale
     * freely — e.g. {@code new Matrix4f().translate(x, y, 0).rotateZ(angle).scale(s, s, 1)}.
     * <p>
     * Only updates the {@code model} uniform; the vertex buffer is not rebuilt.
     *
     * @param transform model matrix in screen/pixel space
     */
    public void setTransform(Matrix4f transform) {
        Matrix4f copy = new Matrix4f(transform);
        renderActions.enqueue(TEXT_POSITION, ctx -> {
            model.set(copy);
            shaderProgram.uniforms().get("model", Matrix4f.class).set(model);
        });
    }

    public void setTextColor(Vector4f color) {
        renderActions.enqueue(TEXT_COLOR, ctx ->
            shaderProgram.uniforms().get(TEXT_COLOR, Vector4f.class).set(color));
    }

    private int countRenderable(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') continue;
            if (fontTexture.getGlyph(c) != null) count++;
        }
        return count;
    }

    /**
     * Updates buffers for the current text. Reallocates the VAO/VBO/EBO when the renderable
     * character count changes; otherwise updates the existing buffers in place.
     */
    private void rebuildBuffers(RenderContext ctx) {
        if (text.isEmpty()) {
            allocatedLength = 0;
            return;
        }
        int renderableCount = countRenderable(text);
        if (renderableCount == 0) {
            allocatedLength = 0;
            return;
        }
        if (renderableCount != allocatedLength) {
            reallocate(ctx, renderableCount);
        }
        fontTexture.computeVertexData(text, vbo, screenPosition, texturePosition);
        ebo.clear();
        int charIndex = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') continue;
            if (fontTexture.getGlyph(c) == null) continue;
            for (int j = 0; j < indices.length; j++) {
                ebo.put((short) (charIndex * 4 + indices[j]));
            }
            charIndex++;
        }
        vao.bind(ctx);
        vao.setDrawMode(BufferDrawMode.TRIANGLES);
        vbo.update(UpdateHint.DYNAMIC);
        ebo.update();
    }

    /**
     * Disposes the existing VAO/VBO/EBO (if any) and creates new ones sized to the renderable
     * character count. Must only be called when {@code len > 0}.
     */
    private void reallocate(RenderContext ctx, int len) {
        if (allocatedLength > 0) {
            vao.dispose();
        }
        vao.createEbo(indices.length * len);
        vao.createVbo(new VertexDataStructure(screenPosition, texturePosition), fourCorners.size() * len);
        vao.init(ctx);
        vbo = vao.getVbo();
        ebo = vao.getEbo();
        vbo.setup(shaderProgram);
        allocatedLength = len;
        LOG.debug("Reallocated text buffers for {} renderable characters", len);
    }

    @Override
    public void doRender(RenderContext ctx) {
        shaderProgram.use(ctx);
        renderActions.processAll(ctx);
        if (allocatedLength > 0) {
            vao.doRender(ctx);
        }
    }

    @Override
    public void dispose() {
        shaderProgram.dispose();
        vao.dispose();
    }
}
