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
 * Reads an RGBA overlay texture and writes a fixed palette index (200) as a normalised
 * R8 value wherever the overlay alpha exceeds 0.1. Rendered into pongFBO with
 * GL_SRC_ALPHA blending so transparent pixels preserve the translated content
 * while opaque pixels overwrite with the palette index.
 */
class WaveIndexBakeRenderer implements RenderedItem {

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
