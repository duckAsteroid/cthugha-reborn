package io.github.duckasteroid.cthugha.audio.io;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.dsp.FastFourierTransform;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.io.Closeable;
import java.util.Collections;
import java.util.List;
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

  default int getSourceIndex(){return 0;}
  default void setSourceIndex(int index){}
  default List<String> getSourceNames(){return Collections.emptyList();}

  default void nextSource() {
    int index = getSourceIndex();
    index++;
    index %= getSourceNames().size();
    setSourceIndex(index);
  }

  default String getSourceName() {
    return getSourceNames().get(getSourceIndex());
  }

  DoubleParameter getAmplitude();

}
