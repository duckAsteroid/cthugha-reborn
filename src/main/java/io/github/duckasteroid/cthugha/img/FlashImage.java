package io.github.duckasteroid.cthugha.img;

import java.awt.image.BufferedImage;

public class FlashImage {
  private final BufferedImage image;
  private int displayFor;

  public FlashImage(BufferedImage image, int displayFor) {
    this.image = image;
    this.displayFor = displayFor;
  }

  public BufferedImage getImage() {
    return image;
  }

  public int getDisplayFor() {
    return displayFor--;
  }


}
