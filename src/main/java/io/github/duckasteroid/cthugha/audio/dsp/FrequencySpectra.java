package io.github.duckasteroid.cthugha.audio.dsp;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.math3.util.Pair;

/**
 * Represents a frequency distribution calculated from a time series sample.
 */
public class FrequencySpectra {
  private final List<Double> binFrequencies;
  private final List<Double> magnitudes;

  private final double maxMagnitude;
  private final double minMagnitude;
  public FrequencySpectra(List<Double> binFrequencies, List<Double> magnitudes,
                          double minMagnitude, double maxMagnitude) {
    this.binFrequencies = binFrequencies;
    this.magnitudes = magnitudes;
    this.maxMagnitude = maxMagnitude;
    this.minMagnitude = minMagnitude;
  }

  public int size() {
    return binFrequencies.size();
  }

  public List<Double> getBinFrequencies() {
    return binFrequencies;
  }

  public double getFrequency(int bin) {
    return binFrequencies.get(bin);
  }

  public double getMagnitude(int bin) {
    return magnitudes.get(bin);
  }

  public double getNormalisedMagnitude(int bin) {
    return (getMagnitude(bin) - minMagnitude) / maxMagnitude;
  }

  public FrequencySpectra combineMaxima(FrequencySpectra other) {
    return combine(pair -> Math.max(pair.getFirst(), pair.getSecond()), other);
  }

  public FrequencySpectra combine(Function<Pair<Double, Double>, Double> combiner, FrequencySpectra other) {
    if (this.size() != other.size())
      throw new IllegalArgumentException("Spectra must be the same size");
    double min = Double.MAX_VALUE;
    double max = 0;
    Double[] result = new Double[size()];
    for (int i = 0; i < result.length; i++) {
      double combined = combiner.apply(Pair.create(this.magnitudes.get(i), other.magnitudes.get(i)));
      result[i] = combined;
      min = Math.min(min, combined);
      max = Math.max(max, combined);
    }
    return new FrequencySpectra(binFrequencies, Arrays.asList(result), min, max);
  }

  public FrequencySpectra scale(double scalar) {
    if (scalar < 0 || scalar > 1)
      throw new IllegalArgumentException("Scalar must be a fraction");
    return scale(value -> value * scalar);
  }

  public FrequencySpectra subtract(double amount) {
    return scale(value -> Math.max(0, value - amount));
  }

  public FrequencySpectra scale(Function<Double, Double> scalar) {
    double min = Double.MAX_VALUE;
    double max = 0;
    Double[] result = new Double[size()];
    for (int i = 0; i < result.length; i++) {
      double combined = scalar.apply(magnitudes.get(i));
      result[i] = combined;
      min = Math.min(min, combined);
      max = Math.max(max, combined);
    }
    return new FrequencySpectra(binFrequencies, Arrays.asList(result), min, max);
  }

  public double maxima() {
    return maxMagnitude;
  }

  public double minima() {
    return minMagnitude;
  }
}
