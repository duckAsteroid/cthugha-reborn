package io.github.duckasteroid.cthugha.audio.dsp;

import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.PointValueExtractor;
import io.github.duckasteroid.cthugha.stats.Stats;
import io.github.duckasteroid.cthugha.stats.StatsFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import org.jtransforms.fft.DoubleFFT_1D;

public class FastFourierTransform {
  private final int size;

  public int getSize() {
    return size;
  }

  public AudioFormat getFormat() {
    return format;
  }

  public List<Double> getBinFrequencies() {
    return binFrequencies;
  }

  private final AudioFormat format;
  private final List<Double> binFrequencies;

  private final DoubleFFT_1D fft;
  /**
   * How we will reduce multi channel audio into a single 1D value
   */
  private PointValueExtractor pointValueExtractor;

  private final Stats fftMax = StatsFactory.stats("audio.fft.max");

  public FastFourierTransform(int size, AudioFormat format, PointValueExtractor pointValueExtractor) {
    this.size = size;
    if (format == null || format.getSampleRate() == AudioSystem.NOT_SPECIFIED)
      throw new IllegalArgumentException("Format sample rate must be specified");
    this.format = format;
    if (pointValueExtractor == null)
      throw new IllegalArgumentException("Value extractor cannot be null");
    this.pointValueExtractor = pointValueExtractor;
    this.binFrequencies = IntStream.range(0,size / 2).mapToObj(this::bin_freq).toList();
    this.fft = new DoubleFFT_1D(size);
  }

  public FrequencySpectra transform(AudioSample sample) {
    double[] data = sample.streamDoublePoints(size, pointValueExtractor).toArray();
    if (sample.intensity(pointValueExtractor) > 200) {

      Double[] magnitudes;
      double min = Double.MAX_VALUE, max = 0;
      if (data.length >= size * 2) {
        fft.realForward(data);
        magnitudes = new Double[size / 2];
        for (int i = 0; i < size; i += 2) {
          int bin = i / 2;
          double magnitude = sqrt(data[i] * data[i]) + sqrt(data[i + 1] * data[i + 1]);
          min = Math.min(min, magnitude);
          max = Math.max(max, magnitude);
          magnitudes[bin] = magnitude;
        }
        // FIXME can we scale magnitude according to data.length to normalise?
        fftMax.add((long)max);
        // FIXME rather than by using local min/max FFT keeps magically scaling in UX
        // FIXME logarithmic x scale for frequencies
        return new FrequencySpectra(binFrequencies, List.of(magnitudes), 0, 508094);
      }
    }
    return null;
  }

  private double bin_freq(int bin) {
    return (bin * format.getSampleRate() / 2) / ( size / 2);
  }
}
