package com.asteroid.duck.opengl.util.audio;

import java.lang.invoke.VarHandle;
import java.nio.FloatBuffer;

/**
 * A ring buffer backed by raw bytes that stores 16-bit little-endian stereo PCM audio.
 *
 * <p>Audio is written as raw bytes (directly from the capture thread) and read back as
 * normalised floats with channel selection. This defers the short-to-float conversion and
 * channel blending to read time, keeping the write path as cheap as possible.</p>
 *
 * <h2>Thread safety</h2>
 * Safe for a single writer and a single reader, without locks, using a seqlock-style protocol
 * over two monotonically increasing byte counters:
 * <ul>
 *   <li>{@code claimed} is published by the writer <em>before</em> it copies a chunk, so it marks
 *       the furthest byte the writer may be touching;</li>
 *   <li>{@code written} is published <em>after</em> the copy, so every byte before it is
 *       complete.</li>
 * </ul>
 * The reader snapshots {@code written}, reads the window ending there, then re-reads
 * {@code claimed}: any byte older than {@code claimed - capacity} may have been overwritten
 * by a writer that lapped the reader mid-read. Those samples are re-read (up to
 * {@value #MAX_READ_ATTEMPTS} attempts) and, failing that, replaced with the oldest
 * known-good sample, so a reader never sees a torn or out-of-order sample (issue #27).
 */
public class RollingAudioBuffer implements AudioSink {

    /** Read attempts before tainted samples are held at the oldest known-good value. */
    static final int MAX_READ_ATTEMPTS = 3;

    /**
     * Raw audio bytes: interleaved little-endian 16-bit stereo PCM.
     * Layout per stereo frame: [L_lo, L_hi, R_lo, R_hi].
     */
    private final byte[] data;

    /** Total bytes the writer has started writing (published before the copy). */
    private volatile long claimed;

    /** Total bytes the writer has finished writing (published after the copy). */
    private volatile long written;

    /** Reader-owned scratch for {@link #read(FloatBuffer, ChannelMode)}; grown on demand. */
    private float[] scratch = new float[0];

    /**
     * Create a ring buffer large enough to hold the given number of stereo frames.
     *
     * @param stereoFrameCapacity number of stereo frames this buffer can hold;
     *                            should be at least 4× the FFT window size to give the reader
     *                            enough history to work with without the writer overwriting it
     */
    public RollingAudioBuffer(int stereoFrameCapacity) {
        this.data = new byte[stereoFrameCapacity * 4]; // 4 bytes per stereo frame
    }

    /**
     * Write raw PCM bytes into the ring buffer.
     * Uses {@link System#arraycopy} for bulk throughput; handles wrap-around with two copies.
     */
    @Override
    public void write(byte[] chunk, int offset, int length) {
        int capacity = data.length;
        int dropped = Math.max(0, length - capacity); // only the last capacity bytes survive
        offset += dropped;
        length -= dropped;
        long start = written + dropped;
        claimed = start + length;
        VarHandle.storeStoreFence(); // claim is visible before any data byte changes

        int pos = (int) (start % capacity);
        if (pos + length <= capacity) {
            System.arraycopy(chunk, offset, data, pos, length);
        } else {
            int firstPart = capacity - pos;
            System.arraycopy(chunk, offset, data, pos, firstPart);
            System.arraycopy(chunk, offset + firstPart, data, 0, length - firstPart);
        }
        written = start + length; // volatile write publishes the data
    }

    /**
     * Read the most recent {@code count} mono samples into {@code dest[0..count-1]}, oldest first.
     *
     * @param dest  destination array; must have length >= count
     * @param count number of samples; clamped to buffer capacity if larger
     * @param mode  which channel(s) to use for the mono output
     */
    public void readSamples(float[] dest, int count, ChannelMode mode) {
        readConsistent(dest, Math.min(count, data.length / 4), mode);
    }

    /**
     * Read from this buffer into a float buffer as interleaved X,Y coordinate pairs.
     * X is normalised to [-1, 1] across the read extent; Y is the audio amplitude.
     *
     * @param dest the target buffer to fill with X,Y pairs
     * @param mode which channel(s) to use for Y
     */
    public void read(FloatBuffer dest, ChannelMode mode) {
        int readExtent = Math.min(Math.min(dest.limit(), dest.remaining()) / 2, data.length / 4);
        if (scratch.length < readExtent) {
            scratch = new float[readExtent];
        }
        readConsistent(scratch, readExtent, mode);
        for (int i = 0; i < readExtent; i++) {
            dest.put(normalise(i, readExtent));
            dest.put(scratch[i]);
        }
    }

    /**
     * Fill {@code dest[0..frames-1]} with the newest {@code frames} samples, guaranteeing none of
     * them was overwritten by the writer while being read.
     */
    private void readConsistent(float[] dest, int frames, ChannelMode mode) {
        int capacity = data.length;
        for (int attempt = 1; ; attempt++) {
            long end = written; // acquire: every byte before end is complete
            long windowStart = end - frames * 4L;
            // Frames before the first byte ever written read as silence.
            int firstWritten = windowStart < 0 ? (int) Math.min(frames, (-windowStart + 3) / 4) : 0;
            for (int i = 0; i < firstWritten; i++) {
                dest[i] = 0f;
            }
            for (int i = firstWritten; i < frames; i++) {
                int pos = (int) ((windowStart + i * 4L) % capacity);
                dest[i] = toFloat(getShortAt(pos), getShortAt((pos + 2) % capacity), mode);
            }
            VarHandle.acquireFence(); // data reads complete before re-checking the writer
            long overwrittenBefore = claimed - capacity;
            if (overwrittenBefore <= windowStart) {
                return; // writer never reached our window
            }
            int firstGood = (int) Math.min(frames, (overwrittenBefore - windowStart + 3) / 4);
            if (attempt == MAX_READ_ATTEMPTS) {
                // Writer keeps lapping us: hold the oldest good sample rather than show a spike.
                float hold = firstGood < frames ? dest[firstGood] : 0f;
                for (int i = 0; i < firstGood; i++) {
                    dest[i] = hold;
                }
                return;
            }
        }
    }

    /** Read a little-endian 16-bit short from {@code bytePos}, wrapping at buffer capacity. */
    private short getShortAt(int bytePos) {
        int capacity = data.length;
        int lo = bytePos % capacity;
        int hi = (bytePos + 1) % capacity;
        return (short) ((data[lo] & 0xFF) | ((data[hi] & 0xFF) << 8));
    }

    private static float toFloat(short l, short r, ChannelMode mode) {
        return switch (mode) {
            case LEFT       -> l / (float) Short.MAX_VALUE;
            case RIGHT      -> r / (float) Short.MAX_VALUE;
            case MONO_BLEND -> (l + r) / (2.0f * Short.MAX_VALUE);
        };
    }

    /** Maps {@code x} in [0, size] to [-1, +1]. */
    static float normalise(float x, float size) {
        return (x / (size / 2.0f)) - 1;
    }
}
