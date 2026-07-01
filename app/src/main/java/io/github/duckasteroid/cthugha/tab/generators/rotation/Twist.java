package io.github.duckasteroid.cthugha.tab.generators.rotation;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.PI;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Smooth whirl / twist: each pixel is rotated around the centre by an angle that decays
 * exponentially with radius — dTheta = maxAngle * exp(-r / falloff). The centre gets the
 * full twist and the edge is barely displaced, producing a "stirring" effect rather than
 * the hard vortex of SpiralGalaxyPlughole.
 */
@AutoService(TabGenerator.class)
public class Twist extends AbstractNode implements TabGenerator {

  public XYParam center = new XYParam("Center", 0, 1, 0.5);
  /** Peak rotation at r=0, in radians. Positive = counter-clockwise. */
  public DoubleParameter maxAngle = new DoubleParameter("Max angle (rad)", -PI, PI, PI / 3.0);
  /** Radius (pixels) at which the twist drops to ~37% of its peak. */
  public DoubleParameter falloff = new DoubleParameter("Falloff radius", 10, 600, 120);

  public Twist() {
    super("Twist");
    initChildren(center, maxAngle, falloff);
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double cx = width  * center.x.value;
    double cy = height * center.y.value;
    double fo = Math.max(falloff.value, 1.0);
    double ma = maxAngle.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double radius = sqrt(dx * dx + dy * dy);
      double newTheta = atan2(dy, dx) + ma * exp(-radius / fo);
      dst.put(dstOffset,     (short) TabGenerator.clamp((int)(cx + radius * cos(newTheta)), 0, width  - 1));
      dst.put(dstOffset + 1, (short) TabGenerator.clamp((int)(cy + radius * sin(newTheta)), 0, height - 1));
    };
  }
}
