package com.asteroid.duck.opengl.util.toggle;

import com.asteroid.duck.opengl.util.RenderContext;

/**
 * A toggle that goes off every N seconds
 */
public class Frequency implements Toggle {
	// time between enabled periods (seconds)
	private final double period;
	// how long enabled period lasts
	private final double dwell;

	/**
	 * Create a periodic toggle that fires once per cycle.
	 *
	 * @param period total cycle length in seconds; the toggle fires at the start of each cycle
	 * @param dwell  how many seconds into the cycle the enabled window lasts;
	 *               must be less than {@code period} to guarantee a silent interval
	 */
	public Frequency(double period, double dwell) {
		this.period = period;
		this.dwell = dwell;
	}

	@Override
	public boolean isRenderEnabled(RenderContext ctx) {
		double elapsedTime = ctx.getClock().elapsed();
		double cycleTime = elapsedTime % period;
		return cycleTime <= dwell;
	}
}
