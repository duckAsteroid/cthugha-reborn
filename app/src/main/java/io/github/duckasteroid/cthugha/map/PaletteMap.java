package io.github.duckasteroid.cthugha.map;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * A 256-entry RGB colour lookup table loaded from a {@code .MAP} file.
 *
 * Each entry is a packed {@code 0xRRGGBB} integer. The active palette is held by
 * {@code JCthugha} and uploaded to the GPU as a 256×1 RGBA texture ({@code paletteTex})
 * whenever {@code paletteDirty} is set. {@code PaletteRenderer} uses this texture to
 * convert R16 palette-index values in {@code displayTex} to on-screen RGBA colours.
 *
 * <p>The constructor calls {@link #ensureForegroundUnique()} to guarantee the last entry
 * (used as the "foreground" wave colour) is unique within the palette.</p>
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
    final int last = colors.length - 1;
    final int color = colors[last];
    int channel = 0;
    while(Arrays.stream(colors).filter(c -> c == colors[last]).count() > 1) {
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
      colors[last] = c.getRGB();
    }
  }

  public int size() {
    return colors.length;
  }

  public String getName() {
    return name;
  }

  public BufferedImage getPaletteImage() {
    return getPaletteImage(1);
  }

  public BufferedImage getPaletteImage(int height) {
    BufferedImage image = new BufferedImage(colors.length, height, BufferedImage.TYPE_INT_RGB);
    for(int i=0; i< colors.length; i++) {
      for (int y = 0; y < height; y++) {
        image.setRGB(i, y, colors[i]);
      }
    }
    return image;
  }
}
