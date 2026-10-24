package com.asteroid.duck.opengl.util.audio;

import com.asteroid.duck.opengl.util.stats.Stats;
import com.asteroid.duck.opengl.util.stats.StatsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.asteroid.duck.opengl.util.audio.LineAcquirer.IDEAL;

/**
 * Background thread that drains an {@link AudioLine} and fans the raw PCM bytes out to one
 * or more {@link AudioSink} instances.
 *
 * <p>The reader runs a tight poll loop: it calls {@link AudioLine#available()} and, when data
 * is ready, reads up to {@value #CHUNK_SIZE} bytes and forwards them to every registered sink.
 * The loop yields the thread when nothing is available so the audio source can refill its internal
 * buffer without busy-spinning at 100 % CPU.</p>
 *
 * <p>A source is injected at any time via {@link #setLine}, which opens and starts it. Replacing
 * the source atomically stops and closes the old line before opening and starting the new one, so
 * the reader always sees a running line once one is set. The thread blocks in
 * {@link #waitForLine()} until the first line arrives.</p>
 *
 * <h2>Thread safety</h2>
 * {@link #setLine} is synchronized on {@code this}; the loop thread waits on the same monitor.
 * {@link #running} is {@code volatile}.
 */
public class AudioReader implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(AudioReader.class);

    /**
     * Size of the {@link javax.sound.sampled.TargetDataLine} internal capture buffer in bytes.
     * Set to 128 stereo frames (≈ 2.7 ms at 48 kHz) — small enough to keep end-to-end latency
     * low while giving the OS enough room to avoid dropouts between reader iterations.
     */
    public static final int LINE_BUFFER_SIZE = 128 * 2 * 2; // 512 bytes

    /**
     * Number of bytes read from the line in a single iteration.
     * Set to 16 stereo frames (≈ 0.33 ms at 48 kHz). Keeping this small reduces the time
     * between audio capture and the data appearing in the sink, which matters for visualisers
     * that need to stay in sync with playback.
     */
    public static final int CHUNK_SIZE = 16 * 2 * 2; // 64 bytes

    private final Stats chunkSize = StatsFactory.stats("Audio: Chunk Size");
    private final Stats available = StatsFactory.stats("Audio: Available");
    private final byte[] audioChunk = new byte[CHUNK_SIZE];
    private final List<AudioSink> sinks;
    private AudioLine mLine = null;
    private volatile boolean running = true;

    /**
     * Create a reader that will fan captured audio to all given sinks.
     *
     * @param sinks the list of sinks to receive every PCM chunk; must not be {@code null} or empty
     */
    public AudioReader(List<AudioSink> sinks) {
        this.sinks = sinks;
    }

    /**
     * Returns {@code true} while the reader loop should continue running.
     * Setting this to {@code false} via {@link #setRunning} causes the loop to exit
     * cleanly after the current iteration.
     *
     * @return {@code true} if the loop is active
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Request the reader loop to stop ({@code false}) or resume ({@code true}).
     * The change takes effect at the start of the next loop iteration.
     *
     * @param running {@code false} to stop the loop after the current read, {@code true} to keep running
     */
    public void setRunning(boolean running) {
        this.running = running;
    }

    public void run() {
        AudioLine line = null;
        try {
            while (running) {
                line = waitForLine();
                int avail = line.available();
                if (avail <= 0) {
                    Thread.yield();
                    continue;
                }
                available.add(avail);
                int toRead = Math.min(avail, CHUNK_SIZE);
                int read = line.read(audioChunk, 0, toRead);
                chunkSize.add(read);

                for (AudioSink sink : sinks) {
                    sink.write(audioChunk, 0, read);
                }
            }
            if (line != null) {
                line.stop();
                line.close();
            }
        } catch (InterruptedException e) {
            LOG.error("Audio reader thread, stopping.", e);
        }
        LOG.debug("Audio reader stopped. chunk={}, available={}", chunkSize, available);
    }

    /**
     * Blocks the calling thread until a non-null {@link AudioLine} has been opened via
     * {@link #setLine}. Uses {@link Object#wait()} on {@code this}, so it releases the lock
     * while waiting and will not spin.
     *
     * @return the currently set line; never {@code null}
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    protected AudioLine waitForLine() throws InterruptedException {
        synchronized (this) {
            while (mLine == null) {
                LOG.debug("Waiting for audio line...");
                wait();
            }
            return mLine;
        }
    }

    /**
     * Replace the active audio source. If a previous line is open it is stopped and closed first.
     * The new source is opened with {@link #LINE_BUFFER_SIZE} and started before being made
     * active. Passing {@code null} suspends capture until a new source is provided.
     *
     * @param source the new audio data source to capture from, or {@code null} to pause capture
     */
    public void setLine(AudioDataSource source) {
        synchronized (this) {
            if (this.mLine != null) {
                this.mLine.stop();
                this.mLine.close();
                this.mLine = null;
            }
            if (source != null) {
                try {
                    AudioLine opened = source.open(IDEAL, LINE_BUFFER_SIZE);
                    opened.start();
                    this.mLine = opened;
                } catch (AudioSourceUnavailableException e) {
                    LOG.error("Line unavailable: {}", source.getName(), e);
                }
            }
            notifyAll();
        }
    }
}
