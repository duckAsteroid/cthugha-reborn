package io.github.duckasteroid.cthugha.tab.generators.wave;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.sin;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Non-radial sine-wave displacement: X is offset by a sine of Y, and Y is offset by a
 * sine of X, using independent frequencies in each axis. Equal frequencies produce smooth
 * diagonal waves; different frequencies produce moiré grid patterns. Pairs well with the
 * flame blur because the wave nodes create stable "pooling" points where colour accumulates.
 */
@AutoService(TabGenerator.class)
public class SineGridWarp extends AbstractNode implements TabGenerator {

  /** Controls how many wave cycles span the screen height (affects X displacement). */
  public DoubleParameter freqX = new DoubleParameter("Freq X", 0.001, 0.1, 0.02);
  /** Controls how many wave cycles span the screen width (affects Y displacement). */
  public DoubleParameter freqY = new DoubleParameter("Freq Y", 0.001, 0.1, 0.03);
  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 80, 20);
  public DoubleParameter phaseX = new DoubleParameter("Phase X", 0, Math.PI * 2, 0);
  public DoubleParameter phaseY = new DoubleParameter("Phase Y", 0, Math.PI * 2, Math.PI / 2);

  public SineGridWarp() {
    super("Sine Grid Warp");
    initChildren(freqX, freqY, amplitude, phaseX, phaseY);
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double fx = freqX.value;
    double fy = freqY.value;
    double amp = amplitude.value;
    double px = phaseX.value;
    double py = phaseY.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      dst.put(dstOffset,     (short) TabGenerator.wrap((int)(dstX + sin(dstY * fx + px) * amp), width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap((int)(dstY + sin(dstX * fy + py) * amp), height));
    };
  }
}
