package io.github.duckasteroid.cthugha.map;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * A map of colors - usually loaded from a .MAP file
 */
public class PaletteMap {
  private final String name;
  public final int[] colors;

  public PaletteMap(String name, int[] colors) {
    this.name = name;
    this.colors = colors;
    ensureForegroundUnique();
  }

  private void ensureForegroundUnique() {
    final int color = colors[255];
    int channel = 0;
    while(Arrays.stream(colors).filter(c -> c == colors[255]).count() > 1) {
      Color c = new Color(color);
      switch (channel) {
        case 0:
          channel++;
          int red = c.getRed();
          red--;
          if (red < 0) {
            red = 255;
          }
          c = new Color(red, c.getGreen(), c.getBlue());
          break;
        case 1:
          channel++;
          int green = c.getGreen();
          green--;
          if (green < 0) {
            green = 255;
          }
          c = new Color(c.getRed(), green, c.getBlue());
          break;
        default:
          channel = 0;
          int blue = c.getGreen();
          blue--;
          if (blue < 0) {
            blue = 255;
          }
          c = new Color(c.getRed(), c.getGreen(), blue);
          break;
      }
      colors[255] = c.getRGB();
    }
  }

  public String getName() {
    return name;
  }

  public BufferedImage getPaletteImage() {
    return getPaletteImage(1);
  }

  public BufferedImage getPaletteImage(int height) {
    BufferedImage image = new BufferedImage(256, height, BufferedImage.TYPE_INT_RGB);
    for(int i=0; i< colors.length; i++) {
      for (int y = 0; y < height; y++) {
        image.setRGB(i, y, colors[i]);
      }
    }
    return image;
  }
}
