package io.github.duckasteroid.cthugha.audio;

import java.io.Closeable;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;

/**
 * Interface to an object that provides audio data
 */
public interface AudioSource extends Closeable {
  /**
   * Copies the next batch of audio data into the sound buffer
   * @param width the number of samples to acquire
   * @return the stream of samples (if there was enough, or empty)
   */
  AudioBuffer.AudioSample sample( final int width );

  AudioFormat getFormat();

  boolean isMono();
}
