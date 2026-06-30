package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

/**
 * Smooth gravitational attractor field: multiple point masses pull pixels toward them
 * with a 1/r² force. Unlike {@link Shatter} there are no hard Voronoi cell edges —
 * the displacement field is continuous, producing organic flowing distortion that pools
 * around each well. The softening term prevents singularities at the well centres.
 *
 * Positive strength pulls content toward the wells; negative pushes it away.
 */
public class GravityWells extends AbstractNode implements TranslateTableSource {

  public IntegerParameter numWells = new IntegerParameter("Wells", 1, 3, 2);
  public XYParam well1 = new XYParam("Well 1", 0, 1, 0.3);
  public XYParam well2 = new XYParam("Well 2", 0, 1, 0.7);
  public XYParam well3 = new XYParam("Well 3", 0, 1, 0.5);
  /** Gravitational pull strength. Higher = stronger distortion. */
  public DoubleParameter strength  = new DoubleParameter("Strength",  0, 60000, 18000);
  /** Softening radius² — prevents singularities at the well centre. */
  public DoubleParameter softening = new DoubleParameter("Softening", 100, 8000, 1500);

  public GravityWells() {
    super("Gravity Wells");
    well3.y.setValue(0.2);
    initChildren(numWells, well1, well2, well3, strength, softening);
  }

  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    int n = Math.min(numWells.value, 3);
    XYParam[] wells = {well1, well2, well3};
    double[] wx = new double[n];
    double[] wy = new double[n];
    for (int i = 0; i < n; i++) {
      wx[i] = width  * wells[i].x.value;
      wy[i] = height * wells[i].y.value;
    }
    double str = strength.value;
    double soft = softening.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double totalDx = 0, totalDy = 0;
      for (int i = 0; i < n; i++) {
        double dx = wx[i] - dstX;
        double dy = wy[i] - dstY;
        double force = str / (dx * dx + dy * dy + soft);
        totalDx += force * dx;
        totalDy += force * dy;
      }
      dst.put(dstOffset,     (short) TranslateTableSource.wrap((int)(dstX + totalDx), width));
      dst.put(dstOffset + 1, (short) TranslateTableSource.wrap((int)(dstY + totalDy), height));
    };
  }
}
