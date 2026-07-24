package com.asteroid.duck.opengl.util.audio.simulated;

/**
 * A supplier of stereo sampled data
 */
public interface StereoDataSource {
	/**
	 * Get the sample of the waveform at the given time.
	 * The returned array should have two elements, representing the left and right channels respectively.
	 * @param time the global time the sample is for
	 * @return the sample values (L/R) at the given time
	 */
	double[] sample(double time);
}
