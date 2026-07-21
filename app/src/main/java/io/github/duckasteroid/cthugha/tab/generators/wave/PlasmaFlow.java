package io.github.duckasteroid.cthugha.tab.generators.wave;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.sin;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

/**
 * Organic turbulence from summed non-radial sine waves: unlike {@link WaveInterference}
 * (which radiates from fixed source points) this warp has no centre — the displacement
 * field tiles smoothly and varies everywhere. Produces a lava-lamp or slow-liquid flow
 * look. Two frequency parameters let you dial from smooth swells (low freq) to tight
 * rippling turbulence (high freq). The phase offsets between axes prevent the grid symmetry
 * that a single-frequency field would show.
 */
@AutoService(TabGenerator.class)
public class PlasmaFlow extends ParamNode implements TabGenerator {

  /** Primary spatial frequency — controls coarseness of the flow pattern. */
  public DoubleParameter freqA = new DoubleParameter("Freq A", 0.001, 0.08, 0.015);
  /** Secondary frequency — cross-couples the axes and breaks grid symmetry. */
  public DoubleParameter freqB = new DoubleParameter("Freq B", 0.001, 0.08, 0.022);
  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 60, 18);

  public PlasmaFlow() {
    super("Plasma Flow");
    initChildren(freqA, freqB, amplitude);
    withResetAction();

    freqA.withDescription("Primary spatial frequency of the flow field. Lower values produce broad, smooth swells; higher values produce tight, rippling turbulence.");
    freqB.withDescription("Secondary spatial frequency that cross-couples the X and Y axes, breaking up the grid symmetry a single frequency would produce.");
    amplitude.withDescription("Maximum pixel displacement contributed by the flow field.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    double fa = freqA.value;
    double fb = freqB.value;
    double amp = amplitude.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      double srcX = dstX + (sin(dstX * fa + dstY * fb) + 0.5 * sin(dstY * fa)) * amp;
      double srcY = dstY + (sin(dstY * fa - dstX * fb) + 0.5 * sin(dstX * fa)) * amp;
      dst.put(dstOffset,     (short) TabGenerator.wrap((int) srcX, width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap((int) srcY, height));
    };
  }
}
