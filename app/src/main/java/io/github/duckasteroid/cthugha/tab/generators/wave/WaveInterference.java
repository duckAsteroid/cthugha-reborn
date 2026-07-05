package io.github.duckasteroid.cthugha.tab.generators.wave;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

/**
 * Wave interference: two or three independent radial wave sources. The displacement at
 * each pixel is the vector sum of sine-wave contributions from every source, each directed
 * radially away from its origin. When sources have the same frequency the pattern is static
 * but intricate; slight frequency differences produce slowly-evolving moiré beating.
 */
@AutoService(TabGenerator.class)
public class WaveInterference extends AbstractNode implements TabGenerator {

  public IntegerParameter numSources = new IntegerParameter("Sources", 2, 3, 2);
  public XYParam source1 = new XYParam("Source 1", 0, 1, 0.25);
  public XYParam source2 = new XYParam("Source 2", 0, 1, 0.75);
  public XYParam source3 = new XYParam("Source 3", 0, 1, 0.5, 0.15);
  public DoubleParameter frequency = new DoubleParameter("Frequency", 0.005, 0.3, 0.07);
  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 40, 8);

  public WaveInterference() {
    super("Wave Interference");
    initChildren(numSources, source1, source2, source3, frequency, amplitude);
    withResetAction();
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    int n = Math.min(numSources.value, 3);
    XYParam[] srcs = {source1, source2, source3};
    double[] sx = new double[n];
    double[] sy = new double[n];
    for (int s = 0; s < n; s++) {
      sx[s] = width  * srcs[s].x.value;
      sy[s] = height * srcs[s].y.value;
    }
    double freq = frequency.value;
    double amp = amplitude.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double totalDx = 0, totalDy = 0;
      for (int s = 0; s < n; s++) {
        double dx = dstX - sx[s];
        double dy = dstY - sy[s];
        double dist = sqrt(dx * dx + dy * dy);
        if (dist < 1.0) continue;
        double wave = sin(dist * freq) * amp / dist;
        totalDx += wave * dx;
        totalDy += wave * dy;
      }
      dst.put(dstOffset,     (short) TabGenerator.wrap((int)(dstX + totalDx), width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap((int)(dstY + totalDy), height));
    };
  }
}
