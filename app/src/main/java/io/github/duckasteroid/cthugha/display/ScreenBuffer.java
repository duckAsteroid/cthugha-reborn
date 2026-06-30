package io.github.duckasteroid.cthugha.display;

import io.github.duckasteroid.cthugha.map.PaletteMap;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.numbers.fraction.Fraction;

/**
 * Represents our screen buffer - an array of byte pixels. Each representing the index into a color
 * {@link PaletteMap}
 */
public class ScreenBuffer {

  public interface PointWrapper {
    Point wrapped(Point p, Dimension constraint);
  }
  public enum PointWrapMode implements PointWrapper {
    LIMIT {
      @Override
      public Point wrapped(Point p, Dimension constraint) {
        return new Point(
          constrain(p.x, 0, constraint.width - 1),
          constrain(p.y, 0, constraint.height - 1));
      }


    },
    WRAP {
      @Override
      public Point wrapped(Point p, Dimension constraint) {
        return new Point(
          wrap(p.x, constraint.width),
          wrap(p.y, constraint.height));
      }
    }
  }
  public static int wrap(int value, int max) {
    value = value % max;
    if (value < 0) {
      return max + value;
    }
    return value;
  }
  public static int constrain(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
  // pixels wide
  public final int width;
  // pixels high
  public final int height;

  /**
   * An indexed colour model for each pixel in the buffer. Starts at 0,0 and proceeds width first.
   */
  public byte[] pixels;

  /**
   * The colors used for this image
   */
  public PaletteMap paletteMap;

  public ScreenBuffer(Dimension size) {
    this(size.width, size.height);
  }

  public ScreenBuffer(int width, int height) {
    this.width = width;
    this.height = height;
    this.pixels = new byte[width * height];
  }

  public Stream<Point> iterate() {
    return IntStream.range(0, pixels.length).mapToObj(this::fromIndex);
  }

  /**
   * Translate an X, Y coordinate into an index in the raster data
   * @param x the X coord
   * @param y the Y coord
   * @return the index within the raster array
   */
  public int index(int x, int y) {
    return ((y % height) * width) + (x % width);
  }

  public int toIndex(Point pt, PointWrapMode mode) {
    Point corrected = mode.wrapped(pt, getDimensions());
    return index(corrected.x, corrected.y);
  }

  public int toIndex(Fraction x, Fraction y) {
    return index(x.multiply(width).intValue(), y.multiply(height).intValue());
  }

  public Point fromIndex(int index) {
    int y = Math.min(height, index / width);
    int x = index % width;
    return new Point(x,y);
  }

  /**
   * Write our buffer onto an image
   * @param image the image to receive this buffer
   */
  public void render(BufferedImage image) {
    image.getGraphics().drawImage(getBufferedImageView(),0,0,null);
  }

  /**
   * Convert the {@link PaletteMap} into an {@link IndexColorModel}
   * @return the color model
   */
  public IndexColorModel getIndexedColorModel() {
    return new IndexColorModel(8, 256, paletteMap.colors, 0, false, -1, DataBuffer.TYPE_BYTE);
  }

  /**
   * Create a {@link WritableRaster} based on this screen buffers data
   * @return a WriteableRaster
   */
  public WritableRaster getWriteableRaster() {
    DataBufferByte dbb = new DataBufferByte(pixels, pixels.length);
    return Raster.createInterleavedRaster(dbb, width, height, width, 1, new int[]{0}, null);
  }

  /**
   * Create a {@link BufferedImage} view of this buffer
   * @return the image
   */
  public BufferedImage getBufferedImageView() {
    return new BufferedImage(getIndexedColorModel(), getWriteableRaster(), true, null);
  }

  /**
   * A 2D Graphics surface using the {@link #getBufferedImageView()}
   * @return a 2d graphics surface
   */
  public Graphics2D getGraphics() {
    return getBufferedImageView().createGraphics();
  }

  /**
   * The foreground color (index 255)
   * @return the foreground color
   */
  public Color getForegroundColor() {
    return new Color(paletteMap.colors[255]);
  }

  /**
   * The background color (index 0)
   * @return the background
   */
  public Color getBackgroundColor() {
    return new Color(paletteMap.colors[0]);
  }

  /**
   * Copy the pixels from the source into this buffer
   * @param source the source of pixels
   */
  public void copy(ScreenBuffer source) {
    System.arraycopy(source.pixels, 0, this.pixels, 0, source.pixels.length);
  }

  /**
   * The dimensions of this buffer
   * @return width and height
   */
  public Dimension getDimensions() {
    return new Dimension(width, height);
  }
}
