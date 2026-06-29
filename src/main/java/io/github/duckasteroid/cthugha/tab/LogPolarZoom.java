package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Log-polar zoom (Droste effect): converts each pixel to log-polar coordinates
 * (u = log r, v = θ), applies a linear translation in that space, then converts back.
 * A translation along u zooms continuously toward or away from the centre; a translation
 * along v adds rotation. Combined they produce an infinite-zoom spiral where the image
 * appears to endlessly wind inward — the Droste / Escher staircase effect.
 *
 * zoom > 0: content from outside spirals inward (zoom-in feel).
 * zoom < 0: content from inside expands outward.
 * rotation shifts which angular sector is sampled, adding a spin to the zoom.
 */
public class LogPolarZoom extends AbstractNode implements TranslateTableSource {

  public XYParam center = new XYParam("Center", 0, 1, 0.5);
  /** Translation in log(r) space. Small values (0.05–0.3) give a gentle zoom. */
  public DoubleParameter zoom     = new DoubleParameter("Zoom",     -1.0, 1.0,  0.12);
  /** Translation in θ space (radians). Adds angular spin to the zoom. */
  public DoubleParameter rotation = new DoubleParameter("Rotation", -Math.PI, Math.PI, 0.05);

  public LogPolarZoom() {
    super("Log-Polar Zoom");
    initChildren(center, zoom, rotation);
  }

  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    double cx = width  * center.x.value;
    double cy = height * center.y.value;
    double zm = zoom.value;
    double rot = rotation.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - cx;
      double dy = dstY - cy;
      double radius = sqrt(dx * dx + dy * dy);
      int srcX, srcY;
      if (radius < 1.0) {
        srcX = (int) cx;
        srcY = (int) cy;
      } else {
        double rSrc = exp(log(radius) + zm);
        double v = atan2(dy, dx) + rot;
        srcX = (int)(cx + rSrc * cos(v));
        srcY = (int)(cy + rSrc * sin(v));
      }
      dst.put(dstOffset,     (short) TranslateTableSource.clamp(srcX, 0, width  - 1));
      dst.put(dstOffset + 1, (short) TranslateTableSource.clamp(srcY, 0, height - 1));
    };
  }
}
