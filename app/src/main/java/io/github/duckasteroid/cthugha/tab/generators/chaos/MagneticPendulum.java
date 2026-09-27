package io.github.duckasteroid.cthugha.tab.generators.chaos;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.sqrt;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.transform.XYParam;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

/**
 * Simulates the classic "magnetic pendulum" toy: each destination pixel is treated as the
 * release point of a damped pendulum bob hanging above three magnets, and is iterated a fixed
 * number of steps under an inverse-cube pull toward each magnet plus a spring restoring it
 * toward its release point. The bob's final resting position (relative to where it started)
 * becomes the pixel's displacement.
 *
 * Inside a basin the flow is smooth, pulling content toward whichever magnet dominates; at
 * basin boundaries the outcome is chaotically sensitive to the starting pixel, so neighbouring
 * pixels can settle near different magnets entirely — producing the fractal, static-like
 * texture the real toy's basin-boundary maps are known for, laid over smoothly flowing basins.
 */
@AutoService(TabGenerator.class)
public class MagneticPendulum extends ParamNode implements TabGenerator {

  public XYParam magnet1 = new XYParam("Magnet 1", 0, 1, 0.5, 0.15).withPadControl();
  public XYParam magnet2 = new XYParam("Magnet 2", 0, 1, 0.2, 0.8).withPadControl();
  public XYParam magnet3 = new XYParam("Magnet 3", 0, 1, 0.8, 0.8).withPadControl();
  public DoubleParameter pull = new DoubleParameter("Magnet Pull", 0, 4_000_000, 800_000);
  public DoubleParameter spring = new DoubleParameter("Restoring Force", 0, 0.2, 0.02);
  public DoubleParameter damping = new DoubleParameter("Damping", 0.5, 0.999, 0.92);
  public DoubleParameter softening = new DoubleParameter("Softening", 10, 5000, 400);
  public IntegerParameter iterations = new IntegerParameter("Iterations", 5, 60, 25);

  public MagneticPendulum() {
    super("Magnetic Pendulum");
    initChildren(magnet1, magnet2, magnet3, pull, spring, damping, softening, iterations);
    withResetAction();

    magnet1.withDescription("Position of the first magnet, as a fraction of screen width/height.");
    magnet2.withDescription("Position of the second magnet, as a fraction of screen width/height.");
    magnet3.withDescription("Position of the third magnet, as a fraction of screen width/height.");
    pull.withDescription("Strength of each magnet's inverse-cube pull on the simulated pendulum bob.");
    spring.withDescription("Restoring force pulling the bob back toward its release point, like the pendulum's own gravity.");
    damping.withDescription("Per-step velocity damping. Lower values settle faster but with less chaotic overshoot near basin boundaries.");
    softening.withDescription("Softens the singularity directly under each magnet, preventing extreme displacement there.");
    iterations.withDescription("Number of simulation steps run per pixel. More iterations let the bob fully settle, sharpening basin boundaries at the cost of generation time.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double cx = width / 2.0;
    double cy = height / 2.0;
    XYParam[] magnets = {magnet1, magnet2, magnet3};
    double[] mx = new double[3];
    double[] my = new double[3];
    for (int i = 0; i < 3; i++) {
      mx[i] = width  * magnets[i].x.value;
      my[i] = height * magnets[i].y.value;
    }
    double k = pull.value;
    double springK = spring.value;
    double damp = damping.value;
    double soft = softening.value;
    int steps = iterations.value;

    return (dstX, dstY, dst, dstOffset, r) -> {
      double px = dstX - cx;
      double py = dstY - cy;
      double px0 = px, py0 = py;
      double vx = 0, vy = 0;
      for (int s = 0; s < steps; s++) {
        double ax = -springK * (px - px0);
        double ay = -springK * (py - py0);
        for (int i = 0; i < 3; i++) {
          double dx = mx[i] - cx - px;
          double dy = my[i] - cy - py;
          double d2 = dx * dx + dy * dy + soft;
          double f = k / (d2 * sqrt(d2));
          ax += f * dx;
          ay += f * dy;
        }
        vx = (vx + ax) * damp;
        vy = (vy + ay) * damp;
        px += vx;
        py += vy;
      }
      int srcX = (int) (dstX + (px - px0));
      int srcY = (int) (dstY + (py - py0));
      dst.put(dstOffset,     (short) TabGenerator.wrap(srcX, width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap(srcY, height));
    };
  }
}
