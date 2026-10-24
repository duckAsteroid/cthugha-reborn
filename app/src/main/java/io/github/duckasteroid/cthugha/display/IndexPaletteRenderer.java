package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.AbstractPassthruRenderer;
import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.Uniform;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;

import java.io.IOException;

/**
 * Palette-converts an R16 indexed texture to RGBA via the {@code "palette"} LUT texture.
 *
 * <p>Replaces render-core's {@code PaletteRenderer}, whose {@code floor(pos * totalEntries)}
 * decode maps exactly {@code 1.0} to entry 0 (wrapping past the end) and, because R16
 * quantisation rounds {@code N/256} slightly down for {@code N ≥ 128}, lands on entry
 * {@code N-1} for the upper half of the palette. It also captures the LUT dimensions once at
 * init, so a palette with a different row count decodes against stale dimensions.</p>
 *
 * <p>Here the normalised value is rounded to the nearest of {@code entries} evenly spaced
 * steps: {@code 0.0} is the first entry and {@code 1.0} the last, whatever the palette size.
 * The LUT width comes from {@code textureSize()} and the entry count from a per-frame uniform
 * (see {@link #setEntries}), so both track palette reloads. The LUT may carry padding texels
 * past {@code entries} (its height is rounded up to whole rows); they are never addressed.</p>
 */
public class IndexPaletteRenderer extends AbstractPassthruRenderer {

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
            uniform sampler2D tex;      // R16 indexed buffer, normalised [0, 1]
            uniform sampler2D palette;  // RGBA LUT, row-major, one texel per entry
            uniform int entries;        // number of real entries in the LUT
            void main() {
                float pos = texture(tex, texCoords).r;
                int idx = clamp(int(round(pos * float(entries - 1))), 0, entries - 1);
                int width = textureSize(palette, 0).x;
                fragColor = texelFetch(palette, ivec2(idx % width, idx / width), 0);
            }
            """;

    private final String textureName;
    private Texture palette;
    private TextureUnit paletteUnit;
    private Uniform<Integer> entriesUniform;
    private int entries;

    /**
     * @param textureName resource-manager name of the R16 indexed texture to display
     * @param entries     initial palette entry count
     */
    public IndexPaletteRenderer(String textureName, int entries) {
        this.textureName = textureName;
        this.entries = entries;
    }

    /** Sets the number of real palette entries; call on the GL thread after uploading a new LUT. */
    public void setEntries(int entries) {
        this.entries = entries;
    }

    @Override
    protected ShaderProgram initShaderProgram(RenderContext ctx) throws IOException {
        return ShaderProgram.compile(
                ShaderSource.fromClass(VERT, IndexPaletteRenderer.class),
                ShaderSource.fromClass(FRAG, IndexPaletteRenderer.class),
                null);
    }

    @Override
    protected Texture initTexture(RenderContext ctx) {
        palette = ctx.getResourceManager().getTexture("palette");
        paletteUnit = ctx.getResourceManager().nextTextureUnit();
        paletteUnit.bind(palette);
        return ctx.getResourceManager().getTexture(textureName);
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        super.init(ctx);
        shaderProgram.use(ctx);
        paletteUnit.useInShader(shaderProgram, "palette");
        entriesUniform = shaderProgram.uniforms().get("entries", Integer.class);
    }

    @Override
    public void doRenderWithShader(RenderContext ctx) {
        entriesUniform.set(entries);
        super.doRenderWithShader(ctx);
    }

    @Override
    public void dispose() {
        paletteUnit.dispose();
        palette.dispose();
        super.dispose();
    }
}
