package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.DoubleParameter;

public class LinearSlider extends AbstractNode implements TranslateTableSource {
  public DoubleParameter focalPoint = new DoubleParameter("Relative focus", 0, 1, 0.3);
  public DoubleParameter speed = new DoubleParameter("Speed", -100, 100, 10);
  // true = focal axis is horizontal (a row); pixels slide left/right.
  // false = focal axis is vertical (a column); pixels slide up/down.
  public BooleanParameter horizontal = new BooleanParameter("Horizontal", true);

  public LinearSlider() {
    initChildren(focalPoint, speed, horizontal);
  }

  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    boolean horiz = horizontal.value;
    double spd = speed.value;
    double fp = focalPoint.value;
    if (horiz) {
      int focus = Math.max((int)(height * fp), 1);
      return (dstX, dstY, dst, dstOffset, r) -> {
        double rowSpeed = (double) Math.abs(dstY - focus) / focus * spd;
        dst.put(dstOffset,     (short) TranslateTableSource.wrap((int)(dstX - rowSpeed), width));
        dst.put(dstOffset + 1, (short) dstY);
      };
    } else {
      int focus = Math.max((int)(width * fp), 1);
      return (dstX, dstY, dst, dstOffset, r) -> {
        double colSpeed = (double) Math.abs(dstX - focus) / focus * spd;
        dst.put(dstOffset,     (short) dstX);
        dst.put(dstOffset + 1, (short) TranslateTableSource.wrap((int)(dstY - colSpeed), height));
      };
    }
  }
}
