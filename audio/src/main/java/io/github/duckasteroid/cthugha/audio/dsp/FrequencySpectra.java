package io.github.duckasteroid.cthugha.audio.dsp;

import java.util.List;


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

  /**
   * How many frequency bins are present in the spectra
   */
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
