package com.asteroid.duck.opengl.util.wave;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor;
import org.joml.Matrix4f;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.Uniform;
import org.joml.Vector4f;

import java.awt.Rectangle;
import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Real-time audio spectrum analyser rendered as a smooth radial (polar) filled shape.
 *
 * <p>This is a pure renderer: it receives pre-computed normalised magnitude values via
 * {@link com.asteroid.duck.opengl.util.audio.analysis.FrequencySink#onSpectrum} and renders them as a smooth continuous shape that
 * radiates both <em>outward</em> and <em>inward</em> from a base circle. The shape has no
 * hard bar edges — GL linear texture filtering interpolates smoothly between adjacent FFT
 * bins as vertices are sampled around the circle. A separate smooth peak-hold line traces
 * the historical maximum outward from the base circle.</p>
 *
 * <p>Vertices are generated entirely in the vertex shader from {@code gl_VertexID}; no per-vertex
 * data is uploaded to the GPU. The FFT texture uses {@code GL_LINEAR} filtering, so increasing
 * {@link #DEFAULT_RING_VERTS} simply adds more angular samples through the same smooth curve
 * without any Java-side changes.</p>
 *
 * <h2>Layout</h2>
 * <ul>
 *   <li>The spectrum starts at 6 o'clock and runs counter-clockwise (bass at bottom, treble
 *       wraps around).</li>
 *   <li>Outward extent = {@code baseRadius + magnitude × outerHeight}.</li>
 *   <li>Inward extent  = {@code baseRadius − magnitude × innerDepth}.</li>
 * </ul>
 *
 * <h2>Colours</h2>
 * <p>The filled shape uses a 3-stop gradient controlled by {@link #withColors}:</p>
 * <ul>
 *   <li><em>inner</em> — colour at the inward tip (maximum inward displacement)</li>
 *   <li><em>base</em>  — colour at the midpoint of the gradient (roughly the base circle)</li>
 *   <li><em>outer</em> — colour at the outward tip (maximum outward displacement)</li>
 * </ul>
 * <p>The peak-hold line colour is set independently via {@link #withPeakColor}.</p>
 * <p>All colours are RGBA ({@link org.joml.Vector4f}); an alpha component below 1 renders
 * translucent, since {@code GL_BLEND} is enabled globally with standard alpha blending.</p>
 *
 * <h2>Repeat / symmetry</h2>
 * <p>The spectrum can be tiled around the circle any number of times via {@link #withRepeats}.
 * Odd-numbered segments are reversed so adjacent copies mirror each other — bass and treble meet
 * at every seam, creating natural bilateral or rotational symmetry. {@code repeats=2} gives a
 * mirrored pair; {@code repeats=4} gives four rotationally-symmetric quadrants.</p>
 *
 * <h2>Typical wiring</h2>
 * <pre>{@code
 * FrequencyProcessor freqProc = new FrequencyProcessor(...);
 * RadialSpectrumAnalyser radial = new RadialSpectrumAnalyser(freqProc)
 *         .withColors(new Vector4f(0, 0.2f, 0.6f, 1),   // inner tip — deep blue
 *                     new Vector4f(0, 0.7f, 0.3f, 1),   // base circle — green
 *                     new Vector4f(0.9f, 0.1f, 0, 1))   // outer tip  — red
 *         .withPeakColor(new Vector4f(1, 1, 1, 1))       // peak line  — white
 *         .withRepeats(2);                            // bilateral symmetry
 * freqProc.addSink(radial);
 *
 * // each frame, on the render thread:
 * freqProc.process();
 * radial.doRender(ctx);
 * }</pre>
 */
public class RadialSpectrumAnalyser extends FrequencyRenderer {

    /**
     * Default number of vertices used to tessellate the circle ({@value}).
     * Higher values produce a smoother curve; GL linear texture interpolation does the heavy lifting.
     */
    public static final int   DEFAULT_RING_VERTS    = 512;

    /** Default base circle radius in NDC units ({@value}). */
    public static final float DEFAULT_BASE_RADIUS   = 0.35f;

    /** Default maximum outward extension in NDC units at full magnitude ({@value}). */
    public static final float DEFAULT_OUTER_HEIGHT  = 0.55f;

    /** Default maximum inward contraction in NDC units at full magnitude ({@value}). */
    public static final float DEFAULT_INNER_DEPTH   = 0.15f;

    /** Default peak-hold dwell ({@value} frames ≈ 0.5 s at 60 fps). */
    public static final int   DEFAULT_DWELL_FRAMES  = 30;

    /** Default peak-hold sag rate ({@value}: full scale falls to zero in ~3 s at 60 fps). */
    public static final float DEFAULT_PEAK_SAG_PER_FRAME = 1.0f / 180f;

    /** Default peak line width in pixels ({@value}). */
    public static final float DEFAULT_PEAK_LINE_WIDTH = 2.0f;

    // ── Shaders ──────────────────────────────────────────────────────────────────

    // language=GLSL
    private static final String VERTEX_FILL = """
            #version 330 core
            uniform sampler1D uFFTTex;
            uniform int   uNumRingVerts;
            uniform int   uRepeats;
            uniform float uBaseRadius;
            uniform float uOuterHeight;
            uniform float uInnerDepth;
            uniform float uAspect;
            uniform mat4  uTransform;

            out float vFillT;   // 0 = inner tip, 1 = outer tip

            const float PI = 3.14159265358979;

            void main() {
                // Even gl_VertexID = inner vertex, odd = outer.
                // Modulo handles the repeated pair at the end that closes the triangle strip.
                int   ringIdx = (gl_VertexID / 2) % uNumRingVerts;
                bool  isOuter = (gl_VertexID % 2) == 1;

                float t     = float(ringIdx) / float(uNumRingVerts);
                float angle = -PI / 2.0 + t * 2.0 * PI;  // 6 o'clock, counter-clockwise

                // Tile the spectrum uRepeats times around the circle.
                // Odd-numbered segments are reversed so adjacent copies mirror each other —
                // bass and treble meet at every seam rather than jumping abruptly.
                float seg      = t * float(uRepeats);
                float localT   = fract(seg);
                float texCoord = (mod(floor(seg), 2.0) < 0.5) ? localT : 1.0 - localT;

                float magnitude = texture(uFFTTex, texCoord).r;

                float radius = isOuter
                    ? uBaseRadius + magnitude * uOuterHeight
                    : uBaseRadius - magnitude * uInnerDepth;

                vFillT = isOuter ? 1.0 : 0.0;

                vec2 dir = vec2(cos(angle), sin(angle));
                gl_Position = uTransform * vec4(dir.x * radius / uAspect, dir.y * radius, 0.0, 1.0);
            }
        """;

    // language=GLSL
    private static final String FRAGMENT_FILL = """
            #version 330 core
            in  float vFillT;
            uniform vec4 uColorInner;  // colour at the inward tip  (vFillT = 0)
            uniform vec4 uColorBase;   // colour at the base circle  (vFillT = 0.5)
            uniform vec4 uColorOuter;  // colour at the outward tip  (vFillT = 1)
            out vec4 fragColor;

            void main() {
                // 3-stop gradient: inner tip → base circle → outer tip
                fragColor = (vFillT < 0.5)
                    ? mix(uColorInner, uColorBase, vFillT * 2.0)
                    : mix(uColorBase,  uColorOuter, (vFillT - 0.5) * 2.0);
            }
        """;

    // language=GLSL
    private static final String VERTEX_PEAK = """
            #version 330 core
            uniform sampler1D uFFTTex;
            uniform int   uNumRingVerts;
            uniform int   uRepeats;
            uniform float uBaseRadius;
            uniform float uOuterHeight;
            uniform float uAspect;
            uniform mat4  uTransform;

            const float PI = 3.14159265358979;

            void main() {
                float t     = float(gl_VertexID) / float(uNumRingVerts);
                float angle = -PI / 2.0 + t * 2.0 * PI;

                float seg      = t * float(uRepeats);
                float localT   = fract(seg);
                float texCoord = (mod(floor(seg), 2.0) < 0.5) ? localT : 1.0 - localT;

                float peak   = texture(uFFTTex, texCoord).g;
                float radius = uBaseRadius + peak * uOuterHeight;

                vec2 dir = vec2(cos(angle), sin(angle));
                gl_Position = uTransform * vec4(dir.x * radius / uAspect, dir.y * radius, 0.0, 1.0);
            }
        """;

    // language=GLSL
    private static final String FRAGMENT_PEAK = """
            #version 330 core
            uniform vec4 uPeakColor;
            out vec4 fragColor;
            void main() {
                fragColor = uPeakColor;
            }
        """;

    // ── Construction-time parameters ────────────────────────────────────────────
    private final int   numRingVerts;
    private final float baseRadius;
    private final float outerHeight;
    private final float innerDepth;

    /** Fill gradient stop at the inward tip (vFillT = 0). RGBA — alpha is honoured. */
    private Vector4f colorInner = new Vector4f(0.0f, 0.2f, 0.6f, 1.0f);
    /** Fill gradient stop at the base circle (vFillT = 0.5). RGBA — alpha is honoured. */
    private Vector4f colorBase  = new Vector4f(0.0f, 0.7f, 0.3f, 1.0f);
    /** Fill gradient stop at the outward tip (vFillT = 1). RGBA — alpha is honoured. */
    private Vector4f colorOuter = new Vector4f(0.9f, 0.1f, 0.0f, 1.0f);
    // colorPeak is inherited from FrequencyRenderer (default: white)

    // ── GL resources ────────────────────────────────────────────────────────────
    /** Empty VAO — all geometry is procedural (gl_VertexID only). */
    private int         emptyVaoId;

    private ShaderProgram fillShader;
    private ShaderProgram peakShader;
    private Uniform<Float>    uFillAspect;
    private Uniform<Float>    uPeakAspect;
    private Uniform<Integer>  uFillRepeats;
    private Uniform<Integer>  uPeakRepeats;
    private Uniform<Matrix4f> uFillTransform;
    private Uniform<Matrix4f> uPeakTransform;

    // ── Per-frame state ──────────────────────────────────────────────────────────
    // magnitudes[], peaks[], dwellCounters[] inherited from FrequencyRenderer
    private volatile float currentAspect = 1.0f;

    /** Number of times the spectrum is tiled around the circle; odd segments are mirrored. */
    private volatile int repeats = 1;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /**
     * Constructs a radial spectrum renderer with fully custom geometry.
     *
     * @param processor    the shared FFT processor that pushes spectrum data to this renderer
     * @param numRingVerts number of vertices tessellating the circle; higher = smoother curve
     * @param baseRadius   base circle radius in NDC units
     * @param outerHeight  maximum outward extension in NDC units at full magnitude
     * @param innerDepth   maximum inward contraction in NDC units at full magnitude
     */
    public RadialSpectrumAnalyser(FrequencyProcessor processor, int numRingVerts,
                                   float baseRadius, float outerHeight, float innerDepth) {
        super(processor.getNumBins());
        this.numRingVerts = numRingVerts;
        this.baseRadius   = baseRadius;
        this.outerHeight  = outerHeight;
        this.innerDepth   = innerDepth;
        this.peakLineWidth = DEFAULT_PEAK_LINE_WIDTH;
    }

    /**
     * Constructs a radial spectrum renderer with default geometry
     * ({@link #DEFAULT_RING_VERTS}, {@link #DEFAULT_BASE_RADIUS},
     * {@link #DEFAULT_OUTER_HEIGHT}, {@link #DEFAULT_INNER_DEPTH}).
     *
     * @param processor the shared FFT processor that pushes spectrum data to this renderer
     */
    public RadialSpectrumAnalyser(FrequencyProcessor processor) {
        this(processor, DEFAULT_RING_VERTS,
             DEFAULT_BASE_RADIUS, DEFAULT_OUTER_HEIGHT, DEFAULT_INNER_DEPTH);
    }

    // ── Configuration ────────────────────────────────────────────────────────────

    /**
     * Sets the three fill gradient colours. Call before {@link #init}.
     *
     * <p>The fill shape is shaded with a 3-stop gradient:</p>
     * <ul>
     *   <li>{@code inner} — at the inward tip (maximum inward displacement from the base circle)</li>
     *   <li>{@code base}  — at the midpoint of the gradient, roughly where the base circle sits</li>
     *   <li>{@code outer} — at the outward tip (maximum outward displacement)</li>
     * </ul>
     *
     * <p>Colours are RGBA — an alpha component below 1 renders the fill translucent, since
     * {@code GL_BLEND} is enabled globally with standard alpha blending.</p>
     *
     * @param inner colour at the inward tip
     * @param base  colour at the base-circle gradient midpoint
     * @param outer colour at the outward tip
     * @return {@code this} for fluent chaining
     */
    public RadialSpectrumAnalyser withColors(Vector4f inner, Vector4f base, Vector4f outer) {
        this.colorInner = new Vector4f(inner);
        this.colorBase  = new Vector4f(base);
        this.colorOuter = new Vector4f(outer);
        return this;
    }

    /**
     * Sets the colour of the smooth peak-hold line. Call before {@link #init}.
     *
     * <p>Colour is RGBA — an alpha component below 1 renders the peak line translucent.</p>
     *
     * @param color peak line colour (default opaque white)
     * @return {@code this} for fluent chaining
     */
    public RadialSpectrumAnalyser withPeakColor(Vector4f color) {
        this.colorPeak = new Vector4f(color);
        return this;
    }

    /**
     * Tunes the peak-hold line ballistics. May be called before or after {@link #init}.
     *
     * @param dwellFrames     frames to hold at the maximum before sagging (e.g. 30 ≈ 0.5 s at 60 fps)
     * @param peakSagPerFrame fraction subtracted per frame during sag (e.g. {@code 1f/180f} ≈ 3 s)
     * @return {@code this} for fluent chaining
     */
    public RadialSpectrumAnalyser withPeakDynamics(int dwellFrames, float peakSagPerFrame) {
        this.dwellFrames     = dwellFrames;
        this.peakSagPerFrame = peakSagPerFrame;
        return this;
    }

    /**
     * Sets the line width of the smooth peak-hold line in pixels.
     *
     * @param width line width in pixels
     * @return {@code this} for fluent chaining
     */
    public RadialSpectrumAnalyser withPeakLineWidth(float width) {
        this.peakLineWidth = width;
        return this;
    }

    /**
     * Sets the number of times the full frequency spectrum is repeated around the circle.
     *
     * <p>Odd-numbered repetition segments run in the reverse direction so that bass and treble
     * meet at every seam rather than jumping abruptly — this creates natural bilateral or
     * rotational symmetry:</p>
     * <ul>
     *   <li>{@code 1} — full spectrum once (default)</li>
     *   <li>{@code 2} — two mirrored halves, each running bass→treble→bass</li>
     *   <li>{@code 4} — four quadrants alternating normal/reversed; bass and treble are mirrored
     *       between adjacent quadrants</li>
     * </ul>
     *
     * <p>Any positive integer is accepted. May be called before or after {@link #init}; if called
     * after, the new value takes effect on the next rendered frame.</p>
     *
     * @param repeats number of spectrum repetitions; must be &gt;= 1
     * @return {@code this} for fluent chaining
     */
    public RadialSpectrumAnalyser withRepeats(int repeats) {
        if (repeats < 1) throw new IllegalArgumentException("repeats must be >= 1");
        this.repeats = repeats;
        return this;
    }

    /** Returns the current repeat count. */
    public int getRepeats() { return repeats; }

    // ── RenderedItem lifecycle ───────────────────────────────────────────────────

    @Override
    public void init(RenderContext ctx) throws IOException {
        initFftTexture(ctx, GL_LINEAR);
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
        updatePeakBallistics();
        uploadFftTexture();

        float aspect = currentAspect;
        Matrix4f t = transform;
        glBindVertexArray(emptyVaoId);

        int currentRepeats = repeats;

        // 3. Draw the smooth filled shape: inner+outer alternating vertices, closed by the extra pair.
        fillShader.use(ctx);
        uFillAspect.set(aspect);
        uFillRepeats.set(currentRepeats);
        uFillTransform.set(t);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, numRingVerts * 2 + 2);

        // 4. Draw the smooth peak-hold line (GL_LINE_LOOP closes it back to vertex 0 automatically).
        glLineWidth(peakLineWidth);
        peakShader.use(ctx);
        uPeakAspect.set(aspect);
        uPeakRepeats.set(currentRepeats);
        uPeakTransform.set(t);
        glDrawArrays(GL_LINE_LOOP, 0, numRingVerts);
    }

    @Override
    public void dispose() {
        if (fillShader != null) { fillShader.dispose(); fillShader = null; }
        if (peakShader != null) { peakShader.dispose(); peakShader = null; }
        if (emptyVaoId != 0) { glDeleteVertexArrays(emptyVaoId); emptyVaoId = 0; }
        disposeFftTexture();
    }

    // ── Initialisation helpers ───────────────────────────────────────────────────

    private void initVao(RenderContext ctx) {
        // No vertex data — all geometry computed in the vertex shader from gl_VertexID.
        // An empty VAO is required by Core Profile even with no vertex attributes.
        emptyVaoId = glGenVertexArrays();
        ctx.getResourceManager().register(() -> glDeleteVertexArrays(emptyVaoId));
    }

    private void initShaders(RenderContext ctx) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, fftTextureId);

        // ── Fill shader ──────────────────────────────────────────────────────────
        fillShader = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_FILL,   RadialSpectrumAnalyser.class),
                ShaderSource.fromClass(FRAGMENT_FILL, RadialSpectrumAnalyser.class),
                null);
        fillShader.use(ctx);
        fillShader.uniforms().get("uFFTTex",      Integer.class).set(0);
        fillShader.uniforms().get("uNumRingVerts", Integer.class).set(numRingVerts);
        fillShader.uniforms().get("uBaseRadius",   Float.class).set(baseRadius);
        fillShader.uniforms().get("uOuterHeight",  Float.class).set(outerHeight);
        fillShader.uniforms().get("uInnerDepth",   Float.class).set(innerDepth);
        fillShader.uniforms().get("uColorInner", Vector4f.class).set(colorInner);
        fillShader.uniforms().get("uColorBase",  Vector4f.class).set(colorBase);
        fillShader.uniforms().get("uColorOuter", Vector4f.class).set(colorOuter);
        uFillAspect   = fillShader.uniforms().get("uAspect",   Float.class);
        uFillAspect.set(1.0f);
        uFillRepeats  = fillShader.uniforms().get("uRepeats", Integer.class);
        uFillRepeats.set(repeats);
        uFillTransform = fillShader.uniforms().get("uTransform", Matrix4f.class);
        uFillTransform.set(new Matrix4f());

        // ── Peak shader ──────────────────────────────────────────────────────────
        peakShader = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_PEAK,   RadialSpectrumAnalyser.class),
                ShaderSource.fromClass(FRAGMENT_PEAK, RadialSpectrumAnalyser.class),
                null);
        peakShader.use(ctx);
        peakShader.uniforms().get("uFFTTex",      Integer.class).set(0);
        peakShader.uniforms().get("uNumRingVerts", Integer.class).set(numRingVerts);
        peakShader.uniforms().get("uBaseRadius",   Float.class).set(baseRadius);
        peakShader.uniforms().get("uOuterHeight",  Float.class).set(outerHeight);
        peakShader.uniforms().get("uPeakColor",    Vector4f.class).set(colorPeak);
        uPeakAspect   = peakShader.uniforms().get("uAspect",   Float.class);
        uPeakAspect.set(1.0f);
        uPeakRepeats  = peakShader.uniforms().get("uRepeats", Integer.class);
        uPeakRepeats.set(repeats);
        uPeakTransform = peakShader.uniforms().get("uTransform", Matrix4f.class);
        uPeakTransform.set(new Matrix4f());
    }
}
