package com.asteroid.duck.opengl.util.audio.simulated;

/**
 * Represents a static mono sinusoidal waveform audio data source.
 *
 * @param freq           the frequency in Hz
 * @param amplitude      the amplitude of the waveform
 * @param phase          the relative phase of the waveform (in degrees)
 */
public record Waveform(double freq, double amplitude, double phase) implements MonoDataSource {

	public Waveform(double freq, double amplitude, double phase) {
		this.freq = freq;
		this.amplitude = amplitude;
		this.phase = phase;
	}

	public Waveform(double freq) {
		this(freq, 1.0, 0.0);
	}

	public static Waveform MIDDLE_C = new Waveform(261.63);

	public double sample(double time) {
		return amplitude * Math.sin(2 * Math.PI * freq * time + Math.toRadians(phase));
	}

	/**
	 * Return this waveform transposed up or down (negative) by the given number of semitones.
	 * The new waveform will have the same phase, amplitude and stereomix.
	 */
	public Waveform transpose(int semitoneSteps) {
		double factor = Math.pow(2.0, (double) semitoneSteps / 12.0);
		return new Waveform(this.freq * factor, amplitude, phase);
	}

	public Waveform amplify(double amplitude) {
		return new Waveform(freq, this.amplitude * amplitude, phase);
	}
}
