package com.asteroid.duck.opengl.util.audio.simulated;
/**
 * A supplier of sampled waveform data for a single channel (mono).
 */
public interface MonoDataSource {
    /**
     * Get the sample of the waveform at the given time.
     * The returned value is in the range -1.0 to +1.0, where 0.0 is silence, -1.0 is maximum negative amplitude and
     * +1.0 is maximum positive amplitude.
     * @param time the global time the sample is for
     * @return the sample value at the given time
     */
    double sample(double time);
}
