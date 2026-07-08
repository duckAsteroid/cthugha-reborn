package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Holds the pre-allocated RG16UI translation map buffer and fills it from a {@link TabMapping}.
 */
public class TabBuffer {
  public final int width;
  public final int height;
  private final ByteBuffer byteBuffer;
  private final ShortBuffer shortBuffer;

  public TabBuffer(Dimension size) {
    this.width = size.width;
    this.height = size.height;
    this.byteBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
    this.shortBuffer = byteBuffer.asShortBuffer();
  }

  /**
   * Fills the translation map in parallel, one row per task.
   * {@link TabMapping#compute} uses absolute ShortBuffer indices so concurrent row
   * writes never overlap. Each worker thread gets its own {@link ThreadLocalRandom}
   * rather than sharing the caller's {@code rng}, which is not thread-safe.
   */
  public void fill(TabMapping mapper, Random rng) {
    IntStream.range(0, height).parallel().forEach(y -> {
      Random rowRng = ThreadLocalRandom.current();
      for (int x = 0; x < width; x++) {
        mapper.compute(x, y, shortBuffer, (y * width + x) * 2, rowRng);
      }
    });
    byteBuffer.rewind();
  }

  public ByteBuffer getBuffer() {
    return byteBuffer;
  }

  /**
   * Creates a {@link TabBuffer} pre-loaded from raw little-endian RG16UI bytes
   * (as written by {@link TabStore}).  The byte array is copied into a new
   * native-order direct buffer ready for GPU upload.
   */
  public static TabBuffer fromBytes(int width, int height, byte[] rawData) {
    TabBuffer t = new TabBuffer(new Dimension(width, height));
    t.byteBuffer.put(rawData);
    t.byteBuffer.rewind();
    return t;
  }
}
