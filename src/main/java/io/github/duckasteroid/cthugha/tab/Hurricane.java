package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.AbstractNode;
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
  public int[] generate(int width, int height) {
    int[] result = new int[width * height];
    int xCenter = (int)(width * this.center.x.value);
    int yCenter = (int)(height * this.center.y.value);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int  speedFactor;
        long sp;

        if (Randomness.value == 0)
          sp = speed.value;
        else {
          speedFactor = Random((Randomness.value + 1) - Randomness.value / 3);
          sp = speed.value * (100L + speedFactor) / 100L;
        }

        int dx = x - xCenter;
        int dy = y - yCenter;


        if (slowX.value || slowY.value) {
          long  dSquared = (long)dx*dx + (long)dy*dy + 1;

          if (slowY.value)
            dx = (int)(dx * 2500L / dSquared);
          if (slowX.value)
            dy = (int)(dy * 2500L / dSquared);
        }

        if (reverse.value)
          sp = (-sp);

        int map_x = (int)(x + (dy * sp) / 700);
        int map_y = (int)(y - (dx * sp) / 700);

        while (map_y < 0)
          map_y += height;
        while (map_x < 0)
          map_x += width;
        map_y = map_y % height;
        map_x = map_x % width;

        result[x + (width * y)] = map_y * width + map_x;
      }
    }
    return result;
  }
}
