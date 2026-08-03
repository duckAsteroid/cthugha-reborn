package com.asteroid.duck.opengl.util.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * A hardware capture source described by a {@link Mixer} and a {@link DataLine.Info}. The
 * underlying {@link TargetDataLine} is not acquired until {@link #open}, so simply discovering a
 * {@code TargetLineSource} (see {@link LineAcquirer}) never touches the device.
 */
public class TargetLineSource implements AudioDataSource {
	private final String name;
	private final Mixer mixer;
	private final DataLine.Info lineInfo;

	public TargetLineSource(String name, Mixer mixer, DataLine.Info lineInfo) {
		this.name = name;
		this.mixer = mixer;
		this.lineInfo = lineInfo;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public AudioFormat[] getSupportedFormats() {
		return lineInfo.getFormats();
	}

	@Override
	public AudioLine open(AudioFormat format, int bufferSize) throws AudioSourceUnavailableException {
		try {
			TargetDataLine line = (TargetDataLine) mixer.getLine(lineInfo);
			line.open(format, bufferSize);
			return new TargetAudioLine(line);
		} catch (LineUnavailableException e) {
			throw new AudioSourceUnavailableException("Line unavailable: " + name, e);
		}
	}
}
