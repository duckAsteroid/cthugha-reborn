package io.github.duckasteroid.cthugha.binding;

import com.asteroid.duck.opengl.util.audio.analysis.BeatDetector;

import java.util.Map;
import java.util.Random;

/**
 * Shared base for Janino-compiled scripts: the elapsed-time field, wave helpers, beat-strength
 * helpers, random helpers, and script-local/global state used by both numeric
 * ({@link AnimScript}) and boolean ({@link ConditionScript}) scripts.
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
 *   state.get(key,def) / state.set(key,value) — per-binding state, private to this script
 *   global.get(key,def) / global.set(key,value) — state shared across every binding
 *   TWO_PI           — 2π constant
 *   Math.*           — all java.lang.Math static members
 * </pre>
 *
 * <h2>Script state</h2>
 * <p>{@link #state} and {@link #global} let a script remember things between ticks — debounce,
 * count, smooth a value, or signal another binding. {@code state} is private to the individual
 * {@link Binding} (backed by {@link Binding#localState()}); {@code global} is shared by every
 * binding in the owning {@link BindingSystem} (backed by {@link BindingSystem#globalState()}).
 * Both are intentionally ephemeral for v1 — reset on restart, not captured by screen configs —
 * since {@code ScreenConfigParams} only knows how to walk leaf value nodes and an arbitrary
 * {@code Map<String,Object>} doesn't fit that model without its own serialization design.</p>
 */
public abstract class ScriptHelpers {

    protected static final double TWO_PI = 2.0 * Math.PI;

    /** Elapsed seconds since the render clock started. Set before each script evaluation. */
    protected double t;

    /** Script-local key/value state, private to the binding this script belongs to. */
    protected ScriptState state = new ScriptState(Map.of());

    /** Key/value state shared across every binding in the owning system. */
    protected ScriptState global = new ScriptState(Map.of());

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

    /**
     * Wires this script instance's {@link #state}/{@link #global} helpers to the backing maps.
     * Called by {@link ScriptParameter}/{@link ConditionParameter} immediately after compiling a
     * fresh script instance, before it is ever evaluated.
     */
    void bindState(Map<String, Object> localState, Map<String, Object> globalState) {
        this.state = new ScriptState(localState != null ? localState : Map.of());
        this.global = new ScriptState(globalState != null ? globalState : Map.of());
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

    /**
     * Typed accessor over a script-local or global {@code Map<String,Object>}, exposed to
     * scripts as {@link #state}/{@link #global}. Separate typed overloads (rather than one
     * {@code Object}-typed pair) keep script call sites unambiguous and allocation-free for the
     * common numeric/boolean cases.
     */
    public static final class ScriptState {
        private final Map<String, Object> map;

        ScriptState(Map<String, Object> map) {
            this.map = map;
        }

        /** Returns the stored value for {@code key} coerced to {@code double}, or {@code def} if absent/not a number. */
        public double get(String key, double def) {
            Object v = map.get(key);
            return v instanceof Number n ? n.doubleValue() : def;
        }

        /** Returns the stored value for {@code key} coerced to {@code boolean}, or {@code def} if absent/not a boolean. */
        public boolean get(String key, boolean def) {
            Object v = map.get(key);
            return v instanceof Boolean b ? b : def;
        }

        /** Returns the stored value for {@code key} as a {@code String}, or {@code def} if absent/not a string. */
        public String get(String key, String def) {
            Object v = map.get(key);
            return v instanceof String s ? s : def;
        }

        /**
         * Stores a numeric value for {@code key} and returns it. Scripts are a single expression
         * (there's no statement sequencing), so returning the stored value lets a script both
         * update state and produce its result in one go, e.g. an incrementing counter:
         * {@code state.set("n", state.get("n", 0.0) + 1)}.
         */
        public double set(String key, double value) {
            map.put(key, value);
            return value;
        }

        /** Stores a boolean value for {@code key} and returns it (see {@link #set(String, double)}). */
        public boolean set(String key, boolean value) {
            map.put(key, value);
            return value;
        }

        /** Stores a string value for {@code key} and returns it (see {@link #set(String, double)}). */
        public String set(String key, String value) {
            map.put(key, value);
            return value;
        }

        /** Returns {@code true} if any value is currently stored for {@code key}. */
        public boolean has(String key) {
            return map.containsKey(key);
        }

        /** Removes any stored value for {@code key}. */
        public void clear(String key) {
            map.remove(key);
        }
    }
}
