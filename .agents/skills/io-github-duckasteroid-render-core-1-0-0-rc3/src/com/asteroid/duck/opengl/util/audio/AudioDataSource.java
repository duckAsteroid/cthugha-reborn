package com.asteroid.duck.opengl.util.audio;

import javax.sound.sampled.AudioFormat;

/**
 * A pluggable, possibly-simulated source of raw PCM audio. Describes itself (name, supported
 * formats) and can be {@linkplain #open opened} to obtain an {@link AudioLine} for actually
 * reading data.
 */
public interface AudioDataSource {
    /**
     * A human-readable name for this source, used in log messages and UI.
     *
     * @return display name; never {@code null}
     */
	String getName();

	/**
	 * The audio formats this source is able to open. May contain wildcard fields
	 * (see {@link javax.sound.sampled.AudioSystem#NOT_SPECIFIED}), mirroring
	 * {@link javax.sound.sampled.DataLine.Info#getFormats()}.
	 *
	 * @return supported formats; never {@code null}, may be empty if unknown
	 */
	AudioFormat[] getSupportedFormats();

	/**
	 * Open this source for reading in the given format.
	 *
	 * @param format the format to open in
	 * @param bufferSize buffer size in bytes
	 * @return a handle for reading, starting, stopping, and closing the opened line
	 * @throws AudioSourceUnavailableException if the source cannot be opened in this format right now
	 */
	AudioLine open(AudioFormat format, int bufferSize) throws AudioSourceUnavailableException;
}
