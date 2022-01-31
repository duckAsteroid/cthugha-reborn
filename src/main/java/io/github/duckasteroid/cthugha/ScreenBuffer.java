package io.github.duckasteroid.cthugha;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

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

  public int[] colors = new int[256];

  public ScreenBuffer(Dimension size) {
    this(size.width, size.height);
  }

  public ScreenBuffer(int width, int height) {
    this.width = width;
    this.height = height;
    this.pixels = new byte[width * height];
  }

  public int index(int x, int y) {
    return ((y % height) * width) + (x % width);
  }

  public void render(WritableRaster imageRaster) {
    for(int y=0; y < height; y++) {
      for(int x=0; x < width; x++) {
        int pixelIndex = index(x,y);
        int colorIndex = pixels[pixelIndex] & 0xff;
        int color = colors[colorIndex];
        imageRaster.setDataElements(x,y,new int[]{color});
      }
    }
  }

  public BufferedImage getBufferedImageView() {
    DataBufferByte dbb = new DataBufferByte(pixels, 1);
    IndexColorModel icm = new IndexColorModel(8, 256, colors, 0, false, -1, DataBuffer.TYPE_BYTE);
    WritableRaster raster =
      Raster.createInterleavedRaster(dbb, width, height, width, 1, new int[]{0}, null);
    return new BufferedImage(icm, raster, true, null);
  }

  public class Pixel {
    private final int x;
    private final int y;

    public Pixel(int x, int y) {
      this.x = x % width;
      this.y = y % height;
    }

    public Pixel getAbove() {
      return new Pixel(x, y - 1);
    }

    public boolean hasAbove() {
      return y > 0;
    }

    public Pixel getBelow() {
      return new Pixel(x, y + 1);
    }

    public boolean hasBelow() {
      return y < height;
    }

    public Pixel getLeft() {
      return new Pixel(x - 1, y);
    }

    public boolean hasLeft() {
      return x > 0;
    }

    public Pixel getRight() {
      return new Pixel(x + 1, y);
    }

    public boolean hasRight() {
      return x < width;
    }
  }

  public void copy(ScreenBuffer buffer) {
    System.arraycopy(buffer.pixels, 0, this.pixels, 0, buffer.pixels.length);
  }
}
