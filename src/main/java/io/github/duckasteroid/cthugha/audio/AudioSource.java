package io.github.duckasteroid.cthugha.audio;

import java.io.Closeable;

/**
 * Interface to an object that provides audio data
 */
public interface AudioSource extends Closeable {
  /**
   * Copies the next batch of audio data into the sound buffer
   * @param sound The sound buffer
   * @param width the width of the buffer
   * @param height
   */
  void sample(final int[] sound, final int width, final int height);
}
