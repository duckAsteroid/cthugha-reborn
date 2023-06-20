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
  private static Kernel createGaussianKernel(int radius) {
    final int r = (int) Math.ceil(radius);
    final int rows = r * 2 + 1;
    final float[] kernelData = new float[rows * rows];
    final double sigma = radius / 3;
    final double sigma22 = 2 * sigma * sigma;
    final double sqrtPiSigma22 = Math.sqrt(Math.PI * sigma22);
    final double radius2 = radius * radius;
    double total = 0;
    int index = 0;
    double distance2;
    int x, y;
    for (y = -r; y <= r; y++) {
      for (x = -r; x <= r; x++) {
        distance2 = 1.0 * x * x + 1.0 * y * y;
        if (distance2 > radius2) {
          kernelData[index] = 0;
        } else {
          kernelData[index] = (float) (Math.exp(-distance2
            / sigma22) / sqrtPiSigma22);
        }
        total += kernelData[index];
        ++index;
      }
    }
    for (index = 0; index < kernelData.length; index++) {
      kernelData[index] /= total;
    }

    return new Kernel(rows, rows, kernelData);
  }

  static RenderingHints renderingHints() {
    Pair<RenderingHints.Key, ?>[] settings = new Pair[] {
      Pair.of(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF),
      Pair.of(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE),
      Pair.of(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR),
      Pair.of(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED),
      Pair.of(RenderingHints.KEY_RESOLUTION_VARIANT, RenderingHints.VALUE_RESOLUTION_VARIANT_DPI_FIT)
    };
    Map<RenderingHints.Key, Object> map = new HashMap<>();
    Arrays.stream(settings).forEach(pair -> map.put(pair.getKey(), pair.getValue()));
    return new RenderingHints(map);
  }
  private final ConvolveOp convolveOp = new ConvolveOp(
    averagingKernel(3),
    ConvolveOp.EDGE_NO_OP,
    renderingHints());

  public void flame(ScreenBuffer screen, WritableRaster target) {
    convolveOp.filter(screen.getBufferedImageView().getRaster(), target);
  }
}
