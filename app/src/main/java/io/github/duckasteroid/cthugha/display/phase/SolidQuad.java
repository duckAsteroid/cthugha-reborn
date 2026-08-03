package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.geom.Vertice;
import com.asteroid.duck.opengl.util.resources.buffer.UpdateHint;
import com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject;
import com.asteroid.duck.opengl.util.resources.buffer.ebo.ElementBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexDataStructure;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexElement;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexElementType;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.Uniform;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;
import java.util.List;

/**
 * Flat-colour filled rectangle, positioned/sized in the same top-left-origin pixel space
 * {@code StringRenderer} uses (via {@link RenderContext#ortho()}). One instance is reused across
 * many {@link #render} calls per frame, each with its own position, size, and colour — used by
 * {@link DebugBeatsPhase} to draw beat-strength bars.
 */
class SolidQuad {

    // language=GLSL
    private static final String VERT = """
            #version 460
            in vec2 screenPosition;
            uniform mat4 projection;
            uniform mat4 model;
            void main() {
                gl_Position = projection * model * vec4(screenPosition, 0.0, 1.0);
            }
            """;

    // language=GLSL
    private static final String FRAG = """
            #version 460
            uniform vec4 color;
            out vec4 fragColor;
            void main() {
                fragColor = color;
            }
            """;

    private ShaderProgram shader;
    private VertexArrayObject vao;
    private Uniform<Matrix4f> uModel;
    private Uniform<Vector4f> uColor;

    void init(RenderContext ctx) throws IOException {
        shader = ShaderProgram.compile(
                ShaderSource.fromClass(VERT, SolidQuad.class),
                ShaderSource.fromClass(FRAG, SolidQuad.class),
                null);
        shader.use(ctx);
        shader.uniforms().get("projection", Matrix4f.class).set(ctx.ortho());
        uModel = shader.uniforms().get("model", Matrix4f.class);
        uColor = shader.uniforms().get("color", Vector4f.class);

        VertexElement screenPosition = new VertexElement(VertexElementType.VEC_2F, "screenPosition");
        VertexDataStructure structure = new VertexDataStructure(List.of(screenPosition));

        vao = new VertexArrayObject();
        VertexBufferObject vbo = vao.createVbo(structure, 4);
        vao.init(ctx);
        vao.bind(ctx);
        vbo.setup(shader);

        List<Vertice> corners = Vertice.standardFourVertices().toList();
        Vector4f unitSquare = new Vector4f(0, 0, 1, 1);
        for (int i = 0; i < corners.size(); i++) {
            vbo.set(i, corners.get(i).from(unitSquare));
        }
        vbo.update(UpdateHint.STATIC);

        ElementBufferObject ebo = vao.createEbo(6);
        ebo.init(ctx);
        List<Short> indices = Vertice.standardSixVertices().map(v -> (short) corners.indexOf(v)).toList();
        ebo.update(indices);
    }

    /** Draws a filled rectangle at pixel coordinates {@code (x, y)} — top-left corner — sized {@code w x h}. */
    void render(RenderContext ctx, float x, float y, float w, float h, Vector4f color) {
        shader.use(ctx);
        uModel.set(new Matrix4f().translate(x, y, 0f).scale(w, h, 1f));
        uColor.set(color);
        vao.bind(ctx);
        vao.doRender(ctx);
    }

    void dispose() {
        if (vao != null) vao.dispose();
        if (shader != null) shader.dispose();
    }
}
