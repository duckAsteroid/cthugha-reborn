package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;

public class Hurricane extends TranslateTableSource {

  private int Randomness = 80;
  private long speed = 30;
  private boolean slowY = false;
  private boolean slowX = false;
  private boolean reverse = false;
  private double xCenter = .3;
  private double yCenter = .8;

  @Override
  public void randomiseParameters() {
    Randomness = Random(150);
    speed = Random(100);
    slowX = rnd.nextBoolean();
    slowY = rnd.nextBoolean();
    reverse = rnd.nextBoolean();
    xCenter = rnd.nextDouble();
    yCenter = rnd.nextDouble();
  }

  @Override
  public int[] generate(Dimension size) {
    int[] result = new int[size.width * size.height];
    int xCenter = (int)(size.width * this.xCenter);
    int yCenter = (int)(size.height * this.yCenter);
    for (int y = 0; y < size.height; y++) {
      for (int x = 0; x < size.width; x++) {
        int  speedFactor;
        long sp;

        if (Randomness == 0)
          sp = speed;
        else {
          speedFactor = Random(Randomness + 1) - Randomness / 3;
          sp = speed * (100L + speedFactor) / 100L;
        }

        int dx = x - xCenter;
        int dy = y - yCenter;


        if (slowX || slowY) {
          long  dSquared = (long)dx*dx + (long)dy*dy + 1;

          if (slowY)
            dx = (int)(dx * 2500L / dSquared);
          if (slowX)
            dy = (int)(dy * 2500L / dSquared);
        }

        if (reverse)
          sp = (-sp);

        int map_x = (int)(x + (dy * sp) / 700);
        int map_y = (int)(y - (dx * sp) / 700);

        while (map_y < 0)
          map_y += size.height;
        while (map_x < 0)
          map_x += size.width;
        map_y = map_y % size.height;
        map_x = map_x % size.width;

        result[x + (size.width * y)] = map_y * size.width + map_x;
      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "Hurricane{" +
      "Randomness=" + Randomness +
      ", speed=" + speed +
      ", slowY=" + slowY +
      ", slowX=" + slowX +
      ", reverse=" + reverse +
      ", xCenter=" + xCenter +
      ", yCenter=" + yCenter +
      '}';
  }
}
