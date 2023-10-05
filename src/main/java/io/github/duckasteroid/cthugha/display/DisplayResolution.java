package io.github.duckasteroid.cthugha.display;

import java.awt.Dimension;

/**
 * An enumeration of standard (named) display resolutions
 */
public enum DisplayResolution {
  CGA(320, 200),
  HVGA(480,320),
  VGA(640, 480),
  SVGA(800,600),
  XGA(1024,768),
  HD(1280, 720),
  FULL_HD(1920,1080),
  UHD_4K(3840,2160);

  private final int width;
  private final int height;

  DisplayResolution(int width, int height) {
    this.width = width;
    this.height = height;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public Dimension getDimensions() {
    return new Dimension(width, height);
  }
}
