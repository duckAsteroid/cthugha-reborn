package io.github.duckasteroid.cthugha;

import io.github.duckasteroid.cthugha.map.PaletteMap;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
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
   * Write our buffer onto an image raster
   * @param imageRaster the raster to receive this buffer
   */
  public void render(BufferedImage imageRaster) {
    imageRaster.getGraphics().drawImage(getBufferedImageView(),0,0,null);
  }
  public IndexColorModel getIndexedColorModel() {
    return new IndexColorModel(8, 256, paletteMap.colors, 0, false, -1, DataBuffer.TYPE_BYTE);
  }
  public WritableRaster getWriteableRaster() {
    DataBufferByte dbb = new DataBufferByte(pixels, pixels.length);
    return Raster.createInterleavedRaster(dbb, width, height, width, 1, new int[]{0}, null);
  }

  public BufferedImage getBufferedImageView() {
    return new BufferedImage(getIndexedColorModel(), getWriteableRaster(), true, null);
  }

  public Graphics2D getGraphics() {
    return getBufferedImageView().createGraphics();
  }

  public Color getForegroundColor() {
    return new Color(paletteMap.colors[255]);
  }

  public Color getBackgroundColor() {
    return new Color(paletteMap.colors[0]);
  }

  public void copy(ScreenBuffer buffer) {
    System.arraycopy(buffer.pixels, 0, this.pixels, 0, buffer.pixels.length);
  }

  public Dimension getDimensions() {
    return new Dimension(width, height);
  }
}
