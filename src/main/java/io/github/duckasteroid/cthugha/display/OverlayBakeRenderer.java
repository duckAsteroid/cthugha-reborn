package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.geom.Rectangle;
import com.asteroid.duck.opengl.util.resources.manager.ResourceManager;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;

import java.io.IOException;

/**
 * Renders a CPU overlay (R8) into the currently bound FBO using alpha blending:
 * non-zero palette indices overwrite the underlying translated content;
 * zero indices (background) are transparent, preserving the translated pixel.
 *
 * With GL_SRC_ALPHA/GL_ONE_MINUS_SRC_ALPHA blending on the R8 FBO:
 *   dest.R = src.R * src.A + dest.R * (1 - src.A)
 * Background (alpha=0): keeps existing translated pixel. Foreground (alpha=1): overwrites.
 */
class OverlayBakeRenderer implements RenderedItem {

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
