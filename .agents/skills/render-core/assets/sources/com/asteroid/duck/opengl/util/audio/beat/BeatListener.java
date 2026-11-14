package com.asteroid.duck.opengl.util.audio.beat;

/**
 * Receives the events a {@link BeatAnalyser} detects. Times are seconds on the analyser's
 * sample clock (samples consumed / sample rate), not wall-clock time.
 *
 * <p>Called on the thread that calls {@link BeatAnalyser#process}. All methods default to
 * no-ops, so a listener implements only what it needs.</p>
 */
public interface BeatListener {

    /**
     * A beat (tactus) occurred.
     * @param time seconds on the analyser's sample clock
     */
    default void onBeat(double time) {}

    /**
     * A downbeat — the first beat of a bar — occurred. Reported straight after the
     * {@link #onBeat} for the same beat, with the same time.
     * @param time seconds on the analyser's sample clock
     */
    default void onDownbeat(double time) {}

    /**
     * A kick (bass drum) onset occurred.
     * @param time     seconds on the analyser's sample clock
     * @param strength onset strength in {@code [0, 1]}
     */
    default void onKick(double time, float strength) {}

    /**
     * The analyser's current tempo estimate. A {@code NaN} tempo (with zero confidence) means it
     * has lost the beat, e.g. because the audio went silent.
     * @param time       seconds on the analyser's sample clock
     * @param bpm        tempo in beats per minute
     * @param confidence confidence in {@code [0, 1]}
     */
    default void onTempo(double time, double bpm, float confidence) {}
}
