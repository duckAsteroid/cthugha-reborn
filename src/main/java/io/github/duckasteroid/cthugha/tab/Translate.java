package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;

/**
 * Performs pixel transforms on the screen buffer
 */
public class Translate {
  final Dimension dims;
  /**
   * The translate table for the buffer specifying the source pixel index for the destination pixel
   */
  private int[] table;

  public Translate(Dimension size, int[] table) {
    this.dims = size;
    if (table == null) throw new IllegalArgumentException("Table must not be null");
    if (table.length != size()) throw new IllegalArgumentException("Table size incorrect: "+
      table.length+" when "+size()+" required.");
    this.table = table;
  }

  public int size() {
    return dims.width * dims.height;
  }

  /**
   * Translate the source pixel array to the destination pixel array.
   * @param source source pixels to read from (in 256 indexed colours)
   * @param destination destination pixels to write to
   */
  public void transform(byte[] source, byte[] destination) {
    byte[] copy;
    if (source == destination) {
      copy = new byte[source.length];
      System.arraycopy(source, 0, copy, 0, source.length);
    } else{
      copy = source;
    }
    for(int i = 0 ; i < source.length; i++) {
      int safePtr = Math.max(0, Math.min(table[i], source.length));
      destination[i] = copy[safePtr];
    }
  }

  public void changeTable(int[] newTable) {
    table = newTable;
  }

  public static int[][] changeTable(int[] oldTable, int[] newTable, int steps) {
    if (newTable == null || newTable.length != oldTable.length) throw new IllegalArgumentException();
    int[][] source = new int[steps + 1][newTable.length];
    source[0] = oldTable;
    for(int i=0; i<newTable.length; i++) {
      float value = source[0][i];
      float stepSize = (newTable[i] - value) / steps;
      for (int step = 1; step < source.length; step++) {
        value += stepSize;
        source[step][i] = (int)value;
      }
    }
    return source;
  }
}
