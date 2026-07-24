package com.asteroid.duck.opengl.util.audio.simulated;

import com.asteroid.duck.opengl.util.audio.AudioLine;
import com.asteroid.duck.opengl.util.timer.Clock;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * The opened, stateful handle for a {@link SimulatedDataSource}: samples the wrapped
 * {@link StereoDataSource} against the {@link Clock} on each {@link #read}.
 */
class SimulatedAudioLine implements AudioLine {

	private final Clock timer;
	private final StereoDataSource source;
	private final AudioFormat format;
	private final int limit;
	private boolean running;
	private double lastRead;

	SimulatedAudioLine(Clock timer, StereoDataSource source, AudioFormat format, int limit) {
		this.timer = timer;
		this.source = source;
		this.format = format;
		this.limit = limit;
		this.lastRead = timer.elapsed();
	}

	@Override
	public int available() {
		return samples(timer.elapsed()) * (bytesPerSample());
	}

	int bytesPerSample() {
		return (format.getSampleSizeInBits() / 8) * format.getChannels();
	}

	int samples(double now) {
		if (!running) return 0;
		int max = limit / bytesPerSample();
		double elapsed = now - lastRead;
		return Math.min((int) (elapsed * format.getSampleRate()), max);
	}

	@Override
	public int read(byte[] array, int start, int length) {
		double now = timer.elapsed();
		double samplePeriod = 1.0 / format.getSampleRate();
		// this is the maximum number of samples we can read (respecting our buffer limit)
		int samples = samples(now);
		if (samples <= 0)
			return 0;

		// this buffer is a short view onto the array calibrated to the start and length given
		ShortBuffer buffer = ByteBuffer.wrap(array, start, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

		// Can the buffer take more than we can theoretically read
		if (buffer.remaining() > samples * floatsPerSample()) {
			// limit it to what we can theoretically read
			buffer.limit(samples * floatsPerSample());
		}
		// read the samples into the buffer
		int samplesRead = 0;
		while (buffer.remaining() >= 2) {
			double time = now + (samplesRead * samplePeriod);
			double[] sample = source.sample(time);
			buffer.put((short)Math.round(sample[0]));
			buffer.put((short)Math.round(sample[1]));
			samplesRead++;
		}
		lastRead = now + (samplesRead * samplePeriod);
		return samplesRead * bytesPerSample();
	}

	private int floatsPerSample() {
		return format.getChannels();
	}

	@Override
	public void start() {
		this.running = true;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public void stop() {
		this.running = false;
	}

	@Override
	public void close() {
		this.running = false;
	}
}
