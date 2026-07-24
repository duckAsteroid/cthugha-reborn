package com.asteroid.duck.opengl.util.audio;

/**
 * Thrown by {@link AudioDataSource#open} when the source cannot be opened — e.g. the underlying
 * hardware line is in use, or a simulated source was asked to open in a format it cannot
 * synthesize. Wraps the underlying cause (such as {@link javax.sound.sampled.LineUnavailableException})
 * where one exists, so callers deal with a single audio-package exception type regardless of the
 * kind of {@link AudioDataSource} involved.
 */
public class AudioSourceUnavailableException extends Exception {
    public AudioSourceUnavailableException(String message) {
        super(message);
    }

    public AudioSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
