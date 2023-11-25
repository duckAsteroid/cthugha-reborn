package io.github.duckasteroid.cthugha.flame;

import static io.github.duckasteroid.cthugha.display.ScreenBuffer.PointWrapMode.WRAP;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import java.awt.Point;
import java.awt.image.WritableRaster;
import java.util.stream.IntStream;

public class JavaFlame implements Flame{

  public BooleanParameter zeroEdge = new BooleanParameter("Zero edge", false);
  public void flame(final ScreenBuffer screen, final WritableRaster target) {
    int ib = (screen.height-1) * screen.width; // bottom row start index
    // wipe out top and bottom lines
    if (zeroEdge.value) {
      for (int i = 0; i < screen.width; i++) {
        screen.pixels[i] = 0;
        screen.pixels[ib + i] = 0;
      }
      ib = 0;
      // wipe out left and right columns
      for (int i = 0; i < screen.height; i++) {
        screen.pixels[ib] = 0;
        screen.pixels[ib + screen.width - 1] = 0;
        ib += screen.width;
      }
    }

    screen.iterate().parallel().forEach(pixel -> {
      int index = screen.toIndex(pixel, WRAP);

      int p1 = screen.pixels[screen.toIndex(offset(pixel, 1, 0), WRAP) ]; // right
      int p2 = screen.pixels[screen.toIndex(offset(pixel, -1, 0), WRAP)]; // left
      int p3 = screen.pixels[screen.toIndex(offset(pixel, 0, 1), WRAP)]; // above
      int p4 = screen.pixels[screen.toIndex(offset(pixel, 0, -1), WRAP)]; // below
      int p5 = screen.pixels[index];

      if( p1 < 0 ) p1 += 255;
      if( p2 < 0 ) p2 += 255;
      if( p3 < 0 ) p3 += 255;
      if( p4 < 0 ) p4 += 255;
      if( p5 < 0 ) p5 += 255;

      // average of pixels
      int s = p1 + p2 + p3 + p4 + p5;
      s = s / 5;
      s--;
      if (s < 0) s = 0;
      screen.pixels[ index ] = (byte) s;

    });
  }

  public static Point offset(Point src, int xOff, int yOff) {
    return new Point(src.x + xOff, src.y + yOff);
  }
}
