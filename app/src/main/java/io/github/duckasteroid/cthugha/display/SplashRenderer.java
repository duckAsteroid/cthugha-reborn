package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.geom.Rectangle;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;

import java.io.IOException;

/**
 * Draws a single RGBA texture full-screen. Used to show a loading splash image before
 * {@link CthughaWindow#init()} allocates the rest of the render pipeline, so the window has
 * at least one visible frame (and one event-pump/buffer-swap) before the slow synchronous
 * startup work begins.
 *
 * <p>The texture is not owned by this renderer (see {@link #setTexture(Texture)}) — whoever
 * loads it is responsible for disposing it.</p>
 */
public class SplashRenderer implements RenderedItem {

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
            void main() {
                fragColor = texture(src, texCoords);
            }
            """;

    private ShaderProgram shader;
    private TextureUnit srcUnit;
    private Rectangle quad;

    @Override
    public void init(RenderContext ctx) throws IOException {
        shader = ShaderProgram.compile(
                ShaderSource.fromClass(VERT, SplashRenderer.class),
                ShaderSource.fromClass(FRAG, SplashRenderer.class),
                null);
        shader.use(ctx);
        srcUnit = ctx.getResourceManager().nextTextureUnit();
        srcUnit.useInShader(shader, "src");
        quad = new Rectangle(ctx, "screenPosition", "texturePosition");
        quad.getVertexArrayObject().bind(ctx);
        quad.getVertexBufferObject().setup(shader);
    }

    public void setTexture(Texture t) {
        srcUnit.bind(t);
    }

    @Override
    public void doRender(RenderContext ctx) {
        shader.use(ctx);
        quad.render(ctx);
    }

    @Override
    public void dispose() {
        if (quad   != null) quad.destroy();
        if (shader != null) shader.dispose();
        if (srcUnit != null) srcUnit.dispose();
    }
}
