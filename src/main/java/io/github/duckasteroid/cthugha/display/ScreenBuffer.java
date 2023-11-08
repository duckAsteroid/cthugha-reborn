package io.github.duckasteroid.cthugha.display;

import io.github.duckasteroid.cthugha.map.PaletteMap;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Represents our screen buffer - an array of byte pixels. Each representing the index into a color
 * {@link PaletteMap}
 */
public class ScreenBuffer {
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

  /**
   * Translate an X, Y coordinate into an index in the raster data
   * @param x the X coord
   * @param y the Y coord
   * @return the index within the raster array
   */
  public int index(int x, int y) {
    return ((y % height) * width) + (x % width);
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
