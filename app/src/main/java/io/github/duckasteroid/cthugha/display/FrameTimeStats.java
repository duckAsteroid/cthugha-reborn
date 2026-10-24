package io.github.duckasteroid.cthugha.display;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Frame-pacing diagnostics: records the wall-clock interval between successive frames and logs a
 * percentile summary once per reporting window. Average FPS hides stutter, so this reports the
 * tail (p95/p99/max) and a count of "janky" frames — those taking more than 1.5x the window's
 * median interval, i.e. frames the eye sees as a hitch.
 *
 * <p>Allocation-free in steady state: intervals go into a pre-sized {@code long[]} and are sorted
 * in place into a second pre-sized array at report time. Enabled via
 * {@code [display] frame_stats_interval} (seconds; 0 = off).
 */
public final class FrameTimeStats {

    private static final Logger LOG = LoggerFactory.getLogger(FrameTimeStats.class);
    /** Enough for a 30s window at 240Hz; frames past this in one window are counted but not sampled. */
    private static final int CAPACITY = 8192;
    private static final double JANK_FACTOR = 1.5;
    /** Individual frames slower than this are logged with a render-vs-swap breakdown. */
    private static final long SLOW_FRAME_NANOS = 100_000_000L;

    private final long intervalNanos;
    private final long[] samples = new long[CAPACITY];
    private final long[] sorted = new long[CAPACITY];
    private int count;
    private long lastFrame = -1;
    private long lastRenderEnd = -1;
    private long windowStart = -1;

    public FrameTimeStats(double intervalSeconds) {
        this.intervalNanos = (long) (intervalSeconds * 1e9);
    }

    /** Call once per frame, at the start of {@code render()}. */
    public void frame() {
        long now = System.nanoTime();
        if (lastFrame < 0) {
            lastFrame = now;
            windowStart = now;
            return;
        }
        long interval = now - lastFrame;
        if (count < CAPACITY) {
            samples[count++] = interval;
        }
        if (interval > SLOW_FRAME_NANOS && lastRenderEnd > lastFrame) {
            // render = our own render() work; outside = buffer swap (vsync/driver), event polling
            // and the render-core loop's own housekeeping between frames.
            LOG.info("slow frame {}ms: render={}ms outside={}ms", ms(interval),
                    ms(lastRenderEnd - lastFrame), ms(now - lastRenderEnd));
        }
        lastFrame = now;
        if (now - windowStart >= intervalNanos) {
            report(now - windowStart);
            count = 0;
            windowStart = now;
        }
    }

    /** Call at the end of {@code render()}, to split slow frames into render vs. swap time. */
    public void renderEnd() {
        lastRenderEnd = System.nanoTime();
    }

    private void report(long windowNanos) {
        if (count == 0) return;
        System.arraycopy(samples, 0, sorted, 0, count);
        Arrays.sort(sorted, 0, count);
        long sum = 0;
        for (int i = 0; i < count; i++) sum += sorted[i];
        long median = sorted[count / 2];
        int janky = 0;
        for (int i = 0; i < count; i++) {
            if (sorted[i] > median * JANK_FACTOR) janky++;
        }
        LOG.info("frames={} fps={} mean={}ms p50={}ms p95={}ms p99={}ms max={}ms janky={}",
                count,
                String.format("%.1f", count / (windowNanos / 1e9)),
                ms(sum / count), ms(median), ms(percentile(0.95)), ms(percentile(0.99)),
                ms(sorted[count - 1]), janky);
    }

    private long percentile(double p) {
        return sorted[Math.min(count - 1, (int) Math.ceil(p * count) - 1)];
    }

    private static String ms(long nanos) {
        return String.format("%.2f", nanos / 1e6);
    }
}
