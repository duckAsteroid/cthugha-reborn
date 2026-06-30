package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Radial sine-wave ripple from a single source point. Each pixel is displaced along the
 * vector from the source by sin(dist * frequency + phase) * amplitude, producing a
 * concentric ring distortion like a stone dropped in water.
 */
public class Ripple extends AbstractNode implements TranslateTableSource {

  public XYParam source = new XYParam("Source", 0, 1, 0.5);
  public DoubleParameter frequency = new DoubleParameter("Frequency", 0.01, 0.5, 0.1);
  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 50, 10);
  public DoubleParameter phase = new DoubleParameter("Phase", 0, 2 * PI, 0);

  public Ripple() {
    super("Ripple");
    initChildren(source, frequency, amplitude, phase);
  }

  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    double sx = width  * source.x.value;
    double sy = height * source.y.value;
    double freq = frequency.value;
    double amp = amplitude.value;
    double ph = phase.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double dx = dstX - sx;
      double dy = dstY - sy;
      double dist = sqrt(dx * dx + dy * dy);
      int srcX, srcY;
      if (dist < 1.0) {
        srcX = dstX;
        srcY = dstY;
      } else {
        double displacement = sin(dist * freq + ph) * amp;
        srcX = (int)(dstX + displacement * dx / dist);
        srcY = (int)(dstY + displacement * dy / dist);
      }
      dst.put(dstOffset,     (short) TranslateTableSource.wrap(srcX, width));
      dst.put(dstOffset + 1, (short) TranslateTableSource.wrap(srcY, height));
    };
  }
}
