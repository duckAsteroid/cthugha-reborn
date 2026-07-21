package io.github.duckasteroid.cthugha.tab;

import java.nio.ShortBuffer;
import java.util.Random;

/**
 * A pre-configured pixel mapping function returned by {@link TabGenerator#generate}.
 * Captures all randomised initial state at generation time; {@code compute} is a pure
 * function of destination coordinates and may be called in any order or in parallel.
 */
@FunctionalInterface
public interface TabMapping {

  /**
   * Write the source (x, y) for destination pixel {@code (dstX, dstY)} into {@code dst}.
   *
   * @param dstX      destination pixel column
   * @param dstY      destination pixel row
   * @param dst       pre-allocated ShortBuffer (RG16UI layout: srcX then srcY per pixel)
   * @param dstOffset absolute short index to write into ({@code dstY * width + dstX} * 2)
   * @param rng       shared random source (safe for sequential use; use ThreadLocalRandom for parallel)
   */
  void compute(int dstX, int dstY, ShortBuffer dst, int dstOffset, Random rng);
}
