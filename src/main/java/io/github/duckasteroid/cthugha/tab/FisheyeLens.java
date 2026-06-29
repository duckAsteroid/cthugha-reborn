package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Barrel / pincushion lens distortion. Coordinates are normalised to [-1, 1], a cubic
 * radial distortion is applied (r_src = r_dst + strength * r_dst³), then scaled back.
 *
 * Positive strength = barrel: centre is magnified, edges compressed — classic wide-angle
 * fisheye look.
 * Negative strength = pincushion: centre compressed, edges stretched outward — telephoto
 * distortion.
 *
 * The distortion boundary is the smaller screen dimension, keeping the effect symmetric
 * regardless of aspect ratio.
 */
public class FisheyeLens extends AbstractNode implements TranslateTableSource {

  public XYParam center = new XYParam("Center", 0, 1, 0.5);
  /** Distortion strength. Positive = barrel (fisheye). Negative = pincushion. */
  public DoubleParameter strength = new DoubleParameter("Strength", -3.0, 3.0, 1.2);

  public FisheyeLens() {
    super("Fisheye Lens");
    initChildren(center, strength);
  }

  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    double cx = width  * center.x.value;
    double cy = height * center.y.value;
    double norm = Math.min(width, height) / 2.0;
    double str = strength.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = (dstX - cx) / norm;
      double dy = (dstY - cy) / norm;
      double radius = sqrt(dx * dx + dy * dy);
      int srcX, srcY;
      if (radius < 1e-6) {
        srcX = dstX;
        srcY = dstY;
      } else {
        double rSrc = radius + str * radius * radius * radius;
        double scale = rSrc / radius;
        srcX = (int)(cx + dx * scale * norm);
        srcY = (int)(cy + dy * scale * norm);
      }
      dst.put(dstOffset,     (short) TranslateTableSource.clamp(srcX, 0, width  - 1));
      dst.put(dstOffset + 1, (short) TranslateTableSource.clamp(srcY, 0, height - 1));
    };
  }
}
