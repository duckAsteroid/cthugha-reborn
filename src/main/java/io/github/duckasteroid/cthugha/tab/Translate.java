package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Random;

/**
 * Holds the pre-allocated RG16UI translation map buffer and fills it from a {@link PixelMapper}.
 */
public class Translate {
  public final int width;
  public final int height;
  private final ByteBuffer byteBuffer;
  private final ShortBuffer shortBuffer;

  public Translate(Dimension size) {
    this.width = size.width;
    this.height = size.height;
    this.byteBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
    this.shortBuffer = byteBuffer.asShortBuffer();
  }

  public void fill(PixelMapper mapper, Random rng) {
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
}
