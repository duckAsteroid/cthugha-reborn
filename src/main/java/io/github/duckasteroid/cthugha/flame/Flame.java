package io.github.duckasteroid.cthugha.flame;

import io.github.duckasteroid.cthugha.ScreenBuffer;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public class Flame {
  private static Kernel averagingKernel(int length) {
    return averagingKernel(length, length);
  }
  private static Kernel averagingKernel(int x, int y) {
    final int size = x * y;
    final float v = .99f / size;
    final float[] matrix = new float[size];
    Arrays.fill(matrix, v);
    return new Kernel(x, y, matrix);
  }
  static RenderingHints renderingHints() {
    Pair<RenderingHints.Key, ?>[] settings = new Pair[] {
      Pair.of(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF),
      Pair.of(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE),
      Pair.of(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR),
      Pair.of(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
    };
    Map<RenderingHints.Key, Object> map = new HashMap<>();
    Arrays.stream(settings).forEach(pair -> map.put(pair.getKey(), pair.getValue()));
    return new RenderingHints(map);
  }
  private final ConvolveOp convolveOp = new ConvolveOp(
    averagingKernel(6),
    ConvolveOp.EDGE_NO_OP,
    renderingHints());

  public void flame(ScreenBuffer screen, WritableRaster target) {
    convolveOp.filter(screen.getBufferedImageView().getRaster(), target);
  }
}
