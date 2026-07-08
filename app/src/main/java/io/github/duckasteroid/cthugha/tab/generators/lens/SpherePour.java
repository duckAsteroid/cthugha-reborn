package io.github.duckasteroid.cthugha.tab.generators.lens;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;
import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.transform.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import java.util.Random;

/**
 * Outward flow that mimics content pouring off the top of a sphere: fastest at the centre,
 * decelerating smoothly to zero at the sphere rim, following the sphere-surface projection
 * cos(θ) = sqrt(1 − r²).
 *
 * Pixels outside the sphere radius are clamped to zero displacement (stationary).
 */
@AutoService(TabGenerator.class)
public class SpherePour extends ParamNode implements TabGenerator {

  public XYParam center = new XYParam("Center", 0, 1, 0.5);
  /** Maximum displacement in pixels applied at the very centre of the sphere. */
  public DoubleParameter maxSpeed = new DoubleParameter("Max Speed", 0.5, 30.0, 4.0);
  /** Sphere boundary radius as a fraction of the shorter screen dimension. */
  public DoubleParameter sphereRadius = new DoubleParameter("Sphere Radius", 0.1, 2.0, 1.0);

  public SpherePour() {
    super("Sphere Pour");
    initChildren(center, maxSpeed, sphereRadius);
    withResetAction();
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double cx = width  * center.x.value;
    double cy = height * center.y.value;
    double boundaryPx = Math.min(width, height) * 0.5 * sphereRadius.value;
    double speed = maxSpeed.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double dist = Math.sqrt(dx * dx + dy * dy);
      int srcX, srcY;
      if (dist < 1e-6) {
        srcX = (int) cx;
        srcY = (int) cy;
      } else {
        double rNorm = Math.min(dist / boundaryPx, 1.0);
        // cos(θ) of sphere-surface angle: 1 at centre, 0 at rim
        double displacement = speed * Math.sqrt(1.0 - rNorm * rNorm);
        // Source offset toward centre → content appears to flow outward each frame
        srcX = (int) Math.round(dstX - dx / dist * displacement);
        srcY = (int) Math.round(dstY - dy / dist * displacement);
      }
      dst.put(dstOffset,     (short) TabGenerator.clamp(srcX, 0, width  - 1));
      dst.put(dstOffset + 1, (short) TabGenerator.clamp(srcY, 0, height - 1));
    };
  }
}
