package io.github.duckasteroid.cthugha.animation;

import com.asteroid.duck.opengl.util.audio.analysis.BeatDetector;

import java.util.Random;

/**
 * Shared base for Janino-compiled scripts: the elapsed-time field, wave helpers, beat-strength
 * helpers, and random helpers used by both numeric ({@link AnimScript}) and boolean
 * ({@link ConditionScript}) scripts.
 *
 * <h2>Available in scripts</h2>
 * <pre>
 *   t                — elapsed seconds since start
 *   sine(hz)         — sine wave at hz, normalised to [0, 1]
 *   cosine(hz)       — cosine wave at hz, normalised to [0, 1]
 *   saw(hz)          — sawtooth ramp 0→1 at hz
 *   tri(hz)          — triangle wave 0→1→0 at hz
 *   pulse(hz,duty)   — square wave; duty in [0,1], e.g. 0.5 = 50%
 *   phase(hz)        — raw angular phase (radians) at hz; use with Math.sin/cos
 *   bass()/snare()/hihat() — beat strength [0,1] for the standard drum-machine bands
 *   beat(name)       — beat strength [0,1] for any named band; 0 if unknown/unavailable
 *   random()         — uniform random double in [0,1)
 *   random(min,max)  — uniform random double in [min,max)
 *   TWO_PI           — 2π constant
 *   Math.*           — all java.lang.Math static members
 * </pre>
 */
public abstract class ScriptHelpers {

    protected static final double TWO_PI = 2.0 * Math.PI;

    /** Elapsed seconds since the render clock started. Set before each script evaluation. */
    protected double t;

    private static volatile BeatDetector beatDetector;
    private static volatile Random random;

    /**
     * Sets the shared beat detector and random source every compiled script reads from.
     * Called once per frame setup (not per script) since there is one of each per running app.
     */
    public static void setContext(BeatDetector bd, Random rng) {
        beatDetector = bd;
        random = rng;
    }

    /** Sine wave at {@code hz} Hz, normalised to [0, 1]. */
    protected double sine(double hz) {
        return (Math.sin(t * TWO_PI * hz) + 1.0) / 2.0;
    }

    /** Cosine wave at {@code hz} Hz, normalised to [0, 1]. */
    protected double cosine(double hz) {
        return (Math.cos(t * TWO_PI * hz) + 1.0) / 2.0;
    }

    /** Sawtooth wave: linear ramp 0→1, resetting at each period. */
    protected double saw(double hz) {
        double p = (t * hz) % 1.0;
        return p < 0 ? p + 1.0 : p;
    }

    /** Triangle wave: linear 0→1→0 per period. */
    protected double tri(double hz) {
        double s = saw(hz);
        return s < 0.5 ? s * 2.0 : (1.0 - s) * 2.0;
    }

    /**
     * Square (pulse) wave. Returns 1.0 for the first {@code duty} fraction of each period,
     * 0.0 for the rest. {@code duty=0.5} is a 50% square wave.
     */
    protected double pulse(double hz, double duty) {
        return saw(hz) < duty ? 1.0 : 0.0;
    }

    /**
     * Raw angular phase in radians at {@code hz} Hz. Useful for combining waves manually:
     * {@code (Math.sin(phase(10) + phase(0.1)) + 1) / 2}
     */
    protected double phase(double hz) {
        return t * TWO_PI * hz;
    }

    /**
     * Beat strength in [0,1] for the named frequency band (e.g. "bass", "snare", "hihat", or any
     * custom band the app registers). Returns 0.0 if beat detection isn't ready yet or the band
     * name is unknown, rather than throwing — a typo shouldn't break the whole script.
     */
    protected double beat(String band) {
        BeatDetector bd = beatDetector;
        if (bd == null) return 0.0;
        try {
            return bd.getBeatStrength(band);
        } catch (IllegalArgumentException e) {
            return 0.0;
        }
    }

    /** Beat strength [0,1] for the "bass" band (kick drum, sub-bass). */
    protected double bass() {
        return beat("bass");
    }

    /** Beat strength [0,1] for the "snare" band (snare, clap, attack transients). */
    protected double snare() {
        return beat("snare");
    }

    /** Beat strength [0,1] for the "hihat" band (hi-hat, cymbals, shimmer). */
    protected double hihat() {
        return beat("hihat");
    }

    /** Uniform random double in [0, 1). Returns 0.0 if no random source is available yet. */
    protected double random() {
        Random r = random;
        return r != null ? r.nextDouble() : 0.0;
    }

    /** Uniform random double in [min, max). */
    protected double random(double min, double max) {
        return min + random() * (max - min);
    }
}
