package com.asteroid.duck.opengl.util.wave;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.Transformable;
import com.asteroid.duck.opengl.util.audio.PboAudioSink;
import com.asteroid.duck.opengl.util.renderaction.PendingValue;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.buffer.BufferDrawMode;
import com.asteroid.duck.opengl.util.resources.buffer.UpdateHint;
import com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexDataStructure;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexElement;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexElementType;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.Uniform;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.awt.*;
import java.io.IOException;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Real-time stereo audio waveform visualiser in a radial (polar) layout.
 *
 * <p>Reads from a shared {@link PboAudioSink}: the sink's audio texture is sampled in the vertex
 * shader to displace {@value #SAMPLE_COUNT} vertices arranged around a circle radially. The caller
 * must invoke {@link PboAudioSink#upload()} exactly once per frame before calling
 * {@link #doRender}, so that multiple visualisers sharing the same sink see identical data.</p>
 *
 * <h2>Buffer sizing</h2>
 * Pass {@link #AUDIO_BUFFER_SIZE} to {@link PboAudioSink#create}. The circle draws the newest
 * {@value #SAMPLE_COUNT} samples ending at the sink's head; the rest of the buffer is headroom
 * for audio written while the GPU copy is still pending (see {@link PboAudioSink}).
 */
public class RadialWave implements RenderedItem, Transformable {

    /** Number of vertices (and audio samples) around the circle. */
    public static final int SAMPLE_COUNT = 1024;

    /**
     * Minimum audio ring buffer size in stereo frames: four times the drawn window, leaving
     * {@code 3 × SAMPLE_COUNT} frames (≈ 64 ms at 48 kHz) of headroom for audio written while
     * the GPU copy is still pending. Pass this to {@link PboAudioSink#create}.
     */
    public static final int AUDIO_BUFFER_SIZE = SAMPLE_COUNT * 4;

    private static final VertexElement DIRECTION = new VertexElement(VertexElementType.VEC_2F, "direction");
    private static final VertexElement AMPLITUDE = new VertexElement(VertexElementType.FLOAT, "amplitude");

    /** Visualise the L+R average as a single line. */
    public static final int CHANNEL_BLEND = 0;
    /** Visualise the left channel only. */
    public static final int CHANNEL_LEFT  = 1;
    /** Visualise the right channel only. */
    public static final int CHANNEL_RIGHT = 2;

    /** Default amplitude function: uniform scale of 1× around the full circle. */
    private AmplitudeFunction amplitudeFunction = AmplitudeFunction.constant(1f);
    private volatile boolean amplitudeDirty = false;

    private final PboAudioSink audioSink;

    private ShaderProgram shader;
    private VertexArrayObject vao;

    private Uniform<Integer>  uStart;
    private Uniform<Integer>  uChannel;
    private Uniform<Float>    uRadius;
    private Uniform<Float>    uAspect;
    private Uniform<Vector2f> uCenter;
    private Uniform<Vector4f> uColour;
    private Uniform<Matrix4f> uTransform;

    private volatile float currentAspect = 1.0f;
    private volatile boolean clearBeforeRender = true;

    private static final String ACTION_LINE_WIDTH   = "lineWidth";
    private static final String ACTION_CHANNEL_MODE = "channelMode";
    private static final String ACTION_RADIUS       = "radius";
    private static final String ACTION_CENTER       = "center";
    private final RenderActionQueue renderActions = new RenderActionQueue(
            ACTION_LINE_WIDTH, ACTION_CHANNEL_MODE, ACTION_RADIUS, ACTION_CENTER);

    // Colour and transform are typically set every frame, so they bypass the action queue: each
    // call copies into a preallocated slot (no-op when unchanged), applied at the next frame.
    private final PendingValue<Vector4f> lineColour = new PendingValue<>(new Vector4f(1.0f), (src, dst) -> dst.set(src));
    private final PendingValue<Matrix4f> transform  = new PendingValue<>(new Matrix4f(), (src, dst) -> dst.set(src));
    private final Vector4f glLineColour = new Vector4f(1.0f); // GL thread only
    private final Matrix4f glTransform  = new Matrix4f();     // GL thread only

    // language=GLSL
    private static final String VERTEX_SHADER = """
            #version 330 core
            in vec2 direction;
            in float amplitude;
            uniform sampler1D uAudioTex;
            uniform int uStart;
            uniform int uChannel;
            uniform vec2 uCenter;
            uniform float uRadius;
            uniform float uAspect;
            uniform mat4 uTransform;

            void main() {
                int sampleIndex = (uStart + gl_VertexID) % textureSize(uAudioTex, 0);
                vec2 stereo = texelFetch(uAudioTex, sampleIndex, 0).rg;
                float sample = (uChannel == 0) ? (stereo.r + stereo.g) * 0.5
                             : (uChannel == 1) ? stereo.r : stereo.g;
                float r = uRadius + sample * amplitude;
                vec2 pos = uCenter + vec2(direction.x * r / uAspect, direction.y * r);
                gl_Position = uTransform * vec4(pos, 0.0, 1.0);
            }
        """;

    // language=GLSL
    private static final String FRAGMENT_SHADER = """
            #version 330 core
            uniform vec4 uColour;
            out vec4 fragColor;

            void main() {
                fragColor = uColour;
            }
        """;

    /**
     * Create a radial waveform visualiser that reads from the given audio sink.
     *
     * @param audioSink the shared audio sink; must have been created with at least
     *                  {@link #AUDIO_BUFFER_SIZE} frames via {@link PboAudioSink#create}
     * @throws IllegalArgumentException if the sink is smaller than {@link #AUDIO_BUFFER_SIZE}
     */
    public RadialWave(PboAudioSink audioSink) {
        this.audioSink = Objects.requireNonNull(audioSink);
        if (audioSink.getStereoFrames() < AUDIO_BUFFER_SIZE) {
            throw new IllegalArgumentException("Audio sink has " + audioSink.getStereoFrames()
                    + " frames; RadialWave needs at least AUDIO_BUFFER_SIZE = " + AUDIO_BUFFER_SIZE);
        }
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        initVbo(ctx);
        initShader(ctx);

        Rectangle win = ctx.getWindow();
        currentAspect = (float) win.width / win.height;
        ctx.addResizeListener((w, h) -> currentAspect = (float) w / h);

        glLineWidth(3.0f);
        ctx.setDesiredUpdateFrequency(60.0);
    }

    private void initVbo(RenderContext ctx) {
        this.vao = new VertexArrayObject();
        vao.setDrawMode(BufferDrawMode.LINE_LOOP);
        vao.init(ctx);
        var vbo = vao.createVbo(new VertexDataStructure(DIRECTION, AMPLITUDE), SAMPLE_COUNT);
        vbo.init(ctx);
        Vector2f scratch = new Vector2f();
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            float angle = (float) (2.0 * Math.PI * i / SAMPLE_COUNT);
            vbo.setElement(i, DIRECTION, scratch.set((float) Math.cos(angle), (float) Math.sin(angle)));
        }
        fillVboAmplitude(vbo);
        vbo.update(UpdateHint.STATIC);
    }

    private void fillVboAmplitude(VertexBufferObject vbo) {
        AmplitudeFunction fn = this.amplitudeFunction;
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            float x = (((float) i / SAMPLE_COUNT) * 2f) - 1f;
            vbo.setElement(i, AMPLITUDE, fn.amplitudeAt(i, x)); // float overload: no boxing
        }
    }

    private void rebuildVboAmplitude() {
        fillVboAmplitude(vao.getVbo());
        vao.getVbo().update(UpdateHint.DYNAMIC);
        amplitudeDirty = false;
    }

    private void initShader(RenderContext ctx) {
        this.shader = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_SHADER, RadialWave.class),
                ShaderSource.fromClass(FRAGMENT_SHADER, RadialWave.class),
                null);
        shader.use(ctx);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, audioSink.getTextureId());
        shader.uniforms().get("uAudioTex", Integer.class).set(0);
        this.uStart   = shader.uniforms().get("uStart",   Integer.class);
        this.uChannel = shader.uniforms().get("uChannel", Integer.class);
        this.uRadius  = shader.uniforms().get("uRadius",  Float.class);
        this.uAspect  = shader.uniforms().get("uAspect",  Float.class);
        this.uCenter    = shader.uniforms().get("uCenter",    Vector2f.class);
        this.uColour    = shader.uniforms().get("uColour",    Vector4f.class);
        this.uTransform = shader.uniforms().get("uTransform", Matrix4f.class);
        uChannel.set(CHANNEL_BLEND);
        uRadius.set(0.5f);
        uAspect.set(1.0f);
        uCenter.set(new Vector2f(0.0f, 0.0f));
        uColour.set(new Vector4f(1.0f));
        uTransform.set(new Matrix4f());
        vao.getVbo().setup(shader);
    }

    @Override
    public void doRender(RenderContext ctx) {
        if (amplitudeDirty) {
            rebuildVboAmplitude();
        }
        if (clearBeforeRender) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }
        shader.use(ctx);
        renderActions.processAll(ctx);
        if (lineColour.takeIfChanged(glLineColour)) {
            uColour.set(glLineColour);
        }
        if (transform.takeIfChanged(glTransform)) {
            uTransform.set(glTransform);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, audioSink.getTextureId());
        // Draw the newest SAMPLE_COUNT samples, ending at the uploaded head.
        uStart.set(Math.floorMod(audioSink.getHead() - SAMPLE_COUNT, audioSink.getStereoFrames()));
        uAspect.set(currentAspect);
        vao.bind(ctx);
        vao.doRender(ctx);
    }

    @Override
    public void dispose() {
        vao.dispose();
        shader.dispose();
    }

    /**
     * Set the GL line width used when drawing the waveform circle.
     * Enqueued as a render action so it takes effect at the start of the next frame.
     *
     * @param v line width in pixels; values &gt; 1.0 produce thicker strokes
     */
    public void setLineWidth(float v) {
        renderActions.enqueue(ACTION_LINE_WIDTH, ctx -> glLineWidth(v));
    }

    /**
     * Set the RGBA colour of the radial waveform line, applied at the start of the next frame. The
     * vector is copied so the caller may reuse it; cheap to call every frame (a no-op when unchanged).
     *
     * @param colour the desired line colour as an RGBA vector in [0, 1] per component
     */
    public void setLineColour(Vector4f colour) {
        lineColour.offer(colour);
    }

    /**
     * Select which channel(s) to visualise. Enqueued as a render action.
     *
     * @param mode one of {@link #CHANNEL_BLEND}, {@link #CHANNEL_LEFT}, or {@link #CHANNEL_RIGHT}
     */
    public void setChannelMode(int mode) {
        renderActions.enqueue(ACTION_CHANNEL_MODE, ctx -> uChannel.set(mode));
    }

    /**
     * Set the base radius of the circle in NDC units. Enqueued as a render action.
     *
     * @param r the base circle radius; 0.5 fills roughly half the shorter screen dimension
     */
    public void setRadius(float r) {
        renderActions.enqueue(ACTION_RADIUS, ctx -> uRadius.set(r));
    }

    /**
     * Set how amplitude varies around the circle.
     * The per-vertex value returned by {@code fn} is multiplied directly by the normalised audio
     * sample, so {@link AmplitudeFunction#constant(float) constant(1f)} gives full-scale radial
     * excursion — the same scale as {@link AudioWave} with the same function.
     * The new function takes effect on the next rendered frame. Passing a function equal to the
     * current one is a no-op, so this is cheap to call every frame.
     *
     * @param fn the new amplitude envelope; use {@link AmplitudeFunction#constant} for uniform
     *           excursion or {@link AmplitudeFunction#ellipse} to taper to zero at the seam
     */
    public void setAmplitudeFunction(AmplitudeFunction fn) {
        if (Objects.requireNonNull(fn).equals(this.amplitudeFunction)) {
            return; // unchanged: skip the VBO rebuild
        }
        this.amplitudeFunction = fn;
        this.amplitudeDirty = true;
    }

    /**
     * Returns the amplitude envelope currently applied during VBO construction.
     *
     * @return the current {@link AmplitudeFunction}; never {@code null}
     */
    public AmplitudeFunction getAmplitudeFunction() {
        return amplitudeFunction;
    }

    /**
     * Set the centre of the circle in NDC coordinates. Enqueued as a render action; the vector
     * is copied so the caller may reuse it after this call.
     *
     * @param center the circle centre in NDC ({@code (0,0)} is the screen centre)
     */
    public void setCenter(Vector2f center) {
        Vector2f copy = new Vector2f(center);
        renderActions.enqueue(ACTION_CENTER, ctx -> uCenter.set(copy));
    }

    /**
     * Set a transform matrix applied to the final NDC position of every vertex, from the start of
     * the next frame. The matrix is copied so the caller may reuse it; cheap to call every frame
     * (a no-op when unchanged, and no allocation when changed).
     *
     * <p>The identity matrix (default) leaves the waveform unchanged. Use JOML to compose
     * rotations, scales, and translations — for example, to spin the circle and pulse its size
     * on a beat:</p>
     * <pre>{@code
     * // each frame:
     * angle += 0.01f;
     * float pulse = 1.0f + 0.1f * beats.getBeatStrength(0);
     * radialWave.setTransform(new Matrix4f().rotateZ(angle).scale(pulse));
     * }</pre>
     *
     * <p>The transform is applied after the aspect-ratio correction, so a rotation matrix
     * produces a clean visual rotation of the already-round circle.</p>
     *
     * @param matrix the transform to apply; pass {@code new Matrix4f()} to reset to identity
     */
    public void setTransform(Matrix4f matrix) {
        transform.offer(matrix);
    }

    /**
     * Control whether the framebuffer is cleared before each frame.
     *
     * @param clear {@code true} to clear on each frame (default); {@code false} to skip and
     *              composite the radial wave over whatever was previously rendered
     */
    public void setClearBeforeRender(boolean clear) {
        this.clearBeforeRender = clear;
    }
}
