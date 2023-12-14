package io.github.duckasteroid.cthugha.audio.dsp;

import static java.lang.Math.abs;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.PointValueExtractor;
import io.github.duckasteroid.cthugha.audio.AudioValue;
import java.util.List;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import org.jtransforms.fft.DoubleFFT_1D;

public class FastFourierTransform {
  private final int size;

  private final AudioFormat format;
  private final List<Double> binFrequencies;

  private final DoubleFFT_1D fft;
  /**
   * How we will reduce multi channel audio into a single 1D value
   */
  private final PointValueExtractor pointValueExtractor;

  public FastFourierTransform(int size, AudioFormat format, PointValueExtractor pointValueExtractor) {
    this.size = size;
    if (format == null || format.getSampleRate() == AudioSystem.NOT_SPECIFIED)
      throw new IllegalArgumentException("Format sample rate must be specified");
    this.format = format;
    if (pointValueExtractor == null)
      throw new IllegalArgumentException("Value extractor cannot be null");
    this.pointValueExtractor = pointValueExtractor;
    this.binFrequencies = IntStream.range(0, size / 2).mapToObj(this::bin_freq).toList();
    this.fft = new DoubleFFT_1D(size);
  }

  public int getSize() {
    return size;
  }

  public FrequencySpectra transform(AudioSample sample) {
    double[] data = sample.streamPoints()
      .limit(size * 2L)
      .map(pointValueExtractor::value)
      .mapToDouble(AudioValue::value)
      //.mapToDouble(new WindowingFunction.Cosine(size * 2)::apply)
      .toArray();
    Double[] magnitudes;
    double min = Double.MAX_VALUE, max = 0;
    if (data.length >= size * 2) {
      fft.realForward(data);
      magnitudes = new Double[size / 2];
      IntStream.range(0, size / 2).parallel().forEach(i -> {
        int fftIndex = i * 2;
        double magnitude = abs(data[fftIndex]) + abs(data[fftIndex + 1]);
        magnitudes[i] = magnitude;
      });
      // FIXME can we scale magnitude according to data.length to normalise?
      // FIXME rather than by using local min/max FFT keeps magically scaling in UX
      // FIXME logarithmic x scale for frequencies
      return new FrequencySpectra(binFrequencies, List.of(magnitudes), 0, 508094);
    }
    return null;
  }

  private double bin_freq(int bin) {
    return (bin * format.getSampleRate() / 2) / ( (double) size / 2);
  }
}
