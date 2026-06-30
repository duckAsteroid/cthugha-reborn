package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.Node;

import java.util.Random;


/**
 * Interface to an object that produces pixel translation tables
 */
public interface TranslateTableSource extends Node {

  /**
   * Capture current parameter state, perform any randomised setup, and return a
   * {@link PixelMapper} that maps each destination pixel to its source pixel.
   * The returned mapper holds all state it needs and may be invoked in parallel.
   *
   * @param width  screen width in pixels
   * @param height screen height in pixels
   * @param rng    random source for both setup and per-pixel noise
   * @return a configured mapper ready for filling a ShortBuffer
   */
  PixelMapper generate(int width, int height, Random rng);

  /**
   * Randomise the generating parameters of this translation.
   *
   * @param rng the random source to use (typically {@code ctx.getRandom()})
   */
  void randomise(Random rng);

  // -------------------------------------------------------------------------
  // Static geometry helpers
  // -------------------------------------------------------------------------

  /** Convert (x, y) pixel coordinates to a flat array index. */
  static int index(int x, int y, int width) {
    return y * width + x;
  }

  /** Wrap a coordinate into [0, max). */
  static int wrap(int v, int max) {
    v = v % max;
    return v < 0 ? max + v : v;
  }

  /** Clamp v to [lo, hi]. */
  static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }
}
