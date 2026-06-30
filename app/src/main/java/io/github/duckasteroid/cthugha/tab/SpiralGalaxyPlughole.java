package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

public class SpiralGalaxyPlughole extends AbstractNode implements TranslateTableSource {

  /**
   * Angular rotation per frame. Positive = counter-clockwise.
   * In plughole mode this is multiplied by 20/r so the vortex spins faster near the centre.
   */
  public DoubleParameter angularSpeed = new DoubleParameter("Angular Speed", -1.0, 1.0, 0.05);

  /**
   * Radial displacement of the source pixel. Positive = source is pulled outward, so pixels
   * appear to flow inward (plughole). Negative = outward explosion.
   */
  public DoubleParameter radialSpeed = new DoubleParameter("Radial Speed", -10.0, 10.0, 1.5);

  /**
   * Plughole mode: angular speed scales as 1/r — fast near centre, slow at the edge.
   * Off = galaxy mode: constant angular speed at every radius.
   */
  public BooleanParameter plughole = new BooleanParameter("Plughole");

  /**
   * Number of spiral arms. 1 = simple vortex or galaxy. 2–8 = multi-arm galaxy pattern
   * achieved by modulating the angular shift with cos(arms * theta).
   */
  public IntegerParameter arms = new IntegerParameter("Arms", 1, 8, 1);

  /** Centre of the spiral as a fraction of the screen dimensions (0.5, 0.5 = middle). */
  public XYParam center = new XYParam("Center", 0.0, 1.0, 0.5);

  public SpiralGalaxyPlughole() {
    super("Spiral Galaxy Plughole");
    initChildren(angularSpeed, radialSpeed, plughole, arms, center);
  }

  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    double cx = width  * center.x.value;
    double cy = height * center.y.value;
    double angSpd = angularSpeed.value;
    double radSpd = radialSpeed.value;
    boolean ph = plughole.value;
    int numArms = arms.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double radius = sqrt(dx * dx + dy * dy);
      double theta = atan2(dy, dx);
      double dTheta = ph ? angSpd * 20.0 / max(radius, 1.0) : angSpd;
      if (numArms > 1) dTheta *= 0.5 + 0.5 * abs(cos(numArms * theta / 2.0));
      double newR = radius + radSpd;
      double newTheta = theta + dTheta;
      dst.put(dstOffset,     (short) TranslateTableSource.clamp((int)(cx + newR * cos(newTheta)), 0, width  - 1));
      dst.put(dstOffset + 1, (short) TranslateTableSource.clamp((int)(cy + newR * sin(newTheta)), 0, height - 1));
    };
  }
}
