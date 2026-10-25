package io.github.duckasteroid.cthugha.display;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepClockTest {

    /** Runs {@code seconds} of frames at {@code refreshHz} (optionally jittered) and counts steps. */
    private static int stepsOver(double seconds, double refreshHz, double speed, double jitterFraction) {
        StepClock clock = new StepClock();
        Random rng = new Random(42);
        double frame = 1.0 / refreshHz;
        int frames = (int) Math.round(seconds * refreshHz);
        int total = 0;
        double t = 0;
        for (int i = 0; i <= frames; i++) {
            double jitter = (rng.nextDouble() * 2 - 1) * jitterFraction * frame;
            total += clock.stepsDue((long) ((t + jitter) * 1e9), speed, refreshHz);
            t += frame;
        }
        return total;
    }

    @Test
    void stepRateIsIndependentOfRefreshRate() {
        // 10 s at Speed 60 is ~600 steps on 60, 144 and 30 Hz displays alike.
        assertEquals(600, stepsOver(10, 60, 60, 0), 2);
        assertEquals(600, stepsOver(10, 144, 60, 0), 2);
        assertEquals(600, stepsOver(10, 30, 60, 0), 2);
        assertEquals(200, stepsOver(10, 60, 20, 0), 2);
        assertEquals(1200, stepsOver(10, 60, 120, 0), 2);
    }

    @Test
    void matchingRateRunsExactlyOneStepPerFrameDespiteJitter() {
        StepClock clock = new StepClock();
        Random rng = new Random(7);
        double frame = 1.0 / 60;
        for (int i = 0; i < 600; i++) {
            double jitter = (rng.nextDouble() * 2 - 1) * 0.3 * frame;
            int steps = clock.stepsDue((long) ((i * frame + jitter) * 1e9), 60, 60);
            assertEquals(1, steps, "frame " + i);
        }
    }

    @Test
    void stallCatchUpIsBounded() {
        StepClock clock = new StepClock();
        clock.stepsDue(0, 60, 60);
        // A 500 ms stall would be 30 steps due; at most ceil(60/60)+1 = 2 run.
        int steps = clock.stepsDue(500_000_000L, 60, 60);
        assertEquals(2, steps);
        // The dropped time isn't carried over into the next frame.
        assertTrue(clock.stepsDue(500_000_000L + 16_666_667L, 60, 60) <= 2);
    }

    @Test
    void maxStepsAllowsFasterThanRefreshRates() {
        assertEquals(2, StepClock.maxSteps(60, 60));
        assertEquals(3, StepClock.maxSteps(120, 60));
        assertEquals(2, StepClock.maxSteps(60, 144));
        assertEquals(2, StepClock.maxSteps(60, 0));
    }
}
