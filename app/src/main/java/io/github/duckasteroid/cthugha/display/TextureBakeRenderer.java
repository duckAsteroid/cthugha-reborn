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
 * Samples an RGBA texture and writes the R channel as a normalised palette index into the
 * currently bound R16 FBO. The alpha channel controls transparency: A=0 preserves the
 * underlying content; A=1 overwrites with the new palette index value.
 * Used for one-shot effects (white flash, PNG image flash) baked into renderFBO.
 */
public class TextureBakeRenderer implements RenderedItem {

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
                vec4 s = texture(src, texCoords);
                fragColor = vec4(s.r, 0.0, 0.0, s.a);
            }
            """;

    private ShaderProgram shader;
    private TextureUnit srcUnit;
    private Rectangle quad;

    @Override
    public void init(RenderContext ctx) throws IOException {
        shader = ShaderProgram.compile(
                ShaderSource.fromClass(VERT, TextureBakeRenderer.class),
                ShaderSource.fromClass(FRAG, TextureBakeRenderer.class),
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
        if (quad    != null) quad.destroy();
        if (shader  != null) shader.dispose();
        if (srcUnit != null) srcUnit.dispose();
    }
}
