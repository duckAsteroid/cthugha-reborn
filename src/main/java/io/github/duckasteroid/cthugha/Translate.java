package io.github.duckasteroid.cthugha;

import static java.lang.Math.abs;

import java.awt.Dimension;
import java.awt.geom.Dimension2D;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Performs pixel transforms on the screen buffer
 */
public class Translate {
  public final Dimension STANDARD = new Dimension(320,204);
  final Dimension dims;
  /**
   * The translate table for the buffer specifying the source pixel index for the destination pixel
   */
  final int[] table;

  public Translate(Dimension size, int[] table) {
    this.dims = size;
    if (table == null) throw new IllegalArgumentException("Table must not be null");
    if (table.length != size()) throw new IllegalArgumentException("Table size incorrect: "+
      table.length+" when "+size()+" required.");
    this.table = table;
  }

  public Translate(InputStream stream) throws IOException {
    this.dims = STANDARD;
    this.table = new int[size()];
    try(DataInputStream data = new DataInputStream(stream)) {
      for (int i = 0; i < table.length; i++) {
        int index = data.readUnsignedShort();
        if (index > size()) throw new IOException("Tab file high: "+index+" at "+i);
      }
    }
  }

  public Dimension getDimensions() {
    return dims;
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

  }

  public Translate stretch(Dimension target) {
    final int targetBuffSize = target.height * target.width;
    double xs,ys;
    int x,y,tp,ox,oy,dx,dy;
    int i,j;

    int[] dst = new int[targetBuffSize];

    //scale
    xs = (double)dims.width / target.width;
    ys = (double)dims.height / target.height;

    for (j=0;j<target.height;j++) {

      y = (int)(j*ys);
      if (y >= dims.height)
        y = dims.height - 1;

      for (i=0;i<target.width;i++) {

        x = (int) (i*xs );
        if (x >= dims.width)
          x = dims.width - 1;

        tp = table[x + y * dims.width];
        ox = tp % dims.width;
        oy = tp / dims.width;
        dx = (int) ((ox - x)/xs );
        dy = (int) ((oy - y)/ys );
        dst[i+j*target.width] = abs(i + dx + (j + dy) * target.width) % targetBuffSize;
      }
    }
    return new Translate(target, dst);
  }

  public int maximum() {
    return IntStream.of(table).max().orElseThrow(() -> new IllegalStateException("No data"));
  }

  @Override
  public String toString() {
    int size = Integer.toString(maximum()).length();
    char[] separator = new char[(dims.width * (size + 3)) + 4];
    Arrays.fill(separator, '-');
    final String separatorLine = new String(separator);

    StringBuilder sb = new StringBuilder(separatorLine + "\n");
    for(int y = 0; y < dims.height; y++) {
      for (int x = 0; x < dims.width; x++) {
        String cell = String.format("%0"+size+"d", table[(y * dims.width) + x]);
        sb.append(cell);
        if (x < dims.width -1) {
          sb.append(" | ");
        }
        else {
          sb.append(" |\n");
        }
      }
      sb.append(separatorLine + "\n");
    }
    return sb.toString();
  }
}
