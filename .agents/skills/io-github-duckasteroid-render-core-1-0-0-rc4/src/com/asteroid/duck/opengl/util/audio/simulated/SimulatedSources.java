package com.asteroid.duck.opengl.util.audio.simulated;

import com.asteroid.duck.opengl.util.timer.Clock;

/**
 * Factory for ready-made simulated {@link SimulatedDataSource}s, for clients that want to add
 * synthetic audio to an {@code AudioSources} without a hardware line — e.g. headless
 * testing/CI, or as an always-available fallback input.
 */
public final class SimulatedSources {
	private SimulatedSources() {}

	/**
	 * A middle-C tone panned slowly left and right at 1 Hz.
	 *
	 * @param clock drives sample generation
	 * @return a stereo simulated source suitable for passing to {@code AudioReader}
	 */
	public static SimulatedDataSource middleC(Clock clock) {
		Waveform midC = Waveform.MIDDLE_C.amplify(100);
		StereoDataSource audio = OscillatingStereoPositioner.fullScale(1.0).wrap(midC);
		return new SimulatedDataSource("Simulated: Middle C (panned)", clock, audio);
	}
}
