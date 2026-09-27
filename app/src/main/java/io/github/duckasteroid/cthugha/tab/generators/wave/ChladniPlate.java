package io.github.duckasteroid.cthugha.tab.generators.wave;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.PI;
import static java.lang.Math.max;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

/**
 * Chladni plate: treats {@code f(x,y) = cos(mπx)cos(nπy) - cos(nπx)cos(mπy)} — the classic
 * standing-wave function whose zero-crossings are the nodal lines a vibrating plate's sand
 * collects along — as a stream function rather than a height field. The displacement at each
 * pixel is the field's rotated gradient (its "curl flow"), which is divergence-free and so
 * never pushes content directly across a nodal line; instead pixels are swept along the
 * contours of {@code f}, circulating within the cells the nodal lines divide the plate into.
 * The result is a lattice of independently swirling cells bounded by the sharp Chladni figure.
 */
@AutoService(TabGenerator.class)
public class ChladniPlate extends ParamNode implements TabGenerator {

  public IntegerParameter modeM = new IntegerParameter("Mode M", 1, 12, 3);
  public IntegerParameter modeN = new IntegerParameter("Mode N", 1, 12, 2);
  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 200, 60);

  public ChladniPlate() {
    super("Chladni Plate");
    initChildren(modeM, modeN, amplitude);
    withResetAction();

    modeM.withDescription("First vibration mode number; together with Mode N sets how many nodal lines divide the plate and how many swirl cells result.");
    modeN.withDescription("Second vibration mode number. Equal to Mode M produces a symmetric grid; a different value produces an asymmetric figure.");
    amplitude.withDescription("Maximum pixel displacement applied along the flow that circulates within each cell bounded by the nodal lines.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double m = modeM.value;
    double n = modeN.value;
    double amp = amplitude.value;
    // Normalises the analytic gradient (magnitude ~ max(m,n)*PI) so Amplitude behaves
    // consistently regardless of which modes are selected.
    double norm = amp / (PI * max(m, n));
    return (dstX, dstY, dst, dstOffset, r) -> {
      double x = dstX / (double) width;
      double y = dstY / (double) height;
      double mpix = m * PI * x, npiy = n * PI * y;
      double npix = n * PI * x, mpiy = m * PI * y;
      double dfdx = -m * PI * sin(mpix) * cos(npiy) + n * PI * sin(npix) * cos(mpiy);
      double dfdy = -n * PI * cos(mpix) * sin(npiy) + m * PI * cos(npix) * sin(mpiy);
      // Rotate the gradient 90 degrees to get a divergence-free flow along f's contours.
      double vx = dfdy * norm;
      double vy = -dfdx * norm;
      int srcX = (int) (dstX + vx);
      int srcY = (int) (dstY + vy);
      dst.put(dstOffset,     (short) TabGenerator.wrap(srcX, width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap(srcY, height));
    };
  }
}
