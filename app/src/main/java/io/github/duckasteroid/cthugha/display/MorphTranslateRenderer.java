package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.AbstractPassthruRenderer;
import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.Uniform;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;

import java.io.IOException;
import java.util.function.DoubleSupplier;

/**
 * The translate pass, blending two translation maps: each output pixel samples {@code tex} at a
 * source position interpolated between map A's and map B's by {@code mix} (see
 * {@link io.github.duckasteroid.cthugha.tab.TableMorph}).
 *
 * <p>Replaces render-core's {@code TranslateTextureRenderer}, which reads a single map. The maps
 * are RG16UI tables of absolute source coordinates. Rather than interpolating coordinates
 * directly, each map's <em>displacement</em> (source − destination) is taken the shorter way
 * round the toroidal buffer before blending, so a pixel whose source wraps across an edge in one
 * map doesn't sweep across the middle of the screen. At {@code mix} 0 or 1 the result is exactly
 * that map's source texel.
 *
 * <p>The two maps are read from texture units the caller owns and rebinds as slot textures
 * rotate; {@link Bake} writes the same blend out as a plain table.
 */
public class MorphTranslateRenderer extends AbstractPassthruRenderer {

    static final String VERT = """
            #version 330 core
            in vec2 screenPosition;
            in vec2 texturePosition;
            out vec2 texCoords;
            void main() {
                texCoords = texturePosition;
                gl_Position = vec4(screenPosition, 0.0, 1.0);
            }
            """;

    /** Wrap-aware blend of two maps' source coordinates for the destination texel {@code dst}. */
    static final String MORPH_FN = """
            uniform usampler2D mapA;    // RG16UI: absolute source (x, y) per destination texel
            uniform usampler2D mapB;
            uniform float mixAmount;    // 0 = map A, 1 = map B

            vec2 shortestDelta(vec2 d, vec2 size) {
                return d - size * round(d / size);
            }

            // The destination texel, from texCoords rather than gl_FragCoord: a shader that ignores
            // texCoords lets the linker drop the texturePosition attribute, which render-core's
            // VertexBufferObject.setup() then configures at location -1.
            ivec2 destination(vec2 texCoords) {
                ivec2 size = textureSize(mapA, 0);
                return clamp(ivec2(texCoords * vec2(size)), ivec2(0), size - 1);
            }

            ivec2 morphedSource(ivec2 dst) {
                ivec2 size = textureSize(mapA, 0);
                vec2 sizeF = vec2(size);
                vec2 dstF = vec2(dst);
                vec2 dA = shortestDelta(vec2(texelFetch(mapA, dst, 0).rg) - dstF, sizeF);
                vec2 dB = shortestDelta(vec2(texelFetch(mapB, dst, 0).rg) - dstF, sizeF);
                ivec2 src = ivec2(round(dstF + mix(dA, dB, mixAmount)));
                return ((src % size) + size) % size;
            }
            """;

    private static final String FRAG = """
            #version 330 core
            in vec2 texCoords;
            out vec4 fragColor;
            uniform sampler2D tex;      // R16 indexed buffer to translate
            """ + MORPH_FN + """
            void main() {
                fragColor = texelFetch(tex, morphedSource(destination(texCoords)), 0);
            }
            """;

    private final String textureName;
    private final TextureUnit mapAUnit;
    private final TextureUnit mapBUnit;
    private final DoubleSupplier mix;
    private Uniform<Float> mixUniform;

    /**
     * @param textureName resource-manager name of the R16 texture to translate
     * @param mapAUnit    unit holding slot A's map
     * @param mapBUnit    unit holding slot B's map
     * @param mix         the blend factor, read on every render
     */
    public MorphTranslateRenderer(String textureName, TextureUnit mapAUnit, TextureUnit mapBUnit,
                                  DoubleSupplier mix) {
        this.textureName = textureName;
        this.mapAUnit = mapAUnit;
        this.mapBUnit = mapBUnit;
        this.mix = mix;
    }

    @Override
    protected ShaderProgram initShaderProgram(RenderContext ctx) throws IOException {
        return ShaderProgram.compile(
                ShaderSource.fromClass(VERT, MorphTranslateRenderer.class),
                ShaderSource.fromClass(FRAG, MorphTranslateRenderer.class),
                null);
    }

    @Override
    protected Texture initTexture(RenderContext ctx) {
        return ctx.getResourceManager().getTexture(textureName);
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        super.init(ctx);
        shaderProgram.use(ctx);
        mapAUnit.useInShader(shaderProgram, "mapA");
        mapBUnit.useInShader(shaderProgram, "mapB");
        mixUniform = shaderProgram.uniforms().get("mixAmount", Float.class);
    }

    @Override
    public void doRenderWithShader(RenderContext ctx) {
        mixUniform.set((float) mix.getAsDouble());
        super.doRenderWithShader(ctx);
    }

    /**
     * Writes the blend of the two maps at a given mix out as a plain RG16UI table, into whatever
     * RG16UI framebuffer is bound — so a morph can restart from the current mixed look.
     */
    public static class Bake extends AbstractPassthruRenderer {

        private static final String FRAG = """
                #version 330 core
                in vec2 texCoords;
                out uvec2 fragMap;
                """ + MORPH_FN + """
                void main() {
                    fragMap = uvec2(morphedSource(destination(texCoords)));
                }
                """;

        private final String placeholderTextureName;
        private final TextureUnit mapAUnit;
        private final TextureUnit mapBUnit;
        private Uniform<Float> mixUniform;
        private float mix;

        /**
         * @param placeholderTextureName any registered texture; the bake samples only the maps,
         *                               but the passthrough base needs one
         */
        public Bake(String placeholderTextureName, TextureUnit mapAUnit, TextureUnit mapBUnit) {
            this.placeholderTextureName = placeholderTextureName;
            this.mapAUnit = mapAUnit;
            this.mapBUnit = mapBUnit;
        }

        /** Sets the mix the next render bakes. */
        public void setMix(double mix) {
            this.mix = (float) mix;
        }

        @Override
        protected ShaderProgram initShaderProgram(RenderContext ctx) throws IOException {
            return ShaderProgram.compile(
                    ShaderSource.fromClass(VERT, Bake.class),
                    ShaderSource.fromClass(FRAG, Bake.class),
                    null);
        }

        @Override
        protected Texture initTexture(RenderContext ctx) {
            return ctx.getResourceManager().getTexture(placeholderTextureName);
        }

        /** The shader has no {@code tex} sampler, so the unit is allocated but not wired to it. */
        @Override
        protected TextureUnit initTextureUnit(RenderContext ctx) {
            return ctx.getResourceManager().nextTextureUnit();
        }

        @Override
        public void init(RenderContext ctx) throws IOException {
            super.init(ctx);
            shaderProgram.use(ctx);
            mapAUnit.useInShader(shaderProgram, "mapA");
            mapBUnit.useInShader(shaderProgram, "mapB");
            mixUniform = shaderProgram.uniforms().get("mixAmount", Float.class);
        }

        @Override
        public void doRenderWithShader(RenderContext ctx) {
            mixUniform.set(mix);
            super.doRenderWithShader(ctx);
        }
    }
}
