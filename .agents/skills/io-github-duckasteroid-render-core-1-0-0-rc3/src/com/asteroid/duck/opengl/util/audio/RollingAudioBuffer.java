package com.asteroid.duck.opengl.util.audio;

import java.nio.FloatBuffer;

/**
 * A ring buffer backed by raw bytes that stores 16-bit little-endian stereo PCM audio.
 *
 * <p>Audio is written as raw bytes (directly from the capture thread) and read back as
 * normalised floats with channel selection. This defers the short-to-float conversion and
 * channel blending to read time, keeping the write path as cheap as possible.</p>
 *
 * <p>Thread-safe for a single writer and single reader: {@code writePos} is {@code volatile},
 * so the reader snapshots it once before reading and is guaranteed to see all bytes written
 * before that snapshot.</p>
 */
public class RollingAudioBuffer implements AudioSink {

    /**
     * Raw audio bytes: interleaved little-endian 16-bit stereo PCM.
     * Layout per stereo frame: [L_lo, L_hi, R_lo, R_hi].
     */
    private final byte[] data;

    /** Write position in bytes; always advances in multiples of 4 (one stereo frame). */
    private volatile int writePos;

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
        int pos = writePos;
        if (pos + length <= capacity) {
            System.arraycopy(chunk, offset, data, pos, length);
        } else {
            int firstPart = capacity - pos;
            System.arraycopy(chunk, offset, data, pos, firstPart);
            System.arraycopy(chunk, offset + firstPart, data, 0, length - firstPart);
        }
        writePos = (pos + length) % capacity; // volatile write after data is visible
    }

    /**
     * Read the most recent {@code count} mono samples into {@code dest[0..count-1]}, oldest first.
     *
     * @param dest  destination array; must have length >= count
     * @param count number of samples; clamped to buffer capacity if larger
     * @param mode  which channel(s) to use for the mono output
     */
    public void readSamples(float[] dest, int count, ChannelMode mode) {
        int pos = writePos; // volatile snapshot — establishes happens-before with write
        int capacity = data.length;
        int clamped = Math.min(count, capacity / 4);
        int startByte = ((pos - clamped * 4) % capacity + capacity) % capacity;

        for (int i = 0; i < clamped; i++) {
            short l = getShortAt(startByte + i * 4);
            short r = getShortAt(startByte + i * 4 + 2);
            dest[i] = toFloat(l, r, mode);
        }
    }

    /**
     * Read from this buffer into a float buffer as interleaved X,Y coordinate pairs.
     * X is normalised to [-1, 1] across the read extent; Y is the audio amplitude.
     *
     * @param dest the target buffer to fill with X,Y pairs
     * @param mode which channel(s) to use for Y
     */
    public void read(FloatBuffer dest, ChannelMode mode) {
        int pos = writePos; // volatile snapshot
        int capacity = data.length;
        int readExtent = Math.min(dest.limit() / 2, capacity / 4);
        int startByte = ((pos - readExtent * 4) % capacity + capacity) % capacity;

        for (int i = 0; i < readExtent && dest.remaining() >= 2; i++) {
            dest.put(normalise(i, readExtent));
            short l = getShortAt(startByte + i * 4);
            short r = getShortAt(startByte + i * 4 + 2);
            dest.put(toFloat(l, r, mode));
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
