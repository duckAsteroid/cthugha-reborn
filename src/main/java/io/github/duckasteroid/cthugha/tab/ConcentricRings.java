package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Concentric rotating rings: each pixel is assigned to a ring based on its distance from
 * the centre (ring index = floor(r / ringWidth)), then rotated by ring_index * deltaAngle.
 * With alternation enabled adjacent rings spin in opposite directions — like meshing gears.
 * The flame blur smears colour along ring boundaries into glowing halos.
 */
public class ConcentricRings extends AbstractNode implements TranslateTableSource {

  public XYParam center = new XYParam("Center", 0, 1, 0.5);
  /** Thickness of each ring in pixels. */
  public DoubleParameter ringWidth = new DoubleParameter("Ring width", 5, 100, 30);
  /** Rotation applied per ring, in radians. */
  public DoubleParameter deltaAngle = new DoubleParameter("Angle per ring (rad)", -Math.PI, Math.PI, 0.3);
  /** When true, odd-numbered rings rotate in the opposite direction. */
  public BooleanParameter alternate = new BooleanParameter("Alternate direction", true);

  public ConcentricRings() {
    super("Concentric Rings");
    initChildren(center, ringWidth, deltaAngle, alternate);
  }

  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    double cx = width  * center.x.value;
    double cy = height * center.y.value;
    double rw = Math.max(ringWidth.value, 1.0);
    double da = deltaAngle.value;
    boolean alt = alternate.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double radius = sqrt(dx * dx + dy * dy);
      double theta = atan2(dy, dx);
      int ring = (int)(radius / rw);
      double angle = ring * da;
      if (alt && (ring & 1) == 1) angle = -angle;
      double newTheta = theta + angle;
      dst.put(dstOffset,     (short) TranslateTableSource.clamp((int)(cx + radius * cos(newTheta)), 0, width  - 1));
      dst.put(dstOffset + 1, (short) TranslateTableSource.clamp((int)(cy + radius * sin(newTheta)), 0, height - 1));
    };
  }
}
