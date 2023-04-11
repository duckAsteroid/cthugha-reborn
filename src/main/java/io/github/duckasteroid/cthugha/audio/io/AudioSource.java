package io.github.duckasteroid.cthugha.audio.io;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import java.io.Closeable;
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
  AudioSample sample(final int width );

  AudioFormat getFormat();

  boolean isMono();

  double getAmplification();

  void setAmplification(double amplification);
}
