package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.events.ResizeListener;
import com.asteroid.duck.opengl.util.geom.Rectangle;
import com.asteroid.duck.opengl.util.renderaction.RenderActionQueue;
import com.asteroid.duck.opengl.util.resources.shader.ShaderProgram;
import com.asteroid.duck.opengl.util.resources.shader.ShaderSource;
import com.asteroid.duck.opengl.util.resources.shader.Uniform;
import com.asteroid.duck.opengl.util.resources.texture.Filter;
import com.asteroid.duck.opengl.util.resources.texture.Texture;
import com.asteroid.duck.opengl.util.resources.texture.TextureUnit;
import com.asteroid.duck.opengl.util.resources.texture.Wrap;
import io.github.duckasteroid.cthugha.params.ContainerNode;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.UiHint;
import io.github.duckasteroid.cthugha.params.transform.TransformParams;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.EnumParameter;
import io.github.duckasteroid.cthugha.video.VideoEntry;
import io.github.duckasteroid.cthugha.video.VideoLibrary;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL14.GL_MAX;
import static org.lwjgl.opengl.GL14.GL_MIN;
import static org.lwjgl.opengl.GL14.glBlendEquation;

/**
 * Screen-overlay phase that plays a single, randomly-chosen video file on loop, alpha-blended
 * full-screen (stretch-to-fill) on top of the core visualisation. Decoding happens on a
 * background thread; the GL thread only ever does a raw {@code glTexSubImage2D} against a
 * texture allocated once, matching {@code CthughaWindow.rebuildTranslateMap()}'s pattern for
 * {@code translateMapTex}. Not part of {@link #indexedRender} — video is real RGBA colour, not
 * a palette index, so it belongs in the RGBA screen pass, not the R16 indexed buffer.
 *
 * <p>Playback rate is paced against each frame's PTS ({@link #speed}), not free-running decode
 * throughput.
 */
public class VideoPhase implements RenderPhase {

    private static final Logger LOG = LoggerFactory.getLogger(VideoPhase.class);

