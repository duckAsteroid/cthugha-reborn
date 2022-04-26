package io.github.duckasteroid.cthugha.audio;

/**
 * A single sample in the stream of audio.
 * Contains 1 or more (nomrally two) pure channels of audio data.
 * These can be combined or read using the @{@link Channel} or {@link PointValueExtractor}
 */
public class AudioPoint {
  final short[] sample;

  public AudioPoint(short[] sample) {
    this.sample = sample;
  }

  public short value(PointValueExtractor ch) {
    return ch.value(sample);
  }

  public double normalised(PointValueExtractor ch) {
    return (double) Short.MAX_VALUE / ch.value(sample);
  }

  public int ranged(Channel ch, int min, int max) {
    int range = max - min;
    double extent = normalised(ch) * range;
    return min + (int) extent;
  }
}
