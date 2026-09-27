package io.github.duckasteroid.cthugha.tab.generators.wave;

import io.github.duckasteroid.cthugha.tab.*;
import com.google.auto.service.AutoService;

import static java.lang.Math.floorDiv;
import static java.lang.Math.floorMod;
import static java.lang.Math.sin;
import static java.lang.Math.PI;

import io.github.duckasteroid.cthugha.params.ParamNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;

/**
 * Basketweave: divides the screen into a checkerboard of square strands. In one strand
 * colour the pixel is displaced by a sine bulge running across the strand's width (as if a
 * vertical thread crosses over its neighbours); in the other, the same bulge runs across
 * the strand's height (a horizontal thread crossing over). The alternation produces the
 * over/under interlacing of a woven fabric or basket.
 */
@AutoService(TabGenerator.class)
public class Basketweave extends ParamNode implements TabGenerator {

  public IntegerParameter strandSize = new IntegerParameter("Strand Size", 8, 160, 40);
  public DoubleParameter amplitude = new DoubleParameter("Amplitude", 0, 60, 14);

  public Basketweave() {
    super("Basketweave");
    initChildren(strandSize, amplitude);
    withResetAction();

    strandSize.withDescription("Width and height, in pixels, of each woven strand.");
    amplitude.withDescription("Maximum pixel displacement produced by a strand's over/under bulge.");
  }

  @Override
  public TabMapping generate(int width, int height, Random rng) {
    int size = strandSize.value;
    double amp = amplitude.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      int col = floorDiv(dstX, size);
      int row = floorDiv(dstY, size);
      double phaseX = floorMod(dstX, size) / (double) size;
      double phaseY = floorMod(dstY, size) / (double) size;
      boolean verticalOnTop = ((col + row) & 1) == 0;
      int srcX, srcY;
      if (verticalOnTop) {
        srcX = (int) (dstX + amp * sin(2 * PI * phaseY));
        srcY = dstY;
      } else {
        srcX = dstX;
        srcY = (int) (dstY + amp * sin(2 * PI * phaseX));
      }
      dst.put(dstOffset,     (short) TabGenerator.wrap(srcX, width));
      dst.put(dstOffset + 1, (short) TabGenerator.wrap(srcY, height));
    };
  }
}
