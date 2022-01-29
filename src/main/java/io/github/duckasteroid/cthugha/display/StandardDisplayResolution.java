package io.github.duckasteroid.cthugha.display;

public enum StandardDisplayResolution {
  QVGA(320,240),
  VGA(640,480),
  SVGA(800,600),
  XGA(1024,768),
  LAPTOP(3840,2160);

  private final int width;
  private final int height;

  StandardDisplayResolution(int width, int height) {
    this.width = width;
    this.height = height;
  }
}
