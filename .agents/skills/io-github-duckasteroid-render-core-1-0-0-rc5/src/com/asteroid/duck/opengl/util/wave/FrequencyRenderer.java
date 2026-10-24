package com.asteroid.duck.opengl.util.wave;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.Transformable;
import com.asteroid.duck.opengl.util.audio.analysis.FrequencyProcessor;
import com.asteroid.duck.opengl.util.audio.analysis.FrequencySink;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Abstract base for FFT-driven spectrum renderers.
 *
 * <p>Owns the per-frame spectrum arrays ({@code magnitudes}, {@code peaks}), the 1-D
 * {@code GL_RG32F} FFT texture (R = magnitude, G = peak level), and the peak-hold
 * ballistics algorithm. Concrete subclasses ({@link SpectrumAnalyser},
 * {@link RadialSpectrumAnalyser}) handle the coordinate-system-specific rendering —
 * Cartesian bars/fill or polar radial shape — without duplicating shared state.</p>
 *
 * <p>All bar/fill/peak colours are {@link org.joml.Vector4f} (RGBA), and the fragment shaders
 * write the alpha channel through unmodified. {@link com.asteroid.duck.opengl.util.GLWindow}
 * enables {@code GL_BLEND} with standard {@code SRC_ALPHA}/{@code ONE_MINUS_SRC_ALPHA}
 * blending globally, so any colour with alpha &lt; 1 renders translucent over whatever was
 * drawn before it in the frame.</p>
 *
 * <p>Implements {@link Transformable}: call {@link #setTransform} at any time (from any
 * thread) to apply a {@link org.joml.Matrix4f} to all output vertex positions. The matrix
 * is read once per frame on the GL thread. The default is the identity matrix (no transform).
 * Example — slow rotation with a beat-driven scale pulse:</p>
 * <pre>{@code
 * float angle = 0f;
 * // each frame:
 * angle += 0.005f;
 * float pulse = 1.0f + 0.15f * beats.getBeatStrength(0);
 * spectrumAnalyser.setTransform(new Matrix4f().rotateZ(angle).scale(pulse));
 * }</pre>
 *
 * <h2>Subclass contract</h2>
 * <ol>
 *   <li>Call {@link #initFftTexture(RenderContext, int)} in {@code init()} with the
 *       desired GL filter ({@code GL_NEAREST} for exact bin lookup,
 *       {@code GL_LINEAR} for smooth interpolation between bins).</li>
 *   <li>Call {@link #updatePeakBallistics()} and {@link #uploadFftTexture()} at the
 *       start of each {@code doRender()} before issuing draw calls.</li>
 *   <li>Call {@link #disposeFftTexture()} inside {@code dispose()} to free the texture
 *       and native upload buffer.</li>
 *   <li>Read {@link #transform} once per frame and push it to all {@code uTransform}
 *       shader uniforms before issuing draw calls.</li>
 * </ol>
 */
public abstract class FrequencyRenderer implements RenderedItem, FrequencySink, Transformable {

    /** Default peak-hold dwell ({@value} frames ≈ 0.5 s at 60 fps). */
    public static final int   DEFAULT_DWELL_FRAMES      = 30;

    /** Default peak sag rate per frame ({@value}): full scale falls to zero in ~3 s at 60 fps. */
    public static final float DEFAULT_PEAK_SAG_PER_FRAME = 1.0f / 180f;

    // ── Per-frame data ────────────────────────────────────────────────────────────

    /** Number of FFT frequency bins, sourced from the owning {@link FrequencyProcessor}. */
    protected final int numBins;

    /** Latest normalised magnitudes delivered by {@link #onSpectrum}; range [0, 1]. */
    protected final float[] magnitudes;

    /** Peak-hold high-water marks per bin; range [0, 1]. Managed by {@link #updatePeakBallistics()}. */
    protected final float[] peaks;

    /** Per-bin countdown frames remaining in the dwell phase. */
    protected final int[]   dwellCounters;

    // ── Shared configuration ──────────────────────────────────────────────────────

    /** When {@code true} (default) the framebuffer is cleared at the start of each frame. */
    protected volatile boolean clearBeforeRender = true;

    /** Frames the peak holds at its maximum before starting to sag. */
    protected int   dwellFrames     = DEFAULT_DWELL_FRAMES;

    /** Fraction subtracted from the peak level per frame during sag. */
    protected float peakSagPerFrame = DEFAULT_PEAK_SAG_PER_FRAME;

    /** Width of the peak line or tick mark in pixels. Subclass default may vary. */
    protected float peakLineWidth   = 2.0f;

    /**
     * Colour of the peak-hold indicator; defaults to opaque white. Set before {@code init()}.
     * The alpha channel is honoured — subclasses render with blending enabled, so a fractional
     * alpha yields a translucent peak indicator.
     */
    protected Vector4f colorPeak = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    // ── Transform ────────────────────────────────────────────────────────────────

    /** Transform matrix applied to all output vertex positions; identity by default. */
    protected volatile Matrix4f transform = new Matrix4f();

    // ── GL resources owned by this class ─────────────────────────────────────────

    /**
     * 1-D {@code GL_RG32F} texture: R channel = magnitude, G channel = peak level.
     * Initialised by {@link #initFftTexture}; freed by {@link #disposeFftTexture}.
     */
    protected int         fftTextureId;

    /** Pre-allocated direct buffer for per-frame texture upload; length = {@code numBins * 2}. */
    protected FloatBuffer fftUploadBuffer;

    // ── Constructor ───────────────────────────────────────────────────────────────

    /**
     * @param numBins number of FFT frequency bins, matching the owning {@link FrequencyProcessor}
     */
    protected FrequencyRenderer(int numBins) {
        this.numBins       = numBins;
        this.magnitudes    = new float[numBins];
        this.peaks         = new float[numBins];
        this.dwellCounters = new int[numBins];
    }

    // ── Transformable ────────────────────────────────────────────────────────────

    /**
     * Sets the transform matrix applied to all output vertex positions.
     * The matrix is copied; the caller may reuse the supplied instance after this call returns.
     * Safe to call from any thread — the new matrix is read by the render thread at the start
     * of the next frame.
     *
     * @param matrix the transform to apply; pass {@code new Matrix4f()} to reset to identity
     */
    @Override
    public void setTransform(Matrix4f matrix) {
        this.transform = new Matrix4f(matrix);
    }

    // ── FrequencySink ─────────────────────────────────────────────────────────────

    /**
     * Receives per-frame spectrum data from the owning {@link FrequencyProcessor}.
     * Copies the latest magnitudes into the internal array for use in the next render.
     * Must not be called directly — registered as a sink via
     * {@link FrequencyProcessor#addSink}.
     */
    @Override
    public final void onSpectrum(float[] magnitudes) {
        System.arraycopy(magnitudes, 0, this.magnitudes, 0, numBins);
    }

    // ── Configuration ─────────────────────────────────────────────────────────────

    /**
     * Controls whether the framebuffer is cleared before each frame.
     *
     * @param clear {@code true} to call {@code glClear} at the start of each frame (default);
     *              {@code false} to composite on top of previously rendered content
     */
    public void setClearBeforeRender(boolean clear) {
        this.clearBeforeRender = clear;
    }

    // ── GL helpers for subclasses ─────────────────────────────────────────────────

    /**
     * Allocates and configures the 1-D {@code GL_RG32F} FFT texture with {@code numBins}
     * texels. Must be called on the GL thread inside the subclass {@code init()}.
     *
     * <p>Also registers the texture for automatic disposal with the
     * {@link com.asteroid.duck.opengl.util.resources.manager.ResourceManager} so the window can
     * clean up even if {@link #disposeFftTexture()} is never called explicitly.</p>
     *
     * @param ctx    render context (used to register auto-disposal)
     * @param filter GL texture filter — {@code GL_NEAREST} for exact per-bin lookup via
     *               {@code texelFetch}, or {@code GL_LINEAR} for smooth interpolation via
     *               {@code texture()}
     */
    protected void initFftTexture(RenderContext ctx, int filter) {
        fftTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_1D, fftTextureId);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, filter);
        glTexImage1D(GL_TEXTURE_1D, 0, GL_RG32F, numBins, 0, GL_RG, GL_FLOAT, (FloatBuffer) null);
        glBindTexture(GL_TEXTURE_1D, 0);
        fftUploadBuffer = memAllocFloat(numBins * 2);
        ctx.getResourceManager().register(this::disposeFftTexture);
    }

    /**
     * Uploads the current frame's magnitudes and peak levels to the 1-D FFT texture.
     * Must be bound on texture unit 0 before this call.
     */
    protected void uploadFftTexture() {
        fftUploadBuffer.clear();
        for (int i = 0; i < numBins; i++) {
            fftUploadBuffer.put(magnitudes[i]);
            fftUploadBuffer.put(peaks[i]);
        }
        fftUploadBuffer.flip();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, fftTextureId);
        glTexSubImage1D(GL_TEXTURE_1D, 0, 0, numBins, GL_RG, GL_FLOAT, fftUploadBuffer);
    }

    /**
     * Advances peak-hold ballistics for every bin: instant rise when a new maximum is
     * seen, hold for {@link #dwellFrames} frames, then sag at {@link #peakSagPerFrame}
     * per frame until back at zero.
     */
    protected void updatePeakBallistics() {
        for (int i = 0; i < numBins; i++) {
            if (magnitudes[i] >= peaks[i]) {
                peaks[i]         = magnitudes[i];
                dwellCounters[i] = dwellFrames;
            } else if (dwellCounters[i] > 0) {
                dwellCounters[i]--;
            } else {
                peaks[i] = Math.max(0.0f, peaks[i] - peakSagPerFrame);
            }
        }
    }

    /**
     * Frees the native upload buffer and the GL FFT texture. Safe to call multiple
     * times — null/zero checks prevent double-free.
     */
    protected void disposeFftTexture() {
        if (fftUploadBuffer != null) {
            memFree(fftUploadBuffer);
            fftUploadBuffer = null;
        }
        if (fftTextureId != 0) {
            glDeleteTextures(fftTextureId);
            fftTextureId = 0;
        }
    }
}
