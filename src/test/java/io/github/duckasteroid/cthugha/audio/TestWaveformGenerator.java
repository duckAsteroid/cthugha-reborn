package io.github.duckasteroid.cthugha.audio;

import static java.lang.Math.PI;

import java.time.Duration;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TestWaveformGenerator {

  /**
   * Create a sampled test data waveform containing one or more frequencies
   * @param length time duration of resulting stream
   * @param sampleRate sampling frequency per second of the stream
   * @param max the maximum amplitude of the wave
   * @param freq a list of frequencies (in Hz) to include
   * @return a generated test waveform containing the given frequencies
   */
  public static Stream<Double> createWaveForm(Duration length, int sampleRate, double max, double ... freq) {
    int samples = (int)length.toSeconds() * sampleRate;
    final double workingMax = max / freq.length; // or we overload the amplitude
    return IntStream.range(0, samples).mapToObj(i -> {
      double result = 0;
      for (int j = 0; j < freq.length; j++) {
        result += Math.sin(i * 2 * PI * freq[j] / sampleRate) * workingMax;
      }
      return result;
    });
  }
}
