package io.github.duckasteroid.cthugha.display;

import io.github.duckasteroid.cthugha.display.BeatClock.TempoScale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatClockTest {

    /** A tracker frozen at one moment. */
    private record Fixed(double bpm, float confidence, double beatPhase, long beatCount, float kickStrength)
            implements BeatClock.Source {}

    private static BeatClock clock(Fixed source, TempoScale scale) {
        return new BeatClock(source, () -> scale);
    }

    @Test
    void normalPassesTheTrackerThrough() {
        BeatClock clock = clock(new Fixed(120, 0.8f, 0.25, 7, 0.5f), TempoScale.NORMAL);

        assertEquals(120, clock.bpm(), 1e-9);
        assertEquals(0.25, clock.beatPhase(), 1e-9);
        assertEquals(7, clock.beatCount());
        assertEquals(0.8f, clock.confidence());
        assertEquals(0.5f, clock.kickStrength());
    }

    @Test
    void halfSpansTwoTrackerBeats() {
        // Second half of a half-time beat: tracker beat 7 (odd) at phase 0.5
        BeatClock clock = clock(new Fixed(140, 1f, 0.5, 7, 0f), TempoScale.HALF);

        assertEquals(70, clock.bpm(), 1e-9);
        assertEquals(0.75, clock.beatPhase(), 1e-9);
        assertEquals(3, clock.beatCount());
    }

    @Test
    void doubleSplitsEachTrackerBeat() {
        BeatClock early = clock(new Fixed(80, 1f, 0.25, 5, 0f), TempoScale.DOUBLE);
        BeatClock late = clock(new Fixed(80, 1f, 0.75, 5, 0f), TempoScale.DOUBLE);

        assertEquals(160, early.bpm(), 1e-9);
        assertEquals(0.5, early.beatPhase(), 1e-9);
        assertEquals(10, early.beatCount());
        assertEquals(0.5, late.beatPhase(), 1e-9);
        assertEquals(11, late.beatCount());
    }

    @Test
    void unknownTempoStaysNaN() {
        assertTrue(Double.isNaN(clock(new Fixed(Double.NaN, 0f, 0, 0, 0f), TempoScale.DOUBLE).bpm()));
    }

    @Test
    void pulseIsFullOnTheBeatAndScaledByConfidence() {
        assertEquals(0.5, clock(new Fixed(120, 0.5f, 0, 0, 0f), TempoScale.NORMAL).beatPulse(), 1e-9);
        assertEquals(Math.exp(-3), clock(new Fixed(120, 1f, 0.5, 0, 0f), TempoScale.NORMAL).beatPulse(6), 1e-9);
    }

    @Test
    void rampRisesOverTheGivenNumberOfBeats() {
        // Third beat of a bar (count 6 % 4 = 2), halfway through it
        BeatClock clock = clock(new Fixed(120, 1f, 0.5, 6, 0f), TempoScale.NORMAL);

        assertEquals(2.5 / 4, clock.beatRamp(4), 1e-9);
        assertEquals(0.5, clock.beatRamp(1), 1e-9);
        assertEquals(0.5, clock.beatRamp(0), 1e-9);
    }
}
