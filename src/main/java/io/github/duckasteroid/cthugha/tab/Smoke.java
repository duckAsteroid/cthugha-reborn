package io.github.duckasteroid.cthugha.tab;

import java.awt.Dimension;
import java.util.Random;

public class Smoke extends TranslateTableSource {

  private int speed; // 30 - 300
  private int randomness; // 0 - 100

  public Smoke() {
    this(100,70);
  }

  public Smoke(int speed, int randomness) {
    this.speed = speed;
    this.randomness = randomness;
  }

  @Override
  public void randomiseParameters() {
    speed = rnd.nextInt(30, 300);
    randomness = rnd.nextInt(100);
  }

  public int[] generate(Dimension size) {
    int map_x, map_y;
    int[] result = new int[size.width * size.height];
    for (int y = 0; y < size.height; y++) {
      for (int x = 0; x < size.width; x++) {
        map_x = x - (5 + rnd.nextInt(12 * randomness / 100)) * speed / 100;
        map_y = y - (5 + rnd.nextInt(12 * randomness / 100)) * speed / 100;

        if (map_y >= size.height || map_y < 0  ||
          map_x >= size.width  || map_x < 0 ) {
          map_x = 0;
          map_y = 0;
        }

        result[x + y * size.width] = map_y * size.width + map_x;

      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "Smoke{" +
      "speed=" + speed +
      ", randomness=" + randomness +
      '}';
  }
}
