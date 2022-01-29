package io.github.duckasteroid.cthugha.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Factory for named statistics instances
 */
public class StatsFactory {
  public static boolean enabled = Boolean.getBoolean("cthugha.stats.enabled");
  public static boolean nanos = System.getProperty("cthugha.stats.time.nanos", "true").equals("true");
  private static HashMap<String, Stats> statistics = new HashMap<>();

  public static Stats stats(String name) {
    if (!enabled) return new Stats() {};

    if (!statistics.containsKey(name)) {
      Statistics s = new Statistics();
      statistics.put(name, s);
    }
    return statistics.get(name);
  }

  public static Stats deltaStats(String name) {
    return deltaStats(name, nanos);
  }

  public static Stats deltaStats(String name, boolean nanos) {
    if (!enabled) return new Stats() {};

    if (!statistics.containsKey(name)) {
      TimeStatistics s = nanos ? TimeStatistics.nano() : TimeStatistics.milli();
      statistics.put(name, s);
    }
    return statistics.get(name);
  }

  public static String getStatisticsSummary() {
    StringBuilder sb = new StringBuilder("Stats:\n");
    ArrayList<String> keys = new ArrayList<>(statistics.keySet());
    keys.sort(String::compareTo);
    for(String key : keys) {
      Stats stats = statistics.get(key);
      sb.append('\t').append(key).append(':').append(stats.toString()).append('\n');
    }
    return sb.toString();
  }
}
