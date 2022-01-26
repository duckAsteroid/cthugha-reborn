package io.github.duckasteroid.cthugha;

import java.awt.Dimension;

/**
 * Represents our screen buffer (a raster image)
 */
public class ScreenBuffer {
  public final int width;
  public final int height;

  /**
   * An indexed colour model for each pixel in the buffer
   */
  public byte[] pixels;

  public ScreenBuffer(Dimension size) {
    this(size.width, size.height);
  }
  public ScreenBuffer(int width, int height) {
    this.width = width;
    this.height = height;
    this.pixels = new byte[width * height];
  }

  public int index(int x, int y) {
    return (y * width) + x;
  }

  public void copy(ScreenBuffer buffer) {
    System.arraycopy(buffer.pixels, 0, this.pixels, 0, buffer.pixels.length);
  }
}
