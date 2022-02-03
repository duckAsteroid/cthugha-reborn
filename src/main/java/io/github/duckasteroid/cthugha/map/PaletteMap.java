package io.github.duckasteroid.cthugha.map;

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
}
