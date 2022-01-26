package io.github.duckasteroid.cthugha.tab;

import static java.lang.Math.abs;

import java.awt.Dimension;
import java.util.Random;

public class Space implements TranslateTableSource {
  private Random rnd = new Random();
  private boolean reverse = false;
  private int Randomness = 70;
  private long speed = 100;

  @Override
  public int[] generate(Dimension size) {
    int map_x, map_y;
    int[] result = new int[size.width * size.height];

    for (int y = 0; y < size.height; y++) {
      for (int x = 0; x < size.width; x++) {
        int dx = x - (size.width / 2);
        int dy = y - (size.height / 2);

        if (!reverse && abs(dx) < 30 && abs(dy) < 20 &&
          Random(abs(dx) + abs(dy)) < 4) {
          map_x = Random(size.width);
          map_y = Random(size.height);
        } else {
          int speedFactor;
          long sp;

          if (Randomness == 0)
            sp = speed;
          else {
            speedFactor = Random(Randomness + 1) - Randomness / 3;
            sp = speed * (100L + speedFactor) / 100L;
          }

          if (reverse)
            sp = (-sp);

          map_x = (int) (x - (dx * sp) / 700);
          map_y = (int) (y - (dy * sp) / 700);
        }

        if (map_y >= size.height || map_y < 0 ||
          map_x >= size.width || map_x < 0) {
          map_x = 0;
          map_y = 0;
        }

        result[x + y * size.width] = map_y * size.width + map_x;

      }
    }
    return result;
  }

  private int Random(int range) {
    if (range > 0) {
      return rnd.nextInt(range);
    }
    return 0;
  }
}
