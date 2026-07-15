package io.github.duckasteroid.cthugha.tab.generators.lens;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.transform.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Classical geometric circle inversion: a pixel at distance r from the centre is mapped
 * to distance R²/r, swapping the inside and outside of the inversion circle. Produces an
 * "alien landscape" look — straight lines become circles, the centre explodes outward,
 * and the area beyond the circle folds inward. Visually unlike any other transform here.
 */
@AutoService(TabGenerator.class)
public class CircleInversion extends ParamNode implements TabGenerator {

  public XYParam center = new XYParam("Center", 0, 1, 0.5).withPadControl();
  /** Inversion radius as a fraction of the shorter screen dimension. */
  public DoubleParameter radius = new DoubleParameter("Radius", 0.05, 1.5, 0.4);

  public CircleInversion() {
    super("Circle Inversion");
    initChildren(center, radius);
    withResetAction();

    center.withDescription("Centre of the inversion circle, as a fraction of screen width/height.");
    radius.withDescription("Inversion radius as a fraction of the shorter screen dimension.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double cx = width  * center.x.value;
    double cy = height * center.y.value;
    double R  = Math.min(width, height) * radius.value;
    double R2 = R * R;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double r2 = dx * dx + dy * dy;
      int srcX, srcY;
      if (r2 < 1.0) {
        srcX = (int) cx;
        srcY = (int) cy;
      } else {
        double scale = R2 / r2;
        srcX = (int)(cx + dx * scale);
        srcY = (int)(cy + dy * scale);
      }
      dst.put(dstOffset,     (short) TabGenerator.clamp(srcX, 0, width  - 1));
      dst.put(dstOffset + 1, (short) TabGenerator.clamp(srcY, 0, height - 1));
    };
  }
}
