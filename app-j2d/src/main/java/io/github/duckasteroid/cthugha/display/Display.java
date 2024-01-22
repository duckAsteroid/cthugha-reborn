package io.github.duckasteroid.cthugha.display;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public interface Display {
  void init();
  BufferedImage getRenderImage();
  Graphics2D getGraphics();
  void render();
}
