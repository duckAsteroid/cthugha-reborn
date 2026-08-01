package io.github.duckasteroid.cthugha.display.phase;

import com.asteroid.duck.opengl.util.RenderContext;
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
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.video.VideoEntry;
import io.github.duckasteroid.cthugha.video.VideoLibrary;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL11.*;

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

    public final BooleanParameter enabled = new BooleanParameter("Enabled", true);
    public final DoubleParameter alpha = new DoubleParameter("Alpha", 0.0, 1.0, 0.5);
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
    private Texture videoTex;

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
            in vec2 screenPosition;
            in vec2 texturePosition;
            out vec2 vTex;
            void main() {
                // Rectangle's UV mapping puts buffer row 0 at v=0 (screen bottom), but FFmpeg's
                // decoded frames are top-down (row 0 = top scanline) — flip to compensate.
                vTex = vec2(texturePosition.x, 1.0 - texturePosition.y);
                gl_Position = vec4(screenPosition, 0.0, 1.0);
            }
            """;

    // language=GLSL
    private static final String FRAG = """
            #version 330 core
            uniform sampler2D uVideo;
            uniform float uAlpha;
            in vec2 vTex;
            out vec4 fragColor;
            void main() {
                vec4 c = texture(uVideo, vTex);
                fragColor = vec4(c.rgb, c.a * uAlpha);
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

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        quad.getVertexArrayObject().bind(ctx);
        quad.render(ctx);
        glDisable(GL_BLEND);
    }

    @Override
    public void registerActions(ParamNode generalGroup, RenderActionQueue renderActions) {
        this.renderActions = renderActions;
        if (currentEntry == null) return;
        ContainerNode videoGroup = new ContainerNode("Video");
        videoGroup.withDescription("Alpha-blended full-screen video overlay, playing on loop for the whole session.");
        videoGroup.addChild(enabled);
        videoGroup.addChild(alpha);
        videoGroup.addChild(speed);
        videoGroup.addChild(paused);
        videoGroup.addChild(loop);
        generalGroup.addChild(videoGroup);
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
