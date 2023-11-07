package io.github.duckasteroid.cthugha.audio;

import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A batch of audio data read from the IO source
 */
public class AudioSample {
  public final ShortBuffer backBuffer;
  public final int channels;
  private final double amplification;

  public AudioSample(ShortBuffer samples, boolean mono, double amplification) {
    this.backBuffer = samples;
    this.channels = mono ? 1 : 2;
    this.amplification = amplification;
  }

  public Stream<AudioPoint> streamPoints() {
    final ShortBuffer projection = backBuffer.slice().asReadOnlyBuffer();
    int size = projection.remaining() / channels;
    return IntStream.range(0, size).mapToObj(index -> {
      short[] sampleData = new short[channels];
      for (int s = 0; s < sampleData.length; s++) {
        sampleData[s] = (short) (projection.get((index * channels) + s) * amplification);
      }
      return new AudioPoint(index, sampleData);
    });
  }

  public Stream<AudioPoint> downsample(int maxLength) {
    if (maxLength < backBuffer.remaining()) {
      final ShortBuffer projection = backBuffer.slice().asReadOnlyBuffer();
      int skipFactor = (int) Math.ceil(projection.remaining() / maxLength );
      int size = projection.remaining() / channels;
      return IntStream.range(0, size)
        .filter(i -> i % skipFactor == 0)
        .mapToObj(index -> {
          short[] sampleData = new short[channels];
          for (int s = 0; s < sampleData.length; s++) {
            sampleData[s] = (short) (projection.get((index * channels) + s) * amplification);
          }
          return new AudioPoint(index, sampleData);
      }).limit(maxLength);
    }
    return streamPoints(maxLength);
  }

  public Stream<AudioPoint> streamPoints(int maxLength) {
    return streamPoints().limit(maxLength);
  }

  public DoubleStream streamDoublePoints(int maxLength, PointValueExtractor ch) {
    return streamPoints(maxLength).mapToDouble(pt -> pt.value(ch));
  }

  public double intensity(PointValueExtractor channel) {
    return streamPoints()
      .mapToInt(pt -> pt.value(channel))
      .mapToDouble(i -> Math.sqrt(i * i))
      .average().orElse(0.0);
  }

  public int size() {
    return backBuffer.remaining() / channels;
  }
}
