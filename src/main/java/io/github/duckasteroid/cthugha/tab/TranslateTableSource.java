package io.github.duckasteroid.cthugha.tab;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * Interface to an object that produces pixel translation tables
 */
public interface TranslateTableSource {

  /**
   * Generate this translation table for the given screen dimensions
   * @param width  screen width in pixels
   * @param height screen height in pixels
   * @return flat-index translation table: result[dstIdx] = srcIdx
   */
  int[] generate(int width, int height);

  /**
   * Randomise the generating parameters of this translation
   */
  void randomise();

  Random random = new Random();

  default int Random(int range) {
    if (range > 0) {
      return random.nextInt(range);
    }
    return 0;
  }

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

  // -------------------------------------------------------------------------
  // Texture data helpers
  // -------------------------------------------------------------------------

  /**
   * Pack a flat-index translation table into a RG16UI ByteBuffer (2 × uint16 per pixel).
   * Each element table[i] is a flat pixel index; this encodes it as (srcX, srcY) uint16 pairs
   * suitable for upload to an OpenGL RG16UI texture.
   */
  static ByteBuffer tableToRG16Buffer(int[] table, int width, int height) {
    int maxIdx = width * height - 1;
    ByteBuffer bb = ByteBuffer.allocateDirect(table.length * 4);
    bb.order(ByteOrder.nativeOrder());
    for (int t : table) {
      int src = clamp(t, 0, maxIdx);
      bb.putShort((short) (src % width));  // srcX
      bb.putShort((short) (src / width));  // srcY
    }
    bb.flip();
    return bb;
  }
}
