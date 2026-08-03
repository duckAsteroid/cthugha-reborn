package com.asteroid.duck.opengl.util.wave;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.RenderedItem;
import com.asteroid.duck.opengl.util.Transformable;
import com.asteroid.duck.opengl.util.audio.PboAudioSink;
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

import java.io.IOException;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL11.GL_SHORT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_1D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * Real-time stereo audio waveform visualiser.
 *
 * <p>Reads from a shared {@link PboAudioSink}: the sink's audio texture is sampled in the vertex
 * shader to displace a horizontal strip of {@value #SCREEN_WIDTH} vertices vertically, producing
 * a scrolling waveform. The caller must invoke {@link PboAudioSink#upload()} exactly once per
 * frame before calling {@link #doRender}, so that multiple visualisers sharing the same sink
 * see identical audio data.</p>
 *
 * <h2>Buffer sizing</h2>
 * Pass {@link #AUDIO_BUFFER_SIZE} to {@link PboAudioSink#create} so the ring buffer is large
 * enough for the write head to lap the read head without a visible glitch.
 */
public class AudioWave implements RenderedItem, Transformable {

    /** Number of vertices drawn per frame — one per horizontal pixel at the target resolution. */
    public static final int SCREEN_WIDTH = 1024;

    /**
     * Required audio ring buffer size in stereo frames.
     * Double the screen width so the write head can lap the read head.
     * Pass this to {@link PboAudioSink#create}.
     */
    public static final int AUDIO_BUFFER_SIZE = SCREEN_WIDTH * 2;

    private static final VertexElement POSITION = new VertexElement(VertexElementType.VEC_2F, "position");

    /** Default amplitude: 1× maps the full normalised audio signal to the full screen height. */
    private AmplitudeFunction amplitudeFunction = AmplitudeFunction.constant(1f);
    private volatile boolean amplitudeDirty = false;

    private final PboAudioSink audioSink;

    private ShaderProgram shader;
    private VertexArrayObject vao;

    /** Visualise the L+R average as a single centred line. */
    public static final int CHANNEL_BLEND  = 0;
    /** Visualise the left channel only as a single centred line. */
    public static final int CHANNEL_LEFT   = 1;
    /** Visualise the right channel only as a single centred line. */
    public static final int CHANNEL_RIGHT  = 2;
    /** Visualise both channels simultaneously — left above centre, right below. */
    public static final int CHANNEL_STEREO = 3;

    private Uniform<Integer>  uHead;
    private Uniform<Integer>  uChannel;
    private Uniform<Float>    uYOffset;
    private Uniform<Vector4f> uColour;
    private Uniform<Matrix4f> uTransform;

    private static final String ACTION_LINE_WIDTH   = "lineWidth";
    private static final String ACTION_LINE_COLOUR  = "lineColour";
    private static final String ACTION_CHANNEL_MODE = "channelMode";
    private static final String ACTION_TRANSFORM    = "transform";
    private final RenderActionQueue renderActions = new RenderActionQueue(ACTION_LINE_WIDTH, ACTION_LINE_COLOUR, ACTION_CHANNEL_MODE, ACTION_TRANSFORM);

    private int channelMode = CHANNEL_BLEND;

    private volatile boolean clearBeforeRender = true;

    /**
     * Create a waveform visualiser that reads from the given audio sink.
     *
     * @param audioSink the shared audio sink whose texture and write-head position are read each
     *                  frame; must have been created with at least {@link #AUDIO_BUFFER_SIZE} frames
     */
    public AudioWave(PboAudioSink audioSink) {
        this.audioSink = Objects.requireNonNull(audioSink);
    }

    @Override
    public void init(RenderContext ctx) throws IOException {
        initVbo(ctx);
        initShader(ctx);
        glLineWidth(6.0f);
        ctx.setDesiredUpdateFrequency(60.0);
    }

    private void initVbo(RenderContext ctx) {
        this.vao = new VertexArrayObject();
        vao.setDrawMode(BufferDrawMode.LINE_STRIP);
        vao.init(ctx);
        VertexDataStructure dataStructure = new VertexDataStructure(POSITION);
        var vbo = vao.createVbo(dataStructure, SCREEN_WIDTH);
        vbo.init(ctx);
        fillVboAmplitude(vbo);
        vbo.update(UpdateHint.STATIC);
    }

    private void fillVboAmplitude(VertexBufferObject vbo) {
        AmplitudeFunction fn = this.amplitudeFunction;
        Vector2f scratch = new Vector2f();
        for (int i = 0; i < SCREEN_WIDTH; i++) {
            float x = (((float) i / SCREEN_WIDTH) * 2f) - 1f;
            vbo.setElement(i, POSITION, scratch.set(x, fn.amplitudeAt(i, x)));
        }
    }

    private void rebuildVboAmplitude() {
        fillVboAmplitude(vao.getVbo());
        vao.getVbo().update(UpdateHint.DYNAMIC);
        amplitudeDirty = false;
    }

    /**
     * Set how amplitude varies across the wave.
     * The new function takes effect on the next rendered frame.
     *
     * @param fn the new amplitude envelope; use {@link AmplitudeFunction#constant} for a flat
     *           waveform or {@link AmplitudeFunction#ellipse} to taper to zero at the edges
     */
    public void setAmplitudeFunction(AmplitudeFunction fn) {
        this.amplitudeFunction = Objects.requireNonNull(fn);
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

    // language=GLSL
    private static final String VERTEX_SHADER = """
            #version 330 core
            in vec2 position;
            uniform sampler1D uAudioTex;
            uniform int uHead;
            uniform int uChannel;
            uniform float uYOffset;
            uniform mat4 uTransform;

            void main() {
                int sampleIndex = (2048 + uHead + gl_VertexID) % 2048;
                vec2 stereo = texelFetch(uAudioTex, sampleIndex, 0).rg;
                float amplitude = position.y *
                    ((uChannel == 0) ? (stereo.r + stereo.g) * 0.5 :
                        (uChannel == 1) ? stereo.r : stereo.g);
                gl_Position = uTransform * vec4(position.x, amplitude + uYOffset, 0.0, 1.0);
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

    private void initShader(RenderContext ctx) {
        this.shader = ShaderProgram.compile(
                ShaderSource.fromClass(VERTEX_SHADER, AudioWave.class),
                ShaderSource.fromClass(FRAGMENT_SHADER, AudioWave.class),
                null);
        shader.use(ctx);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, audioSink.getTextureId());
        shader.uniforms().get("uAudioTex", Integer.class).set(0);
        this.uHead    = shader.uniforms().get("uHead",    Integer.class);
        this.uChannel = shader.uniforms().get("uChannel", Integer.class);
        this.uYOffset = shader.uniforms().get("uYOffset", Float.class);
        this.uColour    = shader.uniforms().get("uColour",    Vector4f.class);
        this.uTransform = shader.uniforms().get("uTransform", Matrix4f.class);
        uChannel.set(CHANNEL_BLEND);
        uYOffset.set(0.0f);
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
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_1D, audioSink.getTextureId());
        uHead.set(audioSink.getHead());

        vao.bind(ctx);
        if (channelMode == CHANNEL_STEREO) {
            uChannel.set(CHANNEL_LEFT);
            uYOffset.set(0.5f);
            vao.doRender(ctx);
            uChannel.set(CHANNEL_RIGHT);
            uYOffset.set(-0.5f);
            vao.doRender(ctx);
        } else {
            uChannel.set(channelMode);
            uYOffset.set(0.0f);
            vao.doRender(ctx);
        }
    }

    @Override
    public void dispose() {
        vao.dispose();
        shader.dispose();
    }

    /**
     * Set the GL line width used when drawing the waveform strip.
     * Enqueued as a render action so it takes effect at the start of the next frame.
     *
     * @param v line width in pixels; values &gt; 1.0 produce thicker strokes
     */
    public void setLineWidth(float v) {
        renderActions.enqueue(ACTION_LINE_WIDTH, ctx -> glLineWidth(v));
    }

    /**
     * Select which channel(s) to visualise. Enqueued as a render action.
     *
     * @param mode one of {@link #CHANNEL_BLEND}, {@link #CHANNEL_LEFT}, {@link #CHANNEL_RIGHT},
     *             or {@link #CHANNEL_STEREO}
     */
    public void setChannelMode(int mode) {
        renderActions.enqueue(ACTION_CHANNEL_MODE, ctx -> this.channelMode = mode);
    }

    /**
     * Set the RGBA colour of the waveform line. Enqueued as a render action; the supplied vector
     * is copied so the caller may reuse or modify it after this call.
     *
     * @param colour the desired line colour as an RGBA vector in [0, 1] per component
     */
    public void setLineColour(Vector4f colour) {
        Vector4f copy = new Vector4f(colour);
        renderActions.enqueue(ACTION_LINE_COLOUR, ctx -> uColour.set(copy));
    }

    /**
     * Set a transform matrix applied to the final NDC position of every vertex.
     * Enqueued as a render action; the matrix is copied so the caller may reuse it after this call.
     *
     * <p>The identity matrix (default) leaves the waveform unchanged. Use JOML to compose
     * rotations, scales, and translations — for example, to tilt the wave and shift it upward:</p>
     * <pre>{@code
     * audioWave.setTransform(new Matrix4f().rotateZ(0.1f).translate(0, 0.3f, 0));
     * }</pre>
     *
     * @param matrix the transform to apply; pass {@code new Matrix4f()} to reset to identity
     */
    public void setTransform(Matrix4f matrix) {
        Matrix4f copy = new Matrix4f(matrix);
        renderActions.enqueue(ACTION_TRANSFORM, ctx -> uTransform.set(copy));
    }

    /**
     * Control whether the framebuffer is cleared before each frame.
     * Set to {@code false} when compositing this waveform over another rendered layer
     * (e.g. a spectrum background), so the underlying content is preserved.
     *
     * @param clear {@code true} to call {@code glClear} at the start of each frame (default);
     *              {@code false} to skip the clear and draw on top of whatever is already in the buffer
     */
    public void setClearBeforeRender(boolean clear) {
        this.clearBeforeRender = clear;
    }
}
