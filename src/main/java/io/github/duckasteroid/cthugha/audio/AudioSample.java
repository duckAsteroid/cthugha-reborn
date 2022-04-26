package io.github.duckasteroid.cthugha.audio;

import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A batch of audio data read from the IO source
 */
public class AudioSample {
  public final short[][] samples;
  public final boolean mono;

  public AudioSample(short[][] samples, boolean mono) {
    this.samples = samples;
    this.mono = mono;
  }

  public Stream<short[]> stream() {
    return Arrays.stream(samples);
  }

  public Stream<AudioPoint> streamPoints() {
    return stream().map(AudioPoint::new);
  }

  public IntStream streamIntegerPoints(Channel ch) {
    return streamPoints().mapToInt(pt -> pt.value(ch));
  }

  public DoubleStream streamDoublePoints(PointValueExtractor ch) {
    return streamPoints().mapToDouble(pt -> pt.value(ch));
  }

  public Stream<short[]> stream(int maxLength) {
    int skip = samples.length / maxLength;
    return IntStream.range(0, samples.length)
      .filter(n -> n % skip == 0)
      .mapToObj(n -> samples[n]);
  }

  public Stream<AudioPoint> streamPoints(int maxLength) {
    return stream(maxLength).map(AudioPoint::new);
  }

  public double intensity(PointValueExtractor channel) {
    return streamPoints()
      .mapToInt(pt -> pt.value(channel))
      .mapToDouble(i -> Math.sqrt(i * i))
      .average().orElse(0.0);
  }
}
