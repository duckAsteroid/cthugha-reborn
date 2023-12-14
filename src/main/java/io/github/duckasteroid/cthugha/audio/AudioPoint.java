package io.github.duckasteroid.cthugha.audio;

/**
 * A single sample in the stream of audio.
 * Contains 1 or more (normally two) pure channels of audio data.
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

  /**
   * Convert the audio value into a number in range 0:1 based on maximum of a short
   */
  public static double normalise(short value) {
    return  (double) value / (double) Short.MAX_VALUE;
  }

  /**
   * Transpose the value onto the given integer scale
   * @param value the audio value to transpose
   * @param fsd the full scale deflection of the integer
   * @return the integer equivalent more than or equal to 0 but less than or equal fsd
   */
  public static int transpose(short value, int fsd) {
    return (int)(normalise(value) * fsd);
  }
  public double normalised(PointValueExtractor ch) {
    return normalise(value(ch));
  }

}
