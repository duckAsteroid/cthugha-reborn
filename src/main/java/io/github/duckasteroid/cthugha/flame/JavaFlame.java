package io.github.duckasteroid.cthugha.flame;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import java.awt.image.WritableRaster;
import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;

public class JavaFlame implements Flame{

  public void flame(final ScreenBuffer screen, final WritableRaster target) {
    int ib = (screen.height-1) * screen.width; // bottom row start index
    // wipe out top and bottom lines
    for(int i=0; i < screen.width; i++) {
      screen.pixels[i] = 0;
      screen.pixels[ib + i] = 0;
    }
    ib = 0;
    // wipe out left and right columns
    for(int i=0; i < screen.height; i++) {
      screen.pixels[ ib ] = 0;
      screen.pixels[ ib + screen.width - 1 ] = 0;
      ib += screen.width;
    }

    IntStream.range(1, (screen.height - 1) * (screen.width - 1)).parallel().forEach(i -> {

      int p1 = screen.pixels[ i+1 ]; // right
      int p2 = screen.pixels[ i + screen.width ]; // below
      int p3 = screen.pixels[ i + screen.width + 1]; // below right
      int p4 = screen.pixels[ i ]; // target pixel itself

      if( p1 < 0 ) p1 += 255;
      if( p2 < 0 ) p2 += 255;
      if( p3 < 0 ) p3 += 255;
      if( p4 < 0 ) p4 += 255;

      // average of pixels
      int s = p1 + p2 + p3 + p4;
      s = s / 4;

      if(s > 0) s--;
      screen.pixels[ i ] = (byte) s;
    });
  }
}
