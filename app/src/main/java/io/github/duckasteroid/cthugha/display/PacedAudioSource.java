package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.audio.AudioDataSource;
import com.asteroid.duck.opengl.util.audio.AudioLine;
import com.asteroid.duck.opengl.util.audio.AudioSourceUnavailableException;

import javax.sound.sampled.AudioFormat;
import java.util.concurrent.locks.LockSupport;

/**
 * Wraps an {@link AudioDataSource} so that its opened line's {@link AudioLine#available()} parks
 * briefly instead of returning 0 immediately when no audio is buffered.
 *
 * <p>render-core's {@code AudioReader.run()} polls {@code available()} in a loop and calls
 * {@code Thread.yield()} when it's 0 — effectively a busy-spin. That burns a full core, and on a
 * virtual thread every yield also resubmits the continuation to the ForkJoin scheduler (allocating
 * a task each time and waking idle carriers), which profiled at ~20MB/s of garbage plus several
 * cores of scheduler churn competing with the render thread. Parking here bounds the loop to
 * roughly {@code 1 / PARK_NANOS} wake-ups per second while idle.
 *
 * <p>{@link #PARK_NANOS} must stay well under the capture buffer's duration
 * ({@code AudioReader.LINE_BUFFER_SIZE}, ~2.7ms at 48kHz stereo 16-bit) so the line can't overrun
 * between polls.
 */
final class PacedAudioSource implements AudioDataSource {

    static final long PARK_NANOS = 500_000L;

    private final AudioDataSource delegate;

    PacedAudioSource(AudioDataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public AudioFormat[] getSupportedFormats() {
        return delegate.getSupportedFormats();
    }

    @Override
    public AudioLine open(AudioFormat format, int bufferSize) throws AudioSourceUnavailableException {
        return new PacedLine(delegate.open(format, bufferSize));
    }

    private record PacedLine(AudioLine line) implements AudioLine {
        @Override
        public int available() {
            int avail = line.available();
            if (avail > 0) return avail;
            LockSupport.parkNanos(PARK_NANOS);
            return line.available();
        }

        @Override public int read(byte[] array, int start, int length) { return line.read(array, start, length); }
        @Override public void start() { line.start(); }
        @Override public void stop() { line.stop(); }
        @Override public boolean isRunning() { return line.isRunning(); }
        @Override public void close() { line.close(); }
    }
}
