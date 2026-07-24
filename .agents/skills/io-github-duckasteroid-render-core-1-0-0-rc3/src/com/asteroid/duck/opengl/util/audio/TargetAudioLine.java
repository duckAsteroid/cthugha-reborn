package com.asteroid.duck.opengl.util.audio;

import javax.sound.sampled.TargetDataLine;

/**
 * An opened {@link TargetDataLine}, wrapped as an {@link AudioLine}.
 */
class TargetAudioLine implements AudioLine {
	private final TargetDataLine line;

	TargetAudioLine(TargetDataLine line) {
		this.line = line;
	}

	@Override
	public int available() {
		return line.available();
	}

	@Override
	public int read(byte[] array, int start, int length) {
		return line.read(array, start, length);
	}

	@Override
	public void start() {
		line.start();
	}

	@Override
	public void stop() {
		line.stop();
	}

	@Override
	public boolean isRunning() {
		return line.isRunning();
	}

	@Override
	public void close() {
		line.close();
	}
}
