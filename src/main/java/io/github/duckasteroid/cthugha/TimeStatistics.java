package io.github.duckasteroid.cthugha;

public class TimeStatistics {
  private long lastTime;
  private double sum;
  private long min;
  private long max;
  private int count;

  public TimeStatistics() {
    reset();
  }

  public void ping() {
    long now = System.nanoTime();
    long diff = now - lastTime;

    min = Math.min(min, diff);
    max = Math.max(max, diff);

    count++;
    sum += diff;

    lastTime = now;
  }

  @Override
  public String toString() {
    double avg = sum / count;
    double hz = 1 / (avg * 0.000000001);
    return "TimeStatistics{" +
      "avg=" + to2DP(avg) +
      "(hz=" + to2DP(hz) + ")" +
      ", min=" + min +
      ", max=" + max +
      " nanoseconds }";
  }

  private static final String to2DP(double d) {
    return String.format("%.2f", d);
  }

  public void reset() {
    min = Long.MAX_VALUE;
    max = Long.MIN_VALUE;
    sum = 0;
    count = 0;
    lastTime = System.nanoTime();
  }
}
