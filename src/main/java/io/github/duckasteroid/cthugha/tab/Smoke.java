package io.github.duckasteroid.cthugha.tab;

import io.github.duckasteroid.cthugha.params.IntegerParameter;
import java.awt.Dimension;
import java.util.Random;

public class Smoke extends TranslateTableSource {

  public IntegerParameter speed = new IntegerParameter("Speed", 30, 300); // 30 - 300
  public IntegerParameter randomness = new IntegerParameter("Randomness", 0, 100); // 0 - 100
  private int[] directionVector = new int[]{ -6, -4};

  public Smoke() {
    this(100,70);
  }

  public Smoke(int speed, int randomness) {
    this.speed.setValue( speed);
    this.randomness.setValue(randomness);
  }

  @Override
  public void randomiseParameters() {
    speed.randomise();
    randomness.randomise();
  }

  public int[] generate(Dimension size) {
    int map_x, map_y;
    int[] result = new int[size.width * size.height];
    for (int y = 0; y < size.height; y++) {
      for (int x = 0; x < size.width; x++) {
        map_x = x - (directionVector[0] + rnd.nextInt(12 * randomness.value / 100)) * speed.value / 100;
        map_y = y - (directionVector[1] + rnd.nextInt(12 * randomness.value / 100)) * speed.value / 100;

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
