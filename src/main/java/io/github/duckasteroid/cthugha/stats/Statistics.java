package io.github.duckasteroid.cthugha.stats;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tracks key statistics on a particular aspect of system performance
 */
public class Statistics implements Stats {
  private double sum;
  private long min;
  private long max;
  private int count;

  protected long last = 0;

  public Statistics() {
    reset();
  }

  public void add(long value) {
    last = value;
    min = Math.min(min, value);
    max = Math.max(max, value);

    count++;
    sum += value;
  }

  public double avg() {
    return sum / count;
  }


  public List<String> renderElements() {
    return Arrays.asList(
      "avg=" + to2DP(avg()),
      "min=" + min,
      "max=" + max,
      "count=" + count);
  }

  @Override
  public String toString() {
    return renderElements().stream().collect(Collectors.joining(", ", "{", "}"));
  }

  public static final String to2DP(double d) {
    return String.format("%.2f", d);
  }

  public void reset() {
    min = Long.MAX_VALUE;
    max = Long.MIN_VALUE;
    sum = 0;
    count = 0;
  }
}
