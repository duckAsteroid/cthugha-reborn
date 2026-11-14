package io.github.duckasteroid.cthugha.display;

import com.asteroid.duck.opengl.util.audio.beat.BeatTrackerSink;

import java.util.function.Supplier;

/**
 * The app's view of the live tempo: render-core's {@link BeatTrackerSink} (tempo, predicted beat
 * phase, beat count, kick drum), with the user's {@link TempoScale} correction applied. The
 * tracker locks at double or half the felt tempo on roughly one non-dance track in five, and
 * {@link TempoScale} lets the user put it right without retuning anything.
 *
 * <p>Every getter is safe on any thread and extrapolates to the current moment, so the phase
 * advances smoothly every frame.</p>
 */
public final class BeatClock {

    /** What the tracker reports, before the tempo scale is applied. */
    public interface Source {
        /** Tempo in BPM; {@code NaN} until there is an estimate. */
        double bpm();
        /** Confidence in the tempo, {@code [0, 1]}. */
        float confidence();
        /** {@code [0, 1)}: 0 on the beat, rising to 1 just before the next. */
        double beatPhase();
        /** Beats so far. */
        long beatCount();
        /** Last kick's strength, decaying, {@code [0, 1]}. */
        float kickStrength();
    }

    /** Correction for a tracker that has locked at the wrong tempo octave. */
    public enum TempoScale {
        HALF("Half"), NORMAL("Normal"), DOUBLE("Double");

        private final String label;

        TempoScale(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Sharpness of {@link #beatPulse()}: it falls to ~5% half a beat after the beat. */
    public static final double DEFAULT_PULSE_SHARPNESS = 6.0;

    private final Source source;
    private final Supplier<TempoScale> scale;

    public BeatClock(Source source, Supplier<TempoScale> scale) {
        this.source = source;
        this.scale = scale;
    }

    /** Adapts a live {@link BeatTrackerSink}. */
    public static Source of(BeatTrackerSink sink) {
        return new Source() {
            @Override public double bpm() { return sink.bpm(); }
            @Override public float confidence() { return sink.confidence(); }
            @Override public double beatPhase() { return sink.beatPhase(); }
            @Override public long beatCount() { return sink.beatCount(); }
            @Override public float kickStrength() { return sink.kickStrength(); }
        };
    }

    /** Tempo in BPM after the tempo scale; {@code NaN} until there is an estimate. */
    public double bpm() {
        return switch (scale.get()) {
            case HALF -> source.bpm() / 2;
            case NORMAL -> source.bpm();
            case DOUBLE -> source.bpm() * 2;
        };
    }

    /** Confidence in the tempo, {@code [0, 1]}; 0 after silence. */
    public float confidence() {
        return source.confidence();
    }

    /** Last kick drum's strength, {@code [0, 1]}, decaying over ~150 ms. */
    public float kickStrength() {
        return source.kickStrength();
    }

    /** Where now falls within the (scaled) beat: 0 on the beat, rising to 1 before the next. */
    public double beatPhase() {
        double phase = source.beatPhase();
        return switch (scale.get()) {
            case HALF -> (Math.floorMod(source.beatCount(), 2) + phase) / 2;
            case NORMAL -> phase;
            case DOUBLE -> {
                double twice = phase * 2;
                yield twice - Math.floor(twice);
            }
        };
    }

    /** (Scaled) beats so far; {@code % 4} cycles a bar, though which beat is the downbeat is unknown. */
    public long beatCount() {
        long count = source.beatCount();
        return switch (scale.get()) {
            case HALF -> Math.floorDiv(count, 2);
            case NORMAL -> count;
            case DOUBLE -> count * 2 + (source.beatPhase() >= 0.5 ? 1 : 0);
        };
    }

    /** A pulse that hits 1 exactly on each beat and decays, scaled by confidence: {@code [0, 1]}. */
    public double beatPulse() {
        return beatPulse(DEFAULT_PULSE_SHARPNESS);
    }

    /** As {@link #beatPulse()}, decaying {@code exp(-sharpness × phase)}; higher is snappier. */
    public double beatPulse(double sharpness) {
        return Math.exp(-sharpness * beatPhase()) * confidence();
    }

    /**
     * A ramp from 0 to 1 over every {@code beats} beats (rounded, at least 1), locked to the beat:
     * {@code beatRamp(4)} rises once per bar.
     */
    public double beatRamp(double beats) {
        long n = Math.max(1, Math.round(beats));
        return (Math.floorMod(beatCount(), n) + beatPhase()) / n;
    }
}
