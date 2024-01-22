package io.github.duckasteroid.cthugha.flame;

import io.github.duckasteroid.cthugha.display.ScreenBuffer;
import java.awt.image.WritableRaster;

public interface Flame {
  void flame(ScreenBuffer screen, WritableRaster target);
}
