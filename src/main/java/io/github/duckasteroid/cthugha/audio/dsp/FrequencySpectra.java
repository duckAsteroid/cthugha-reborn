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

  public double maxima() {
    return maxMagnitude;
  }

  public double minima() {
    return minMagnitude;
  }

  public double getMaxFrequency() {
    return binFrequencies.get(binFrequencies.size() - 1);
  }
}
