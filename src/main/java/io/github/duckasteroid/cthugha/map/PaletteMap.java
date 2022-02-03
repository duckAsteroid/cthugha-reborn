package io.github.duckasteroid.cthugha.map;

import java.awt.image.BufferedImage;

/**
 * A map of colors - usually loaded from a .MAP file
 */
public class PaletteMap {
  private final String name;
  public final int[] colors;

  public PaletteMap(String name, int[] colors) {
    this.name = name;
    this.colors = colors;
  }

  public String getName() {
    return name;
  }

  public BufferedImage getPaletteImage() {
    BufferedImage image = new BufferedImage(256, 1, BufferedImage.TYPE_INT_RGB);
    for(int i=0; i< colors.length; i++) {
      image.setRGB(i, 0, colors[i]);
    }
    return image;
  }
}
