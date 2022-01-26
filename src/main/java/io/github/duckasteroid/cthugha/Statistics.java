package io.github.duckasteroid.cthugha;

public class Statistics {
  private double sum;
  private long min;
  private long max;
  private int count;

  public Statistics() {
    reset();
  }

  public void add(long value) {
    min = Math.min(min, value);
    max = Math.max(max, value);

    count++;
    sum += value;
  }

  public double avg() {
    return sum / count;
  }

  @Override
  public String toString() {

    return "Statistics{" +
      "avg=" + to2DP(avg()) +
      ", min=" + min +
      ", max=" + max +
      ", count=" + count +
      " }";
  }

  protected static final String to2DP(double d) {
    return String.format("%.2f", d);
  }

  public void reset() {
    min = Long.MAX_VALUE;
    max = Long.MIN_VALUE;
    sum = 0;
    count = 0;
  }
}
