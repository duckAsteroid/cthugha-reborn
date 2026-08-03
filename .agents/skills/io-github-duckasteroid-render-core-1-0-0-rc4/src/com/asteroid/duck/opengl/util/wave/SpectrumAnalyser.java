package com.asteroid.duck.opengl.util.wave;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor;
import org.joml.Matrix4f;
import com.asteroid.duck.opengl.util.resources.buffer.BufferDrawMode;
import com.asteroid.duck.opengl.util.resources.buffer.UpdateHint;
import com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexDataStructure;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexElement;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexElementType;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.Uniform;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Real-time audio spectrum analyser — Cartesian bar-chart or smooth filled-area renderer.
 *
 * <p>This class is a pure renderer: it receives pre-computed normalised magnitude values via
 * {@link com.asteroid.duck.opengl.util.audio.analysis.FrequencySink#onSpectrum} and draws them as either discrete filled bars
 * ({@link RenderMode#BARS}) or a continuous smooth filled region ({@link RenderMode#FILLED}).
 * Both modes support configurable frequency layout, bar direction, peak-hold ballistics,
 * and colour gradients. All audio capture and FFT computation is the caller's
 * responsibility — wire this up as a sink on a {@link FrequencyProcessor}.</p>
 *
 * <h2>Render modes</h2>
 * <ul>
 *   <li>{@link RenderMode#BARS} (default) — discrete filled bars with inter-bar gaps and
 *       per-bin floating peak-tick markers. Uses {@code texelFetch} for exact bin lookup.</li>
 *   <li>{@link RenderMode#FILLED} — a continuous filled area drawn as a
 *       {@code GL_TRIANGLE_STRIP} with {@code GL_LINEAR} texture sampling, giving a smooth
 *       curve between adjacent FFT bins.</li>
 * </ul>
 *
 * <h2>Frequency layout</h2>
 * <ul>
 *   <li>{@link BarLayout#NORMAL} (default) — low frequency (bass) on the left.</li>
 *   <li>{@link BarLayout#REVERSED} — bass on the right.</li>
 *   <li>{@link BarLayout#MIRRORED} — bass at the centre, treble at both edges. In BARS
 *       mode this doubles the number of display bars (each bin appears once per side).</li>
 * </ul>
 *
 * <h2>Bar direction</h2>
 * <ul>
 *   <li>{@link BarDirection#UP} (default) — bars grow upward from the screen bottom.</li>
 *   <li>{@link BarDirection#DOWN} — bars hang downward from the screen top.</li>
 *   <li>{@link BarDirection#BOTH} — bars extend symmetrically from the screen centre,
 *       inward and outward simultaneously.</li>
 * </ul>
 *
 * <h2>Typical wiring</h2>
 * <pre>{@code
 * FrequencyProcessor freqProc = new FrequencyProcessor(...);
 * SpectrumAnalyser analyser = new SpectrumAnalyser(freqProc)
 *         .withBarColors(new Vector4f(0, 0.8f, 0, 1), new Vector4f(0.8f, 0, 0, 1))
 *         .withLayout(BarLayout.MIRRORED)
 *         .withDirection(BarDirection.BOTH)
 *         .withRenderMode(RenderMode.FILLED);
 * freqProc.addSink(analyser);
 *
 * // each frame, on the render thread:
 * freqProc.process();
 * analyser.doRender(ctx);
 * }</pre>
 */
public class SpectrumAnalyser extends FrequencyRenderer {

    /** Default gap fraction between adjacent bars ({@value}): 15% of each slot is empty. */
    public static final float DEFAULT_GAP            = 0.15f;

    /** Default peak-tick line width in pixels ({@value}). */
    public static final float DEFAULT_PEAK_LINE_WIDTH = 3.0f;

    // ── Enums ────────────────────────────────────────────────────────────────────

    /**
     * Direction in which bars (and the filled region) extend from their baseline.
     * The ordinal value is passed directly to the GLSL {@code uBarDir} uniform.
     */
    public enum BarDirection {
        /** Bars grow upward from the screen bottom (y = −1). */
        UP,
        /** Bars hang downward from the screen top (y = +1). */
        DOWN,
        /** Bars extend symmetrically from the screen centre in both directions. */
        BOTH
    }

    /**
     * Horizontal mapping of FFT bins to display positions.
     * {@link #MIRRORED} doubles the number of display bars in {@link RenderMode#BARS} mode.
     * The ordinal value is passed directly to the GLSL {@code uLayout} uniform.
     */
    public enum BarLayout {
        /** Low frequency (bass) on the left, high frequency (treble) on the right. */
        NORMAL,
        /** Treble on the left, bass on the right. */
        REVERSED,
        /** Bass at the centre, treble at both edges; the spectrum is shown mirrored. */
        MIRRORED
    }

    /**
     * Visual rendering style.
     */
    public enum RenderMode {
        /** Discrete filled bars with inter-bar gaps and per-bin peak ticks. */
        BARS,
        /** Smooth continuous filled area with a smooth peak-hold line. */
        FILLED
    }

    // ── Vertex attribute ─────────────────────────────────────────────────────────

    private static final VertexElement VERTEX =
            new VertexElement(VertexElementType.VEC_2F, "vertex");

    // ── Shaders ─────────────────────────────────────────────────────────────────

    // language=GLSL
    private static final String VERTEX_BARS = """
            #version 330 core
            // x = NDC x position; y = role flag (0.0 = base vertex, 1.0 = tip vertex)
            in vec2 vertex;
            uniform sampler1D uFFTTex;   // GL_RG32F: R = magnitude, G = peak
            uniform int uBarDir;          // 0=UP, 1=DOWN, 2=BOTH
            uniform int uLayout;          // 0=NORMAL, 1=REVERSED, 2=MIRRORED
            uniform int uNumBins;
            uniform mat4 uTransform;
            out float vT;                 // 0 = base colour, magnitude = tip colour

            void main() {
                int barIndex = gl_VertexID / 6;
                bool isTop = vertex.y > 0.5;

                // Map display bar index to FFT bin index
                int texIdx;
                if (uLayout == 2) {  // MIRRORED: bass in centre
                    texIdx = (barIndex < uNumBins) ? uNumBins - 1 - barIndex : barIndex - uNumBins;
                } else if (uLayout == 1) {  // REVERSED
                    texIdx = uNumBins - 1 - barIndex;
                } else {
                    texIdx = barIndex;
                }
                float magnitude = texelFetch(uFFTTex, texIdx, 0).r;

                float y;
                if (uBarDir == 1) {  // DOWN: hangs from screen top
                    y = isTop ? 1.0 : 1.0 - magnitude * 2.0;
                    vT = isTop ? 0.0 : magnitude;
                } else if (uBarDir == 2) {  // BOTH: symmetric from centre
                    y = isTop ? magnitude : -magnitude;
                    vT = magnitude;
                } else {  // UP (default): rises from screen bottom
                    y = isTop ? magnitude * 2.0 - 1.0 : -1.0;
                    vT = isTop ? magnitude : 0.0;
                }
                gl_Position = uTransform * vec4(vertex.x, y, 0.0, 1.0);
            }
        """;

    // language=GLSL
    private static final String VERTEX_FILLED = """
            #version 330 core
            uniform sampler1D uFFTTex;
            uniform int uNumFillSamples;
            uniform int uBarDir;
            uniform int uLayout;
            uniform mat4 uTransform;
            out float vT;

            void main() {
                int sampleIdx = gl_VertexID / 2;
                bool isTop = (gl_VertexID % 2) == 1;
                // Normalised position [0, 1] across the display width
                float t = float(sampleIdx) / float(uNumFillSamples - 1);

                // Texture coordinate: apply layout mapping
                float texCoord;
                if (uLayout == 2) {        // MIRRORED: bass centre, treble edges
                    texCoord = abs(2.0 * t - 1.0);
                } else if (uLayout == 1) { // REVERSED
                    texCoord = 1.0 - t;
                } else {                   // NORMAL
                    texCoord = t;
                }

                float magnitude = texture(uFFTTex, texCoord).r;
                float x = t * 2.0 - 1.0;  // NDC x: −1 (left) to +1 (right)

                float y;
                if (uBarDir == 1) {  // DOWN
                    y = isTop ? 1.0 : 1.0 - magnitude * 2.0;
                    vT = isTop ? 0.0 : magnitude;
                } else if (uBarDir == 2) {  // BOTH
                    y = isTop ? magnitude : -magnitude;
                    vT = magnitude;
                } else {  // UP
                    y = isTop ? magnitude * 2.0 - 1.0 : -1.0;
                    vT = isTop ? magnitude : 0.0;
                }
                gl_Position = uTransform * vec4(x, y, 0.0, 1.0);
            }
        """;

    // language=GLSL — shared by both BARS and FILLED
    private static final String FRAGMENT_GRADIENT = """
            #version 330 core
            in float vT;
            out vec4 fragColor;
            uniform vec4 uColorLow;
            uniform vec4 uColorHigh;

            void main() {
                fragColor = mix(uColorLow, uColorHigh, vT);
            }
        """;

    // language=GLSL
    private static final String VERTEX_PEAK_BARS = """
            #version 330 core
            // x = NDC x position of this tick edge; y unused (computed from texture)
            in vec2 vertex;
            uniform sampler1D uFFTTex;
            uniform int uBarDir;
            uniform int uLayout;
            uniform int uNumBins;
            uniform mat4 uTransform;

            void main() {
                int barIndex = gl_VertexID / 4;
                // 0 = positive-side tick, 1 = negative-side tick (used in BOTH)
                int pairIdx = (gl_VertexID / 2) % 2;

                int texIdx;
                if (uLayout == 2) {
                    texIdx = (barIndex < uNumBins) ? uNumBins - 1 - barIndex : barIndex - uNumBins;
                } else if (uLayout == 1) {
                    texIdx = uNumBins - 1 - barIndex;
                } else {
                    texIdx = barIndex;
                }
                float peak = texelFetch(uFFTTex, texIdx, 0).g;

                float y;
                if (uBarDir == 1) {  // DOWN: first pair = bottom tick, second off-screen
                    y = (pairIdx == 0) ? 1.0 - peak * 2.0 : 3.0;
                } else if (uBarDir == 2) {  // BOTH: top and bottom ticks
                    y = (pairIdx == 0) ? peak : -peak;
                } else {  // UP: first pair = top tick, second off-screen
                    y = (pairIdx == 0) ? peak * 2.0 - 1.0 : -3.0;
                }
                gl_Position = uTransform * vec4(vertex.x, y, 0.0, 1.0);
            }
        """;

    // language=GLSL
    private static final String VERTEX_PEAK_LINE = """
            #version 330 core
            uniform sampler1D uFFTTex;
            uniform int uNumFillSamples;
            uniform int uBarDir;
            uniform int uLayout;
            uniform float uPeakSign;  // +1.0 positive side, −1.0 negative side (BOTH)
            uniform mat4 uTransform;

            void main() {
                float t = float(gl_VertexID) / float(uNumFillSamples - 1);

                float texCoord;
                if (uLayout == 2) {
                    texCoord = abs(2.0 * t - 1.0);
                } else if (uLayout == 1) {
                    texCoord = 1.0 - t;
                } else {
                    texCoord = t;
                }

                float peak = texture(uFFTTex, texCoord).g;
                float x = t * 2.0 - 1.0;

                float y;
                if (uBarDir == 1) {  // DOWN
                    y = 1.0 - peak * 2.0;
                } else if (uBarDir == 2) {  // BOTH
                    y = uPeakSign * peak;
                } else {  // UP
                    y = peak * 2.0 - 1.0;
                }
                gl_Position = uTransform * vec4(x, y, 0.0, 1.0);
            }
        """;

    // language=GLSL — shared by both peak styles
    private static final String FRAGMENT_PEAK = """
            #version 330 core
            out vec4 fragColor;
            uniform vec4 uPeakColor;
            void main() { fragColor = uPeakColor; }
        """;

    // ── Construction-time parameters ────────────────────────────────────────────

    private final float gapFraction;

    /** Bar gradient: colour at the base of each bar (magnitude = 0). RGBA — alpha is honoured. */
    private Vector4f barColorLow  = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    /** Bar gradient: colour at the tip of each bar (magnitude = 1). RGBA — alpha is honoured. */
    private Vector4f barColorHigh = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    // ── Visual configuration (set before init unless noted) ──────────────────────

    /** Direction bars extend from their baseline. Runtime-changeable via {@link #withDirection}. */
    private volatile BarDirection direction    = BarDirection.UP;

    /** Horizontal frequency layout. Must be set before {@link #init}. */
    private BarLayout             layout       = BarLayout.NORMAL;

    /** Rendering style. Must be set before {@link #init}. */
    private RenderMode            renderMode   = RenderMode.BARS;

    /**
     * Number of sample positions for smooth-fill rendering; higher values give a finer curve.
     * Must be set before {@link #init}. Default: 512.
     */
    private int numFillSamples = 512;

    /** Computed in {@link #init}: numBins (NORMAL/REVERSED) or numBins×2 (MIRRORED). */
    private int numDisplayBars;

    // ── GL resources ─────────────────────────────────────────────────────────────

    // BARS mode
    private ShaderProgram    barShader;
    private ShaderProgram    peakBarsShader;
    private VertexArrayObject barVao;
    private VertexArrayObject peakBarsVao;

    // FILLED mode
    private ShaderProgram fillShader;
    private ShaderProgram peakLineShader;
    private int emptyFillVaoId;

    // ── Runtime uniforms (direction and transform are updated each frame) ────────

    private Uniform<Integer>  uBarsDir;
    private Uniform<Integer>  uPeakBarsDir;
    private Uniform<Integer>  uFillDir;
    private Uniform<Integer>  uPeakLineDir;
    private Uniform<Float>    uPeakLineSign;
    private Uniform<Matrix4f> uBarsTransform;
    private Uniform<Matrix4f> uPeakBarsTransform;
    private Uniform<Matrix4f> uFillTransform;
    private Uniform<Matrix4f> uPeakLineTransform;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /**
     * Constructs a renderer sized to match {@code processor}'s output.
     * Register this instance as a sink: {@code processor.addSink(analyser)}.
     *
     * @param processor   the shared FFT processor that pushes spectrum data to this analyser
     * @param gapFraction fraction [0, 1) of each bar slot used as a gap between bars
     *                    (only relevant in {@link RenderMode#BARS} mode)
     */
    public SpectrumAnalyser(FrequencyProcessor processor, float gapFraction) {
        super(processor.getNumBins());
        this.gapFraction = gapFraction;
        this.peakLineWidth = DEFAULT_PEAK_LINE_WIDTH;
    }

    /**
     * Constructs a renderer with {@link #DEFAULT_GAP} gap between bars.
     *
     * @param processor the shared FFT processor that pushes spectrum data to this analyser
     */
    public SpectrumAnalyser(FrequencyProcessor processor) {
        this(processor, DEFAULT_GAP);
    }

    // ── Configuration ────────────────────────────────────────────────────────────

    /**
     * Sets the bar gradient colours (base → tip). Call before {@link #init}.
     *
     * <p>The gradient colour at any point in the bar is {@code mix(low, high, vT)}, where
     * {@code vT} is 0 at the base and the current normalised magnitude at the tip. This
     * means quiet bars show mostly {@code low} and loud bars blend toward {@code high}.</p>
     *
     * <p>Colours are RGBA — an alpha component below 1 renders the bars translucent, since
     * {@code GL_BLEND} is enabled globally with standard alpha blending.</p>
     *
     * @param low  colour at the base (zero energy end)
     * @param high colour at the tip (full energy end)
     * @return {@code this} for fluent chaining
     */
    public SpectrumAnalyser withBarColors(Vector4f low, Vector4f high) {
        this.barColorLow  = new Vector4f(low);
        this.barColorHigh = new Vector4f(high);
        return this;
    }

    /**
     * Sets the colour of the peak-hold indicator (tick marks in BARS mode, continuous line
     * in FILLED mode). Call before {@link #init}.
     *
     * <p>Colour is RGBA — an alpha component below 1 renders the peak indicator translucent.</p>
     *
     * @param color peak indicator colour (default opaque white)
     * @return {@code this} for fluent chaining
     */
    public SpectrumAnalyser withPeakColor(Vector4f color) {
        this.colorPeak = new Vector4f(color);
        return this;
    }

    /**
     * Tunes the peak-hold ballistics. May be called before or after {@link #init}.
     *
     * @param dwellFrames     frames to hold at the maximum before sagging (e.g. 30 ≈ 0.5 s at 60 fps)
     * @param peakSagPerFrame fraction subtracted per frame during sag (e.g. {@code 1f/180f} ≈ 3 s)
     * @return {@code this} for fluent chaining
     */
    public SpectrumAnalyser withPeakDynamics(int dwellFrames, float peakSagPerFrame) {
        this.dwellFrames     = dwellFrames;
        this.peakSagPerFrame = peakSagPerFrame;
        return this;
    }

    /**
     * Sets the peak indicator line width in pixels. May be called before or after {@link #init}.
     *
     * @param width line width in pixels
     * @return {@code this} for fluent chaining
     */
    public SpectrumAnalyser withPeakLineWidth(float width) {
        this.peakLineWidth = width;
        return this;
    }

    /**
     * Sets the horizontal frequency layout. Must be called before {@link #init}.
     *
     * @param layout {@link BarLayout#NORMAL}, {@link BarLayout#REVERSED}, or
     *               {@link BarLayout#MIRRORED}
     * @return {@code this} for fluent chaining
     */
    public SpectrumAnalyser withLayout(BarLayout layout) {
        this.layout = layout;
        return this;
    }

    /**
     * Sets the direction bars extend from their baseline. May be called before or after
     * {@link #init} — the new direction takes effect on the next rendered frame.
     *
     * @param direction {@link BarDirection#UP}, {@link BarDirection#DOWN}, or
     *                  {@link BarDirection#BOTH}
     * @return {@code this} for fluent chaining
     */
    public SpectrumAnalyser withDirection(BarDirection direction) {
        this.direction = direction;
        return this;
    }

    /**
     * Sets the rendering style. Must be called before {@link #init}.
     *
     * @param mode {@link RenderMode#BARS} (discrete bars) or {@link RenderMode#FILLED}
     *             (smooth continuous area)
     * @return {@code this} for fluent chaining
     */
    public SpectrumAnalyser withRenderMode(RenderMode mode) {
        this.renderMode = mode;
        return this;
    }

    /**
     * Sets the number of horizontal sample positions used in {@link RenderMode#FILLED} mode.
     * Higher values give a finer, smoother curve but increase vertex-shader work. Must be
     * called before {@link #init}.
     *
     * @param samples number of sample positions (default 512)
     * @return {@code this} for fluent chaining
     */
    public SpectrumAnalyser withNumFillSamples(int samples) {
        this.numFillSamples = samples;
        return this;
    }

    /**
     * Controls whether the framebuffer is cleared before each frame.
     *
     * @param clear {@code true} to clear on each frame (default);
     *              {@code false} to composite over previously rendered content
     */
    @Override
    public void setClearBeforeRender(boolean clear) {
        super.setClearBeforeRender(clear);
    }

    // ── RenderedItem lifecycle ───────────────────────────────────────────────────

    @Override
    public void init(RenderContext ctx) throws IOException {
        numDisplayBars = (layout == BarLayout.MIRRORED) ? numBins * 2 : numBins;
        initFftTexture(ctx, GL_LINEAR);  // GL_LINEAR: FILLED uses texture(); BARS uses texelFetch (filter ignored)
        if (renderMode == RenderMode.BARS) {
            initBarsVbos(ctx);
            initBarsShaders(ctx);
        } else {
            initFillVao(ctx);
            initFillShaders(ctx);
        }
        ctx.setDesiredUpdateFrequency(60.0);
    }

    @Override
    public void doRender(RenderContext ctx) {
        if (clearBeforeRender) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }
        updatePeakBallistics();
        uploadFftTexture();
        if (renderMode == RenderMode.BARS) {
            renderBars(ctx);
        } else {
            renderFilled(ctx);
        }
    }

    @Override
    public void dispose() {
        if (renderMode == RenderMode.BARS) {
            disposeBars();
        } else {
            disposeFilled();
        }
        disposeFftTexture();
    }

    // ── BARS mode ────────────────────────────────────────────────────────────────

    private void initBarsVbos(RenderContext ctx) {
        float slotWidth = 2.0f / numDisplayBars;
        float barWidth  = slotWidth * (1.0f - gapFraction);
        float halfGap   = slotWidth * gapFraction / 2.0f;

        // ── Bar VAO: GL_TRIANGLES, 6 vertices per bar ────────────────────────────
        barVao = new VertexArrayObject();
        barVao.setDrawMode(BufferDrawMode.TRIANGLES);
        barVao.init(ctx);
        var barVbo = barVao.createVbo(new VertexDataStructure(VERTEX), numDisplayBars * 6);
        barVbo.init(ctx);
        for (int i = 0; i < numDisplayBars; i++) {
            float xL = -1.0f + i * slotWidth + halfGap;
            float xR = xL + barWidth;
            int base = i * 6;
            barVbo.setElement(base + 0, VERTEX, new Vector2f(xL, 0.0f)); // BL (base)
            barVbo.setElement(base + 1, VERTEX, new Vector2f(xR, 0.0f)); // BR (base)
            barVbo.setElement(base + 2, VERTEX, new Vector2f(xL, 1.0f)); // TL (tip)
            barVbo.setElement(base + 3, VERTEX, new Vector2f(xR, 0.0f)); // BR (base)
            barVbo.setElement(base + 4, VERTEX, new Vector2f(xR, 1.0f)); // TR (tip)
            barVbo.setElement(base + 5, VERTEX, new Vector2f(xL, 1.0f)); // TL (tip)
        }
        barVbo.update(UpdateHint.STATIC);

        // ── Peak VAO: GL_LINES, 4 vertices per bar (2 pairs: positive + negative) ─
        // Odd pair (index 1) is placed off-screen for UP/DOWN; both pairs drawn for BOTH.
        peakBarsVao = new VertexArrayObject();
        peakBarsVao.setDrawMode(BufferDrawMode.LINES);
        peakBarsVao.init(ctx);
        var peakVbo = peakBarsVao.createVbo(new VertexDataStructure(VERTEX), numDisplayBars * 4);
        peakVbo.init(ctx);
        for (int i = 0; i < numDisplayBars; i++) {
            float xL = -1.0f + i * slotWidth + halfGap;
            float xR = xL + barWidth;
            peakVbo.setElement(i * 4 + 0, VERTEX, new Vector2f(xL, 0.0f)); // positive tick left
            peakVbo.setElement(i * 4 + 1, VERTEX, new Vector2f(xR, 0.0f)); // positive tick right
            peakVbo.setElement(i * 4 + 2, VERTEX, new Vector2f(xL, 0.0f)); // negative tick left
            peakVbo.setElement(i * 4 + 3, VERTEX, new Vector2f(xR, 0.0f)); // negative tick right
        }
        peakVbo.update(UpdateHint.STATIC);
    }

    private void initBarsShaders(RenderContext ctx) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, fftTextureId);

        barShader = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_BARS,      SpectrumAnalyser.class),
                ShaderSource.fromClass(FRAGMENT_GRADIENT, SpectrumAnalyser.class),
                null);
        barShader.use(ctx);
        barShader.uniforms().get("uFFTTex",   Integer.class).set(0);
        barShader.uniforms().get("uColorLow",  Vector4f.class).set(barColorLow);
        barShader.uniforms().get("uColorHigh", Vector4f.class).set(barColorHigh);
        barShader.uniforms().get("uLayout",   Integer.class).set(layout.ordinal());
        barShader.uniforms().get("uNumBins",  Integer.class).set(numBins);
        uBarsDir = barShader.uniforms().get("uBarDir", Integer.class);
        uBarsDir.set(direction.ordinal());
        uBarsTransform = barShader.uniforms().get("uTransform", Matrix4f.class);
        uBarsTransform.set(new Matrix4f());
        barVao.bind(ctx);
        barVao.getVbo().update(UpdateHint.STATIC);
        barVao.getVbo().setup(barShader);

        peakBarsShader = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_PEAK_BARS, SpectrumAnalyser.class),
                ShaderSource.fromClass(FRAGMENT_PEAK,    SpectrumAnalyser.class),
                null);
        peakBarsShader.use(ctx);
        peakBarsShader.uniforms().get("uFFTTex",    Integer.class).set(0);
        peakBarsShader.uniforms().get("uPeakColor", Vector4f.class).set(colorPeak);
        peakBarsShader.uniforms().get("uLayout",    Integer.class).set(layout.ordinal());
        peakBarsShader.uniforms().get("uNumBins",   Integer.class).set(numBins);
        uPeakBarsDir = peakBarsShader.uniforms().get("uBarDir", Integer.class);
        uPeakBarsDir.set(direction.ordinal());
        uPeakBarsTransform = peakBarsShader.uniforms().get("uTransform", Matrix4f.class);
        uPeakBarsTransform.set(new Matrix4f());
        peakBarsVao.bind(ctx);
        peakBarsVao.getVbo().update(UpdateHint.STATIC);
        peakBarsVao.getVbo().setup(peakBarsShader);
    }

    private void renderBars(RenderContext ctx) {
        int dir = direction.ordinal();
        Matrix4f t = transform;

        barShader.use(ctx);
        uBarsDir.set(dir);
        uBarsTransform.set(t);
        barVao.doRender(ctx);

        glLineWidth(peakLineWidth);
        peakBarsShader.use(ctx);
        uPeakBarsDir.set(dir);
        uPeakBarsTransform.set(t);
        peakBarsVao.doRender(ctx);
    }

    private void disposeBars() {
        if (barShader     != null) { barShader.dispose();     barShader     = null; }
        if (peakBarsShader != null) { peakBarsShader.dispose(); peakBarsShader = null; }
        if (barVao        != null) { barVao.dispose();        barVao        = null; }
        if (peakBarsVao   != null) { peakBarsVao.dispose();   peakBarsVao   = null; }
    }

    // ── FILLED mode ──────────────────────────────────────────────────────────────

    private void initFillVao(RenderContext ctx) {
        // Procedural geometry — no vertex data, just a VAO required by Core Profile.
        emptyFillVaoId = glGenVertexArrays();
        ctx.getResourceManager().register(() -> {
            if (emptyFillVaoId != 0) glDeleteVertexArrays(emptyFillVaoId);
        });
    }

    private void initFillShaders(RenderContext ctx) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, fftTextureId);

        fillShader = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_FILLED,    SpectrumAnalyser.class),
                ShaderSource.fromClass(FRAGMENT_GRADIENT, SpectrumAnalyser.class),
                null);
        fillShader.use(ctx);
        fillShader.uniforms().get("uFFTTex",        Integer.class).set(0);
        fillShader.uniforms().get("uColorLow",       Vector4f.class).set(barColorLow);
        fillShader.uniforms().get("uColorHigh",      Vector4f.class).set(barColorHigh);
        fillShader.uniforms().get("uLayout",         Integer.class).set(layout.ordinal());
        fillShader.uniforms().get("uNumFillSamples", Integer.class).set(numFillSamples);
        uFillDir = fillShader.uniforms().get("uBarDir", Integer.class);
        uFillDir.set(direction.ordinal());
        uFillTransform = fillShader.uniforms().get("uTransform", Matrix4f.class);
        uFillTransform.set(new Matrix4f());

        peakLineShader = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_PEAK_LINE, SpectrumAnalyser.class),
                ShaderSource.fromClass(FRAGMENT_PEAK,    SpectrumAnalyser.class),
                null);
        peakLineShader.use(ctx);
        peakLineShader.uniforms().get("uFFTTex",        Integer.class).set(0);
        peakLineShader.uniforms().get("uPeakColor",     Vector4f.class).set(colorPeak);
        peakLineShader.uniforms().get("uLayout",        Integer.class).set(layout.ordinal());
        peakLineShader.uniforms().get("uNumFillSamples",Integer.class).set(numFillSamples);
        uPeakLineDir  = peakLineShader.uniforms().get("uBarDir",    Integer.class);
        uPeakLineSign = peakLineShader.uniforms().get("uPeakSign",  Float.class);
        uPeakLineDir.set(direction.ordinal());
        uPeakLineSign.set(1.0f);
        uPeakLineTransform = peakLineShader.uniforms().get("uTransform", Matrix4f.class);
        uPeakLineTransform.set(new Matrix4f());
    }

    private void renderFilled(RenderContext ctx) {
        int dir = direction.ordinal();
        Matrix4f t = transform;
        glBindVertexArray(emptyFillVaoId);

        // Smooth filled area: numFillSamples pairs of (base, tip) vertices
        fillShader.use(ctx);
        uFillDir.set(dir);
        uFillTransform.set(t);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, numFillSamples * 2);

        // Continuous peak-hold line(s)
        glLineWidth(peakLineWidth);
        peakLineShader.use(ctx);
        uPeakLineDir.set(dir);
        uPeakLineSign.set(1.0f);
        uPeakLineTransform.set(t);
        glDrawArrays(GL_LINE_STRIP, 0, numFillSamples);

        if (direction == BarDirection.BOTH) {
            // Second peak line on the negative side (symmetric with the positive one)
            uPeakLineSign.set(-1.0f);
            glDrawArrays(GL_LINE_STRIP, 0, numFillSamples);
        }
    }

    private void disposeFilled() {
        if (fillShader     != null) { fillShader.dispose();     fillShader     = null; }
        if (peakLineShader != null) { peakLineShader.dispose(); peakLineShader = null; }
        if (emptyFillVaoId != 0)   { glDeleteVertexArrays(emptyFillVaoId); emptyFillVaoId = 0; }
    }
}
