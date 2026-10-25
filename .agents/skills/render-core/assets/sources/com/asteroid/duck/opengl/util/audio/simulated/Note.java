package com.asteroid.duck.opengl.util.audio.simulated;

import java.util.*;

/**
 * A musical note that gates a {@link MonoDataSource} according to a rhythmic bar pattern.
 *
 * <p>Time is divided into bars of length {@code barLength} seconds. Within each bar the
 * {@code barPattern} list encodes which equal-sized slots are "on" ({@code true}) or silent
 * ({@code false}). When the current slot is on, the note delegates to {@code source} and
 * routes the mono sample through {@code positioner} to produce a stereo output. When the
 * slot is off, both channels return {@code 0.0}.</p>
 *
 * @param source     the mono waveform generator that provides the raw sample amplitude
 * @param positioner the stereo panner that converts the mono sample into a left/right pair;
 *                   use {@link StaticStereoPositioner#CENTER} for centred mono output
 * @param barPattern the rhythmic on/off pattern for one bar; {@code true} = sound, {@code false} = silence.
 *                   An empty list is treated as always-on
 * @param barLength  duration of a single bar in seconds; determines tempo together with barPattern length
 */
public record Note(MonoDataSource source, StereoPositioner positioner, List<Boolean> barPattern, double barLength) implements StereoDataSource {
	public static final double[] NO_SOUND = new double[] {0.0, 0.0};

	private static final List<Boolean> ALWAYS_ON = List.of(true);

	/**
	 * Convenience constructor that parses a {@code bitPattern} string into a bar pattern.
	 *
	 * @param source     the mono waveform generator
	 * @param positioner the stereo panner
	 * @param barLength  duration of one bar in seconds
	 * @param bitPattern a string of {@code '1'} (sound) and {@code '0'} (silence) characters
	 *                   encoding the bar pattern; empty string means always-on
	 */
	public Note(MonoDataSource source, StereoPositioner positioner, double barLength, String bitPattern) {
		this(source, positioner, from(bitPattern), barLength);
	}

	/**
	 * Convenience constructor that centres the mono source and parses a bit pattern.
	 *
	 * @param source  the mono waveform generator
	 * @param length  duration of one bar in seconds
	 * @param pattern a {@code '1'}/{@code '0'} bit-pattern string; equivalent to calling
	 *                {@link #Note(MonoDataSource, StereoPositioner, double, String)} with
	 *                {@link StaticStereoPositioner#CENTER}
	 */
	public Note(MonoDataSource source, double length, String pattern) {
		this(source, StaticStereoPositioner.CENTER, length, pattern);
	}

	/**
	 * Parse a bit-pattern string into a list of boolean bar slots.
	 * Each {@code '1'} character maps to {@code true} (sound) and any other character to
	 * {@code false} (silence). An empty string returns the built-in always-on sentinel.
	 *
	 * @param bitPattern the pattern string; must not be {@code null}
	 * @return an immutable list of booleans representing the bar pattern
	 */
	public static List<Boolean> from(String bitPattern) {
		Objects.requireNonNull(bitPattern, "BitPattern must not be null");
		if (bitPattern.isEmpty()) {
			return ALWAYS_ON;
		}
		Boolean[] barPattern = new Boolean[bitPattern.length()];
		for(int i = 0; i < bitPattern.length(); i++) {
			char c = bitPattern.charAt(i);
			if (c == '1') {
				barPattern[i] = true;
			}
			else {
				barPattern[i] = false;
			}
		}
		return Arrays.asList(barPattern);
	}

	@Override
	public double[] sample(double time) {
		double timeInBar = time % barLength;
		int index = (int)(timeInBar / barLength * barPattern.size());
		if (barPattern.get(index)) {
			var channelSample = source.sample(time);
			return positioner.sample(channelSample, time);
		}
		else {
			return NO_SOUND;
		}
	}
}
