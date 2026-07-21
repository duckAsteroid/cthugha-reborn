package io.github.duckasteroid.cthugha.tab.generators.fractal;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

import io.github.duckasteroid.cthugha.params.ParamNode;
import io.github.duckasteroid.cthugha.params.transform.XYParam;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import java.nio.ShortBuffer;
import java.util.Random;

/**
 * Escape-time fractal displacement: each destination pixel is mapped to a point in the
 * complex plane and iterated under z = z² + c. Pixels that escape the bail-out radius are
 * pulled toward a source pixel whose angle and distance are driven by the escape iteration
 * count and the final orbit angle; pixels still inside the set after {@link #maxIterations}
 * steps are left undistorted. The result is a Mandelbrot (or Julia)-shaped island of stable
 * content surrounded by swirling, self-similar warp driven by the fractal boundary.
 *
 * In Mandelbrot mode the screen position becomes c (z₀ = 0). In Julia mode the screen
 * position becomes z₀ and c is the fixed {@link #juliaC} constant instead.
 */
@AutoService(TabGenerator.class)
public class Mandelbrot extends ParamNode implements TabGenerator {

  public XYParam center = new XYParam("Center", -2.0, 2.0, -0.5, 0.0).withPadControl();
  /** Pixels per unit of the complex plane. Higher = more magnified. */
  public DoubleParameter zoom = new DoubleParameter("Zoom", 50, 4000, 250);
  /** Escape-time iteration cap. */
  public IntegerParameter maxIterations = new IntegerParameter("Iterations", 8, 200, 40);
  /** Extra rotation applied to the displacement angle, scaled by escape time. */
  public DoubleParameter spin = new DoubleParameter("Spin", -Math.PI, Math.PI, 0.6);
  /** Maximum pixel displacement for a point that escapes on the very last iteration. */
  public DoubleParameter displacement = new DoubleParameter("Displacement", 0, 400, 120);
  /** When on, screen position is the Julia orbit seed z0 and {@link #juliaC} supplies c. */
  public BooleanParameter juliaMode = new BooleanParameter("Julia Mode");
  public XYParam juliaC = new XYParam("Julia Constant", -2.0, 2.0, -0.4, 0.6).withPadControl();

  public Mandelbrot() {
    super("Mandelbrot");
    initChildren(center, zoom, maxIterations, spin, displacement, juliaMode, juliaC);
    withResetAction();

    center.withDescription("Point in the complex plane mapped to the middle of the screen.");
    zoom.withDescription("Pixels per unit of the complex plane. Higher = more magnified.");
    maxIterations.withDescription("Escape-time iteration cap. Higher = sharper boundary, slower to generate.");
    spin.withDescription("Extra rotation applied to the displacement angle, scaled by escape time.");
    displacement.withDescription("Maximum pixel displacement for a point that escapes on the very last iteration.");
    juliaMode.withDescription("When on, screen position is the Julia orbit seed and Julia Constant supplies c, "
        + "instead of a Mandelbrot set (c = screen position, z0 = 0).");
    juliaC.withDescription("Fixed complex constant c used when Julia Mode is on.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double cx = width / 2.0;
    double cy = height / 2.0;
    double scale = 1.0 / zoom.value;
    double centerRe = center.x.value;
    double centerIm = center.y.value;
    int maxIter = maxIterations.value;
    double spinAmt = spin.value;
    double dispScale = displacement.value;
    boolean julia = juliaMode.value;
    double juliaRe = juliaC.x.value;
    double juliaIm = juliaC.y.value;

    return (dstX, dstY, dst, dstOffset, r) -> {
      double pRe = centerRe + (dstX - cx) * scale;
      double pIm = centerIm + (dstY - cy) * scale;

      double zRe, zIm, cRe, cIm;
      if (julia) {
        zRe = pRe; zIm = pIm;
        cRe = juliaRe; cIm = juliaIm;
      } else {
        zRe = 0; zIm = 0;
        cRe = pRe; cIm = pIm;
      }

      int i = 0;
      while (i < maxIter && zRe * zRe + zIm * zIm < 4.0) {
        double nRe = zRe * zRe - zIm * zIm + cRe;
        zIm = 2 * zRe * zIm + cIm;
        zRe = nRe;
        i++;
      }

      int srcX, srcY;
      if (i >= maxIter) {
        srcX = dstX;
        srcY = dstY;
      } else {
        double t = i / (double) maxIter;
        double angle = atan2(zIm, zRe) + t * spinAmt;
        double radius = t * dispScale;
        srcX = dstX + (int) (radius * cos(angle));
        srcY = dstY + (int) (radius * sin(angle));
      }
      dst.put(dstOffset,     (short) TabGenerator.clamp(srcX, 0, width  - 1));
      dst.put(dstOffset + 1, (short) TabGenerator.clamp(srcY, 0, height - 1));
    };
  }
}
