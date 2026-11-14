package com.asteroid.duck.opengl.util.audio.beat;

import com.asteroid.duck.opengl.util.audio.AudioSink;

import java.time.Duration;

/**
 * Live beat tracking: an {@link AudioSink} that runs a {@link BeatTracker} on the audio thread
 * and lets the render thread ask where the beat is <em>now</em>.
 *
 * <p>The audio thread publishes an immutable snapshot (tempo, last beat, period, last kick, and
 * the sample-clock time it was taken at) through a volatile field — on every beat, kick and
 * tempo update, and at least every {@value #PUBLISH_INTERVAL_SECONDS} s — so it allocates only
 * a few small objects a second. The render thread extrapolates from the snapshot to the current
 * moment using {@link System#nanoTime()}, so the beat phase advances smoothly every frame between
 * snapshots, and keeps ticking at the last tempo if the audio stops.</p>
 *
 * <p>{@link #setLatency} shifts what "now" means: a positive latency makes the visuals lead the
 * analysis by that much, to make up for audio output latency (the sound reaching the listener
 * later than the samples reach this sink) and display latency.</p>
 *
 * <pre>{@code
 * BeatTrackerSink beats = new BeatTrackerSink(48_000f);
 * AudioReader reader = new AudioReader(List.of(frequencyProcessor, beats));
 * // each frame:
 * float pulse = (float) Math.exp(-6 * beats.beatPhase()) * beats.confidence();
 * float kick  = beats.kickStrength();
 * }</pre>
 */
public final class BeatTrackerSink implements AudioSink {

    /** Time constant of the {@link #kickStrength()} envelope, seconds. */
    public static final double KICK_DECAY_SECONDS = 0.15;
    static final double PUBLISH_INTERVAL_SECONDS = 0.1;
    private static final int BLOCK = 512;

    /** Everything the render thread needs, as of one moment on the sample clock. */
    private record Snapshot(double sampleTime, long nanos, double bpm, float confidence,
                            double lastBeat, double period, long beatCount,
                            double lastKick, float kickStrength) {}

    private final float sampleRate;
    private final BeatTracker tracker;
    private final float[] mono = new float[BLOCK];
    private volatile Snapshot snapshot;
    private volatile double latencySeconds;

    // Audio-thread state
    private long samples;
    private double lastPublished = Double.NEGATIVE_INFINITY;
    private boolean changed;
    private double bpm = Double.NaN;
    private float confidence;
    private double lastBeat = Double.NaN;
    private long beatCount;
    private double lastKick = Double.NaN;
    private float kickStrength;

    /**
     * @param sampleRate sample rate of the PCM written to this sink, in Hz
     */
    public BeatTrackerSink(float sampleRate) {
        this.sampleRate = sampleRate;
        this.tracker = new BeatTracker(sampleRate, new BeatListener() {
            @Override
            public void onBeat(double time) {
                lastBeat = time;
                beatCount++;
                changed = true;
            }

            @Override
            public void onKick(double time, float strength) {
                lastKick = time;
                kickStrength = strength;
                changed = true;
            }

            @Override
            public void onTempo(double time, double estimate, float estimateConfidence) {
                bpm = estimate;
                confidence = estimateConfidence;
                changed = true;
            }
        });
        this.snapshot = new Snapshot(0, System.nanoTime(), Double.NaN, 0, Double.NaN, Double.NaN, 0, Double.NaN, 0);
    }

    /**
     * Audio thread: decode interleaved 16-bit little-endian stereo to mono and track it.
     */
    @Override
    public void write(byte[] data, int offset, int length) {
        int frames = length / 4;
        int i = offset;
        for (int done = 0; done < frames; ) {
            int n = Math.min(BLOCK, frames - done);
            for (int f = 0; f < n; f++, i += 4) {
                int left = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
                int right = (short) ((data[i + 2] & 0xFF) | (data[i + 3] << 8));
                mono[f] = (left + right) / 65536f;
            }
            tracker.process(mono, 0, n);
            samples += n;
            done += n;
        }
        double now = samples / (double) sampleRate;
        if (changed || now - lastPublished >= PUBLISH_INTERVAL_SECONDS) {
            snapshot = new Snapshot(now, System.nanoTime(), bpm, confidence, lastBeat, tracker.period(),
                    beatCount, lastKick, kickStrength);
            lastPublished = now;
            changed = false;
        }
    }

    /**
     * Shift "now" forward (positive) or back to line visuals up with what the listener hears.
     * @param latency how far the visuals should lead the analysis
     */
    public void setLatency(Duration latency) {
        this.latencySeconds = latency.toNanos() / 1e9;
    }

    /** @return the latency set by {@link #setLatency} (zero by default) */
    public Duration latency() {
        return Duration.ofNanos(Math.round(latencySeconds * 1e9));
    }

    /** @return the current tempo estimate in BPM, or {@code NaN} until there is one */
    public double bpm() {
        return snapshot.bpm();
    }

    /** @return confidence in the tempo, {@code [0, 1]}; 0 until there is a tempo */
    public float confidence() {
        return snapshot.confidence();
    }

    /**
     * Where the current moment falls within the beat: 0 on the beat, rising to 1 just before
     * the next. A pulse that hits exactly on the beat is {@code exp(-k * beatPhase())}.
     * @return phase in {@code [0, 1)}; 0 until the first beat
     */
    public double beatPhase() {
        Snapshot s = snapshot;
        if (Double.isNaN(s.lastBeat()) || !(s.period() > 0)) return 0;
        double beats = (now(s) - s.lastBeat()) / s.period();
        return beats - Math.floor(beats);
    }

    /**
     * Beats so far, extrapolated to now. {@code beatCount() % 4} cycles through a bar, though
     * which beat is the downbeat isn't known yet.
     * @return the number of beats
     */
    public long beatCount() {
        Snapshot s = snapshot;
        if (Double.isNaN(s.lastBeat()) || !(s.period() > 0)) return s.beatCount();
        return s.beatCount() + Math.max(0, (long) Math.floor((now(s) - s.lastBeat()) / s.period()));
    }

    /**
     * The last kick's strength, decaying with a {@value #KICK_DECAY_SECONDS} s time constant.
     * @return envelope in {@code [0, 1]}
     */
    public float kickStrength() {
        Snapshot s = snapshot;
        if (Double.isNaN(s.lastKick())) return 0;
        double since = Math.max(0, now(s) - s.lastKick());
        return (float) (s.kickStrength() * Math.exp(-since / KICK_DECAY_SECONDS));
    }

    private double now(Snapshot s) {
        return s.sampleTime() + (System.nanoTime() - s.nanos()) / 1e9 + latencySeconds;
    }
}
