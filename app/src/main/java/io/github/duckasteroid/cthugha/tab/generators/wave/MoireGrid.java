package io.github.duckasteroid.cthugha.tab.generators.wave;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Moiré grid: overlays two independent line gratings, each at its own frequency and
 * rotation angle, as {@code cos(freq * (x*cosθ + y*sinθ))}. Unlike {@link SineGridWarp}'s
 * axis-separable X/Y waves (which only produce moiré when viewed at an angle), these two
 * gratings can be rotated to any angle relative to each other, so the beat pattern between
 * them forms genuine angled moiré fringes. Each pixel is displaced along the summed
 * gradient of both gratings, pulling content toward — or away from — the fringe bands.
 */
@AutoService(TabGenerator.class)
public class MoireGrid extends ParamNode implements TabGenerator {

  public DoubleParameter frequency1 = new DoubleParameter("Frequency 1", 0.005, 0.3, 0.05);
  public DoubleParameter angle1     = new DoubleParameter("Angle 1", -Math.PI, Math.PI, 0);
  public DoubleParameter frequency2 = new DoubleParameter("Frequency 2", 0.005, 0.3, 0.055);
  public DoubleParameter angle2     = new DoubleParameter("Angle 2", -Math.PI, Math.PI, 0.2);
  public DoubleParameter amplitude  = new DoubleParameter("Amplitude", 0, 150, 40);

  public MoireGrid() {
    super("Moire Grid");
    initChildren(frequency1, angle1, frequency2, angle2, amplitude);
    withResetAction();

    frequency1.withDescription("Spatial frequency of the first line grating.");
    angle1.withDescription("Rotation angle of the first line grating, in radians.");
    frequency2.withDescription("Spatial frequency of the second line grating. A value close to Frequency 1 produces slow, wide beat fringes.");
    angle2.withDescription("Rotation angle of the second line grating, in radians. A small offset from Angle 1 produces angled moiré fringes.");
    amplitude.withDescription("Pixel displacement scale applied along the combined gradient of both gratings.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double f1 = frequency1.value, a1 = angle1.value;
    double f2 = frequency2.value, a2 = angle2.value;
    double amp = amplitude.value;
    double cos1 = cos(a1), sin1 = sin(a1);
    double cos2 = cos(a2), sin2 = sin(a2);
    return (dstX, dstY, dst, dstOffset, r) -> {
      double proj1 = dstX * cos1 + dstY * sin1;
      double proj2 = dstX * cos2 + dstY * sin2;
      double s1 = sin(f1 * proj1);
      double s2 = sin(f2 * proj2);
      double vx = amp * (-f1 * cos1 * s1 - f2 * cos2 * s2);
      double vy = amp * (-f1 * sin1 * s1 - f2 * sin2 * s2);
      int srcX = (int) (dstX + vx);
      int srcY = (int) (dstY + vy);
      dst.put(dstOffset,     (short) TabGenerator.wrap(srcX, width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap(srcY, height));
    };
  }
}
