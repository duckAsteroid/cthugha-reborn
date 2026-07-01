package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Random;

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

  public void fill(TabMapping mapper, Random rng) {
    shortBuffer.rewind();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        mapper.compute(x, y, shortBuffer, (y * width + x) * 2, rng);
      }
    }
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
