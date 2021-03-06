package io.github.duckasteroid.cthugha.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

class TimeStatistics extends Statistics {
  private Long lastTime = null;
  private final LongSupplier supplier;
  private final double oneSecond;

  private TimeStatistics(double oneSecond, LongSupplier supplier) {
    this.oneSecond = oneSecond;
    this.supplier = supplier;
  }

  public static TimeStatistics nano() {
    return new TimeStatistics(TimeUnit.SECONDS.toNanos(1), () -> System.nanoTime());
  }

  public static TimeStatistics milli() {
    return new TimeStatistics(TimeUnit.SECONDS.toMillis(1), () -> System.currentTimeMillis());
  }

  public void ping() {
    final long now = supplier.getAsLong();
    if (lastTime != null) {
      final long diff = now - lastTime;
      add(diff);
    }
    lastTime = now;
  }

  @Override
  public List<String> renderElements() {
    List<String> superElements = super.renderElements();
    List<String> renderElements = new ArrayList<>(superElements.size() + 1);
    renderElements.add("avg="+to2DP(oneSecond / avg())+" Hz");
    renderElements.addAll(superElements);
    return renderElements;
  }
}
