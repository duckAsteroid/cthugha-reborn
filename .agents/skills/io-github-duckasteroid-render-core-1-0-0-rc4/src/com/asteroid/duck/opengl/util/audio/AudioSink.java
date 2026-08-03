package com.asteroid.duck.opengl.util.audio;

/**
 * Receives raw interleaved 16-bit stereo PCM bytes from a capture source.
 *
 * <p>Implementations must be callable from a background audio thread while the GL render
 * thread may be reading from the same underlying store. See {@link PboAudioSink} and
 * {@link com.asteroid.duck.opengl.util.audio.RollingAudioBuffer} for the two concrete
 * strategies (GPU-mapped PBO vs. heap ring buffer).</p>
 */
public interface AudioSink {
    /**
     * Write a chunk of raw PCM bytes into this sink's ring buffer.
     *
     * <p>The data is interleaved little-endian 16-bit stereo: four bytes per stereo frame
     * in the order {@code [L_lo, L_hi, R_lo, R_hi]}. The length is always a multiple of 4.</p>
     *
     * @param data   buffer containing the PCM bytes
     * @param offset index of the first byte to consume within {@code data}
     * @param length number of bytes to consume; always a multiple of 4 (one stereo frame = 4 bytes)
     */
    void write(byte[] data, int offset, int length);
}
