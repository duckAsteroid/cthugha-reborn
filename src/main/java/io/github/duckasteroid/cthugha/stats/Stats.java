package io.github.duckasteroid.cthugha.stats;

public interface Stats {
  default void add(long value) {}
  default void ping() {}
}
