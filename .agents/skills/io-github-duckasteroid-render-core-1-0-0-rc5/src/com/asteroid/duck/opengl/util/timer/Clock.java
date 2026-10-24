package com.asteroid.duck.opengl.util.timer;

import java.time.Duration;

/**
 * A running clock that reports how much time has elapsed since it was started.
 *
 * <p>Implementations may be backed by wall-clock time ({@link ClockImpl}), a fixed snapshot
 * ({@link StaticClock}), or any other time source.</p>
 */
public interface Clock {

	/**
	 * The time elapsed (in seconds) since the clock started.
	 *
	 * @return elapsed time in seconds
	 */
	double elapsed();

	/**
	 * Start tracking {@code duration} from now.
	 *
	 * <p>The returned {@link Timer} snapshots the current {@link #elapsed()} value
	 * and uses this clock for all subsequent queries.</p>
	 *
	 * @param duration the duration to track; must be positive
	 * @return a live timer rooted at the current clock position
	 */
	default Timer track(Duration duration) {
		return new Timer(this, duration);
	}
}
