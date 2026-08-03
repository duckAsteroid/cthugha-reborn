package com.asteroid.duck.opengl.util.resources.shader.vars.dynamic;

import com.asteroid.duck.opengl.util.RenderContext;

public class Waveform {
	private double frequency = 1.0;
	private double amplitude = 1.0;
	private double offset = 0.0;
	private double phase = 0.0;

	public Waveform() {
	}

	public Waveform(double frequency, double amplitude, double offset, double phase) {
		this.frequency = frequency;
    this.amplitude = amplitude;
    this.offset = offset;
    this.phase = phase;
	}

	public Waveform(double frequency, double amplitude) {
		this.frequency = frequency;
		this.amplitude = amplitude;
	}

	public double sinewave(RenderContext ctx) {
		return (amplitude * Math.sin(2 * Math.PI * frequency * ctx.getClock().elapsed() + phase)) + offset;
	}

	public double sawtooth(RenderContext ctx) {
		double period = 1.0 / frequency; // Calculate the period
		double x = ctx.getClock().elapsed() % period; // Get the time within the current period
		return 2 * amplitude * (x / period - 0.5);
	}

	public double getFrequency() {
		return frequency;
	}

	public void setFrequency(double frequency) {
		this.frequency = frequency;
	}

	public double getAmplitude() {
		return amplitude;
	}

	public void setAmplitude(double amplitude) {
		this.amplitude = amplitude;
	}

	public double getOffset() {
		return offset;
	}

	public void setOffset(double offset) {
		this.offset = offset;
	}

	public double getPhase() {
		return phase;
	}

	public void setPhase(double phase) {
		this.phase = phase;
	}
}
