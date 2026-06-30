package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.PI;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

/**
 * Kaleidoscope: divides the screen into N equal angular sectors around a centre point,
 * then folds every pixel into the first sector by mirroring. All sectors reflect the same
 * wedge of source content, producing radial symmetry. With the flame and palette pipeline
 * the colour bands smear symmetrically across sector boundaries.
 */
public class Kaleidoscope extends AbstractNode implements TranslateTableSource {

  public XYParam center = new XYParam("Center", 0, 1, 0.5);
  public IntegerParameter arms = new IntegerParameter("Arms", 2, 16, 6);

  public Kaleidoscope() {
    super("Kaleidoscope");
    initChildren(center, arms);
  }

  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    double cx = width  * center.x.value;
    double cy = height * center.y.value;
    double sectorAngle = 2.0 * PI / Math.max(arms.value, 2);
    double halfSector = sectorAngle / 2.0;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double radius = sqrt(dx * dx + dy * dy);
      double theta = ((atan2(dy, dx) % sectorAngle) + sectorAngle) % sectorAngle;
      if (theta > halfSector) theta = sectorAngle - theta;
      dst.put(dstOffset,     (short) TranslateTableSource.clamp((int)(cx + radius * cos(theta)), 0, width  - 1));
      dst.put(dstOffset + 1, (short) TranslateTableSource.clamp((int)(cy + radius * sin(theta)), 0, height - 1));
    };
  }
}
