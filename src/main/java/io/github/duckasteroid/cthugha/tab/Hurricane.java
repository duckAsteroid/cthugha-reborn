package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractNode;
import java.nio.ShortBuffer;
import java.util.Random;
import io.github.duckasteroid.cthugha.params.XYParam;
import io.github.duckasteroid.cthugha.params.values.BooleanParameter;
import io.github.duckasteroid.cthugha.params.values.IntegerParameter;
import io.github.duckasteroid.cthugha.params.values.LongParameter;

public class Hurricane extends AbstractNode implements TranslateTableSource {

  public IntegerParameter Randomness = new IntegerParameter("Randomness", 0, 150, 80);
  private LongParameter speed = new LongParameter("Speed", 0, 100, 30);
  private BooleanParameter slowY = new BooleanParameter("SlowY");
  private BooleanParameter slowX = new BooleanParameter("SlowX");;
  private BooleanParameter reverse = new BooleanParameter("revers");;
  private XYParam center = new XYParam("Center", 0, 1, 0.5);

  public Hurricane() {
    super("Hurricane");
    initChildren(Randomness, speed, slowY, slowX, reverse, center);
  }
  @Override
  public PixelMapper generate(int width, int height, Random rng) {
    int xCenter = (int)(width  * this.center.x.value);
    int yCenter = (int)(height * this.center.y.value);
    int rand = Randomness.value;
    long spd = speed.value;
    boolean sx = slowX.value;
    boolean sy = slowY.value;
    boolean rev = reverse.value;
    return (dstX, dstY, dst, dstOffset, r) -> {
      long sp;
      if (rand == 0) {
        sp = spd;
      } else {
        int bound = (rand + 1) - rand / 3;
        int speedFactor = bound > 0 ? r.nextInt(bound) : 0;
        sp = spd * (100L + speedFactor) / 100L;
      }
      int dx = dstX - xCenter;
      int dy = dstY - yCenter;
      if (sx || sy) {
        long dSquared = (long)dx*dx + (long)dy*dy + 1;
        if (sy) dx = (int)(dx * 2500L / dSquared);
        if (sx) dy = (int)(dy * 2500L / dSquared);
      }
      if (rev) sp = -sp;
      int map_x = (int)(dstX + (dy * sp) / 700);
      int map_y = (int)(dstY - (dx * sp) / 700);
      while (map_y < 0) map_y += height;
      while (map_x < 0) map_x += width;
      dst.put(dstOffset,     (short)(map_x % width));
      dst.put(dstOffset + 1, (short)(map_y % height));
    };
  }
}
