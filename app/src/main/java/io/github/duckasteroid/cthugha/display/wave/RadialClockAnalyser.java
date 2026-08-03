package io.github.duckasteroid.cthugha.display.wave;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.Uniform;
import com.asteroid.duck.opengl.util.wave.FrequencyRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Rectangle;
import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Retro "clock face" spectrum analyser: one capsule per FFT bin, evenly spaced as ticks around a
 * circle. At rest (magnitude = 0) every capsule collapses to a round dot sitting on the base
 * ring; as a bin's magnitude rises its capsule stretches into a radial bar per {@link GrowthMode}.
 *
 * <p>Unlike {@code RadialSpectrumAnalyser} this class has no peak-hold indicator and applies no
 * smoothing -- length tracks the current per-bin magnitude directly -- and bins are sampled
 * discretely ({@code texelFetch}), not interpolated between neighbours, so ticks never blend into
 * their neighbours.</p>
 *
 * <p>Each capsule is a single quad, rotated to its bin's angle and sized to bound the capsule at
 * its current length; the fragment shader discards pixels outside an analytic capsule (rounded
 * line-segment) signed-distance shape, giving a constant-width bar with round caps -- and a round
 * dot for free when the segment length collapses to zero -- without any extra geometry.</p>
 *
 * <h2>Layout</h2>
 * <p>Matches {@code RadialSpectrumAnalyser}: starts at 6 o'clock and runs counter-clockwise, one
 * tick per FFT bin (times {@link #withRepeats}).</p>
 *
 * <h2>Width</h2>
 * <p>Capsule width is not set directly -- it is derived from the bin count (and repeat count) so
 * that adjacent capsules can never overlap, even when {@link GrowthMode#INWARD} or
 * {@link GrowthMode#BOTH} pulls a capsule's inner end all the way to its most-inward possible
 * radius. {@link #withWidthFraction} scales that maximum non-overlapping width down to leave
 * gaps between ticks.</p>
 *
 * <h2>Colours</h2>
 * <p>A 3-stop gradient controlled by {@link #withColors}, keyed by absolute radius rather than
 * position along an individual capsule: {@code base} at the resting ring radius, {@code inner} at
 * the deepest possible inward extent, {@code outer} at the furthest possible outward extent.</p>
 */
public class RadialClockAnalyser extends FrequencyRenderer {

    /** Direction a capsule's far/near ends move away from the base ring as magnitude rises. */
    public enum GrowthMode {
        /** Outer end moves outward with magnitude; inner end stays pinned at the base ring. */
        OUTWARD,
        /** Inner end moves inward with magnitude; outer end stays pinned at the base ring. */
        INWARD,
        /** Both ends move away from the base ring simultaneously. */
        BOTH
    }

    /** Default base circle radius in NDC units ({@value}). */
    public static final float DEFAULT_BASE_RADIUS = 0.35f;

    /** Default maximum outward extension in NDC units at full magnitude ({@value}). */
    public static final float DEFAULT_OUTER_HEIGHT = 0.35f;

    /** Default maximum inward contraction in NDC units at full magnitude ({@value}). */
    public static final float DEFAULT_INNER_DEPTH = 0.15f;

    /** Default fraction of the maximum non-overlapping width used by each capsule ({@value}). */
    public static final float DEFAULT_WIDTH_FRACTION = 0.7f;

    // ── Shaders ──────────────────────────────────────────────────────────────────

    // language=GLSL
    private static final String VERTEX_SHADER = """
            #version 330 core
            uniform sampler1D uFFTTex;
            uniform int   uNumBins;
            uniform int   uRepeats;
            uniform int   uGrowthMode;   // 0 = OUTWARD, 1 = INWARD, 2 = BOTH
            uniform float uBaseRadius;
            uniform float uOuterHeight;
            uniform float uInnerDepth;
            uniform float uHalfWidth;
            uniform float uAspect;
            uniform mat4  uTransform;

            out float vU;            // radial local coordinate (~world radius) at this fragment
            out float vV;            // tangential local coordinate
            flat out float vRNear;
            flat out float vRFar;

            const float PI = 3.14159265358979;

            void main() {
                int totalSlots = uNumBins * uRepeats;
                int slotIdx = gl_VertexID / 6;
                int corner  = gl_VertexID % 6;

                int segment  = slotIdx / uNumBins;
                int localIdx = slotIdx % uNumBins;
                bool reversed = (segment % 2) == 1;
                int binIdx = reversed ? (uNumBins - 1 - localIdx) : localIdx;

                float magnitude = texelFetch(uFFTTex, binIdx, 0).r;

                float rNear;
                float rFar;
                if (uGrowthMode == 1) {           // INWARD
                    rNear = uBaseRadius - magnitude * uInnerDepth;
                    rFar  = uBaseRadius;
                } else if (uGrowthMode == 2) {    // BOTH
                    rNear = uBaseRadius - magnitude * uInnerDepth;
                    rFar  = uBaseRadius + magnitude * uOuterHeight;
                } else {                          // OUTWARD (default)
                    rNear = uBaseRadius;
                    rFar  = uBaseRadius + magnitude * uOuterHeight;
                }

                // Bounding-box corners for a quad covering [rNear-halfWidth, rFar+halfWidth] x
                // [-halfWidth, halfWidth], laid out as two triangles (BL,BR,TL / BR,TR,TL) --
                // "near/far" stands in for that pattern's left/right, "side" for its bottom/top.
                bool isFar;
                float side;
                if (corner == 0)      { isFar = false; side = -1.0; }
                else if (corner == 1) { isFar = true;  side = -1.0; }
                else if (corner == 2) { isFar = false; side =  1.0; }
                else if (corner == 3) { isFar = true;  side = -1.0; }
                else if (corner == 4) { isFar = true;  side =  1.0; }
                else                  { isFar = false; side =  1.0; }

                float u = (isFar ? rFar : rNear) + (isFar ? uHalfWidth : -uHalfWidth);
                float v = side * uHalfWidth;

                vU = u;
                vV = v;
                vRNear = rNear;
                vRFar  = rFar;

                float t     = float(slotIdx) / float(totalSlots);
                float angle = -PI / 2.0 + t * 2.0 * PI;  // 6 o'clock, counter-clockwise
                vec2 dir  = vec2(cos(angle), sin(angle));
                vec2 perp = vec2(-sin(angle), cos(angle));
                vec2 pos  = dir * u + perp * v;

                gl_Position = uTransform * vec4(pos.x / uAspect, pos.y, 0.0, 1.0);
            }
        """;

    // language=GLSL
    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in float vU;
            in float vV;
            flat in float vRNear;
            flat in float vRFar;

            uniform float uHalfWidth;
            uniform float uBaseRadius;
            uniform float uInnerDepth;
            uniform float uOuterHeight;
            uniform vec4  uColorInner;  // colour at the deepest possible inward extent
            uniform vec4  uColorBase;   // colour at the resting ring radius
            uniform vec4  uColorOuter;  // colour at the furthest possible outward extent

            out vec4 fragColor;

            void main() {
                // Distance from this fragment to the capsule's core segment [rNear, rFar] (v = 0).
                float cu = clamp(vU, vRNear, vRFar);
                float d  = length(vec2(vU - cu, vV));
                if (d > uHalfWidth) discard;

                vec4 color;
                if (vU <= uBaseRadius) {
                    float f = clamp((vU - (uBaseRadius - uInnerDepth)) / max(uInnerDepth, 1e-6), 0.0, 1.0);
                    color = mix(uColorInner, uColorBase, f);
                } else {
                    float f = clamp((vU - uBaseRadius) / max(uOuterHeight, 1e-6), 0.0, 1.0);
                    color = mix(uColorBase, uColorOuter, f);
                }
                fragColor = color;
            }
        """;

    // ── Construction-time parameters ────────────────────────────────────────────
    private final float baseRadius;
    private final float outerHeight;
    private final float innerDepth;

    /** Colour at the deepest possible inward extent (v-fill-style gradient stop). RGBA. */
    private Vector4f colorInner = new Vector4f(0.0f, 0.2f, 0.6f, 1.0f);
    /** Colour at the resting ring radius. RGBA. */
    private Vector4f colorBase = new Vector4f(0.0f, 0.7f, 0.3f, 1.0f);
    /** Colour at the furthest possible outward extent. RGBA. */
    private Vector4f colorOuter = new Vector4f(0.9f, 0.1f, 0.0f, 1.0f);

    // ── GL resources ────────────────────────────────────────────────────────────
    /** Empty VAO -- all geometry is procedural (gl_VertexID only). */
    private int emptyVaoId;

    private ShaderProgram shader;
    private Uniform<Float> uAspect;
    private Uniform<Integer> uRepeatsUniform;
    private Uniform<Integer> uGrowthModeUniform;
    private Uniform<Float> uHalfWidthUniform;
    private Uniform<Matrix4f> uTransformUniform;

    // ── Per-frame / runtime-mutable state ────────────────────────────────────────
    private volatile float currentAspect = 1.0f;
    private volatile int repeats = 1;
    private volatile GrowthMode growthMode = GrowthMode.OUTWARD;
    private volatile float widthFraction = DEFAULT_WIDTH_FRACTION;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /**
     * Constructs a radial clock renderer with fully custom geometry.
     *
     * @param processor   the shared FFT processor that pushes spectrum data to this renderer
     * @param baseRadius  base ring radius (dot position) in NDC units
     * @param outerHeight maximum outward extension in NDC units at full magnitude
     * @param innerDepth  maximum inward contraction in NDC units at full magnitude
     */
    public RadialClockAnalyser(FrequencyProcessor processor, float baseRadius,
                                float outerHeight, float innerDepth) {
        super(processor.getNumBins());
        this.baseRadius = baseRadius;
        this.outerHeight = outerHeight;
        this.innerDepth = innerDepth;
    }

    /**
     * Constructs a radial clock renderer with default geometry ({@link #DEFAULT_BASE_RADIUS},
     * {@link #DEFAULT_OUTER_HEIGHT}, {@link #DEFAULT_INNER_DEPTH}).
     *
     * @param processor the shared FFT processor that pushes spectrum data to this renderer
     */
    public RadialClockAnalyser(FrequencyProcessor processor) {
        this(processor, DEFAULT_BASE_RADIUS, DEFAULT_OUTER_HEIGHT, DEFAULT_INNER_DEPTH);
    }

    // ── Configuration ────────────────────────────────────────────────────────────

    /**
     * Sets the three gradient colours, keyed by absolute radius. Call before {@link #init}.
     *
     * @param inner colour at the deepest possible inward extent
     * @param base  colour at the resting ring radius
     * @param outer colour at the furthest possible outward extent
     * @return {@code this} for fluent chaining
     */
    public RadialClockAnalyser withColors(Vector4f inner, Vector4f base, Vector4f outer) {
        this.colorInner = new Vector4f(inner);
        this.colorBase = new Vector4f(base);
        this.colorOuter = new Vector4f(outer);
        return this;
    }

    /**
     * Sets the direction capsules grow from the base ring. May be called before or after
     * {@link #init}; the new mode takes effect on the next rendered frame.
     *
     * @param mode {@link GrowthMode#OUTWARD}, {@link GrowthMode#INWARD}, or {@link GrowthMode#BOTH}
     * @return {@code this} for fluent chaining
     */
    public RadialClockAnalyser withGrowthMode(GrowthMode mode) {
        this.growthMode = mode;
        return this;
    }

    /**
     * Sets the number of times the full set of bins is tiled around the circle. Odd-numbered
     * repetitions mirror their bin order so bass and treble meet at every seam, exactly as
     * {@code RadialSpectrumAnalyser.withRepeats} does. May be called before or after {@link #init}.
     *
     * @param repeats number of repetitions; must be &gt;= 1
     * @return {@code this} for fluent chaining
     */
    public RadialClockAnalyser withRepeats(int repeats) {
        if (repeats < 1) throw new IllegalArgumentException("repeats must be >= 1");
        this.repeats = repeats;
        return this;
    }

    /**
     * Sets the fraction of the maximum non-overlapping capsule width to actually use, leaving a
     * gap between adjacent ticks. May be called before or after {@link #init}.
     *
     * @param fraction fraction in {@code (0, 1]}; {@code 1.0} means adjacent capsules just touch
     *                 at their worst-case (most inward) radius
     * @return {@code this} for fluent chaining
     */
    public RadialClockAnalyser withWidthFraction(float fraction) {
        this.widthFraction = fraction;
        return this;
    }

    /** Returns the current repeat count. */
    public int getRepeats() { return repeats; }

    /**
     * Computes the current capsule half-width: {@code widthFraction} of the chord half-length at
     * the worst-case (most inward) radius any capsule can reach, for one angular slot -- so
     * adjacent capsules can never overlap regardless of current magnitude or growth mode.
     */
    private float computeHalfWidth() {
        int totalSlots = numBins * repeats;
        float innerRadiusForWidth = (growthMode == GrowthMode.OUTWARD) ? baseRadius : baseRadius - innerDepth;
        return widthFraction * innerRadiusForWidth * (float) Math.sin(Math.PI / totalSlots);
    }

    // ── RenderedItem lifecycle ───────────────────────────────────────────────────

    @Override
    public void init(RenderContext ctx) throws IOException {
        initFftTexture(ctx, GL_NEAREST);
        initVao(ctx);
        initShaders(ctx);

        Rectangle win = ctx.getWindow();
        currentAspect = (float) win.width / win.height;
        ctx.addResizeListener((w, h) -> currentAspect = (float) w / h);

        ctx.setDesiredUpdateFrequency(60.0);
    }

    @Override
    public void doRender(RenderContext ctx) {
        if (clearBeforeRender) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }
        uploadFftTexture();

        glBindVertexArray(emptyVaoId);
        shader.use(ctx);
        uAspect.set(currentAspect);
        uRepeatsUniform.set(repeats);
        uGrowthModeUniform.set(growthMode.ordinal());
        uHalfWidthUniform.set(computeHalfWidth());
        uTransformUniform.set(transform);

        int totalSlots = numBins * repeats;
        glDrawArrays(GL_TRIANGLES, 0, totalSlots * 6);
    }

    @Override
    public void dispose() {
        if (shader != null) { shader.dispose(); shader = null; }
        if (emptyVaoId != 0) { glDeleteVertexArrays(emptyVaoId); emptyVaoId = 0; }
        disposeFftTexture();
    }

    // ── Initialisation helpers ───────────────────────────────────────────────────

    private void initVao(RenderContext ctx) {
        // No vertex data -- all geometry computed in the vertex shader from gl_VertexID.
        emptyVaoId = glGenVertexArrays();
        ctx.getResourceManager().register(() -> glDeleteVertexArrays(emptyVaoId));
    }

    private void initShaders(RenderContext ctx) {
        glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, fftTextureId);

        shader = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_SHADER, RadialClockAnalyser.class),
                ShaderSource.fromClass(FRAGMENT_SHADER, RadialClockAnalyser.class),
                null);
        shader.use(ctx);
        shader.uniforms().get("uFFTTex", Integer.class).set(0);
        shader.uniforms().get("uNumBins", Integer.class).set(numBins);
        shader.uniforms().get("uBaseRadius", Float.class).set(baseRadius);
        shader.uniforms().get("uOuterHeight", Float.class).set(outerHeight);
        shader.uniforms().get("uInnerDepth", Float.class).set(innerDepth);
        shader.uniforms().get("uColorInner", Vector4f.class).set(colorInner);
        shader.uniforms().get("uColorBase", Vector4f.class).set(colorBase);
        shader.uniforms().get("uColorOuter", Vector4f.class).set(colorOuter);

        uAspect = shader.uniforms().get("uAspect", Float.class);
        uAspect.set(1.0f);
        uRepeatsUniform = shader.uniforms().get("uRepeats", Integer.class);
        uRepeatsUniform.set(repeats);
        uGrowthModeUniform = shader.uniforms().get("uGrowthMode", Integer.class);
        uGrowthModeUniform.set(growthMode.ordinal());
        uHalfWidthUniform = shader.uniforms().get("uHalfWidth", Float.class);
        uHalfWidthUniform.set(computeHalfWidth());
        uTransformUniform = shader.uniforms().get("uTransform", Matrix4f.class);
        uTransformUniform.set(new Matrix4f());
    }
}
