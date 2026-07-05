package io.github.duckasteroid.cthugha.tab.generators.lens;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

/**
 * Shatter / Voronoi zoom: randomly places N seed points and assigns each pixel to its
 * nearest seed (its Voronoi cell). Within each cell every pixel is sampled from a position
 * displaced away from the seed by zoomStrength, making the cell content appear to collapse
 * toward its seed. The flame pipeline then smears colour along cell boundaries, producing
 * a cracked-glass or shattered-mirror glow effect.
 *
 * Positive zoomStrength = content compresses toward seed (shatter/pull-in).
 * Negative zoomStrength = content expands away from seed (explosion).
 */
@AutoService(TabGenerator.class)
public class Shatter extends ParamNode implements TabGenerator {

  public IntegerParameter cells = new IntegerParameter("Cells", 2, 64, 20);
  public DoubleParameter zoomStrength = new DoubleParameter("Zoom strength", -1.0, 1.0, 0.12);

  public Shatter() {
    super("Shatter");
    initChildren(cells, zoomStrength);
    withResetAction();
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    int n = Math.min(cells.value, 64);
    int[] seedX = new int[n];
    int[] seedY = new int[n];
    for (int i = 0; i < n; i++) {
      seedX[i] = rng.nextInt(width);
      seedY[i] = rng.nextInt(height);
    }
    double zoom = zoomStrength.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      int nearest = 0;
      long nearestDist = Long.MAX_VALUE;
      for (int i = 0; i < n; i++) {
        long dx = dstX - seedX[i];
        long dy = dstY - seedY[i];
        long d = dx * dx + dy * dy;
        if (d < nearestDist) { nearestDist = d; nearest = i; }
      }
      int srcX = (int)(dstX + (dstX - seedX[nearest]) * zoom);
      int srcY = (int)(dstY + (dstY - seedY[nearest]) * zoom);
      dst.put(dstOffset,     (short) TabGenerator.wrap(srcX, width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap(srcY, height));
    };
  }
}
