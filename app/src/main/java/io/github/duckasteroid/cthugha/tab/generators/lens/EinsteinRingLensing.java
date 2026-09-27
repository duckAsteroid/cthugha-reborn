package io.github.duckasteroid.cthugha.tab.generators.lens;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.PI;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.transform.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Point-mass gravitational lensing: applies the classic lens equation
 * {@code β = θ - θ_E²/θ} to invert what a Schwarzschild lens does to a background image.
 * Unlike {@link GravityWells}' single-step Newtonian pull, the source radius crosses zero
 * exactly at the Einstein radius {@link #ringRadius} — content from a vanishingly small
 * region around the lens centre is smeared all the way around that radius, producing a bright
 * Einstein ring. Just inside the ring the source radius goes negative, which is handled by
 * flipping the sampling angle 180°, mirroring the true lensing topology where a second,
 * inverted image forms inside the ring.
 */
@AutoService(TabGenerator.class)
public class EinsteinRingLensing extends ParamNode implements TabGenerator {

  public XYParam center = new XYParam("Center", 0, 1, 0.5).withPadControl();
  /** Einstein radius, as a fraction of min(width, height). */
  public DoubleParameter ringRadius = new DoubleParameter("Ring Radius", 0.02, 0.6, 0.18);
  /** Softens the singularity at the lens centre. */
  public DoubleParameter softening = new DoubleParameter("Core Softening", 0.5, 50, 4);

  public EinsteinRingLensing() {
    super("Einstein Ring");
    initChildren(center, ringRadius, softening);
    withResetAction();

    center.withDescription("Position of the lens centre, as a fraction of screen width/height.");
    ringRadius.withDescription("Einstein radius: the image-plane radius at which the source radius reaches zero and content is smeared into a ring, as a fraction of the smaller screen dimension.");
    softening.withDescription("Softens the singularity at the lens centre; higher values blur the innermost image into a soft core instead of a sharp point.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double cx = width * center.x.value;
    double cy = height * center.y.value;
    double rE = ringRadius.value * Math.min(width, height);
    double soft = softening.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double rImg = sqrt(dx * dx + dy * dy) + soft;
      double theta = atan2(dy, dx);
      double rSrc = rImg - (rE * rE) / rImg;
      double srcR = Math.abs(rSrc);
      double srcTheta = rSrc >= 0 ? theta : theta + PI;
      int srcX = (int) Math.round(cx + srcR * cos(srcTheta));
      int srcY = (int) Math.round(cy + srcR * sin(srcTheta));
      dst.put(dstOffset,     (short) TabGenerator.clamp(srcX, 0, width  - 1));
      dst.put(dstOffset + 1, (short) TabGenerator.clamp(srcY, 0, height - 1));
    };
  }
}