    static {
        // FFmpeg's native av_log writes straight to stderr, bypassing SLF4J/logback entirely —
        // by default it dumps a full container/stream info block on every grabber.start() call,
        // which repeats on every loop restart. This is a process-wide native setting (not
        // per-grabber), so set it once here rather than per-instance. AV_LOG_ERROR still lets
        // genuine decode errors through.
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);
    }

    public enum ColorMode { COLOR, GRAYSCALE, BLACK_WHITE }

    // GPU compositing mode against whatever's already in the screen framebuffer (the core
    // visualisation). Each mode pairs a glBlendFunc/glBlendEquation with a matching adjustment
    // to the colour the fragment shader outputs, so `alpha` keeps acting as an intensity dial
    // for every mode, not just NORMAL — see the `uBlendMode` branch in FRAG and the switch in
    // screenRender for the paired GL state. DARKEN/LIGHTEN use MIN/MAX equations, which ignore
    // glBlendFunc factors entirely (per the GL spec), so their alpha-fade is baked into the
    // shader output instead (mixing towards each equation's identity colour: white for MIN,
    // black for MAX).
    public enum BlendMode { NORMAL, ADD, MULTIPLY, SCREEN, DARKEN, LIGHTEN }

    public final BooleanParameter enabled = new BooleanParameter("Enabled", true);
    public final DoubleParameter alpha = new DoubleParameter("Alpha", 0.0, 1.0, 0.5);
    public final EnumParameter<BlendMode> blendMode =
            new EnumParameter<>("Blend Mode", Arrays.asList(BlendMode.values()));
    public final EnumParameter<ColorMode> colorMode =
            new EnumParameter<>("Color Mode", Arrays.asList(ColorMode.values()));
    public final BooleanParameter invert = new BooleanParameter("Invert", false);
    // 0 disables the vignette entirely (Darkness defaults to 0). Radius is the normalised
    // (screen-diagonal-independent) distance from centre where the darkening fade begins.
    public final DoubleParameter vignetteRadius = new DoubleParameter("Vignette Radius", 0.05, 1.5, 0.75);
    public final DoubleParameter vignetteDarkness = new DoubleParameter("Vignette Darkness", 0.0, 1.0, 0.0);
    // Same translate/scale/shear/rotate(+pivot)/perspective convention as QuotePhase's own
    // `transform` and each wave model's — see TransformParams.applyTo for the exact NDC-space
    // matrix built from these params.
    public final TransformParams transform = new TransformParams("Transform");
    // Playback rate multiplier, read live by the decode thread every frame (same unsynchronised
    // cross-thread read convention as `alpha`/`enabled` above — animatable via the standard
    // "Animate" binding since it's just a normal DoubleParameter leaf). 0 holds on the current
    // frame (pause); values above 1 fast-forward.
    public final DoubleParameter speed = new DoubleParameter("Speed", 0.0, 4.0, 1.0);
    // Independent of `speed` so a binding/trigger can pause/resume without disturbing whatever
    // rate was dialled in — resuming picks the current `speed` back up rather than a fixed value.
    public final BooleanParameter paused = new BooleanParameter("Paused", false);
    public final BooleanParameter loop = new BooleanParameter("Loop", true);

    private final VideoLibrary videoLibrary = new VideoLibrary(Paths.get("videos"));

    // Picked eagerly at construction (not in init()) because CthughaWindow calls
    // registerActions() — via ActionTreeBuilder.build() — BEFORE init(), so registerActions()
    // cannot gate on any GL resource (e.g. `shader`) that init() creates; it needs a signal
    // for "is there a video to play" that already exists at construction time. Also the source
    // of truth VideosLibraryNode reads to sync its picker's initial selection.
    private volatile VideoEntry currentEntry =
            videoLibrary.entries().isEmpty() ? null : videoLibrary.random();

    private RenderActionQueue renderActions;

    private ShaderProgram shader;
    private Rectangle quad;
    private TextureUnit texUnit;
    private Uniform<Float> uAlpha;
    private Uniform<Integer> uColorMode;
    private Uniform<Boolean> uInvert;
    private Uniform<Integer> uBlendMode;
    private Uniform<Float> uAspect;
    private Uniform<Float> uVigRadius;
    private Uniform<Float> uVigDarkness;
    private Uniform<Matrix4f> uTransform;
    private Texture videoTex;

    // Updated via a resize listener rather than queried per-frame; used only to keep the
    // vignette circular (not elliptical) on non-square windows.
    private int windowWidth = 1;
    private int windowHeight = 1;
    private final ResizeListener resizeListener = (w, h) -> {
        windowWidth = w;
        windowHeight = h;
    };

    private FFmpegFrameGrabber grabber;
    private Thread decodeThread;
    private volatile boolean running = false;

    private int videoWidth;
    private int videoHeight;
    private int videoStride;

    // Producer (decode thread) publishes the latest fully-copied frame here; consumer (GL
    // thread) does getAndSet(null) each screenRender() to consume-and-clear — same handoff
    // primitive QrOverlay already uses for its own pendingUrl.
    private final AtomicReference<ByteBuffer> pendingFrame = new AtomicReference<>();
    // Two alternating app-owned buffers the decode thread copies into, so publishing a frame
    // never allocates — sized lazily on the first successful grab, once videoStride is known.
    private ByteBuffer copyBufA;
    private ByteBuffer copyBufB;
    private boolean useA = true;

    // language=GLSL
    private static final String VERT = """
            #version 330 core
            uniform mat4 uTransform;
            in vec2 screenPosition;
            in vec2 texturePosition;
            out vec2 vTex;
            void main() {
                // Rectangle's UV mapping puts buffer row 0 at v=0 (screen bottom), but FFmpeg's
                // decoded frames are top-down (row 0 = top scanline) — flip to compensate.
                vTex = vec2(texturePosition.x, 1.0 - texturePosition.y);
                // Moves the quad's corners in NDC space (translate/rotate/scale/shear) — the UVs
                // above are untouched, so the video image itself isn't distorted, just placed.
                gl_Position = uTransform * vec4(screenPosition, 0.0, 1.0);
            }
            """;

    // language=GLSL
    private static final String FRAG = """
            #version 330 core
            uniform sampler2D uVideo;
            uniform float uAlpha;
            uniform int uColorMode; // 0 = colour, 1 = greyscale, 2 = black & white
            uniform bool uInvert;
            uniform int uBlendMode; // 0 NORMAL, 1 ADD, 2 MULTIPLY, 3 SCREEN, 4 DARKEN, 5 LIGHTEN
            uniform float uAspect;
            uniform float uVigRadius;
            uniform float uVigDarkness;
            in vec2 vTex;
            out vec4 fragColor;
            void main() {
                vec4 c = texture(uVideo, vTex);
                vec3 rgb = c.rgb;
                if (uColorMode == 1) {
                    float lum = dot(rgb, vec3(0.299, 0.587, 0.114));
                    rgb = vec3(lum);
                } else if (uColorMode == 2) {
                    float lum = dot(rgb, vec3(0.299, 0.587, 0.114));
                    rgb = vec3(step(0.5, lum));
                }
                if (uInvert) {
                    rgb = vec3(1.0) - rgb;
                }

                vec2 centred = (vTex - 0.5) * vec2(uAspect, 1.0);
                float vig = 1.0 - smoothstep(uVigRadius * 0.5, uVigRadius, length(centred));
                rgb *= mix(1.0, vig, uVigDarkness);

                // Fixed-function blending (glBlendFunc/glBlendEquation, set per-mode in
                // screenRender) does the actual compositing against the framebuffer; here we
                // only shape the colour handed to that blend stage so `uAlpha` still behaves as
                // an intensity dial under every mode, not just NORMAL.
                vec3 outColor;
                float outAlpha = c.a * uAlpha;
                if (uBlendMode == 1 || uBlendMode == 3) {
                    // ADD, SCREEN — additive-style blendFuncs read no src alpha factor.
                    outColor = rgb * outAlpha;
                } else if (uBlendMode == 2 || uBlendMode == 4) {
                    // MULTIPLY, DARKEN — fade towards white (the identity colour for both
                    // GL_DST_COLOR*GL_ZERO multiply and the MIN equation) as alpha drops.
                    outColor = mix(vec3(1.0), rgb, outAlpha);
                } else if (uBlendMode == 5) {
                    // LIGHTEN — fade towards black (the identity colour for the MAX equation).
                    outColor = mix(vec3(0.0), rgb, outAlpha);
                } else {
                    outColor = rgb;
                }
                fragColor = vec4(outColor, outAlpha);
            }
            """;

    @Override
    public void init(RenderContext ctx) throws IOException {
        if (currentEntry == null) {
            LOG.warn("No videos in manifest.json / videos/ dir — VideoPhase disabled");
            return;
        }
        LOG.info("Playing video overlay: {}", currentEntry.file());

        shader = ShaderProgram.compile(
                ShaderSource.fromClass(VERT, VideoPhase.class),
                ShaderSource.fromClass(FRAG, VideoPhase.class),
                null);
        shader.use(ctx);
        uAlpha = shader.uniforms().get("uAlpha", Float.class);
        uColorMode = shader.uniforms().get("uColorMode", Integer.class);
        uInvert = shader.uniforms().get("uInvert", Boolean.class);
        uBlendMode = shader.uniforms().get("uBlendMode", Integer.class);
        uAspect = shader.uniforms().get("uAspect", Float.class);
        uVigRadius = shader.uniforms().get("uVigRadius", Float.class);
        uVigDarkness = shader.uniforms().get("uVigDarkness", Float.class);
        uTransform = shader.uniforms().get("uTransform", Matrix4f.class);

        ctx.addResizeListener(resizeListener);
        java.awt.Rectangle win = ctx.getWindow();
        windowWidth = win.width;
        windowHeight = win.height;

        texUnit = ctx.getResourceManager().nextTextureUnit();
        texUnit.useInShader(shader, "uVideo");

        quad = new Rectangle(ctx, "screenPosition", "texturePosition");
        quad.getVertexArrayObject().bind(ctx);
        quad.getVertexBufferObject().setup(shader);

        videoTex = new Texture();
        videoTex.setInternalFormat(GL_RGBA);
        videoTex.setImageFormat(GL_RGBA);
        videoTex.setDataType(GL_UNSIGNED_BYTE);
        videoTex.setFilter(Filter.LINEAR);
        videoTex.setWrap(Wrap.CLAMP_TO_EDGE);

        grabber = newGrabber(currentEntry);
        try {
            grabber.start();
        } catch (FrameGrabber.Exception e) {
            throw new IOException("Failed to start video grabber for " + currentEntry.file(), e);
        }
        videoWidth = grabber.getImageWidth();
        videoHeight = grabber.getImageHeight();
        // GPU storage allocated once here on the GL thread; every later frame reuses it via
        // glTexSubImage2D (see screenRender), never reallocating.
        videoTex.generate(videoWidth, videoHeight, (ByteBuffer) null);

        running = true;
        decodeThread = Thread.ofVirtual()
                .uncaughtExceptionHandler(this::onDecodeThreadDied)
                .start(this::decodeLoop);
    }

    private void onDecodeThreadDied(Thread t, Throwable e) {
        LOG.error("Video decode thread died unexpectedly; video overlay stopped", e);
        running = false;
    }

    private FFmpegFrameGrabber newGrabber(VideoEntry entry) {
        FFmpegFrameGrabber g = new FFmpegFrameGrabber(videoLibrary.pathOf(entry).toFile());
        // Ask FFmpeg's internal swscale to hand back RGBA directly, so the Java side does zero
        // colour-space conversion — matches GL_RGBA/GL_UNSIGNED_BYTE exactly (byte order R,G,B,A).
        g.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
        return g;
    }

    /** The video currently playing (or about to start playing, right after {@link #loadVideo}). */
    public VideoEntry currentEntry() {
        return currentEntry;
    }

    public VideoLibrary videoLibrary() {
        return videoLibrary;
    }

    /**
     * Switches playback to {@code entry} live. Callable from any thread (mirrors
     * {@code FlashPhase.requestFlash}) — the blocking teardown of the old decode thread/grabber
     * happens here, off the GL thread, so switching videos never stalls the render loop; only
     * the (fast) texture reallocation is marshalled onto the GL thread via {@link #renderActions}.
     */
    public void loadVideo(VideoEntry entry) {
        if (entry == null || entry.equals(currentEntry)) return;

        stopPlayback();

        FFmpegFrameGrabber newGrabber = newGrabber(entry);
        try {
            newGrabber.start();
        } catch (FrameGrabber.Exception e) {
            LOG.error("Failed to start video grabber for {}", entry.file(), e);
            return;
        }
        int newWidth = newGrabber.getImageWidth();
        int newHeight = newGrabber.getImageHeight();
        grabber = newGrabber;
        currentEntry = entry;
        LOG.info("Loading video overlay: {}", entry.file());

        renderActions.enqueue("loadVideo", ctx -> {
            if (newWidth != videoWidth || newHeight != videoHeight) {
                videoWidth = newWidth;
                videoHeight = newHeight;
                videoTex.generate(videoWidth, videoHeight, (ByteBuffer) null);
            }
            // Sized lazily off the new stream's stride on its first decoded frame.
            copyBufA = null;
            copyBufB = null;

            running = true;
            decodeThread = Thread.ofVirtual()
                    .uncaughtExceptionHandler(this::onDecodeThreadDied)
                    .start(this::decodeLoop);
        });
    }

    /**
     * Runs entirely on decodeThread; only this thread ever calls grabber.grab*()/restart().
     * Paces itself against each frame's PTS (scaled by {@link #speed}) rather than decoding
     * flat-out, so playback runs at real speed by default and can be slowed/sped/paused live.
     */
    private void decodeLoop() {
        long lastPtsUs = -1;
        try {
            while (running) {
                double currentSpeed = speed.value;
                if (paused.value || currentSpeed <= 0.0) {
                    // Paused — hold the last displayed frame; don't advance or burn CPU.
                    if (!sleepMillis(50)) return;
                    continue;
                }

                Frame frame;
                try {
                    frame = grabber.grabImage();
                } catch (FrameGrabber.Exception e) {
                    LOG.warn("Error decoding video frame; restarting stream", e);
                    safeRestart();
                    lastPtsUs = -1;
                    continue;
                }
                if (frame == null) {
                    // End of stream.
                    if (loop.value) {
                        safeRestart();
                        lastPtsUs = -1;
                        continue;
                    }
                    // Hold the last displayed frame until looping is turned back on.
                    while (running && !loop.value) {
                        if (!sleepMillis(100)) return;
                    }
                    if (running) {
                        safeRestart();
                        lastPtsUs = -1;
                    }
                    continue;
                }
                if (frame.image == null) continue;

                // Pace this frame's display against wall-clock time, scaled by the current speed,
                // rather than showing it as soon as it's decoded — otherwise playback runs at
                // whatever rate the decoder happens to keep up with, not the video's own rate.
                long ptsUs = frame.timestamp;
                if (lastPtsUs >= 0) {
                    long deltaUs = ptsUs - lastPtsUs;
                    if (deltaUs > 0) {
                        long sleepUs = (long) (deltaUs / currentSpeed);
                        if (sleepUs > 0 && !sleepMillis(sleepUs / 1000, (int) (sleepUs % 1000) * 1000)) {
                            return;
                        }
                    }
                }
                lastPtsUs = ptsUs;

                ByteBuffer src = (ByteBuffer) frame.image[0];
                // The Buffer object is reused by the grabber across calls, so its position/limit
                // may be wherever the previous consumer (us) left them — always reset to the
                // full extent before reading, rather than trusting incoming position/limit.
                ((Buffer) src).clear();

                if (copyBufA == null) {
                    videoStride = frame.imageStride;
                    int cap = src.remaining();
                    copyBufA = BufferUtils.createByteBuffer(cap);
                    copyBufB = BufferUtils.createByteBuffer(cap);
                }
                ByteBuffer dst = useA ? copyBufA : copyBufB;
                useA = !useA;
                dst.clear();
                dst.put(src);
                dst.flip();
                pendingFrame.set(dst);
            }
        } catch (Throwable t) {
            LOG.error("Video decode loop terminated unexpectedly", t);
        }
    }

    private boolean sleepMillis(long millis) {
        return sleepMillis(millis, 0);
    }

    /** @return false if interrupted (caller should stop), true if the sleep completed normally. */
    private boolean sleepMillis(long millis, int nanos) {
        try {
            Thread.sleep(millis, nanos);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void safeRestart() {
        try {
            grabber.restart();
        } catch (FrameGrabber.Exception e) {
            LOG.error("Failed to restart video grabber; stopping decode thread", e);
            running = false;
        }
    }

    @Override
    public void screenRender(RenderContext ctx) {
        if (shader == null || !enabled.value) return;

        ByteBuffer frame = pendingFrame.getAndSet(null);
        if (frame != null) {
            texUnit.bind(videoTex);
            // Row length may exceed the frame width in pixels (swscale alignment padding);
            // tell GL the true row stride so it doesn't read the padding bytes as pixel data.
            glPixelStorei(GL_UNPACK_ROW_LENGTH, videoStride / 4);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, videoWidth, videoHeight,
                    GL_RGBA, GL_UNSIGNED_BYTE, frame);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        }
        // No new frame ready yet (decode thread hasn't kept up) — just redraw the last upload;
        // this is also how playback naturally throttles to whatever the decoder can sustain.

        shader.use(ctx);
        texUnit.bind(videoTex);
        uAlpha.set((float) alpha.value);
        uColorMode.set(colorMode.getEnumeration().ordinal());
        uInvert.set(invert.value);
        uBlendMode.set(blendMode.getEnumeration().ordinal());
        uAspect.set(windowHeight == 0 ? 1.0f : (float) windowWidth / windowHeight);
        uVigRadius.set((float) vignetteRadius.value);
        uVigDarkness.set((float) vignetteDarkness.value);
        uTransform.set(transform.applyTo(new Matrix4f()));

        glEnable(GL_BLEND);
        applyBlendMode(blendMode.getEnumeration());
        quad.getVertexArrayObject().bind(ctx);
        quad.render(ctx);
        // Reset to the GL default so later screen-pass phases aren't left with a MIN/MAX
        // equation or a non-standard blendFunc from whichever mode was active this frame.
        glBlendEquation(GL_FUNC_ADD);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_BLEND);
    }

    private static void applyBlendMode(BlendMode mode) {
        switch (mode) {
            case NORMAL -> {
                glBlendEquation(GL_FUNC_ADD);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            }
            case ADD -> {
                glBlendEquation(GL_FUNC_ADD);
                glBlendFunc(GL_ONE, GL_ONE);
            }
            case MULTIPLY -> {
                glBlendEquation(GL_FUNC_ADD);
                glBlendFunc(GL_DST_COLOR, GL_ZERO);
            }
            case SCREEN -> {
                glBlendEquation(GL_FUNC_ADD);
                glBlendFunc(GL_ONE_MINUS_DST_COLOR, GL_ONE);
            }
            case DARKEN -> {
                // MIN/MAX equations ignore glBlendFunc factors entirely (GL spec) — the
                // alpha-fade for these two modes is baked into the shader's output colour
                // instead (see FRAG).
                glBlendEquation(GL_MIN);
                glBlendFunc(GL_ONE, GL_ONE);
            }
            case LIGHTEN -> {
                glBlendEquation(GL_MAX);
                glBlendFunc(GL_ONE, GL_ONE);
            }
        }
    }

    @Override
    public void registerActions(ParamNode generalGroup, RenderActionQueue renderActions) {
        this.renderActions = renderActions;
        if (currentEntry == null) return;
        // Named "Playback", not "Video" — the picker's EnumParameter is already named "Video"
        // (VideosLibraryNode) and ParamNode's children list allows duplicate names, which broke
        // path resolution (Videos/Video/... always found the picker, never this group).
        ContainerNode videoGroup = new ContainerNode("Playback");
        videoGroup.withDescription("Alpha-blended full-screen video overlay, playing on loop for the whole session.");
        // Mirrors the "Video" picker's current thumbnail (see VideosLibraryNode) so the
        // currently-loaded video is visible from this panel too, without switching tabs.
        videoGroup.withUiHint(UiHint.PREVIEW_OF, "Video");
        // Rendered as a play/pause overlay on the preview thumbnail instead of its own row —
        // see UiHint.PAUSE_CONTROL. `paused` stays a normal, addressable, serialized leaf.
        videoGroup.withUiHint(UiHint.PAUSE_CONTROL, "Paused");
        paused.withUiHint(UiHint.HIDDEN, "true");
        videoGroup.addChild(enabled);
        videoGroup.addChild(alpha);
        videoGroup.addChild(blendMode);
        videoGroup.addChild(colorMode);
        videoGroup.addChild(invert);
        videoGroup.addChild(vignetteRadius);
        videoGroup.addChild(vignetteDarkness);
        videoGroup.addChild(transform);
        videoGroup.addChild(speed);
        videoGroup.addChild(paused);
        videoGroup.addChild(loop);
        // Inserted first so playback controls render above the picker grid.
        generalGroup.addChildFirst(videoGroup);
    }

    /** Stops the current decode thread and grabber, if any. Safe to call off the GL thread. */
    private void stopPlayback() {
        running = false;
        if (decodeThread != null) {
            decodeThread.interrupt();
            try {
                decodeThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (FrameGrabber.Exception e) {
                LOG.warn("Error closing video grabber", e);
            }
        }
    }

    @Override
    public void dispose() {
        stopPlayback();
        if (quad != null) quad.destroy();
        if (shader != null) shader.dispose();
        if (videoTex != null) videoTex.dispose();
        if (texUnit != null) texUnit.dispose();
    }
}
