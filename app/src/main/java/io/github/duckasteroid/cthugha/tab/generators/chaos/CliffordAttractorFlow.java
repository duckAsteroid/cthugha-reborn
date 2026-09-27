package io.github.duckasteroid.cthugha.tab.generators.chaos;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.transform.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

/**
 * Uses the Clifford strange attractor map
 * {@code (x, y) -> (sin(ay) + c·cos(ax), sin(bx) + d·cos(by))} as a flow field rather than
 * a point-cloud renderer: each pixel is mapped into attractor space, iterated a few times,
 * and the resulting displacement (how far that point moved under the map) becomes the
 * pixel's own displacement. Because the map has no fixed symmetry axis, the resulting warp
 * is organic and asymmetric — flowing swirls with no repeating structure, unlike the
 * radial/spiral distortions elsewhere in this package.
 */
@AutoService(TabGenerator.class)
public class CliffordAttractorFlow extends ParamNode implements TabGenerator {

  public XYParam center = new XYParam("Center", -3, 3, 0, 0).withPadControl();
  public DoubleParameter zoom = new DoubleParameter("Zoom", 50, 800, 220);
  public DoubleParameter a = new DoubleParameter("A", -3, 3, -1.4);
  public DoubleParameter b = new DoubleParameter("B", -3, 3, 1.6);
  public DoubleParameter c = new DoubleParameter("C", -3, 3, 1.0);
  public DoubleParameter d = new DoubleParameter("D", -3, 3, 0.7);
  public IntegerParameter iterations = new IntegerParameter("Iterations", 1, 6, 2);
  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 400, 120);

  public CliffordAttractorFlow() {
    super("Clifford Attractor Flow");
    initChildren(center, zoom, a, b, c, d, iterations, amplitude);
    withResetAction();

    center.withDescription("Point in attractor space mapped to the middle of the screen.");
    zoom.withDescription("Pixels per unit of attractor space. Higher = more magnified.");
    a.withDescription("Clifford attractor parameter a.");
    b.withDescription("Clifford attractor parameter b.");
    c.withDescription("Clifford attractor parameter c.");
    d.withDescription("Clifford attractor parameter d.");
    iterations.withDescription("Number of attractor map iterations applied before measuring displacement; more iterations fold the flow further, producing finer swirl detail.");
    amplitude.withDescription("Pixel displacement scale applied to the attractor map's per-pixel movement.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double cx = width / 2.0;
    double cy = height / 2.0;
    double scale = 1.0 / zoom.value;
    double centerU = center.x.value;
    double centerV = center.y.value;
    double av = a.value, bv = b.value, cv = c.value, dv = d.value;
    int iters = iterations.value;
    double amp = amplitude.value;

    return (dstX, dstY, dst, dstOffset, r) -> {
      double u0 = centerU + (dstX - cx) * scale;
      double v0 = centerV + (dstY - cy) * scale;
      double u = u0, v = v0;
      for (int i = 0; i < iters; i++) {
        double nu = sin(av * v) + cv * cos(av * u);
        double nv = sin(bv * u) + dv * cos(bv * v);
        u = nu;
        v = nv;
      }
      int srcX = (int) (dstX + (u - u0) * amp);
      int srcY = (int) (dstY + (v - v0) * amp);
      dst.put(dstOffset,     (short) TabGenerator.wrap(srcX, width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap(srcY, height));
    };
  }
}
