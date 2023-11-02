package io.github.duckasteroid.cthugha.audio;

/**
 * A single sample in the stream of audio.
 * Contains 1 or more (nomrally two) pure channels of audio data.
 * These can be combined or read using the @{@link Channel} or {@link PointValueExtractor}
 */
public class AudioPoint {
  final short[] sample;
  public final int index;

  public AudioPoint(int index, short[] sample) {
    this.index = index;
    this.sample = sample;
  }

  public short value(PointValueExtractor ch) {
    return ch.value(sample);
  }

  public static double normalise(short value) {
    return  (double) value / (double) Short.MAX_VALUE;
  }

  public static int transpose(short value, int fsd) {
    return (int)(normalise(value) * fsd);
  }
  public double normalised(PointValueExtractor ch) {
    return normalise(value(ch));
  }

}
