package com.asteroid.duck.opengl.util.audio;

/**
 * A handle onto an {@link AudioDataSource} that has been successfully opened via
 * {@link AudioDataSource#open}. Its existence is proof the source is open — there is no
 * separate {@code isOpen()} to check.
 */
public interface AudioLine extends AutoCloseable {
    /**
     * How many bytes are currently available to read.
     *
     * @return number of bytes available
     */
    int available();

    /**
     * Read raw audio data into the given array.
     *
     * @param array the array to read into
     * @param start start index in the array, the first byte read will go here
     * @param length maximum number of bytes to read
     * @return number of bytes actually read
     */
    int read(byte[] array, int start, int length);

    /**
     * Start (or resume) delivering data.
     */
    void start();

    /**
     * Pause delivery. The line remains open and can be {@link #start() restarted}.
     */
    void stop();

    /**
     * Returns {@code true} if the line has been {@linkplain #start() started} and is actively
     * delivering audio data.
     *
     * @return {@code true} if the line is currently capturing and delivering data
     */
    boolean isRunning();

    /**
     * Release the underlying resource. The line cannot be restarted after this — open a new one
     * via {@link AudioDataSource#open}.
     */
    @Override
    void close();
}
