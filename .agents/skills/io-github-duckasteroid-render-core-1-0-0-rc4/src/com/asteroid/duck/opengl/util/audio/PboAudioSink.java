package com.asteroid.duck.opengl.util.audio;

import com.asteroid.duck.opengl.util.RenderContext;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL30.GL_RG;
import static org.lwjgl.opengl.GL30.glMapBufferRange;
import static org.lwjgl.opengl.GL31.GL_RG16_SNORM;
import static org.lwjgl.opengl.GL44.*;

/**
 * Captures raw PCM bytes from an {@link AudioReader} into a persistently-mapped PBO and owns
 * the 1-D {@code GL_RG16_SNORM} texture that the PBO feeds each frame.
 *
 * <h2>Usage</h2>
 * <ol>
 *   <li>Create via {@link #create} inside a {@code RenderedItem.init()} call (GL context required).</li>
 *   <li>Register with an {@link AudioReader} so the audio thread writes into it.</li>
 *   <li>Call {@link #upload()} once per frame on the GL thread before any visualiser that reads
 *       from the texture renders.</li>
 *   <li>Pass {@link #getTextureId()} and {@link #getHead()} (in stereo frames / texels) to
 *       visualisers that need them.</li>
 * </ol>
 *
 * <p>Disposal is registered with the {@link com.asteroid.duck.opengl.util.resources.manager.ResourceManager}
 * at creation time, so GL resources are cleaned up automatically at shutdown.</p>
 *
 * <h2>Thread safety</h2>
 * {@link #write} is called from the audio thread; {@link #upload} and {@link #getHead} are called
 * from the GL/render thread. The PBO is mapped with {@code GL_MAP_COHERENT_BIT}, so writes are
 * visible to the GPU without an explicit flush. {@code head} is {@code volatile}.
 */
public class PboAudioSink implements AudioSink {

    private final int pboId;
    private final int textureId;
    private final ByteBuffer mapped;
    private final int byteSize;
    private final int stereoFrames;
    private volatile int head;

    private PboAudioSink(int pboId, int textureId, ByteBuffer mapped, int stereoFrames) {
        this.pboId        = pboId;
        this.textureId    = textureId;
        this.mapped       = mapped;
        this.byteSize     = stereoFrames * 4; // 2 channels × 2 bytes per short
        this.stereoFrames = stereoFrames;
    }

    /**
     * Allocate a PBO and a 1-D {@code GL_RG16_SNORM} audio texture.
     *
     * @param stereoFrames number of stereo frames (= texture width in texels); typically
     *                     {@code SCREEN_WIDTH × 2} so the write head can lap the read head
     * @param ctx          render context used to register the dispose callback
     * @return the fully initialised sink
     */
    public static PboAudioSink create(int stereoFrames, RenderContext ctx) {
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_1D, textureId);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage1D(GL_TEXTURE_1D, 0, GL_RG16_SNORM, stereoFrames, 0, GL_RG, GL_SHORT, (ByteBuffer) null);
        glBindTexture(GL_TEXTURE_1D, 0);

        int pboId = glGenBuffers();
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboId);
        int byteSize = stereoFrames * 4;
        int flags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
        glBufferStorage(GL_PIXEL_UNPACK_BUFFER, byteSize, flags);
        ByteBuffer mapped = glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, byteSize, flags);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);

        PboAudioSink sink = new PboAudioSink(pboId, textureId, mapped, stereoFrames);
        ctx.getResourceManager().register(sink::dispose);
        return sink;
    }

    /**
     * Write raw PCM bytes into the PBO ring buffer.
     * Called on the audio thread; safe to call concurrently with {@link #upload} and {@link #getHead}.
     */
    @Override
    public void write(byte[] chunk, int offset, int length) {
        mapped.position(head);
        if (head + length > byteSize) {
            int firstPart = byteSize - head;
            mapped.put(chunk, offset, firstPart);
            mapped.position(0);
            mapped.put(chunk, offset + firstPart, length - firstPart);
            head = length - firstPart;
        } else {
            mapped.put(chunk, offset, length);
            head = (head + length) % byteSize;
        }
    }

    /**
     * Copy the PBO contents into the audio texture.
     * Must be called once per frame on the GL thread, before any visualiser draws from the texture.
     */
    public void upload() {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboId);
        glBindTexture(GL_TEXTURE_1D, textureId);
        glTexSubImage1D(GL_TEXTURE_1D, 0, 0, stereoFrames, GL_RG, GL_SHORT, 0L);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glBindTexture(GL_TEXTURE_1D, 0);
    }

    /**
     * Returns the GL handle for the 1-D {@code GL_RG16_SNORM} audio texture.
     * Pass to visualisers so they can bind the texture before sampling it in the vertex shader.
     *
     * @return the OpenGL texture object name; valid for the lifetime of this sink
     */
    public int getTextureId() {
        return textureId;
    }

    /**
     * Current write-head position in <em>stereo frames</em> (texels); pass to the
     * {@code uHead} shader uniform. Shaders sample the audio texture with
     * {@code texelFetch}, so the index must be in texel units, not bytes.
     *
     * @return the write-head position as a texel index in [0, stereoFrames)
     */
    public int getHead() {
        return head / 4;
    }

    /** Release the PBO and texture. Called automatically via the ResourceManager at shutdown. */
    public void dispose() {
        glDeleteBuffers(pboId);
        glDeleteTextures(textureId);
    }
}
