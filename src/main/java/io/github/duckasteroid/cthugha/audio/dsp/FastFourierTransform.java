package io.github.duckasteroid.cthugha.audio.dsp;

import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.audio.AudioSample;
import io.github.duckasteroid.cthugha.audio.PointValueExtractor;
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
  private PointValueExtractor pointValueExtractor;

  public FastFourierTransform(int size, AudioFormat format, PointValueExtractor pointValueExtractor) {
    this.size = size;
    if (format == null || format.getSampleRate() == AudioSystem.NOT_SPECIFIED)
      throw new IllegalArgumentException("Format sample rate must be specified");
    this.format = format;
    if (pointValueExtractor == null)
      throw new IllegalArgumentException("Value extractor cannot be null");
    this.pointValueExtractor = pointValueExtractor;
    this.binFrequencies = IntStream.range(0,size).mapToObj(this::bin_freq).toList();
    this.fft = new DoubleFFT_1D(size);
  }

  public FrequencySpectra transform(AudioSample sample) {
    if (sample.intensity(pointValueExtractor) > 200) {
      double[] data = sample.streamDoublePoints(pointValueExtractor).toArray();
      Double[] magnitudes;
      double min = Double.MAX_VALUE, max = 0;
      if (data.length >= size * 2) {
        fft.realForward(data);
        magnitudes = new Double[size];
        for (int i = 0; i < size * 2; i += 2) {
          int bin = i / 2;
          double magnitude = sqrt(data[i] * data[i]) + sqrt(data[i + 1] * data[i + 1]);
          min = Math.min(min, magnitude);
          max = Math.max(max, magnitude);
          magnitudes[bin] = magnitude;
        }
        // FIXME can we scale magnitude according to data.length to normalise?
        // rather than by using local min/max FFT keeps magically scaling in UX
        return new FrequencySpectra(binFrequencies, List.of(magnitudes), min, max);
      }
    }
    return null;
  }

  private double bin_freq(int bin) {
    return (bin * format.getSampleRate() / 2) / ( size / 2);
  }
}
